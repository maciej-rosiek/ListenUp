package de.danoeh.antennapod.adskip.detector;

import java.io.IOException;
import java.io.InputStream;

public final class EpisodeAudio {
    public interface StreamOpener {
        InputStream open() throws IOException;
    }

    public interface CopyOpener {
        InputStream openCopy(int copyIndex) throws IOException;
    }

    public final StreamOpener playedFile;
    public final CopyOpener extraCopies;
    public final long declaredDurationMs;

    public EpisodeAudio(StreamOpener playedFile, CopyOpener extraCopies, long declaredDurationMs) {
        this.playedFile = playedFile;
        this.extraCopies = extraCopies;
        this.declaredDurationMs = declaredDurationMs;
    }
}
