package dev.vibe.aiaura;

/** Minecraft 1.8's relative-mouse gain; counts are integers, angles retain vanilla float rounding. */
public final class MouseMath {
    private MouseMath(){}
    public static float gain(float sensitivity){float f=sensitivity*.6F+.2F;return f*f*f*8F;}
    public static float step(float sensitivity){return (float)(gain(sensitivity)*.15D);}
    public static float angle(float previous,int counts,float sensitivity){return (float)((double)previous+(double)(counts*gain(sensitivity))*.15D);}
    public static float pitch(float previous,int counts,float sensitivity){return Math.max(-90,Math.min(90,angle(previous,counts,sensitivity)));}
    public static double wrap(double angle){angle%=360;return angle>=180?angle-360:angle< -180?angle+360:angle;}
}
