package de.danoeh.antennapod.adskip.detector;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class DownloadDiffAdDetector implements AdDetector {
    public static final long EXPLAINED_TOLERANCE_MS = 4000;
    private static final long MERGE_GAP_MS = 500;

    private final KnownAdLibrary library;
    private final boolean useKnownAds;
    private final boolean compareDownloads;
    private final int extraCopies;

    public DownloadDiffAdDetector(KnownAdLibrary library, boolean useKnownAds, boolean compareDownloads,
                                  int extraCopies) {
        this.library = library;
        this.useKnownAds = useKnownAds;
        this.compareDownloads = compareDownloads;
        this.extraCopies = extraCopies;
    }

    @Override
    public DetectionResult detect(EpisodeAudio episode) throws IOException {
        FrameHashes played = hash(episode.playedFile.open());
        List<KnownAd> knownAds = library.all();
        List<AdSpan> knownSpans = useKnownAds ? findKnownAds(played, knownAds) : List.of();
        long unexplainedMs = unexplainedMs(played, knownSpans, episode.declaredDurationMs);
        boolean explained = unexplainedMs != DetectionResult.UNKNOWN && unexplainedMs <= EXPLAINED_TOLERANCE_MS;
        if (!compareDownloads || (useKnownAds && explained)) {
            return new DetectionResult(knownSpans, 0, unexplainedMs);
        }

        List<FrameHashes> copies = new ArrayList<>();
        copies.add(played);
        for (int i = 0; i < extraCopies; i++) {
            copies.add(hash(episode.extraCopies.openCopy(i)));
        }
        DownloadComparison comparison = DownloadComparison.of(copies);
        List<AdSpan> spans = new ArrayList<>(knownSpans);
        for (FrameSpan span : comparison.adSpansOf(0)) {
            spans.add(played.toAdSpan(span, AdSpan.Source.DOWNLOAD_DIFF));
        }
        library.add(AdClipLearner.withoutKnown(AdClipLearner.clips(comparison.adSequencesOfAllCopies()), knownAds));
        List<AdSpan> merged = AdSpan.merge(spans, MERGE_GAP_MS);
        return new DetectionResult(merged, extraCopies, unexplainedMs(played, merged, episode.declaredDurationMs));
    }

    private List<AdSpan> findKnownAds(FrameHashes played, List<KnownAd> knownAds) {
        KnownAdMatcher matcher = new KnownAdMatcher(played.hashes());
        List<AdSpan> spans = new ArrayList<>();
        for (KnownAd ad : knownAds) {
            List<KnownAdMatcher.Match> matches = matcher.find(ad);
            for (KnownAdMatcher.Match match : matches) {
                spans.add(played.toAdSpan(match.span, AdSpan.Source.KNOWN_AD));
            }
            if (!matches.isEmpty()) {
                library.markSeen(ad.id);
            }
        }
        return AdSpan.merge(spans, MERGE_GAP_MS);
    }

    private static long unexplainedMs(FrameHashes played, List<AdSpan> spans, long declaredDurationMs) {
        if (declaredDurationMs <= 0) {
            return DetectionResult.UNKNOWN;
        }
        long showMs = played.durationUs() / 1000 - AdSpan.totalMs(spans);
        return Math.abs(showMs - declaredDurationMs);
    }

    private static FrameHashes hash(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            return Mp3FrameReader.read(in);
        }
    }
}
