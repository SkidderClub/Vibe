package dev.vibe.notebot;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Classic and OpenNBS songs, quantized to Minecraft's 20 Hz tick clock. */
public final class NoteSong {
    public enum Instrument {
        HARP("harp"), BASS("bassattack"), BASS_DRUM("bd"), SNARE("snare"), HAT("hat");

        public final String sound;

        Instrument(String sound) {
            this.sound = "note." + sound;
        }
    }

    public static final class Note {
        public final Instrument instrument;
        public final int pitch;
        public final int velocity;

        private Note(Instrument instrument, int pitch, int velocity) {
            this.instrument = instrument;
            this.pitch = pitch;
            this.velocity = velocity;
        }

        public int key(boolean anyInstrument) {
            return (anyInstrument ? 0 : instrument.ordinal() * 25) + pitch;
        }

        public float soundPitch() {
            return (float) Math.pow(2, (pitch - 12) / 12.0);
        }
    }

    private static final int MAX_TICKS = 20 * 60 * 60;
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final Instrument[] INSTRUMENTS = Instrument.values();
    private final Map<Integer, List<Note>> beats;
    public final String name;
    public final int lastTick;
    public final int skippedNotes;

    private NoteSong(String name, Map<Integer, List<Note>> beats, int lastTick, int skippedNotes) {
        this.name = name;
        this.beats = Collections.unmodifiableMap(beats);
        this.lastTick = lastTick;
        this.skippedNotes = skippedNotes;
    }

    public Map<Integer, List<Note>> beats() { return beats; }

    public static NoteSong read(Path path, double speed, boolean fitOctaves, boolean replaceInstruments) throws IOException {
        if (Files.size(path) > MAX_BYTES) throw new IOException("Song exceeds 8 MiB.");
        try (InputStream input = Files.newInputStream(path)) {
            return read(input, path.getFileName().toString(), speed, fitOctaves, replaceInstruments);
        }
    }

    static NoteSong read(InputStream input, String filename, double speed, boolean fitOctaves,
                         boolean replaceInstruments) throws IOException {
        DataInputStream data = new DataInputStream(new BufferedInputStream(input));
        int length = u16(data);
        int version = 0;
        if (length == 0) {
            version = data.readUnsignedByte();
            if (version < 1 || version > 6) throw new IOException("Unsupported NBS version " + version + ".");
            data.readUnsignedByte(); // Vanilla instrument count.
            if (version >= 3) length = u16(data);
        }
        int layers = u16(data);
        String name = string(data);
        string(data); // Author.
        string(data); // Original author.
        string(data); // Description.
        int tempo = u16(data);
        if (tempo == 0 || speed <= 0 || !Double.isFinite(speed)) throw new IOException("Invalid song tempo.");
        // Autosave, autosave interval, time signature and five editing statistics.
        byte[] editor = new byte[23];
        data.readFully(editor);
        string(data); // Imported filename.
        if (version >= 4) data.readFully(new byte[4]); // Editor loop metadata.

        Map<Integer, List<Note>> beats = new TreeMap<>();
        int tick = -1, skipped = 0, count = 0;
        double ticksPerStep = 2000.0 / (tempo * speed);
        for (int delta; (delta = u16(data)) != 0;) {
            tick += delta;
            int gameTick = gameTick(tick, ticksPerStep);
            int layer = -1;
            for (int step; (step = u16(data)) != 0;) {
                layer += step;
                if (layer >= layers || ++count > 250_000) throw new IOException("Invalid NBS note data.");
                int instrument = data.readUnsignedByte();
                int pitch = data.readUnsignedByte() - 33;
                int velocity = 100;
                if (version >= 4) {
                    velocity = Math.min(100, data.readUnsignedByte());
                    data.readUnsignedByte(); // Panning.
                    pitch += Math.round((short) u16(data) / 100.0F);
                }
                if (velocity == 0) continue;
                if (fitOctaves) {
                    if (pitch < 0) pitch = Math.floorMod(pitch, 12);
                    if (pitch > 24) pitch = 13 + Math.floorMod(pitch - 13, 12);
                }
                if (pitch < 0 || pitch > 24 || (instrument >= INSTRUMENTS.length && !replaceInstruments)) {
                    skipped++;
                    continue;
                }
                Instrument voice = instrument < INSTRUMENTS.length ? INSTRUMENTS[instrument] : Instrument.HARP;
                List<Note> beat = beats.computeIfAbsent(gameTick, unused -> new ArrayList<>());
                if (beat.size() >= 128) throw new IOException("Too many simultaneous notes.");
                beat.add(new Note(voice, pitch, velocity));
            }
        }
        if (beats.isEmpty()) throw new IOException("No playable notes for the current instrument and pitch settings.");
        beats.replaceAll((time, notes) -> Collections.unmodifiableList(notes));
        int end = gameTick(Math.max(tick, length - 1), ticksPerStep);
        return new NoteSong(name.trim().isEmpty() ? filename : name.trim(), beats, end, skipped);
    }

    private static int gameTick(int tick, double ticksPerStep) throws IOException {
        double time = tick * ticksPerStep;
        if (time > MAX_TICKS) throw new IOException("Song exceeds one hour at the selected speed.");
        return (int) Math.round(time);
    }

    private static int u16(DataInputStream data) throws IOException {
        return data.readUnsignedByte() | data.readUnsignedByte() << 8;
    }

    private static String string(DataInputStream data) throws IOException {
        int length = Integer.reverseBytes(data.readInt());
        if (length < 0 || length > 65_536) throw new IOException("Invalid NBS string length.");
        byte[] value = new byte[length];
        data.readFully(value);
        return new String(value, StandardCharsets.UTF_8);
    }
}
