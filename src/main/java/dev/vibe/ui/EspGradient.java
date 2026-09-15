package dev.vibe.ui;

import dev.vibe.module.impl.Esp2DSettings.Gradient;
import java.util.*;

/** CPU reference also used by the editor strip and the shader's uniform preparation. */
public final class EspGradient {
    public final float[] positions;
    public final int[] colors;
    public final float dx, dy, phase;
    public EspGradient(Gradient gradient, double seconds) {
        int n=gradient.count.getInt();
        List<Integer> indices=new ArrayList<Integer>(); for(int i=0;i<n;i++) indices.add(i);
        Collections.sort(indices,Comparator.comparingDouble(i -> gradient.positions.get(i).getDouble()));
        positions=new float[n]; colors=new int[n];
        for(int i=0;i<n;i++) { positions[i]=gradient.positions.get(indices.get(i)).getFloat(); colors[i]=gradient.colors.get(indices.get(i)).getArgb(); }
        double angle=Math.toRadians(gradient.direction.getDouble()); dx=(float)Math.cos(angle); dy=(float)Math.sin(angle);
        phase=(float)((seconds*gradient.speed.getDouble())%2);
    }
    public int sample(float x, float y, float width, float height) {
        float extent=Math.abs(dx)*width+Math.abs(dy)*height;
        float t=.5F+((x-width*.5F)*dx+(y-height*.5F)*dy)/Math.max(.001F,extent);
        return at(triangle(t+phase));
    }
    public int at(float t) {
        if(t<=positions[0]) return colors[0];
        for(int i=1;i<colors.length;i++) if(t<=positions[i]) {
            float blend=Math.max(0,Math.min(1,(t-positions[i-1])/Math.max(.00001F,positions[i]-positions[i-1])));
            return blend(colors[i-1],colors[i],blend);
        }
        return colors[colors.length-1];
    }
    public static float triangle(float t) { float p=(t%2+2)%2; return p<=1?p:2-p; }
    public static int blend(int a,int b,float t) {
        int result=0; for(int shift=0;shift<=24;shift+=8) result|=Math.round(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t)<<shift;
        return result;
    }
}
