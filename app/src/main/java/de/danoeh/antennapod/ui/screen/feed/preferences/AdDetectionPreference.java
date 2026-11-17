package de.danoeh.antennapod.ui.screen.feed.preferences;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import androidx.preference.Preference;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.adskip.AdSkipStore;
import de.danoeh.antennapod.adskip.FeedAdSettings;
import de.danoeh.antennapod.databinding.AdDetectionSettingsDialogBinding;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

final class AdDetectionPreference {
    private static final String TAG = "AdDetectionPreference";

    private final Preference preference;
    private final long feedId;
    private FeedAdSettings settings;

    private AdDetectionPreference(Preference preference, long feedId) {
        this.preference = preference;
        this.feedId = feedId;
    }

    static Disposable setup(Preference preference, long feedId) {
        AdDetectionPreference adDetection = new AdDetectionPreference(preference, feedId);
        AdSkipStore store = AdSkipStore.getInstance(preference.getContext());
        return Single.fromCallable(() -> store.getFeedSettings(feedId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adDetection::show, error -> Log.e(TAG, "Failed to load ad detection settings", error));
    }

    private void show(FeedAdSettings loaded) {
        settings = loaded;
        preference.setSummary(summary(preference.getContext(), settings));
        preference.setOnPreferenceClickListener(clicked -> {
            showDialog();
            return true;
        });
    }

    private void showDialog() {
        Context context = preference.getContext();
        AdDetectionSettingsDialogBinding binding = AdDetectionSettingsDialogBinding.inflate(
                LayoutInflater.from(context));
        binding.compareDownloadsCheckbox.setChecked(settings.compareDownloads);
        binding.knownAdsCheckbox.setChecked(settings.knownAds);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.ad_detection_label)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> save(new FeedAdSettings(feedId,
                        binding.knownAdsCheckbox.isChecked(), binding.compareDownloadsCheckbox.isChecked())))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private void save(FeedAdSettings newSettings) {
        settings = newSettings;
        preference.setSummary(summary(preference.getContext(), newSettings));
        AdSkipStore store = AdSkipStore.getInstance(preference.getContext());
        Completable.fromAction(() -> store.saveFeedSettings(newSettings))
                .subscribeOn(Schedulers.io())
                .subscribe(() -> { }, error -> Log.e(TAG, "Failed to save ad detection settings", error));
    }

    private static String summary(Context context, FeedAdSettings settings) {
        if (settings.knownAds && settings.compareDownloads) {
            return context.getString(R.string.ad_detection_summary_both);
        } else if (settings.compareDownloads) {
            return context.getString(R.string.ad_detection_summary_compare);
        } else if (settings.knownAds) {
            return context.getString(R.string.ad_detection_summary_known);
        }
        return context.getString(R.string.ad_detection_summary_off);
    }
}
