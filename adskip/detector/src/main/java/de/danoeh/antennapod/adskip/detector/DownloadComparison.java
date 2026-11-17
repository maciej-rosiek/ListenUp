package de.danoeh.antennapod.adskip.detector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DownloadComparison {
    public static final int MIN_SHARED_BLOCK_FRAMES = 200;
    public static final int MIN_AD_FRAMES = 77;

    private final List<FrameHashes> copies;
    private final List<boolean[]> sharedWithAllOthers = new ArrayList<>();

    private DownloadComparison(List<FrameHashes> copies) {
        this.copies = copies;
    }

    public static DownloadComparison of(List<FrameHashes> copies) {
        DownloadComparison comparison = new DownloadComparison(copies);
        comparison.compareAllPairs();
        return comparison;
    }

    private void compareAllPairs() {
        for (FrameHashes copy : copies) {
            boolean[] shared = new boolean[copy.size()];
            Arrays.fill(shared, true);
            sharedWithAllOthers.add(shared);
        }
        for (int i = 0; i < copies.size(); i++) {
            for (int j = i + 1; j < copies.size(); j++) {
                List<MatchBlock> blocks = SequenceAligner.align(copies.get(i).hashes(), copies.get(j).hashes());
                keepOnlyCovered(sharedWithAllOthers.get(i), SequenceAligner.coveredByLongBlocks(
                        copies.get(i).size(), blocks, MIN_SHARED_BLOCK_FRAMES, true));
                keepOnlyCovered(sharedWithAllOthers.get(j), SequenceAligner.coveredByLongBlocks(
                        copies.get(j).size(), blocks, MIN_SHARED_BLOCK_FRAMES, false));
            }
        }
    }

    private static void keepOnlyCovered(boolean[] shared, boolean[] covered) {
        for (int i = 0; i < shared.length; i++) {
            shared[i] &= covered[i];
        }
    }

    public List<FrameSpan> adSpansOf(int copyIndex) {
        boolean[] shared = sharedWithAllOthers.get(copyIndex);
        List<FrameSpan> spans = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= shared.length; i++) {
            boolean isAd = i < shared.length && !shared[i];
            if (isAd && start < 0) {
                start = i;
            } else if (!isAd && start >= 0) {
                if (i - start >= MIN_AD_FRAMES) {
                    spans.add(new FrameSpan(start, i));
                }
                start = -1;
            }
        }
        return spans;
    }

    public List<long[]> adSequencesOfAllCopies() {
        List<long[]> sequences = new ArrayList<>();
        for (int i = 0; i < copies.size(); i++) {
            for (FrameSpan span : adSpansOf(i)) {
                sequences.add(copies.get(i).slice(span));
            }
        }
        return sequences;
    }
}
