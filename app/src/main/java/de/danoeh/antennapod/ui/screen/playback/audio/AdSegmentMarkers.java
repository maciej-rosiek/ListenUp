package de.danoeh.antennapod.ui.screen.playback.audio;

import android.content.Context;
import android.util.Log;
import androidx.core.util.Consumer;
import de.danoeh.antennapod.adskip.AdSegment;
import de.danoeh.antennapod.adskip.AdSkipStore;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;

public final class AdSegmentMarkers {
    private static final String TAG = "AdSegmentMarkers";

    public final float[] starts;
    public final float[] ends;

    private AdSegmentMarkers(float[] starts, float[] ends) {
        this.starts = starts;
        this.ends = ends;
    }

    public static Disposable load(Context context, long mediaId, long durationMs,
                                  Consumer<AdSegmentMarkers> onLoaded) {
        return Single.fromCallable(() -> of(AdSkipStore.getInstance(context).getSegments(mediaId), durationMs))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onLoaded::accept, error -> Log.e(TAG, "Failed to load ad segments", error));
    }

    private static AdSegmentMarkers of(List<AdSegment> segments, long durationMs) {
        List<AdSegment> skipped = new ArrayList<>();
        for (AdSegment segment : segments) {
            if (!segment.kept) {
                skipped.add(segment);
            }
        }
        float[] starts = new float[skipped.size()];
        float[] ends = new float[skipped.size()];
        if (durationMs <= 0) {
            return new AdSegmentMarkers(new float[0], new float[0]);
        }
        for (int i = 0; i < skipped.size(); i++) {
            starts[i] = skipped.get(i).startMs / (float) durationMs;
            ends[i] = skipped.get(i).endMs / (float) durationMs;
        }
        return new AdSegmentMarkers(starts, ends);
    }
}
