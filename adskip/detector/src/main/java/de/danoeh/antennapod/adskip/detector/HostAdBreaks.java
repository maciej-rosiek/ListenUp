package de.danoeh.antennapod.adskip.detector;

import java.util.ArrayList;
import java.util.List;

public final class HostAdBreaks {
    public static final String MEGAPHONE_HEADER = "x-megaphone-payload-2";
    private static final int FIELD_LAST_BYTE = 1;
    private static final int FIELD_FIRST_BYTE = 4;

    private HostAdBreaks() {
    }

    public static List<FrameSpan> framesOf(String megaphoneHeader, FrameHashes played) {
        List<FrameSpan> spans = new ArrayList<>();
        if (megaphoneHeader == null || megaphoneHeader.isEmpty()) {
            return spans;
        }
        String slots = megaphoneHeader.split("@", 2)[0];
        for (String slot : slots.split(",")) {
            String[] fields = slot.split("#", -1);
            if (fields.length <= FIELD_FIRST_BYTE) {
                continue;
            }
            try {
                long firstByte = Long.parseLong(fields[FIELD_FIRST_BYTE]);
                long endByte = Long.parseLong(fields[FIELD_LAST_BYTE]) + 1;
                if (endByte > firstByte) {
                    spans.add(played.framesWithin(firstByte, endByte));
                }
            } catch (NumberFormatException ignored) {
                spans.clear();
                return spans;
            }
        }
        return spans;
    }
}
