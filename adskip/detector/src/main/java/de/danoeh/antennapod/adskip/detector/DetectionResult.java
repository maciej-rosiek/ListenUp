package de.danoeh.antennapod.adskip.detector;

import java.util.List;

public final class DetectionResult {
    public static final long UNKNOWN = -1;

    public final List<AdSpan> spans;
    public final int copiesCompared;
    public final long unexplainedMs;

    public DetectionResult(List<AdSpan> spans, int copiesCompared, long unexplainedMs) {
        this.spans = spans;
        this.copiesCompared = copiesCompared;
        this.unexplainedMs = unexplainedMs;
    }
}
