package dev.vibe.aiaura;

/** Causal velocity continuity in mouse-count space. Quantization is applied after filtering. */
public final class AuraAimFilter {
    private double x,y;
    public void reset(){x=y=0;}
    public double[] advance(double desiredX,double desiredY,double elapsedMs,double maxX,double maxY){
        double blend=1-Math.exp(-Math.max(1,Math.min(100,elapsedMs))/65D);
        x=axis(x,desiredX,blend,maxX);y=axis(y,desiredY,blend,maxY);return new double[]{x,y};
    }
    private static double axis(double current,double desired,double blend,double maximum){
        double change=(desired-current)*blend,acceleration=Math.max(1,maximum*.45);
        double next=current+Math.max(-acceleration,Math.min(acceleration,change));return Math.abs(next)<.05&&Math.abs(desired)<.05?0:next;
    }
}
