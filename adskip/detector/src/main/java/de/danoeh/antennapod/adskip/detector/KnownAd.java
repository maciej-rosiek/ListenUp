package de.danoeh.antennapod.adskip.detector;

public final class KnownAd {
    public final long id;
    public final long[] hashes;

    public KnownAd(long id, long[] hashes) {
        this.id = id;
        this.hashes = hashes;
    }
}
