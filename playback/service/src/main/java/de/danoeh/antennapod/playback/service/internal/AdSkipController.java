package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.media3.common.Player;
import de.danoeh.antennapod.adskip.AdSegment;
import de.danoeh.antennapod.adskip.AdSegmentsChangedEvent;
import de.danoeh.antennapod.adskip.AdSkipSession;
import de.danoeh.antennapod.adskip.AdSkipStore;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.service.R;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class AdSkipController {
    private static final String TAG = "AdSkipController";
    private static final long REVERT_WINDOW_MS = 30_000;
    private static final long PRECISE_CHECK_HORIZON_MS = 1500;
    private static final long END_TOLERANCE_MS = 300;
    private static final float CUE_VOLUME = 0.8f;

    private final Context context;
    private final Player player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable checkRunnable = this::check;
    private final SoundPool soundPool;
    private final int cueSoundId;
    private List<AdSegment> segments = new ArrayList<>();
    private long mediaId = -1;
    private Disposable loader;
    private boolean seekingItself = false;
    private AdSegment lastSkipped;
    private long lastSkippedAt;

    public AdSkipController(Context context, Player player) {
        this.context = context;
        this.player = player;
        soundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        cueSoundId = soundPool.load(context, R.raw.skip_cue, 1);
        EventBus.getDefault().register(this);
    }

    public void onMediaChanged(FeedMedia media) {
        if (media.getId() == mediaId) {
            return;
        }
        mediaId = media.getId();
        segments = new ArrayList<>();
        AdSkipSession.reset();
        lastSkipped = null;
        loadSegments();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAdSegmentsChanged(AdSegmentsChangedEvent event) {
        if (event.mediaId == mediaId) {
            loadSegments();
        }
    }

    private void loadSegments() {
        if (loader != null) {
            loader.dispose();
        }
        long loadingMediaId = mediaId;
        loader = Single.fromCallable(() -> AdSkipStore.getInstance(context).getSegments(loadingMediaId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(loaded -> {
                    if (loadingMediaId == mediaId) {
                        segments = loaded;
                        check();
                    }
                }, error -> Log.e(TAG, "Failed to load ad segments", error));
    }

    public void onSeek(long fromMs, long toMs) {
        if (seekingItself || lastSkipped == null) {
            return;
        }
        boolean withinRevertWindow = System.currentTimeMillis() - lastSkippedAt < REVERT_WINDOW_MS;
        if (withinRevertWindow && toMs < fromMs && lastSkipped.contains(toMs)) {
            AdSkipSession.allow(lastSkipped.id);
        }
    }

    public void check() {
        handler.removeCallbacks(checkRunnable);
        if (!player.isPlaying() || segments.isEmpty() || !isCurrentMedia()) {
            return;
        }
        long positionMs = player.getCurrentPosition();
        for (AdSegment segment : segments) {
            if (isSkippable(segment) && segment.contains(positionMs)
                    && segment.endMs - positionMs > END_TOLERANCE_MS) {
                skip(segment, positionMs);
                return;
            }
        }
        scheduleNextCheck(positionMs);
    }

    private boolean isCurrentMedia() {
        return player.getCurrentMediaItem() != null
                && String.valueOf(mediaId).equals(player.getCurrentMediaItem().mediaId);
    }

    private boolean isSkippable(AdSegment segment) {
        return AdSegment.TYPE_AD.equals(segment.type) && !segment.kept && !AdSkipSession.isAllowed(segment.id);
    }

    private void scheduleNextCheck(long positionMs) {
        float speed = Math.max(0.1f, player.getPlaybackParameters().speed);
        for (AdSegment segment : segments) {
            long untilStartMs = (long) ((segment.startMs - positionMs) / speed);
            if (isSkippable(segment) && untilStartMs > 0 && untilStartMs < PRECISE_CHECK_HORIZON_MS) {
                handler.postDelayed(checkRunnable, untilStartMs + 20);
                return;
            }
        }
    }

    private void skip(AdSegment segment, long positionMs) {
        long durationMs = player.getDuration();
        long targetMs = durationMs > 0 ? Math.min(segment.endMs, durationMs) : segment.endMs;
        Log.d(TAG, "Skipping ad " + segment.startMs + "-" + segment.endMs + " at " + positionMs);
        seekingItself = true;
        player.seekTo(targetMs);
        seekingItself = false;
        lastSkipped = segment;
        lastSkippedAt = System.currentTimeMillis();
        soundPool.play(cueSoundId, CUE_VOLUME, CUE_VOLUME, 1, 0, 1.0f);
        int skippedSeconds = (int) Math.round((targetMs - positionMs) / 1000.0);
        EventBus.getDefault().post(new MessageEvent(context.getString(R.string.ad_skipped, skippedSeconds),
                ignored -> undo(segment), context.getString(R.string.ad_skip_undo)));
    }

    private void undo(AdSegment segment) {
        if (segment.mediaId != mediaId) {
            return;
        }
        AdSkipSession.allow(segment.id);
        seekingItself = true;
        player.seekTo(segment.startMs);
        seekingItself = false;
        EventBus.getDefault().post(new MessageEvent(context.getString(R.string.ad_playing_skipped_part),
                ignored -> keepForever(segment), context.getString(R.string.ad_never_skip)));
    }

    private void keepForever(AdSegment segment) {
        Single.fromCallable(() -> {
            AdSkipStore.getInstance(context).updateSegment(segment.withKept(true));
            return segment;
        })
                .subscribeOn(Schedulers.io())
                .subscribe(ignored -> { }, error -> Log.e(TAG, "Failed to keep ad segment", error));
    }

    public void release() {
        handler.removeCallbacks(checkRunnable);
        if (loader != null) {
            loader.dispose();
        }
        EventBus.getDefault().unregister(this);
        soundPool.release();
    }
}
