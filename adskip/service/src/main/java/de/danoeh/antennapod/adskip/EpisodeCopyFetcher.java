package de.danoeh.antennapod.adskip;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import de.danoeh.antennapod.adskip.detector.EpisodeAudio;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.net.common.UserAgentInterceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;

public class EpisodeCopyFetcher implements EpisodeAudio.CopyOpener {
    private static final long PAUSE_BETWEEN_COPIES_MS = 2000;

    private final Context context;
    private final String url;
    private final String runId = Integer.toHexString(new SecureRandom().nextInt(0x1000000));

    public EpisodeCopyFetcher(Context context, String url) {
        this.context = context;
        this.url = url;
    }

    @Override
    public InputStream openCopy(int copyIndex) throws IOException {
        if (!isOnUnmeteredNetwork(context)) {
            throw new WifiRequiredException();
        }
        if (copyIndex > 0) {
            pause();
        }
        String userAgent = UserAgentInterceptor.USER_AGENT + " (copy " + runId + "-" + (copyIndex + 1) + ")";
        OkHttpClient client = AntennapodHttpClient.getHttpClient().newBuilder()
                .addNetworkInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .build()))
                .build();
        Response response = client.newCall(new Request.Builder().url(url).build()).execute();
        ResponseBody body = response.body();
        if (!response.isSuccessful() || body == null) {
            response.close();
            throw new IOException("HTTP " + response.code() + " for extra copy " + (copyIndex + 1));
        }
        return body.byteStream();
    }

    private static void pause() throws IOException {
        try {
            Thread.sleep(PAUSE_BETWEEN_COPIES_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    public static boolean isOnUnmeteredNetwork(Context context) {
        ConnectivityManager connectivityManager = context.getSystemService(ConnectivityManager.class);
        if (connectivityManager == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(
                connectivityManager.getActiveNetwork());
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && !connectivityManager.isActiveNetworkMetered();
    }

    public static class WifiRequiredException extends IOException {
        public WifiRequiredException() {
            super("Extra copies are only downloaded on Wi-Fi");
        }
    }
}
