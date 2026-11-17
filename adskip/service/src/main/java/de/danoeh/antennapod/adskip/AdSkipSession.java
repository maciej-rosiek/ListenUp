package de.danoeh.antennapod.adskip;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AdSkipSession {
    private static final Set<Long> allowedSegmentIds = ConcurrentHashMap.newKeySet();

    private AdSkipSession() {
    }

    public static void allow(long segmentId) {
        allowedSegmentIds.add(segmentId);
    }

    public static boolean isAllowed(long segmentId) {
        return allowedSegmentIds.contains(segmentId);
    }

    public static void reset() {
        allowedSegmentIds.clear();
    }
}
