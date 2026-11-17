package de.danoeh.antennapod.adskip;

import de.danoeh.antennapod.adskip.detector.DownloadDiffAdDetector.CompareMode;

public final class FeedAdSettings {
    public final long feedId;
    public final boolean knownAds;
    public final boolean compareDownloads;

    public FeedAdSettings(long feedId, boolean knownAds, boolean compareDownloads) {
        this.feedId = feedId;
        this.knownAds = knownAds;
        this.compareDownloads = compareDownloads;
    }

    public boolean isEnabled() {
        return knownAds || compareDownloads;
    }

    public CompareMode compareMode() {
        if (!compareDownloads) {
            return CompareMode.NEVER;
        }
        return knownAds ? CompareMode.WHEN_UNEXPLAINED : CompareMode.ALWAYS;
    }
}
