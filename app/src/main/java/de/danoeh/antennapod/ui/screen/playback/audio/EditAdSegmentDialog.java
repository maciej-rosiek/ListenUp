package de.danoeh.antennapod.ui.screen.playback.audio;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.adskip.AdSegment;
import de.danoeh.antennapod.adskip.AdSkipStore;
import de.danoeh.antennapod.databinding.EditAdSegmentDialogBinding;
import de.danoeh.antennapod.databinding.EditAdSegmentRowBinding;
import de.danoeh.antennapod.ui.common.Converter;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class EditAdSegmentDialog extends DialogFragment {
    public static final String TAG = "EditAdSegmentDialog";
    private static final String ARG_ID = "id";
    private static final String ARG_MEDIA_ID = "media_id";
    private static final String ARG_START_MS = "start_ms";
    private static final String ARG_END_MS = "end_ms";
    private static final String ARG_TYPE = "type";
    private static final String ARG_SOURCE = "source";
    private static final String ARG_KEPT = "kept";
    private static final long MIN_LENGTH_MS = 1000;

    private AdSegment segment;
    private long startMs;
    private long endMs;
    private EditAdSegmentDialogBinding viewBinding;

    public static EditAdSegmentDialog newInstance(AdSegment segment) {
        EditAdSegmentDialog dialog = new EditAdSegmentDialog();
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_ID, segment.id);
        arguments.putLong(ARG_MEDIA_ID, segment.mediaId);
        arguments.putLong(ARG_START_MS, segment.startMs);
        arguments.putLong(ARG_END_MS, segment.endMs);
        arguments.putString(ARG_TYPE, segment.type);
        arguments.putString(ARG_SOURCE, segment.source);
        arguments.putBoolean(ARG_KEPT, segment.kept);
        dialog.setArguments(arguments);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle arguments = requireArguments();
        segment = new AdSegment(arguments.getLong(ARG_ID), arguments.getLong(ARG_MEDIA_ID),
                arguments.getLong(ARG_START_MS), arguments.getLong(ARG_END_MS), arguments.getString(ARG_TYPE),
                arguments.getString(ARG_SOURCE), arguments.getBoolean(ARG_KEPT));
        startMs = segment.startMs;
        endMs = segment.endMs;
        viewBinding = EditAdSegmentDialogBinding.inflate(getLayoutInflater());
        viewBinding.startRow.boundaryLabel.setText(R.string.ad_segment_start);
        viewBinding.endRow.boundaryLabel.setText(R.string.ad_segment_end);
        setupNudges(viewBinding.startRow, true);
        setupNudges(viewBinding.endRow, false);
        showTimes();
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ad_segment_edit)
                .setView(viewBinding.getRoot())
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> save())
                .setNegativeButton(R.string.cancel_label, null)
                .create();
    }

    private void setupNudges(EditAdSegmentRowBinding row, boolean start) {
        row.minusFive.setOnClickListener(v -> nudge(start, -5000));
        row.minusOne.setOnClickListener(v -> nudge(start, -1000));
        row.plusOne.setOnClickListener(v -> nudge(start, 1000));
        row.plusFive.setOnClickListener(v -> nudge(start, 5000));
    }

    private void nudge(boolean start, long deltaMs) {
        if (start) {
            startMs = Math.max(0, Math.min(endMs - MIN_LENGTH_MS, startMs + deltaMs));
        } else {
            endMs = Math.max(startMs + MIN_LENGTH_MS, endMs + deltaMs);
        }
        showTimes();
    }

    private void showTimes() {
        viewBinding.startRow.boundaryTime.setText(Converter.getDurationStringLong((int) startMs));
        viewBinding.endRow.boundaryTime.setText(Converter.getDurationStringLong((int) endMs));
    }

    private void save() {
        AdSkipStore store = AdSkipStore.getInstance(requireContext());
        AdSegment edited = segment.withBounds(startMs, endMs);
        Completable.fromAction(() -> store.updateSegment(edited))
                .subscribeOn(Schedulers.io())
                .subscribe(() -> { }, error -> Log.e(TAG, "Failed to save ad segment", error));
    }
}
