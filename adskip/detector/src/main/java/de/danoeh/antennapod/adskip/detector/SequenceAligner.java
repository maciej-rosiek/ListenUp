package de.danoeh.antennapod.adskip.detector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SequenceAligner {

    private SequenceAligner() {
    }

    public static List<MatchBlock> align(long[] a, long[] b) {
        List<MatchBlock> blocks = new ArrayList<>();
        Deque<Range> pending = new ArrayDeque<>();
        pending.push(new Range(0, a.length, 0, b.length));
        while (!pending.isEmpty()) {
            alignRange(a, b, pending.pop(), blocks, pending);
        }
        blocks.sort(Comparator.comparingInt(block -> block.startA));
        return joinTouchingBlocks(blocks);
    }

    private static void alignRange(long[] a, long[] b, Range range, List<MatchBlock> blocks, Deque<Range> pending) {
        if (range.isEmpty()) {
            return;
        }
        int[][] anchors = uniqueCommonAnchors(a, b, range);
        if (anchors.length == 0) {
            alignByCommonEnds(a, b, range, blocks, pending);
            return;
        }
        int previousEndA = range.startA;
        int previousEndB = range.startB;
        for (int[] anchor : anchors) {
            if (anchor[0] < previousEndA || anchor[1] < previousEndB) {
                continue;
            }
            int startA = anchor[0];
            int startB = anchor[1];
            while (startA > previousEndA && startB > previousEndB && a[startA - 1] == b[startB - 1]) {
                startA--;
                startB--;
            }
            int endA = anchor[0];
            int endB = anchor[1];
            while (endA < range.endA && endB < range.endB && a[endA] == b[endB]) {
                endA++;
                endB++;
            }
            pending.push(new Range(previousEndA, startA, previousEndB, startB));
            blocks.add(new MatchBlock(startA, startB, endA - startA));
            previousEndA = endA;
            previousEndB = endB;
        }
        pending.push(new Range(previousEndA, range.endA, previousEndB, range.endB));
    }

    private static void alignByCommonEnds(long[] a, long[] b, Range range, List<MatchBlock> blocks,
                                          Deque<Range> pending) {
        int prefix = 0;
        while (range.startA + prefix < range.endA && range.startB + prefix < range.endB
                && a[range.startA + prefix] == b[range.startB + prefix]) {
            prefix++;
        }
        int suffix = 0;
        while (range.endA - suffix > range.startA + prefix && range.endB - suffix > range.startB + prefix
                && a[range.endA - suffix - 1] == b[range.endB - suffix - 1]) {
            suffix++;
        }
        if (prefix > 0) {
            blocks.add(new MatchBlock(range.startA, range.startB, prefix));
        }
        if (suffix > 0) {
            blocks.add(new MatchBlock(range.endA - suffix, range.endB - suffix, suffix));
        }
        if (prefix > 0 || suffix > 0) {
            pending.push(new Range(range.startA + prefix, range.endA - suffix,
                    range.startB + prefix, range.endB - suffix));
        }
    }

    private static int[][] uniqueCommonAnchors(long[] a, long[] b, Range range) {
        Map<Long, int[]> occurrences = new HashMap<>();
        for (int i = range.startA; i < range.endA; i++) {
            int[] entry = occurrences.computeIfAbsent(a[i], hash -> new int[]{0, 0, 0, 0});
            entry[0]++;
            entry[1] = i;
        }
        for (int i = range.startB; i < range.endB; i++) {
            int[] entry = occurrences.get(b[i]);
            if (entry != null) {
                entry[2]++;
                entry[3] = i;
            }
        }
        List<int[]> pairs = new ArrayList<>();
        for (int[] entry : occurrences.values()) {
            if (entry[0] == 1 && entry[2] == 1) {
                pairs.add(new int[]{entry[1], entry[3]});
            }
        }
        pairs.sort(Comparator.comparingInt(pair -> pair[0]));
        return longestIncreasingInB(pairs);
    }

    private static int[][] longestIncreasingInB(List<int[]> pairs) {
        int count = pairs.size();
        int[] tailIndex = new int[count];
        int[] predecessor = new int[count];
        int length = 0;
        for (int i = 0; i < count; i++) {
            int valueB = pairs.get(i)[1];
            int low = 0;
            int high = length;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (pairs.get(tailIndex[middle])[1] < valueB) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            predecessor[i] = low > 0 ? tailIndex[low - 1] : -1;
            tailIndex[low] = i;
            if (low == length) {
                length++;
            }
        }
        int[][] sequence = new int[length][];
        int index = length > 0 ? tailIndex[length - 1] : -1;
        for (int i = length - 1; i >= 0; i--) {
            sequence[i] = pairs.get(index);
            index = predecessor[index];
        }
        return sequence;
    }

    private static List<MatchBlock> joinTouchingBlocks(List<MatchBlock> blocks) {
        List<MatchBlock> joined = new ArrayList<>();
        for (MatchBlock block : blocks) {
            if (block.length == 0) {
                continue;
            }
            MatchBlock last = joined.isEmpty() ? null : joined.get(joined.size() - 1);
            if (last != null && last.endA() == block.startA && last.endB() == block.startB) {
                joined.set(joined.size() - 1, new MatchBlock(last.startA, last.startB, last.length + block.length));
            } else {
                joined.add(block);
            }
        }
        return joined;
    }

    public static boolean[] coveredByLongBlocks(int length, List<MatchBlock> blocks, int minBlockLength,
                                                boolean sideA) {
        boolean[] covered = new boolean[length];
        for (MatchBlock block : blocks) {
            if (block.length >= minBlockLength) {
                int start = sideA ? block.startA : block.startB;
                Arrays.fill(covered, start, start + block.length, true);
            }
        }
        return covered;
    }

    private static final class Range {
        final int startA;
        final int endA;
        final int startB;
        final int endB;

        Range(int startA, int endA, int startB, int endB) {
            this.startA = startA;
            this.endA = endA;
            this.startB = startB;
            this.endB = endB;
        }

        boolean isEmpty() {
            return startA >= endA || startB >= endB;
        }
    }
}
