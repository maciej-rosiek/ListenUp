package de.danoeh.antennapod.adskip.detector;

public final class FrameSpan {
    public final int start;
    public final int end;

    public FrameSpan(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public int length() {
        return end - start;
    }

    public boolean overlaps(FrameSpan other) {
        return start < other.end && other.start < end;
    }

    @Override
    public String toString() {
        return "[" + start + ", " + end + ")";
    }
}
