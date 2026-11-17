package de.danoeh.antennapod.adskip.detector;

import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static de.danoeh.antennapod.adskip.detector.DownloadDiffAdDetector.CompareMode.ALWAYS;
import static de.danoeh.antennapod.adskip.detector.DownloadDiffAdDetector.CompareMode.WHEN_UNEXPLAINED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DownloadDiffAdDetectorTest {
    private static final long TOLERANCE_MS = 30;

    @Test
    public void findsInsertedAdsByComparingDownloads() throws Exception {
        // given
        SyntheticMp3 played = new SyntheticMp3().frames("preroll-a", 0, 300)
                .frames("show", 0, 2000).frames("midroll-a", 0, 400).frames("show", 2000, 5000);
        SyntheticMp3 second = new SyntheticMp3().frames("preroll-b", 0, 250)
                .frames("show", 0, 2000).frames("midroll-b", 0, 500).frames("show", 2000, 5000);
        SyntheticMp3 third = new SyntheticMp3()
                .frames("show", 0, 2000).frames("midroll-c", 0, 300).frames("show", 2000, 5000);
        DownloadDiffAdDetector detector = new DownloadDiffAdDetector(new InMemoryKnownAdLibrary(), false, ALWAYS, 2, 2);

        // when
        DetectionResult result = detector.detect(episode(played, 0, second, third));

        // then
        assertEquals(2, result.copiesCompared);
        assertEquals(2, result.spans.size());
        assertSpan(result.spans.get(0), 0, 300);
        assertSpan(result.spans.get(1), 2300, 2700);
    }

    @Test
    public void countsFramesAsShowOnlyWhenEveryOtherCopyHasThem() throws Exception {
        // given
        SyntheticMp3 played = new SyntheticMp3()
                .frames("show", 0, 2000).frames("campaign", 0, 800).frames("show", 2000, 4000);
        SyntheticMp3 sameAdSameSlot = new SyntheticMp3()
                .frames("show", 0, 2000).frames("campaign", 0, 800).frames("show", 2000, 4000);
        SyntheticMp3 otherAd = new SyntheticMp3()
                .frames("show", 0, 2000).frames("other", 0, 600).frames("show", 2000, 4000);
        DownloadDiffAdDetector detector = new DownloadDiffAdDetector(new InMemoryKnownAdLibrary(), false, ALWAYS, 2, 2);

        // when
        DetectionResult result = detector.detect(episode(played, 0, sameAdSameSlot, otherAd));

        // then
        assertEquals(1, result.spans.size());
        assertSpan(result.spans.get(0), 2000, 2800);
    }

    @Test
    public void fetchesMoreCopiesUntilTheFeedDurationIsExplained() throws Exception {
        // given
        SyntheticMp3 played = new SyntheticMp3()
                .frames("show", 0, 2000).frames("campaign", 0, 800).frames("show", 2000, 4000);
        SyntheticMp3 sameAd = new SyntheticMp3()
                .frames("show", 0, 2000).frames("campaign", 0, 800).frames("show", 2000, 4000);
        SyntheticMp3 otherAd = new SyntheticMp3()
                .frames("show", 0, 2000).frames("other", 0, 600).frames("show", 2000, 4000);
        DownloadDiffAdDetector detector = new DownloadDiffAdDetector(new InMemoryKnownAdLibrary(), false, ALWAYS, 2, 5);

        // when
        DetectionResult result = detector.detect(
                episode(played, SyntheticMp3.frameMs(4000), sameAd, sameAd, otherAd, otherAd, otherAd));

        // then
        assertEquals(5, result.copiesCompared);
        assertEquals(1, result.spans.size());
        assertSpan(result.spans.get(0), 2000, 2800);
    }

    @Test
    public void downloadsTheCopiesOfABatchAtTheSameTime() throws Exception {
        // given
        SyntheticMp3 played = new SyntheticMp3().frames("ad", 0, 300).frames("show", 0, 3000);
        SyntheticMp3 copy = new SyntheticMp3().frames("show", 0, 3000);
        CountDownLatch allCopiesRequested = new CountDownLatch(2);
        EpisodeAudio episode = new EpisodeAudio(played::stream, index -> {
            allCopiesRequested.countDown();
            try {
                if (!allCopiesRequested.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Copy " + index + " was not downloaded together with the other");
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            return copy.stream();
        }, 0);
        DownloadDiffAdDetector detector = new DownloadDiffAdDetector(new InMemoryKnownAdLibrary(), false, ALWAYS, 2, 2);

        // when
        DetectionResult result = detector.detect(episode);

        // then
        assertEquals(2, result.copiesCompared);
        assertSpan(result.spans.get(0), 0, 300);
    }

    @Test
    public void findsAdClipsTaggedInTheFileWithoutExtraDownloads() throws Exception {
        // given
        SyntheticMp3 played = new SyntheticMp3()
                .frames("show", 0, 1000).clipTagFrame(299).frames("ad", 0, 299).frames("show", 1000, 3000);
        DownloadDiffAdDetector detector = new DownloadDiffAdDetector(
                new InMemoryKnownAdLibrary(), false, WHEN_UNEXPLAINED, 2, 5);

        // when
        DetectionResult result = detector.detect(episode(played, SyntheticMp3.frameMs(3000), null, null));

        // then
        assertEquals(0, result.copiesCompared);
        assertEquals(AdSpan.Source.CLIP_TAG, result.spans.get(0).source);
        assertSpan(result.spans.get(0), 1000, 1300);
    }

    @Test
    public void findsAdBreaksListedByTheHostWithoutExtraDownloads() throws Exception {
        // given
        SyntheticMp3 played = new SyntheticMp3()
                .frames("show", 0, 1000).frames("ad", 0, 300).frames("show", 1000, 3000);
        long firstByte = 1000L * SyntheticMp3.FRAME_BYTES;
        long lastByte = 1300L * SyntheticMp3.FRAME_BYTES - 1;
        String header = "#" + lastByte + "#mid#1#" + firstByte + "##true#true,#" + (firstByte - 1) + "#mid#2#"
                + firstByte + "##true#true@episode#128000#0";
        DownloadDiffAdDetector detector = new DownloadDiffAdDetector(
                new InMemoryKnownAdLibrary(), false, WHEN_UNEXPLAINED, 2, 5);

        // when
        DetectionResult result = detector.detect(new EpisodeAudio(played::stream, index -> {
            throw new AssertionError("No extra copy must be downloaded");
        }, SyntheticMp3.frameMs(3000), header));

        // then
        assertEquals(1, result.spans.size());
        assertEquals(AdSpan.Source.HOST_AD_BREAKS, result.spans.get(0).source);
        assertSpan(result.spans.get(0), 1000, 1300);
    }

    @Test
    public void findsNothingWhenAllCopiesAreIdentical() throws Exception {
        // given
        SyntheticMp3 played = new SyntheticMp3().frames("show", 0, 3000);
        SyntheticMp3 copy = new SyntheticMp3().frames("show", 0, 3000);
        DownloadDiffAdDetector detector = new DownloadDiffAdDetector(new InMemoryKnownAdLibrary(), false, ALWAYS, 2, 2);

        // when
        DetectionResult result = detector.detect(episode(played, 0, copy, copy));

        // then
        assertEquals(2, result.copiesCompared);
        assertTrue(result.spans.isEmpty());
    }

    @Test
    public void learnsAdClipsAndFindsThemInTheNextEpisodeWithoutExtraDownloads() throws Exception {
        // given
        InMemoryKnownAdLibrary library = new InMemoryKnownAdLibrary();
        new DownloadDiffAdDetector(library, true, WHEN_UNEXPLAINED, 2, 2).detect(episode(
                new SyntheticMp3().frames("campaign", 0, 600).frames("show-1", 0, 3000),
                0,
                new SyntheticMp3().frames("other", 0, 500).frames("show-1", 0, 3000),
                new SyntheticMp3().frames("show-1", 0, 3000)));
        SyntheticMp3 nextEpisode = new SyntheticMp3()
                .frames("show-2", 0, 1500).frames("campaign", 0, 600).frames("show-2", 1500, 4000);

        // when
        DetectionResult result = new DownloadDiffAdDetector(library, true, WHEN_UNEXPLAINED, 2, 2)
                .detect(episode(nextEpisode, SyntheticMp3.frameMs(4000), null, null));

        // then
        assertEquals(0, result.copiesCompared);
        assertEquals(1, result.spans.size());
        assertEquals(AdSpan.Source.KNOWN_AD, result.spans.get(0).source);
        assertSpan(result.spans.get(0), 1500, 2100);
    }

    private static EpisodeAudio episode(SyntheticMp3 played, long declaredDurationMs, SyntheticMp3... copies) {
        return new EpisodeAudio(played::stream, index -> {
            if (copies[index] == null) {
                throw new AssertionError("Extra copy " + index + " must not be downloaded");
            }
            return copies[index].stream();
        }, declaredDurationMs);
    }

    private static void assertSpan(AdSpan span, int startFrame, int endFrame) {
        List.of(new long[]{SyntheticMp3.frameMs(startFrame), span.startMs},
                new long[]{SyntheticMp3.frameMs(endFrame), span.endMs})
                .forEach(pair -> assertEquals(pair[0], pair[1], TOLERANCE_MS));
    }
}
