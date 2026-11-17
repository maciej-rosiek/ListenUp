package de.danoeh.antennapod.adskip;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import de.danoeh.antennapod.model.feed.FeedMedia;

public final class AdDetection {
    public static final String ARG_HOST_AD_BREAKS = "listenup_host_ad_breaks";
    private static final String WORK_PREFIX = "ad_detection_";

    private AdDetection() {
    }

    public static void onEpisodeDownloaded(Context context, FeedMedia media, long declaredDurationMs,
                                           String hostAdBreaksHeader) {
        AdSkipStore store = AdSkipStore.getInstance(context);
        store.clearEpisode(media.getId());
        long feedId = media.getItem() != null ? media.getItem().getFeedId() : 0;
        if (!store.getFeedSettings(feedId).isEnabled()) {
            return;
        }
        store.saveAnalysis(new AdAnalysis(media.getId(), AdAnalysis.State.QUEUED, declaredDurationMs, 0, -1, null,
                hostAdBreaksHeader));
        enqueue(context, media.getId(), false, Constraints.NONE);
    }

    public static void detectAgain(Context context, long mediaId) {
        AdSkipStore store = AdSkipStore.getInstance(context);
        store.saveAnalysis(store.getAnalysis(mediaId).withState(AdAnalysis.State.QUEUED, null));
        enqueue(context, mediaId, true, Constraints.NONE);
    }

    static void enqueueOnWifi(Context context, long mediaId, boolean forceCompare) {
        enqueue(context, mediaId, forceCompare,
                new Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build());
    }

    private static void enqueue(Context context, long mediaId, boolean forceCompare, Constraints constraints) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AdDetectionWorker.class)
                .setConstraints(constraints)
                .setInputData(new Data.Builder()
                        .putLong(AdDetectionWorker.INPUT_MEDIA_ID, mediaId)
                        .putBoolean(AdDetectionWorker.INPUT_FORCE_COMPARE, forceCompare)
                        .build())
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_PREFIX + mediaId, ExistingWorkPolicy.REPLACE, request);
    }
}
