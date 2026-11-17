package de.danoeh.antennapod.adskip.detector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;

final class SyntheticMp3 {
    static final int FRAME_BYTES = 417;
    static final double FRAME_MS = 1152 * 1000.0 / 44100;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    SyntheticMp3 id3Tag(int payloadBytes) {
        bytes.writeBytes(new byte[]{'I', 'D', '3', 4, 0, 0,
                (byte) ((payloadBytes >> 21) & 0x7F), (byte) ((payloadBytes >> 14) & 0x7F),
                (byte) ((payloadBytes >> 7) & 0x7F), (byte) (payloadBytes & 0x7F)});
        bytes.writeBytes(new byte[payloadBytes]);
        return this;
    }

    SyntheticMp3 frames(String content, int from, int to) {
        for (int i = from; i < to; i++) {
            byte[] frame = new byte[FRAME_BYTES];
            new Random((content + i).hashCode()).nextBytes(frame);
            frame[0] = (byte) 0xFF;
            frame[1] = (byte) 0xFB;
            frame[2] = (byte) 0x90;
            frame[3] = (byte) 0x64;
            bytes.writeBytes(frame);
        }
        return this;
    }

    SyntheticMp3 clipTagFrame(int followingFrames) {
        byte[] frame = new byte[FRAME_BYTES];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        frame[2] = (byte) 0x90;
        frame[3] = (byte) 0x64;
        ByteBuffer tag = ByteBuffer.wrap(frame, 36, FRAME_BYTES - 36);
        tag.putInt(0x55555555).putInt(0x0F).putInt(followingFrames).putInt((followingFrames + 1) * FRAME_BYTES);
        System.arraycopy("Lavc".getBytes(StandardCharsets.US_ASCII), 0, frame, 36 + 120, 4);
        bytes.writeBytes(frame);
        return this;
    }

    ByteArrayInputStream stream() {
        return new ByteArrayInputStream(bytes.toByteArray());
    }

    static long frameMs(int frames) {
        return (long) (frames * FRAME_MS);
    }
}
