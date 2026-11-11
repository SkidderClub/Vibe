package dev.vibe.hud;

/** Passive samples; missing values remain missing, and a long client tick is marked as a client stall. */
public final class NetworkSamples {
    public final double[] ping=new double[120],jitter=new double[120];public final boolean[] stall=new boolean[120];public int cursor,count;private double previous=Double.NaN;
    public NetworkSamples(){reset();}
    public void reset(){java.util.Arrays.fill(ping,Double.NaN);java.util.Arrays.fill(jitter,Double.NaN);java.util.Arrays.fill(stall,false);cursor=count=0;previous=Double.NaN;}
    public void add(double ms,boolean stopped){ping[cursor]=ms;jitter[cursor]=Double.isNaN(ms)||Double.isNaN(previous)?Double.NaN:Math.abs(ms-previous);stall[cursor]=stopped;previous=ms;cursor=(cursor+1)%ping.length;count=Math.min(ping.length,count+1);}
    public int at(int index){return (cursor-count+index+ping.length)%ping.length;}
}
