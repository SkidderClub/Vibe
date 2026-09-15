package dev.vibe.media;

/** Hann-windowed radix-2 FFT over real PCM. Silence produces zero, never synthetic animation. */
public final class AudioSpectrum {
    public static final int SIZE = 2048;
    private final float[] ring = new float[SIZE];
    private final double[] real = new double[SIZE], imaginary = new double[SIZE];
    private int cursor, count;
    private volatile Frame frame = Frame.SILENT;
    public synchronized void accept(float[] mono, int length, int sampleRate) {
        for (int i=0; i<length; i++) {
            float value = mono[i]; ring[cursor] = Float.isNaN(value) || Float.isInfinite(value) ? 0 : Math.max(-1, Math.min(1, value));
            cursor = (cursor+1) & (SIZE-1); count++;
        }
        if (count < SIZE/2) return;
        count = 0;
        double rms = 0;
        for (int i=0; i<SIZE; i++) {
            double sample = ring[(cursor+i)&(SIZE-1)]; rms += sample*sample;
            real[i] = sample * (.5-.5*Math.cos(2*Math.PI*i/(SIZE-1))); imaginary[i] = 0;
        }
        for (int i=1, j=0; i<SIZE; i++) {
            int bit = SIZE>>1; for (; (j&bit)!=0; bit>>=1) j ^= bit; j ^= bit;
            if (i<j) { double temp=real[i]; real[i]=real[j]; real[j]=temp; }
        }
        for (int len=2; len<=SIZE; len<<=1) {
            double angle=-2*Math.PI/len, stepR=Math.cos(angle), stepI=Math.sin(angle);
            for (int i=0; i<SIZE; i+=len) {
                double wr=1, wi=0;
                for (int j=0; j<len/2; j++) {
                    int a=i+j, b=a+len/2;
                    double vr=real[b]*wr-imaginary[b]*wi, vi=real[b]*wi+imaginary[b]*wr;
                    real[b]=real[a]-vr; imaginary[b]=imaginary[a]-vi; real[a]+=vr; imaginary[a]+=vi;
                    double next=wr*stepR-wi*stepI; wi=wr*stepI+wi*stepR; wr=next;
                }
            }
        }
        float[] magnitudes = new float[SIZE/2];
        for (int i=0; i<magnitudes.length; i++) magnitudes[i]=(float)(Math.hypot(real[i],imaginary[i])*4/SIZE);
        frame = new Frame(magnitudes, sampleRate, (float)Math.sqrt(rms/SIZE), System.nanoTime());
    }
    public Frame frame() { return System.nanoTime()-frame.created > 400_000_000L ? Frame.SILENT : frame; }
    public synchronized void clear() { java.util.Arrays.fill(ring, 0); cursor=count=0; frame=Frame.SILENT; }
    public static final class Frame {
        public static final Frame SILENT = new Frame(new float[SIZE/2], 48000, 0, 0);
        public final float[] magnitudes;
        public final int sampleRate;
        public final float rms;
        public final long created;
        Frame(float[] magnitudes, int sampleRate, float rms, long created) {
            this.magnitudes=magnitudes; this.sampleRate=sampleRate; this.rms=rms; this.created=created;
        }
        public float band(int index, int total, float minHz, float maxHz) {
            double maximum=Math.min(sampleRate*.5, Math.max(minHz+1, maxHz));
            double low=minHz*Math.pow(maximum/minHz, index/(double)total);
            double high=minHz*Math.pow(maximum/minHz, (index+1)/(double)total);
            int from=Math.max(1, Math.min(magnitudes.length-1, (int)(low*SIZE/sampleRate)));
            int to=Math.max(from+1, Math.min(magnitudes.length, (int)Math.ceil(high*SIZE/sampleRate)));
            float peak=0; for(int i=from;i<to;i++) peak=Math.max(peak, magnitudes[i]);
            return (float)Math.log1p(peak*20)/3;
        }
    }
}
