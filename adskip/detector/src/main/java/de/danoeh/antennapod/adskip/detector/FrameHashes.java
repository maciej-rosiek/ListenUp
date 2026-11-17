package de.danoeh.antennapod.adskip.detector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FrameHashes {
    private final long[] hashes;
    private final long[] startUs;
    private final long[] byteOffsets;
    private final long durationUs;
    private final List<FrameSpan> taggedClips;

    public FrameHashes(long[] hashes, long[] startUs, long[] byteOffsets, long durationUs,
                       List<FrameSpan> taggedClips) {
        this.hashes = hashes;
        this.startUs = startUs;
        this.byteOffsets = byteOffsets;
        this.durationUs = durationUs;
        this.taggedClips = taggedClips;
    }

    public List<FrameSpan> taggedClips() {
        return taggedClips;
    }

    public FrameSpan framesWithin(long firstByte, long endByte) {
        int start = firstFrameAtOrAfter(firstByte);
        int end = firstFrameAtOrAfter(endByte);
        return new FrameSpan(start, end);
    }

    private int firstFrameAtOrAfter(long byteOffset) {
        int index = Arrays.binarySearch(byteOffsets, byteOffset);
        return index >= 0 ? index : -index - 1;
    }

    public List<AdSpan> toAdSpans(List<FrameSpan> spans, AdSpan.Source source) {
        List<AdSpan> adSpans = new ArrayList<>();
        for (FrameSpan span : spans) {
            if (span.length() > 0) {
                adSpans.add(toAdSpan(span, source));
            }
        }
        return adSpans;
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
