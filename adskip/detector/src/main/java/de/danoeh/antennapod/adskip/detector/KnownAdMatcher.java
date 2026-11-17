package de.danoeh.antennapod.adskip.detector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class KnownAdMatcher {
    private static final int MAX_POSITIONS_PER_HASH = 16;
    private static final double MIN_VOTE_SHARE = 0.3;
    private static final double MIN_EQUAL_SHARE = 0.9;

    private final long[] played;
    private final Map<Long, int[]> positionsByHash = new HashMap<>();

    public KnownAdMatcher(long[] played) {
        this.played = played;
        Map<Long, Integer> counts = new HashMap<>();
        for (long hash : played) {
            counts.merge(hash, 1, Integer::sum);
        }
        Map<Long, Integer> filled = new HashMap<>();
        for (int i = 0; i < played.length; i++) {
            int count = counts.get(played[i]);
            if (count > MAX_POSITIONS_PER_HASH) {
                continue;
            }
            int[] positions = positionsByHash.computeIfAbsent(played[i], hash -> new int[count]);
            int index = filled.merge(played[i], 1, Integer::sum) - 1;
            positions[index] = i;
        }
    }

    public List<Match> find(KnownAd ad) {
        Map<Integer, Integer> votesByOffset = new HashMap<>();
        for (int i = 0; i < ad.hashes.length; i++) {
            int[] positions = positionsByHash.get(ad.hashes[i]);
            if (positions == null) {
                continue;
            }
            for (int position : positions) {
                votesByOffset.merge(position - i, 1, Integer::sum);
            }
        }
        List<Match> matches = new ArrayList<>();
        int minVotes = Math.max(1, (int) (ad.hashes.length * MIN_VOTE_SHARE));
        votesByOffset.entrySet().stream()
                .filter(entry -> entry.getValue() >= minVotes)
                .sorted((first, second) -> Integer.compare(second.getValue(), first.getValue()))
                .forEach(entry -> {
                    FrameSpan span = new FrameSpan(Math.max(0, entry.getKey()),
                            Math.min(played.length, entry.getKey() + ad.hashes.length));
                    if (countEqual(ad, entry.getKey()) >= ad.hashes.length * MIN_EQUAL_SHARE
                            && matches.stream().noneMatch(match -> match.span.overlaps(span))) {
                        matches.add(new Match(ad.id, span));
                    }
                });
        return matches;
    }

    private int countEqual(KnownAd ad, int offset) {
        int equal = 0;
        for (int i = 0; i < ad.hashes.length; i++) {
            int position = offset + i;
            if (position >= 0 && position < played.length && played[position] == ad.hashes[i]) {
                equal++;
            }
        }
        return equal;
    }

    public static final class Match {
        public final long knownAdId;
        public final FrameSpan span;

        public Match(long knownAdId, FrameSpan span) {
            this.knownAdId = knownAdId;
            this.span = span;
        }
    }
}
