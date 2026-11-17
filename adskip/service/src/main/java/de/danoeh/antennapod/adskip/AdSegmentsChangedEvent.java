package de.danoeh.antennapod.adskip;

public final class AdSegmentsChangedEvent {
    public final long mediaId;

    public AdSegmentsChangedEvent(long mediaId) {
        this.mediaId = mediaId;
    }
}
