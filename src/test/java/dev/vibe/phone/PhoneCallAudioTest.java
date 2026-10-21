package dev.vibe.phone;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhoneCallAudioTest {
    @Test public void decodesTheBundledClipAndWaitsForTheFinalSamplesToDrain() throws Exception {
        Sink sink = new Sink(); PhoneCallAudio audio = new PhoneCallAudio(() -> sink);
        try {
            audio.start(0);
            assertTrue(sink.draining.await(10, TimeUnit.SECONDS));
            assertFalse(audio.isFinished()); assertTrue(sink.samples > 10000); assertEquals(0, sink.peak);
            sink.release.countDown(); await(audio);
            assertEquals("", audio.error());
            System.out.println("Bundled Phone call MP3: " + sink.samples / (double) sink.channels / sink.rate + " seconds decoded; EOF waits for audio drain.");
        } finally { sink.release.countDown(); audio.close(); }
    }
    @Test public void stoppingAnOldCallCannotCompleteTheNextCall() throws Exception {
        Sink first = new Sink(), second = new Sink(); AtomicInteger starts = new AtomicInteger();
        PhoneCallAudio audio = new PhoneCallAudio(() -> starts.getAndIncrement() == 0 ? first : second);
        try {
            audio.start(.5F); assertTrue(first.draining.await(10, TimeUnit.SECONDS));
            audio.start(.5F); assertTrue(second.draining.await(10, TimeUnit.SECONDS));
            assertFalse(audio.isFinished()); assertTrue(second.peak > 0);
            second.release.countDown(); await(audio); assertEquals("", audio.error());
        } finally { first.release.countDown(); second.release.countDown(); audio.close(); }
    }
    @Test public void anUnavailableDeviceEndsCleanly() throws Exception {
        PhoneCallAudio audio = new PhoneCallAudio(() -> { throw new IllegalStateException("No audio device"); });
        try { audio.start(1); await(audio); assertEquals("Call audio unavailable", audio.error()); }
        finally { audio.close(); }
    }
    private static void await(PhoneCallAudio audio) throws Exception {
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!audio.isFinished() && System.nanoTime() < limit) Thread.sleep(5);
        assertTrue("Audio worker did not finish", audio.isFinished());
    }
    private static final class Sink implements AudioDevice {
        final CountDownLatch draining = new CountDownLatch(1), release = new CountDownLatch(1);
        private Decoder decoder;
        volatile boolean open;
        long samples;
        int peak, channels, rate;
        @Override public void open(Decoder decoder) { this.decoder = decoder; open = true; }
        @Override public boolean isOpen() { return open; }
        @Override public void write(short[] data, int offset, int length) {
            samples += length; channels = decoder.getOutputChannels(); rate = decoder.getOutputFrequency();
            for (int i = offset; i < offset + length; i++) peak = Math.max(peak, Math.abs((int) data[i]));
        }
        @Override public void flush() {
            draining.countDown();
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
        @Override public void close() { open = false; release.countDown(); }
        @Override public int getPosition() { return 0; }
    }
}
