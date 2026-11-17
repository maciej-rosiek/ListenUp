package de.danoeh.antennapod.adskip.detector;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class DownloadDiffAdDetector implements AdDetector {
    public enum CompareMode { NEVER, WHEN_UNEXPLAINED, ALWAYS }

    public static final long EXPLAINED_TOLERANCE_MS = 4000;
    private static final long MERGE_GAP_MS = 500;

    private final KnownAdLibrary library;
    private final boolean useKnownAds;
    private final CompareMode compareMode;
    private final int minExtraCopies;
    private final int maxExtraCopies;

    public DownloadDiffAdDetector(KnownAdLibrary library, boolean useKnownAds, CompareMode compareMode,
                                  int minExtraCopies, int maxExtraCopies) {
        this.library = library;
        this.useKnownAds = useKnownAds;
        this.compareMode = compareMode;
        this.minExtraCopies = minExtraCopies;
        this.maxExtraCopies = maxExtraCopies;
    }

    @Override
    public DetectionResult detect(EpisodeAudio episode) throws IOException {
        FrameHashes played = hash(episode.playedFile.open());
        List<KnownAd> knownAds = library.all();
        List<AdSpan> markedSpans = new ArrayList<>(played.toAdSpans(played.taggedClips(), AdSpan.Source.CLIP_TAG));
        markedSpans.addAll(played.toAdSpans(HostAdBreaks.framesOf(episode.hostAdBreaksHeader, played),
                AdSpan.Source.HOST_AD_BREAKS));
        if (useKnownAds) {
            markedSpans.addAll(findKnownAds(played, knownAds));
        }
        List<AdSpan> sureSpans = AdSpan.merge(markedSpans, MERGE_GAP_MS);
        long unexplainedMs = unexplainedMs(played, sureSpans, episode.declaredDurationMs);
        boolean explained = unexplainedMs != DetectionResult.UNKNOWN && unexplainedMs <= EXPLAINED_TOLERANCE_MS;
        boolean feedDurationMayIncludeAds = explained && sureSpans.isEmpty();
        if (compareMode == CompareMode.NEVER
                || (compareMode == CompareMode.WHEN_UNEXPLAINED && explained && !feedDurationMayIncludeAds)) {
            return new DetectionResult(sureSpans, 0, unexplainedMs);
        }

        List<FrameHashes> copies = new ArrayList<>();
        copies.add(played);
        copies.addAll(hashCopiesInParallel(episode.extraCopies, 0, minExtraCopies));
        DownloadComparison comparison = DownloadComparison.of(copies);
        List<AdSpan> spans = withDiffSpans(sureSpans, played, comparison);
        if (feedDurationMayIncludeAds) {
            return learnedResult(spans, copies, comparison, knownAds,
                    spans.isEmpty() ? unexplainedMs : DetectionResult.UNKNOWN);
        }
        long remainingMs = unexplainedMs(played, spans, episode.declaredDurationMs);
        if (remainingMs != DetectionResult.UNKNOWN && remainingMs > EXPLAINED_TOLERANCE_MS
                && maxExtraCopies > minExtraCopies) {
            copies.addAll(hashCopiesInParallel(episode.extraCopies, minExtraCopies, maxExtraCopies));
            comparison = DownloadComparison.of(copies);
            spans = withDiffSpans(sureSpans, played, comparison);
        }
        return learnedResult(spans, copies, comparison, knownAds,
                unexplainedMs(played, spans, episode.declaredDurationMs));
    }

    private DetectionResult learnedResult(List<AdSpan> spans, List<FrameHashes> copies,
                                          DownloadComparison comparison, List<KnownAd> knownAds, long unexplainedMs) {
        library.add(AdClipLearner.withoutKnown(AdClipLearner.clips(comparison.adSequencesOfAllCopies()), knownAds));
        return new DetectionResult(spans, copies.size() - 1, unexplainedMs);
    }

    private static List<FrameHashes> hashCopiesInParallel(EpisodeAudio.CopyOpener opener, int fromIndex,
                                                          int toIndex) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(toIndex - fromIndex);
        try {
            List<Future<FrameHashes>> futures = new ArrayList<>();
            for (int index = fromIndex; index < toIndex; index++) {
                int copyIndex = index;
                futures.add(executor.submit(() -> hash(opener.openCopy(copyIndex))));
            }
            List<FrameHashes> copies = new ArrayList<>();
            for (Future<FrameHashes> future : futures) {
                copies.add(future.get());
            }
            return copies;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<AdSpan> withDiffSpans(List<AdSpan> sureSpans, FrameHashes played,
                                              DownloadComparison comparison) {
        List<AdSpan> spans = new ArrayList<>(sureSpans);
        for (FrameSpan span : comparison.adSpansOf(0)) {
            spans.add(played.toAdSpan(span, AdSpan.Source.DOWNLOAD_DIFF));
        }
        return AdSpan.merge(spans, MERGE_GAP_MS);
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
