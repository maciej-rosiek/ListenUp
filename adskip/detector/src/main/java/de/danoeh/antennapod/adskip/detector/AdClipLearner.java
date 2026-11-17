package de.danoeh.antennapod.adskip.detector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class AdClipLearner {
    public static final int MIN_CLIP_FRAMES = 115;

    private AdClipLearner() {
    }

    public static List<long[]> clips(List<long[]> adSequences) {
        Set<ClipKey> clips = new LinkedHashSet<>();
        for (int i = 0; i < adSequences.size(); i++) {
            long[] sequence = adSequences.get(i);
            TreeSet<Integer> cuts = new TreeSet<>(List.of(0, sequence.length));
            for (int j = 0; j < adSequences.size(); j++) {
                if (i == j) {
                    continue;
                }
                for (MatchBlock block : SequenceAligner.align(sequence, adSequences.get(j))) {
                    if (block.length >= MIN_CLIP_FRAMES) {
                        cuts.add(block.startA);
                        cuts.add(block.endA());
                    }
                }
            }
            Integer previous = null;
            for (int cut : cuts) {
                if (previous != null && cut - previous >= MIN_CLIP_FRAMES) {
                    clips.add(new ClipKey(Arrays.copyOfRange(sequence, previous, cut)));
                }
                previous = cut;
            }
        }
        List<long[]> result = new ArrayList<>();
        for (ClipKey clip : clips) {
            result.add(clip.hashes);
        }
        return result;
    }

    public static List<long[]> withoutKnown(List<long[]> clips, List<KnownAd> knownAds) {
        List<long[]> unknown = new ArrayList<>();
        for (long[] clip : clips) {
            KnownAdMatcher matcher = new KnownAdMatcher(clip);
            boolean known = knownAds.stream().anyMatch(ad -> matcher.find(ad).stream()
                    .anyMatch(match -> match.span.length() >= clip.length * 0.9));
            if (!known) {
                unknown.add(clip);
            }
        }
        return unknown;
    }

    private static final class ClipKey {
        final long[] hashes;

        ClipKey(long[] hashes) {
            this.hashes = hashes;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ClipKey && Arrays.equals(hashes, ((ClipKey) other).hashes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(hashes);
        }
    }
}
