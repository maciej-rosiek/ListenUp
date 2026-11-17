package de.danoeh.antennapod.adskip;

public final class AdSegment {
    public static final String TYPE_AD = "ad";
    public static final String SOURCE_DOWNLOAD_DIFF = "download_diff";
    public static final String SOURCE_KNOWN_AD = "known_ad";
    public static final String SOURCE_HOST_MARKED = "host_marked";
    public static final String SOURCE_USER = "user";

    public final long id;
    public final long mediaId;
    public final long startMs;
    public final long endMs;
    public final String type;
    public final String source;
    public final boolean kept;

    public AdSegment(long id, long mediaId, long startMs, long endMs, String type, String source, boolean kept) {
        this.id = id;
        this.mediaId = mediaId;
        this.startMs = startMs;
        this.endMs = endMs;
        this.type = type;
        this.source = source;
        this.kept = kept;
    }

    public long durationMs() {
        return endMs - startMs;
    }

    public boolean contains(long positionMs) {
        return positionMs >= startMs && positionMs < endMs;
    }

    public AdSegment withBounds(long newStartMs, long newEndMs) {
        return new AdSegment(id, mediaId, newStartMs, newEndMs, type, SOURCE_USER, kept);
    }

    public AdSegment withKept(boolean newKept) {
        return new AdSegment(id, mediaId, startMs, endMs, type, source, newKept);
    }
}
