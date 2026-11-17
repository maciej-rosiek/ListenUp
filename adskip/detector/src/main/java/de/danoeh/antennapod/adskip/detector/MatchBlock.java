package de.danoeh.antennapod.adskip.detector;

public final class MatchBlock {
    public final int startA;
    public final int startB;
    public final int length;

    public MatchBlock(int startA, int startB, int length) {
        this.startA = startA;
        this.startB = startB;
        this.length = length;
    }

    public int endA() {
        return startA + length;
    }

    public int endB() {
        return startB + length;
    }

    @Override
    public String toString() {
        return "A" + startA + " B" + startB + " len " + length;
    }
}
