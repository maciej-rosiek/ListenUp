package de.danoeh.antennapod.adskip.detector;

import java.io.IOException;

public interface AdDetector {
    DetectionResult detect(EpisodeAudio episode) throws IOException;
}
