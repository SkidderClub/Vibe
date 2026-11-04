package dev.vibe.notebot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class NoteSongTest {
    @Test public void readsClassicTimingAndInstrumentOrder() throws Exception {
        NoteSong song = NoteSong.read(new ByteArrayInputStream(fixture(0, 1000, 1, 45)),
                "Classic.nbs", 1, false, false);
        assertEquals("Classic.nbs", song.name);
        assertEquals(4, song.lastTick);
        assertEquals(NoteSong.Instrument.BASS, song.beats().get(0).get(0).instrument);
        assertEquals(12, song.beats().get(0).get(0).pitch);
        assertEquals(1, song.beats().get(0).get(0).soundPitch(), 0);
        assertTrue(song.beats().containsKey(4));
    }

    @Test public void modernNotesFitTheLegacyRangeAndRespectInstrumentFallback() throws Exception {
        for (int version : new int[]{1, 2, 3, 4, 5, 6}) {
            byte[] bytes = fixture(version, 1000, 15, 21);
            NoteSong song = NoteSong.read(new ByteArrayInputStream(bytes), "Modern.nbs", 2, true, true);
            NoteSong.Note first = song.beats().get(0).get(0);
            assertEquals(NoteSong.Instrument.HARP, first.instrument);
            assertEquals(version >= 4 ? 1 : 0, first.pitch);
            assertEquals(version >= 4 ? 72 : 100, first.velocity);
            assertTrue(song.beats().containsKey(2));
            try {
                NoteSong.read(new ByteArrayInputStream(bytes), "Modern.nbs", 1, true, false);
                fail("New instruments should be skipped when requested");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("No playable notes"));
            }
        }
    }

    @Test(expected = IOException.class)
    public void truncatedSongFailsBeforePlayback() throws Exception {
        byte[] valid = fixture(5, 1000, 0, 45);
        NoteSong.read(new ByteArrayInputStream(Arrays.copyOf(valid, valid.length - 1)),
                "Truncated.nbs", 1, true, true);
    }

    @Test public void exactMatchesAreReservedBeforeTuningAndWrapIn25Steps() {
        NoteSong.Instrument harp = NoteSong.Instrument.HARP, bass = NoteSong.Instrument.BASS;
        assertEquals(1, NoteBlockPlan.clicks(24, 0));
        assertEquals(24, NoteBlockPlan.clicks(0, 24));
        Map<Integer, String> exact = NoteBlockPlan.assign(Arrays.asList(0, 24, 37), Arrays.asList(
                new NoteBlockPlan.Block<>("harp24", harp, 24),
                new NoteBlockPlan.Block<>("harp22", harp, 22),
                new NoteBlockPlan.Block<>("bass12", bass, 12)), false, true);
        assertEquals("harp22", exact.get(0));
        assertEquals("harp24", exact.get(24));
        assertEquals("bass12", exact.get(37));
        assertTrue(NoteBlockPlan.assign(Arrays.asList(12), Arrays.asList(
                new NoteBlockPlan.Block<>("bass12", bass, 12)), false, false).isEmpty());
        assertEquals("bass12", NoteBlockPlan.assign(Arrays.asList(12), Arrays.asList(
                new NoteBlockPlan.Block<>("bass12", bass, 12)), true, false).get(12));
    }

    static byte[] fixture(int version, int tempo, int instrument, int key) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        shortLE(data, version == 0 ? 3 : 0);
        if (version != 0) {
            data.writeByte(version);
            data.writeByte(16);
            if (version >= 3) shortLE(data, 3);
        }
        shortLE(data, 1);
        data.write(new byte[16]);
        shortLE(data, tempo);
        data.write(new byte[27]);
        if (version >= 4) data.write(new byte[4]);
        for (int delta : new int[]{1, 2}) {
            shortLE(data, delta);
            shortLE(data, 1);
            data.writeByte(instrument);
            data.writeByte(key);
            if (version >= 4) {
                data.writeByte(72);
                data.writeByte(100);
                shortLE(data, 100);
            }
            shortLE(data, 0);
        }
        shortLE(data, 0);
        return bytes.toByteArray();
    }

    private static void shortLE(DataOutputStream data, int value) throws IOException {
        data.writeByte(value & 255);
        data.writeByte(value >>> 8);
    }
}
