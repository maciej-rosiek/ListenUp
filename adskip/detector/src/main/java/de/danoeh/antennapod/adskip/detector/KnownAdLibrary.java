package de.danoeh.antennapod.adskip.detector;

import java.util.List;

public interface KnownAdLibrary {
    List<KnownAd> all();

    void add(List<long[]> adClips);

    void markSeen(long knownAdId);
}
