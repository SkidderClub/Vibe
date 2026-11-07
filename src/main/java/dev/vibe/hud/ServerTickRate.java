package dev.vibe.hud;

/** S03 server-time intervals, never client FPS or the locally advancing world clock. */
public final class ServerTickRate {
    private static Object connection;
    private static long ticks,time,last;
    private static double rate=Double.NaN;
    private ServerTickRate(){}
    public static synchronized void observe(Object source,long serverTicks,long now){
        if(source!=connection){connection=source;time=0;rate=Double.NaN;}
        if(time!=0&&now>time&&serverTicks>ticks){double sample=Math.min(20,(serverTicks-ticks)*1000D/(now-time));rate=Double.isNaN(rate)?sample:rate*.65+sample*.35;}
        ticks=serverTicks;time=last=now;
    }
    public static synchronized double value(Object source,long now){return source==connection&&now-last<10000?rate:Double.NaN;}
}
