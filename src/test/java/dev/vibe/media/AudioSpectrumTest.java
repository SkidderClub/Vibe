package dev.vibe.media;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioSpectrumTest {
    @Test public void fftFindsKnownFrequencyAndClearsOnStop() {
        AudioSpectrum spectrum=new AudioSpectrum();float[] samples=new float[AudioSpectrum.SIZE];
        int bin=40,rate=48000;
        for(int i=0;i<samples.length;i++)samples[i]=(float)(.5*Math.sin(2*Math.PI*bin*i/samples.length));
        spectrum.accept(samples,samples.length,rate);AudioSpectrum.Frame frame=spectrum.frame();
        int peak=0;for(int i=1;i<frame.magnitudes.length;i++)if(frame.magnitudes[i]>frame.magnitudes[peak])peak=i;
        assertEquals(bin,peak);assertEquals(.5,frame.magnitudes[peak],.002);assertEquals(.5/Math.sqrt(2),frame.rms,.001);
        spectrum.clear();assertEquals(0,spectrum.frame().rms,0);
    }
    @Test public void silenceAndNonFiniteSamplesDoNotProduceWaves() {
        AudioSpectrum spectrum=new AudioSpectrum();float[] samples=new float[AudioSpectrum.SIZE];
        samples[0]=Float.NaN;samples[4]=Float.POSITIVE_INFINITY;
        spectrum.accept(samples,samples.length,44100);
        for(int i=0;i<128;i++)assertEquals(0,spectrum.frame().band(i,128,35,16000),0);
    }
    @Test public void bandRangeRemainsFiniteAtDifferentOutputRates() {
        AudioSpectrum spectrum=new AudioSpectrum();float[] samples=new float[AudioSpectrum.SIZE];java.util.Arrays.fill(samples,.4f);
        spectrum.accept(samples,samples.length,8000);
        for(int i=0;i<128;i++)assertTrue(Float.isFinite(spectrum.frame().band(i,128,2000,22000)));
    }
}
