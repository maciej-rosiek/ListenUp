package de.danoeh.antennapod.ui.screen.playback.audio;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.adskip.AdAnalysis;
import de.danoeh.antennapod.adskip.AdDetection;
import de.danoeh.antennapod.adskip.AdSegment;
import de.danoeh.antennapod.adskip.AdSegmentsChangedEvent;
import de.danoeh.antennapod.adskip.AdSkipSession;
import de.danoeh.antennapod.adskip.AdSkipStore;
import de.danoeh.antennapod.adskip.detector.DownloadDiffAdDetector;
import de.danoeh.antennapod.databinding.AdSegmentListDialogBinding;
import de.danoeh.antennapod.databinding.AdSegmentListRowBinding;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.ui.common.Converter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

public class AdSegmentListDialog extends DialogFragment {
    public static final String TAG = "AdSegmentListDialog";
    private static final String ARG_MEDIA_ID = "media_id";
    private static final int MENU_PLAY = 1;
    private static final int MENU_TOGGLE_KEPT = 2;
    private static final int MENU_EDIT = 3;
    private static final int MENU_DELETE = 4;

    private AdSegmentListDialogBinding viewBinding;
    private Disposable disposable;
    private long mediaId;

    public static AdSegmentListDialog newInstance(long mediaId) {
        AdSegmentListDialog dialog = new AdSegmentListDialog();
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_MEDIA_ID, mediaId);
        dialog.setArguments(arguments);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mediaId = requireArguments().getLong(ARG_MEDIA_ID);
        viewBinding = AdSegmentListDialogBinding.inflate(getLayoutInflater());
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ad_segments_label)
                .setView(viewBinding.getRoot())
                .setPositiveButton(R.string.close_label, null)
                .setNeutralButton(R.string.ad_detect_again, (dialog, which) -> {
                    Completable.fromAction(() -> AdDetection.detectAgain(requireContext(), mediaId))
                            .subscribeOn(Schedulers.io())
                            .subscribe(() -> { }, error -> Log.e(TAG, "Failed to queue ad detection", error));
                })
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        load();
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAdSegmentsChanged(AdSegmentsChangedEvent event) {
        if (event.mediaId == mediaId) {
            load();
        }
    }

    private void load() {
        if (disposable != null) {
            disposable.dispose();
        }
        AdSkipStore store = AdSkipStore.getInstance(requireContext());
        disposable = Single.fromCallable(() -> new EpisodeAds(store.getAnalysis(mediaId), store.getSegments(mediaId)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::show, error -> Log.e(TAG, "Failed to load ad segments", error));
    }

    private void show(EpisodeAds ads) {
        viewBinding.adDetectionStatus.setText(statusText(ads));
        viewBinding.adSegmentContainer.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (AdSegment segment : ads.segments) {
            AdSegmentListRowBinding row = AdSegmentListRowBinding.inflate(inflater,
                    viewBinding.adSegmentContainer, false);
            row.adSegmentRange.setText(getString(R.string.ad_segment_range,
                    Converter.getDurationStringLong((int) segment.startMs),
                    Converter.getDurationStringLong((int) segment.endMs),
                    Math.round(segment.durationMs() / 1000.0)));
            row.adSegmentDetails.setText(segment.kept
                    ? getString(R.string.ad_segment_kept) : sourceText(segment));
            row.adSegmentIcon.setImageTintList(ColorStateList.valueOf(segment.kept
                    ? getResources().getColor(R.color.medium_gray, null) : ChapterSeekBar.AD_SEGMENT_COLOR));
            row.getRoot().setOnClickListener(view -> showSegmentMenu(view, segment));
            viewBinding.adSegmentContainer.addView(row.getRoot());
        }
    }

    private String statusText(EpisodeAds ads) {
        StringBuilder status = new StringBuilder();
        AdAnalysis analysis = ads.analysis;
        switch (analysis.state) {
            case QUEUED -> status.append(getString(R.string.ad_detection_state_queued));
            case WAITING_FOR_WIFI -> status.append(getString(R.string.ad_detection_state_waiting_for_wifi));
            case RUNNING -> status.append(getString(R.string.ad_detection_state_running));
            case FAILED -> status.append(getString(R.string.ad_detection_state_failed, analysis.message));
            case DONE -> status.append(analysis.copiesCompared > 0
                    ? getString(R.string.ad_detection_state_done, analysis.copiesCompared)
                    : getString(R.string.ad_detection_state_done_known));
            case NONE -> { }
        }
        if (analysis.state == AdAnalysis.State.DONE
                && analysis.unexplainedMs > DownloadDiffAdDetector.EXPLAINED_TOLERANCE_MS) {
            status.append('\n').append(getString(R.string.ad_detection_unexplained,
                    (int) (analysis.unexplainedMs / 1000)));
        }
        if (ads.segments.isEmpty()) {
            status.append(status.length() > 0 ? "\n" : "").append(getString(R.string.ad_segments_empty));
        }
        return status.toString();
    }

    private String sourceText(AdSegment segment) {
        return switch (segment.source) {
            case AdSegment.SOURCE_KNOWN_AD -> getString(R.string.ad_segment_source_known);
            case AdSegment.SOURCE_USER -> getString(R.string.ad_segment_source_user);
            default -> getString(R.string.ad_segment_source_diff);
        };
    }

    private void showSegmentMenu(View anchor, AdSegment segment) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(Menu.NONE, MENU_PLAY, Menu.NONE, R.string.ad_segment_play);
        popup.getMenu().add(Menu.NONE, MENU_TOGGLE_KEPT, Menu.NONE,
                segment.kept ? R.string.ad_segment_skip : R.string.ad_segment_keep);
        popup.getMenu().add(Menu.NONE, MENU_EDIT, Menu.NONE, R.string.ad_segment_edit);
        popup.getMenu().add(Menu.NONE, MENU_DELETE, Menu.NONE, R.string.delete_label);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_PLAY -> {
                    AdSkipSession.allow(segment.id);
                    PlaybackController.bindToMedia3Service(requireContext(),
                            controller -> controller.seekTo(segment.startMs));
                }
                case MENU_TOGGLE_KEPT -> write(store -> store.updateSegment(segment.withKept(!segment.kept)));
                case MENU_EDIT -> EditAdSegmentDialog.newInstance(segment)
                        .show(getParentFragmentManager(), EditAdSegmentDialog.TAG);
                case MENU_DELETE -> write(store -> store.deleteSegment(segment));
                default -> {
                    return false;
                }
            }
            return true;
        });
        popup.show();
    }

    private void write(StoreAction action) {
        AdSkipStore store = AdSkipStore.getInstance(requireContext());
        Completable.fromAction(() -> action.apply(store))
                .subscribeOn(Schedulers.io())
                .subscribe(() -> { }, error -> Log.e(TAG, "Failed to change ad segment", error));
    }

    private interface StoreAction {
        void apply(AdSkipStore store);
    }

    private static final class EpisodeAds {
        final AdAnalysis analysis;
        final List<AdSegment> segments;

        EpisodeAds(AdAnalysis analysis, List<AdSegment> segments) {
            this.analysis = analysis;
            this.segments = segments;
        }
    }
}
