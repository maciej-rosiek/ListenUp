package de.danoeh.antennapod.adskip.detector;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class Mp3FrameReaderTest {

    @Test
    public void readsEveryFrameAndSkipsId3Tags() throws Exception {
        // given
        SyntheticMp3 file = new SyntheticMp3()
                .id3Tag(5000)
                .frames("show", 0, 10)
                .id3Tag(300)
                .frames("show", 10, 15);

        // when
        FrameHashes frames = Mp3FrameReader.read(file.stream());

        // then
        assertEquals(15, frames.size());
        assertEquals(Math.round(15 * 1152 * 1_000_000.0 / 44100), frames.durationUs());
        assertNotEquals(frames.hashes()[0], frames.hashes()[1]);
    }
}
