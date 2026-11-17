package de.danoeh.antennapod.adskip;

public final class AdAnalysis {
    public enum State { NONE, QUEUED, WAITING_FOR_WIFI, RUNNING, DONE, FAILED }

    public final long mediaId;
    public final State state;
    public final long declaredDurationMs;
    public final int copiesCompared;
    public final long unexplainedMs;
    public final String message;

    public AdAnalysis(long mediaId, State state, long declaredDurationMs, int copiesCompared, long unexplainedMs,
                      String message) {
        this.mediaId = mediaId;
        this.state = state;
        this.declaredDurationMs = declaredDurationMs;
        this.copiesCompared = copiesCompared;
        this.unexplainedMs = unexplainedMs;
        this.message = message;
    }

    public static AdAnalysis none(long mediaId) {
        return new AdAnalysis(mediaId, State.NONE, 0, 0, -1, null);
    }

    public AdAnalysis withState(State newState, String newMessage) {
        return new AdAnalysis(mediaId, newState, declaredDurationMs, copiesCompared, unexplainedMs, newMessage);
    }

    public AdAnalysis done(int newCopiesCompared, long newUnexplainedMs) {
        return new AdAnalysis(mediaId, State.DONE, declaredDurationMs, newCopiesCompared, newUnexplainedMs, null);
    }
}
