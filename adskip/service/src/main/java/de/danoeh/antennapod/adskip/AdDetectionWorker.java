package de.danoeh.antennapod.adskip;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import de.danoeh.antennapod.adskip.detector.DetectionResult;
import de.danoeh.antennapod.adskip.detector.DownloadDiffAdDetector;
import de.danoeh.antennapod.adskip.detector.DownloadDiffAdDetector.CompareMode;
import de.danoeh.antennapod.adskip.detector.EpisodeAudio;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class AdDetectionWorker extends Worker {
    private static final String TAG = "AdDetectionWorker";
    static final String INPUT_MEDIA_ID = "media_id";
    static final String INPUT_FORCE_COMPARE = "force_compare";
    private static final int MIN_EXTRA_COPIES = 2;
    private static final int MAX_EXTRA_COPIES = 5;
    private static final int MAX_ATTEMPTS = 3;

    public AdDetectionWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        long mediaId = getInputData().getLong(INPUT_MEDIA_ID, -1);
        boolean forceCompare = getInputData().getBoolean(INPUT_FORCE_COMPARE, false);
        AdSkipStore store = AdSkipStore.getInstance(getApplicationContext());
        AdAnalysis analysis = store.getAnalysis(mediaId);
        FeedMedia media = DBReader.getFeedMedia(mediaId);
        if (media == null || !media.isDownloaded() || media.getLocalFileUrl() == null
                || !new File(media.getLocalFileUrl()).exists()) {
            store.saveAnalysis(analysis.withState(AdAnalysis.State.FAILED,
                    getApplicationContext().getString(R.string.ad_detection_not_downloaded)));
            return Result.failure();
        }
        File file = new File(media.getLocalFileUrl());
        if (!isMp3(file)) {
            store.saveAnalysis(analysis.withState(AdAnalysis.State.FAILED,
                    getApplicationContext().getString(R.string.ad_detection_not_mp3)));
            return Result.failure();
        }

        FeedAdSettings settings = store.getFeedSettings(media.getItem() != null ? media.getItem().getFeedId() : 0);
        CompareMode compareMode = forceCompare ? CompareMode.ALWAYS : settings.compareMode();
        store.saveAnalysis(analysis.withState(AdAnalysis.State.RUNNING, null));
        DownloadDiffAdDetector detector = new DownloadDiffAdDetector(store, settings.knownAds, compareMode,
                MIN_EXTRA_COPIES, MAX_EXTRA_COPIES);
        try {
            DetectionResult result = detector.detect(new EpisodeAudio(() -> new FileInputStream(file),
                    new EpisodeCopyFetcher(getApplicationContext(), media.getDownloadUrl()),
                    analysis.declaredDurationMs, analysis.hostAdBreaksHeader));
            store.replaceDetectedSegments(mediaId, result.spans);
            store.saveAnalysis(analysis.done(result.copiesCompared, result.unexplainedMs));
            Log.d(TAG, "Found " + result.spans.size() + " ads in " + media.getEpisodeTitle());
            return Result.success();
        } catch (EpisodeCopyFetcher.WifiRequiredException e) {
            store.saveAnalysis(analysis.withState(AdAnalysis.State.WAITING_FOR_WIFI, null));
            AdDetection.enqueueOnWifi(getApplicationContext(), mediaId, forceCompare);
            return Result.success();
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Ad detection failed", e);
            if (getRunAttemptCount() + 1 < MAX_ATTEMPTS && !isStopped()) {
                store.saveAnalysis(analysis.withState(AdAnalysis.State.QUEUED, e.getMessage()));
                return Result.retry();
            }
            store.saveAnalysis(analysis.withState(AdAnalysis.State.FAILED, String.valueOf(e.getMessage())));
            return Result.failure();
        }
    }

    private static boolean isMp3(File file) {
        byte[] start = new byte[3];
        try (InputStream in = new FileInputStream(file)) {
            if (in.read(start) < start.length) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        boolean id3 = start[0] == 'I' && start[1] == 'D' && start[2] == '3';
        boolean frameSync = (start[0] & 0xFF) == 0xFF && (start[1] & 0xE6) == 0xE2;
        return id3 || frameSync;
    }
}
