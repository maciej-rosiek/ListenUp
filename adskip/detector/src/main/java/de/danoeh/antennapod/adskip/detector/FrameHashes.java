package de.danoeh.antennapod.adskip.detector;

import java.util.Arrays;

public final class FrameHashes {
    private final long[] hashes;
    private final long[] startUs;
    private final long durationUs;

    public FrameHashes(long[] hashes, long[] startUs, long durationUs) {
        this.hashes = hashes;
        this.startUs = startUs;
        this.durationUs = durationUs;
    }

    public int size() {
        return hashes.length;
    }

    public long[] hashes() {
        return hashes;
    }

    public long durationUs() {
        return durationUs;
    }

    public long startUs(int frame) {
        return frame < startUs.length ? startUs[frame] : durationUs;
    }

    public long[] slice(FrameSpan span) {
        return Arrays.copyOfRange(hashes, span.start, span.end);
    }

    public AdSpan toAdSpan(FrameSpan span, AdSpan.Source source) {
        return new AdSpan(startUs(span.start) / 1000, startUs(span.end) / 1000, source);
    }

    public boolean sameContentAs(FrameHashes other) {
        return Arrays.equals(hashes, other.hashes);
    }
}
