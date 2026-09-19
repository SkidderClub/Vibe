package dev.vibe.ui;

import dev.vibe.media.AudioSpectrum;
import dev.vibe.module.impl.MusicModule;
import java.awt.Color;
import org.lwjgl.opengl.GL11;

/** Screen-bottom audio spectrum with time-correct attack/release and independently falling peaks. */
public final class MusicVisualizer {
    private final float[] levels=new float[128], peaks=new float[128];
    private long last;
    public void draw(MusicModule m,AudioSpectrum.Frame frame,int width,int height) {
        long now=System.nanoTime();float dt=last==0?.016f:Math.min(.1f,(now-last)/1e9f);last=now;
        int count=m.bands.getInt();float highest=0;
        float response=.015f+m.smoothing.getFloat()*.004f;
        for(int i=0;i<count;i++) {
            float target=Math.min(1,frame.band(i,count,m.minHz.getFloat(),m.maxHz.getFloat())*m.gain.getFloat());
            float amount=1-(float)Math.exp(-dt/(target>levels[i]?Math.max(.008,response*.3):response));
            levels[i]+=(target-levels[i])*amount;peaks[i]=Math.max(levels[i],peaks[i]-dt*.4f);highest=Math.max(highest,levels[i]);
        }
        if(highest<.001)return;
        float total=width*m.waveWidth.getFloat()/100,left=(width-total)/2,bottom=height-m.bottom.getFloat();
        float amplitude=Math.min(m.waveHeight.getFloat(),Math.max(0,bottom-5));
        if (DebugOverlay.isActive() && overlapsDebugText(m, count, left, total, bottom, amplitude)) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_CURRENT_BIT|GL11.GL_LINE_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);GL11.glLineWidth(m.lineWidth.getFloat());
            if(m.style.is("Bars")) {
                float cell=total/count,gap=Math.min(cell*.8f,m.gap.getFloat());
                for(int i=0;i<count;i++) {
                    int bin=m.mirror.isEnabled()?Math.min(count-1,Math.abs(i-count/2)*2):i;
                    float x=left+i*cell,y=bottom-levels[bin]*amplitude;
                    GL11.glBegin(GL11.GL_QUADS);color(m,i/(float)count,.18f);GL11.glVertex2f(x,bottom);GL11.glVertex2f(x+cell-gap,bottom);
                    color(m,i/(float)count,1);GL11.glVertex2f(x+cell-gap,y);GL11.glVertex2f(x,y);GL11.glEnd();
                    if(m.peaks.isEnabled()){float py=bottom-peaks[bin]*amplitude;GL11.glBegin(GL11.GL_LINES);GL11.glVertex2f(x,py);GL11.glVertex2f(x+cell-gap,py);GL11.glEnd();}
                }
            } else if(m.style.is("Line")) {
                drawLine(m,count,left,total,bottom,amplitude,0,1.0F);
            } else {
                // A regular strip emitted as bottom/top pairs makes Waves
                // deterministic on fixed-function drivers. The old mixed
                // primitive path intermittently dropped the whole strip.
                int layers=m.layers.getInt(),points=Math.max(24,count*4);
                for(int layer=layers-1;layer>=0;layer--) {
                    float scale=1.0F-layer*.13F;
                    GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
                    for(int i=0;i<=points;i++) {
                        float t=i/(float)points;
                        float value=waveValue(m,count,t,layer)*scale;
                        float x=left+total*t, y=bottom-value*amplitude;
                        color(m,t,.055F);GL11.glVertex2f(x,bottom);
                        color(m,t,.72F/(1.0F+layer*.35F));GL11.glVertex2f(x,y);
                    }
                    GL11.glEnd();
                    drawLine(m,count,left,total,bottom,amplitude,layer,Math.max(.25F,.75F-layer*.12F));
                }
            }
        } finally{GL11.glPopAttrib();}
    }
    private void drawLine(MusicModule m,int count,float left,float total,float bottom,float amplitude,int layer,float alpha){
        int points=Math.max(24,count*4);GL11.glBegin(GL11.GL_LINE_STRIP);
        for(int i=0;i<=points;i++){
            float t=i/(float)points;
            color(m,t,alpha);
            GL11.glVertex2f(left+total*t,bottom-waveValue(m,count,t,layer)*amplitude*(1.0F-layer*.13F));
        }
        GL11.glEnd();
    }
    private float waveValue(MusicModule m,int count,float t,int layer){
        float position=m.mirror.isEnabled()?Math.abs(t*2.0F-1.0F):t;
        return sample(levels,count,Math.min(1.0F,position+layer*.012F))*(float)Math.pow(Math.sin(Math.PI*t),.35);
    }
    private boolean overlapsDebugText(MusicModule m, int count, float left, float total, float bottom, float amplitude) {
        float padding = m.lineWidth.getFloat() / (2 * new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft()).getScaleFactor());
        if (m.style.is("Bars")) {
            float cell = total / count, gap = Math.min(cell * .8f, m.gap.getFloat());
            for (int i = 0; i < count; i++) {
                int bin = m.mirror.isEnabled() ? Math.min(count - 1, Math.abs(i - count / 2) * 2) : i;
                float x = left + i * cell;
                if (DebugOverlay.overlaps(x, bottom - levels[bin] * amplitude, x + cell - gap, bottom)) return true;
                if (m.peaks.isEnabled()) {
                    float y = bottom - peaks[bin] * amplitude;
                    if (DebugOverlay.overlaps(x - padding, y - padding, x + cell - gap + padding, y + padding)) return true;
                }
            }
        } else {
            int layers = m.style.is("Waves") ? m.layers.getInt() : 1, points = count * 4;
            for (int layer = 0; layer < layers; layer++) {
                float previousX = left, previousY = bottom;
                for (int i = 1; i <= points; i++) {
                    float t = i / (float) points, pos = m.mirror.isEnabled() ? Math.abs(t * 2 - 1) : t;
                    float value = sample(levels, count, Math.min(1, pos + layer * .012f)) * (float) Math.pow(Math.sin(Math.PI * t), .35);
                    float x = left + total * t, y = bottom - value * amplitude * (1 - layer * .13f);
                    float lower = m.style.is("Line") ? Math.max(previousY, y) : bottom;
                    if (DebugOverlay.overlaps(previousX - padding, Math.min(previousY, y) - padding, x + padding, lower + padding)) return true;
                    previousX = x; previousY = y;
                }
            }
        }
        return false;
    }

    private static float sample(float[] values,int count,float position) {
        float p=position*(count-1);int i=(int)p;float t=p-i;
        float a=values[Math.max(0,i-1)],b=values[i],c=values[Math.min(count-1,i+1)],d=values[Math.min(count-1,i+2)];
        return Math.max(0,Math.min(1,.5f*((2*b)+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t)));
    }
    private static void color(MusicModule m,float t,float alpha) {
        int color=m.first.getArgb();
        if(m.waveColor.is("Rainbow"))color=Color.HSBtoRGB((System.currentTimeMillis()%120000/12000f*m.rainbowSpeed.getFloat()+t)%1,.75f,1);
        else if(m.waveColor.is("Gradient")) {
            int b=m.second.getArgb(),a=color;
            color=(Math.round(((a>>>24)&255)*(1-t)+((b>>>24)&255)*t)<<24)|
                    (Math.round(((a>>16)&255)*(1-t)+((b>>16)&255)*t)<<16)|
                    (Math.round(((a>>8)&255)*(1-t)+((b>>8)&255)*t)<<8)|Math.round((a&255)*(1-t)+(b&255)*t);
        }
        GL11.glColor4f(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,
                ((color>>>24)&255)/255f*alpha*m.waveOpacity.getFloat()/100);
    }
    public void clear(){java.util.Arrays.fill(levels,0);java.util.Arrays.fill(peaks,0);last=0;}
}
