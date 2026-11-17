package de.danoeh.antennapod.adskip.detector;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Mp3FrameReader {
    private static final int[] MPEG1_KBPS = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320};
    private static final int[] MPEG2_KBPS = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160};
    private static final int[] MPEG1_SAMPLE_RATES = {44100, 48000, 32000};
    private static final int HEADER_BYTES = 4;
    private static final int ID3_HEADER_BYTES = 10;
    private static final int BUFFER_BYTES = 64 * 1024;
    private static final int DIGEST_BYTES = 16;
    private static final int CLIP_TAG_ENCODER_OFFSET = 120;

    private final InputStream in;
    private final byte[] buffer = new byte[BUFFER_BYTES];
    private int position = 0;
    private int limit = 0;
    private long bufferStartOffset = 0;
    private boolean endOfStream = false;

    private final MessageDigest digest;
    private final byte[] digestOutput = new byte[DIGEST_BYTES];
    private long[] hashes = new long[1 << 16];
    private long[] startUs = new long[1 << 16];
    private long[] byteOffsets = new long[1 << 16];
    private final List<FrameSpan> taggedClips = new ArrayList<>();
    private int frameCount = 0;
    private double elapsedUs = 0;

    private Mp3FrameReader(InputStream in) {
        this.in = in;
        try {
            this.digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static FrameHashes read(InputStream in) throws IOException {
        return new Mp3FrameReader(in).readAll();
    }

    private FrameHashes readAll() throws IOException {
        boolean inSync = false;
        while (fill(HEADER_BYTES) >= HEADER_BYTES) {
            if (isId3Tag()) {
                skipId3Tag();
                inSync = false;
                continue;
            }
            FrameHeader header = FrameHeader.parse(buffer, position);
            if (header == null || fill(header.frameBytes) < header.frameBytes) {
                position++;
                inSync = false;
                continue;
            }
            if (!inSync && !isConfirmedByNextHeader(header)) {
                position++;
                continue;
            }
            inSync = true;
            if (frameCount == 0 && header.isVbrInfoFrame(buffer, position)) {
                position += header.frameBytes;
                continue;
            }
            addFrame(header);
            int clipFrames = header.clipTagFrameCount(buffer, position);
            if (clipFrames > 0) {
                taggedClips.add(new FrameSpan(frameCount - 1, frameCount + clipFrames));
            }
            position += header.frameBytes;
        }
        List<FrameSpan> clipsWithinFile = new ArrayList<>();
        for (FrameSpan clip : taggedClips) {
            clipsWithinFile.add(new FrameSpan(clip.start, Math.min(clip.end, frameCount)));
        }
        return new FrameHashes(Arrays.copyOf(hashes, frameCount), Arrays.copyOf(startUs, frameCount),
                Arrays.copyOf(byteOffsets, frameCount), Math.round(elapsedUs), clipsWithinFile);
    }

    private boolean isConfirmedByNextHeader(FrameHeader header) throws IOException {
        int needed = header.frameBytes + HEADER_BYTES;
        if (fill(needed) < needed) {
            return true;
        }
        FrameHeader next = FrameHeader.parse(buffer, position + header.frameBytes);
        return next != null && next.sampleRate == header.sampleRate;
    }

    private void addFrame(FrameHeader header) {
        if (frameCount == hashes.length) {
            hashes = Arrays.copyOf(hashes, frameCount * 2);
            startUs = Arrays.copyOf(startUs, frameCount * 2);
            byteOffsets = Arrays.copyOf(byteOffsets, frameCount * 2);
        }
        digest.update(buffer, position, header.frameBytes);
        try {
            digest.digest(digestOutput, 0, DIGEST_BYTES);
        } catch (DigestException e) {
            throw new IllegalStateException(e);
        }
        long hash = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            hash = (hash << 8) | (digestOutput[i] & 0xFF);
        }
        hashes[frameCount] = hash;
        startUs[frameCount] = Math.round(elapsedUs);
        byteOffsets[frameCount] = bufferStartOffset + position;
        frameCount++;
        elapsedUs += header.samples * 1_000_000.0 / header.sampleRate;
    }

    private boolean isId3Tag() {
        return buffer[position] == 'I' && buffer[position + 1] == 'D' && buffer[position + 2] == '3';
    }

    private void skipId3Tag() throws IOException {
        if (fill(ID3_HEADER_BYTES) < ID3_HEADER_BYTES) {
            skip(limit - position);
            return;
        }
        int size = ((buffer[position + 6] & 0x7F) << 21) | ((buffer[position + 7] & 0x7F) << 14)
                | ((buffer[position + 8] & 0x7F) << 7) | (buffer[position + 9] & 0x7F);
        boolean hasFooter = (buffer[position + 5] & 0x10) != 0;
        skip(ID3_HEADER_BYTES + size + (hasFooter ? ID3_HEADER_BYTES : 0));
    }

    private void skip(long bytes) throws IOException {
        long inBuffer = Math.min(bytes, limit - position);
        position += (int) inBuffer;
        long remaining = bytes - inBuffer;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                endOfStream = true;
                break;
            }
            bufferStartOffset += read;
            remaining -= read;
        }
    }

    private int fill(int needed) throws IOException {
        int available = limit - position;
        if (available >= needed || endOfStream) {
            return available;
        }
        System.arraycopy(buffer, position, buffer, 0, available);
        bufferStartOffset += position;
        limit = available;
        position = 0;
        while (limit < needed) {
            int read = in.read(buffer, limit, buffer.length - limit);
            if (read < 0) {
                endOfStream = true;
                break;
            }
            limit += read;
        }
        return limit - position;
    }

    private static final class FrameHeader {
        final int frameBytes;
        final int samples;
        final int sampleRate;
        final boolean mpeg1;
        final boolean mono;

        private FrameHeader(int frameBytes, int samples, int sampleRate, boolean mpeg1, boolean mono) {
            this.frameBytes = frameBytes;
            this.samples = samples;
            this.sampleRate = sampleRate;
            this.mpeg1 = mpeg1;
            this.mono = mono;
        }

        static FrameHeader parse(byte[] data, int offset) {
            int b1 = data[offset + 1] & 0xFF;
            int b2 = data[offset + 2] & 0xFF;
            int b3 = data[offset + 3] & 0xFF;
            if ((data[offset] & 0xFF) != 0xFF || (b1 & 0xE0) != 0xE0) {
                return null;
            }
            int version = (b1 >> 3) & 0x03;
            int layer = (b1 >> 1) & 0x03;
            int bitrateIndex = b2 >> 4;
            int sampleRateIndex = (b2 >> 2) & 0x03;
            if (version == 1 || layer != 1 || bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3) {
                return null;
            }
            boolean mpeg1 = version == 3;
            int divisor = mpeg1 ? 1 : (version == 2 ? 2 : 4);
            int sampleRate = MPEG1_SAMPLE_RATES[sampleRateIndex] / divisor;
            int bitrate = (mpeg1 ? MPEG1_KBPS : MPEG2_KBPS)[bitrateIndex] * 1000;
            int padding = (b2 >> 1) & 0x01;
            int frameBytes = (mpeg1 ? 144 : 72) * bitrate / sampleRate + padding;
            boolean mono = (b3 >> 6) == 3;
            return new FrameHeader(frameBytes, mpeg1 ? 1152 : 576, sampleRate, mpeg1, mono);
        }

        int clipTagFrameCount(byte[] data, int offset) {
            int tagOffset = offset + HEADER_BYTES + sideInfoBytes();
            if (tagOffset + CLIP_TAG_ENCODER_OFFSET + 4 > offset + frameBytes) {
                return -1;
            }
            int flags = readInt(data, tagOffset + 4);
            int frames = readInt(data, tagOffset + 8);
            int bytes = readInt(data, tagOffset + 12);
            int encoderOffset = tagOffset + CLIP_TAG_ENCODER_OFFSET;
            boolean knownEncoder = hasTag(data, encoderOffset, "Lavc") || hasTag(data, encoderOffset, "Lavf")
                    || hasTag(data, encoderOffset, "LAME");
            if ((flags & 0x03) != 0x03 || !knownEncoder || frames <= 0) {
                return -1;
            }
            long expectedBytes = (long) (frames + 1) * frameBytes;
            return Math.abs(bytes - expectedBytes) < frames + 1 + frameBytes ? frames : -1;
        }

        private int sideInfoBytes() {
            return mpeg1 ? (mono ? 17 : 32) : (mono ? 9 : 17);
        }

        private static int readInt(byte[] data, int offset) {
            return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                    | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
        }

        boolean isVbrInfoFrame(byte[] data, int offset) {
            int tagOffset = offset + HEADER_BYTES + sideInfoBytes();
            return hasTag(data, tagOffset, "Xing") || hasTag(data, tagOffset, "Info")
                    || hasTag(data, offset + HEADER_BYTES + 32, "VBRI");
        }

        private boolean hasTag(byte[] data, int offset, String tag) {
            if (offset + tag.length() > data.length) {
                return false;
            }
            for (int i = 0; i < tag.length(); i++) {
                if (data[offset + i] != tag.charAt(i)) {
                    return false;
                }
            }
            return true;
        }
    }
}
