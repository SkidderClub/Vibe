package dev.vibe.phone;

import java.io.*;
import java.util.function.Supplier;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.*;

/** Local MP3 decoding and audio-device work never run on the game thread. */
public final class PhoneCallAudio implements PhoneCallPlayer {
    public static final String RESOURCE = "/assets/vibe/phone/call-ringing.mp3";
    private final Supplier<AudioDevice> devices;
    private volatile Session current;

    public PhoneCallAudio() { this(JavaSoundAudioDevice::new); }
    PhoneCallAudio(Supplier<AudioDevice> devices) { this.devices = devices; }

    private static final class Session {
        volatile boolean cancelled, finished;
        volatile float volume;
        volatile Player player;
        volatile String error = "";
    }

    @Override public void start(float volume) {
        close();
        Session session = new Session(); session.volume = gain(volume); current = session;
        Thread worker = new Thread(() -> play(session), "VibeOS-Call");
        worker.setDaemon(true); worker.start();
    }
    private void play(Session session) {
        try (InputStream resource = PhoneCallAudio.class.getResourceAsStream(RESOURCE)) {
            if (resource == null) throw new IOException("Missing call audio");
            try (InputStream input = new BufferedInputStream(resource)) {
                AudioDevice device = devices.get();
                // Scaling PCM also supports mixers that have no hardware gain control.
                AudioDevice scaled = new AudioDevice() {
                    @Override public void open(javazoom.jl.decoder.Decoder decoder) throws JavaLayerException { device.open(decoder); }
                    @Override public boolean isOpen() { return device.isOpen(); }
                    @Override public void write(short[] samples, int offset, int length) throws JavaLayerException {
                        float volume = session.volume;
                        for (int i = offset; i < offset + length; i++) samples[i] = (short) Math.round(samples[i] * volume);
                        if (!session.cancelled) device.write(samples, offset, length);
                    }
                    @Override public void flush() { if (!session.cancelled) device.flush(); }
                    @Override public void close() { device.close(); }
                    @Override public int getPosition() { return device.getPosition(); }
                };
                try {
                    Player player = new Player(input, scaled); session.player = player;
                    if (session.cancelled) player.close(); else player.play();
                } finally { scaled.close(); }
            }
        } catch (IOException | JavaLayerException | RuntimeException failure) {
            if (!session.cancelled) session.error = "Call audio unavailable";
        } finally {
            Player player = session.player;
            if (player != null) try { player.close(); } catch (RuntimeException ignored) { }
            session.finished = true;
        }
    }
    private static float gain(float volume) { return Math.max(0, Math.min(1, volume)); }
    @Override public void setVolume(float volume) { Session session = current; if (session != null) session.volume = gain(volume); }
    @Override public boolean isFinished() { Session session = current; return session != null && session.finished; }
    @Override public String error() { Session session = current; return session == null ? "" : session.error; }
    @Override public void close() {
        Session session = current; current = null;
        if (session == null) return;
        session.cancelled = true;
        Thread closer = new Thread(() -> {
            Player player = session.player;
            if (player != null) try { player.close(); } catch (RuntimeException ignored) { }
        }, "VibeOS-Call-Close");
        closer.setDaemon(true); closer.start();
    }
}
