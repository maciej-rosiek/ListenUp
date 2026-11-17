package de.danoeh.antennapod.adskip.detector;

import java.util.ArrayList;
import java.util.List;

final class InMemoryKnownAdLibrary implements KnownAdLibrary {
    private final List<KnownAd> ads = new ArrayList<>();

    @Override
    public List<KnownAd> all() {
        return new ArrayList<>(ads);
    }

    @Override
    public void add(List<long[]> adClips) {
        for (long[] clip : adClips) {
            ads.add(new KnownAd(ads.size() + 1, clip));
        }
    }

    @Override
    public void markSeen(long knownAdId) {
    }
}
