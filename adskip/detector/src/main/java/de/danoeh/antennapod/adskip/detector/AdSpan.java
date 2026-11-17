package de.danoeh.antennapod.adskip.detector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class AdSpan {
    public enum Source { DOWNLOAD_DIFF, KNOWN_AD, CLIP_TAG, HOST_AD_BREAKS }

    public final long startMs;
    public final long endMs;
    public final Source source;

    public AdSpan(long startMs, long endMs, Source source) {
        this.startMs = startMs;
        this.endMs = endMs;
        this.source = source;
    }

    public long durationMs() {
        return endMs - startMs;
    }

    public static List<AdSpan> merge(List<AdSpan> spans, long maxGapMs) {
        List<AdSpan> sorted = new ArrayList<>(spans);
        sorted.sort(Comparator.comparingLong(span -> span.startMs));
        List<AdSpan> merged = new ArrayList<>();
        for (AdSpan span : sorted) {
            AdSpan last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && span.startMs - last.endMs <= maxGapMs) {
                Source source = last.source;
                merged.set(merged.size() - 1, new AdSpan(last.startMs, Math.max(last.endMs, span.endMs), source));
            } else {
                merged.add(span);
            }
        }
        return merged;
    }

    public static long totalMs(List<AdSpan> spans) {
        long total = 0;
        for (AdSpan span : spans) {
            total += span.durationMs();
        }
        return total;
    }

    @Override
    public String toString() {
        return format(startMs) + "-" + format(endMs) + " " + source;
    }

    private static String format(long ms) {
        return String.format(Locale.ROOT, "%d:%05.2f", ms / 60000, (ms % 60000) / 1000.0);
    }
}
