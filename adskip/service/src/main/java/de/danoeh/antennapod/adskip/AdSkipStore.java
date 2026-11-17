package de.danoeh.antennapod.adskip;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.NonNull;
import de.danoeh.antennapod.adskip.detector.AdSpan;
import de.danoeh.antennapod.adskip.detector.KnownAd;
import de.danoeh.antennapod.adskip.detector.KnownAdLibrary;
import org.greenrobot.eventbus.EventBus;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class AdSkipStore extends SQLiteOpenHelper implements KnownAdLibrary {
    private static final String DATABASE_NAME = "adskip.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_SEGMENTS = "segments";
    private static final String TABLE_ANALYSIS = "analysis";
    private static final String TABLE_KNOWN_ADS = "known_ads";
    private static final String TABLE_FEED_SETTINGS = "feed_settings";

    private static AdSkipStore instance;

    private AdSkipStore(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized AdSkipStore getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new AdSkipStore(context.getApplicationContext());
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_SEGMENTS + " (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " media_id INTEGER NOT NULL, start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL,"
                + " type TEXT NOT NULL, source TEXT NOT NULL, kept INTEGER NOT NULL DEFAULT 0,"
                + " user_edited INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX segments_media ON " + TABLE_SEGMENTS + " (media_id)");
        db.execSQL("CREATE TABLE " + TABLE_ANALYSIS + " (media_id INTEGER PRIMARY KEY, state TEXT NOT NULL,"
                + " declared_duration_ms INTEGER NOT NULL DEFAULT 0, copies_compared INTEGER NOT NULL DEFAULT 0,"
                + " unexplained_ms INTEGER NOT NULL DEFAULT -1, message TEXT, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE " + TABLE_KNOWN_ADS + " (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " hashes BLOB NOT NULL, frames INTEGER NOT NULL, first_seen INTEGER NOT NULL,"
                + " last_seen INTEGER NOT NULL, times_seen INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE " + TABLE_FEED_SETTINGS + " (feed_id INTEGER PRIMARY KEY,"
                + " known_ads INTEGER NOT NULL, compare_downloads INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public List<AdSegment> getSegments(long mediaId) {
        return querySegments(mediaId, "media_id = ?");
    }

    private List<AdSegment> querySegments(long mediaId, String selection) {
        List<AdSegment> segments = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_SEGMENTS,
                new String[]{"id", "start_ms", "end_ms", "type", "source", "kept"},
                selection, new String[]{String.valueOf(mediaId)}, null, null, "start_ms")) {
            while (cursor.moveToNext()) {
                segments.add(new AdSegment(cursor.getLong(0), mediaId, cursor.getLong(1), cursor.getLong(2),
                        cursor.getString(3), cursor.getString(4), cursor.getInt(5) == 1));
            }
        }
        return segments;
    }

    public void replaceDetectedSegments(long mediaId, List<AdSpan> spans) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            List<AdSegment> userSegments = querySegments(mediaId, "media_id = ? AND user_edited = 1");
            db.delete(TABLE_SEGMENTS, "media_id = ? AND user_edited = 0", new String[]{String.valueOf(mediaId)});
            for (AdSpan span : spans) {
                boolean overlapsUserSegment = userSegments.stream()
                        .anyMatch(segment -> span.startMs < segment.endMs && segment.startMs < span.endMs);
                if (overlapsUserSegment) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("media_id", mediaId);
                values.put("start_ms", span.startMs);
                values.put("end_ms", span.endMs);
                values.put("type", AdSegment.TYPE_AD);
                values.put("source", span.source == AdSpan.Source.KNOWN_AD
                        ? AdSegment.SOURCE_KNOWN_AD : AdSegment.SOURCE_DOWNLOAD_DIFF);
                db.insert(TABLE_SEGMENTS, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        notifyChanged(mediaId);
    }

    public void updateSegment(AdSegment segment) {
        ContentValues values = new ContentValues();
        values.put("start_ms", segment.startMs);
        values.put("end_ms", segment.endMs);
        values.put("source", segment.source);
        values.put("kept", segment.kept ? 1 : 0);
        values.put("user_edited", 1);
        getWritableDatabase().update(TABLE_SEGMENTS, values, "id = ?", new String[]{String.valueOf(segment.id)});
        notifyChanged(segment.mediaId);
    }

    public void deleteSegment(AdSegment segment) {
        getWritableDatabase().delete(TABLE_SEGMENTS, "id = ?", new String[]{String.valueOf(segment.id)});
        notifyChanged(segment.mediaId);
    }

    public void clearEpisode(long mediaId) {
        getWritableDatabase().delete(TABLE_SEGMENTS, "media_id = ?", new String[]{String.valueOf(mediaId)});
        getWritableDatabase().delete(TABLE_ANALYSIS, "media_id = ?", new String[]{String.valueOf(mediaId)});
        notifyChanged(mediaId);
    }

    public AdAnalysis getAnalysis(long mediaId) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_ANALYSIS,
                new String[]{"state", "declared_duration_ms", "copies_compared", "unexplained_ms", "message"},
                "media_id = ?", new String[]{String.valueOf(mediaId)}, null, null, null)) {
            if (!cursor.moveToFirst()) {
                return AdAnalysis.none(mediaId);
            }
            return new AdAnalysis(mediaId, AdAnalysis.State.valueOf(cursor.getString(0)), cursor.getLong(1),
                    cursor.getInt(2), cursor.getLong(3), cursor.getString(4));
        }
    }

    public void saveAnalysis(AdAnalysis analysis) {
        ContentValues values = new ContentValues();
        values.put("media_id", analysis.mediaId);
        values.put("state", analysis.state.name());
        values.put("declared_duration_ms", analysis.declaredDurationMs);
        values.put("copies_compared", analysis.copiesCompared);
        values.put("unexplained_ms", analysis.unexplainedMs);
        values.put("message", analysis.message);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(TABLE_ANALYSIS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        notifyChanged(analysis.mediaId);
    }

    public FeedAdSettings getFeedSettings(long feedId) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_FEED_SETTINGS,
                new String[]{"known_ads", "compare_downloads"},
                "feed_id = ?", new String[]{String.valueOf(feedId)}, null, null, null)) {
            if (!cursor.moveToFirst()) {
                return new FeedAdSettings(feedId, false, false);
            }
            return new FeedAdSettings(feedId, cursor.getInt(0) == 1, cursor.getInt(1) == 1);
        }
    }

    public void saveFeedSettings(FeedAdSettings settings) {
        ContentValues values = new ContentValues();
        values.put("feed_id", settings.feedId);
        values.put("known_ads", settings.knownAds ? 1 : 0);
        values.put("compare_downloads", settings.compareDownloads ? 1 : 0);
        getWritableDatabase().insertWithOnConflict(TABLE_FEED_SETTINGS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public List<KnownAd> all() {
        List<KnownAd> ads = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_KNOWN_ADS, new String[]{"id", "hashes"},
                null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                ads.add(new KnownAd(cursor.getLong(0), toHashes(cursor.getBlob(1))));
            }
        }
        return ads;
    }

    @Override
    public void add(List<long[]> adClips) {
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (long[] clip : adClips) {
                ContentValues values = new ContentValues();
                values.put("hashes", toBytes(clip));
                values.put("frames", clip.length);
                values.put("first_seen", now);
                values.put("last_seen", now);
                db.insert(TABLE_KNOWN_ADS, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void markSeen(long knownAdId) {
        getWritableDatabase().execSQL("UPDATE " + TABLE_KNOWN_ADS
                + " SET last_seen = ?, times_seen = times_seen + 1 WHERE id = ?",
                new Object[]{System.currentTimeMillis(), knownAdId});
    }

    private static byte[] toBytes(long[] hashes) {
        ByteBuffer buffer = ByteBuffer.allocate(hashes.length * Long.BYTES);
        buffer.asLongBuffer().put(hashes);
        return buffer.array();
    }

    private static long[] toHashes(byte[] bytes) {
        long[] hashes = new long[bytes.length / Long.BYTES];
        ByteBuffer.wrap(bytes).asLongBuffer().get(hashes);
        return hashes;
    }

    private static void notifyChanged(long mediaId) {
        EventBus.getDefault().post(new AdSegmentsChangedEvent(mediaId));
    }
}
