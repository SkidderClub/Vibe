package dev.vibe.hud;

import dev.vibe.ui.RenderUtils;
import dev.vibe.ui.effect.EffectState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Filled geometry with an alpha fringe; no driver-dependent rounded line outlines. */
public final class HudShapes {
    private HudShapes() { }
    private static EffectState state() {
        boolean clip=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        EffectState state=new EffectState();
        if(clip)GL11.glEnable(GL11.GL_SCISSOR_TEST);
        org.lwjgl.opengl.GL20.glUseProgram(0);GL11.glShadeModel(GL11.GL_SMOOTH);
        return state;
    }


    public static void round(float x,float y,float w,float h,float radius,int color) {
        gradient(x,y,w,h,radius,color,color);
    }
    public static void gradient(float x,float y,float w,float h,float radius,int left,int right) {
        if(w<=0||h<=0)return;
        float r=Math.max(0,Math.min(radius,Math.min(w,h)/2));
        try(EffectState ignored=state()) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            tint(RenderUtils.blend(left,right,.5F),1);GL11.glVertex2f(x+w/2,y+h/2);
            for(int i=0;i<=68;i++)vertex(x,y,w,h,r,i,left,right,1);
            GL11.glEnd();
            if(r>0) {
                GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
                for(int i=0;i<=68;i++) {
                    vertex(x,y,w,h,r,i,left,right,1);
                    vertex(x-.6F,y-.6F,w+1.2F,h+1.2F,r+.6F,i,left,right,0);
                }
                GL11.glEnd();
            }
        }
    }
    private static void vertex(float x,float y,float w,float h,float r,int i,int a,int b,float alpha) {
        int c=(i%68)/17;double angle=Math.toRadians(-90+c*90+(i%17)*90.0/16);
        float px=x+(c<2?w-r:r)+(float)Math.cos(angle)*r;
        float py=y+(c==0||c==3?r:h-r)+(float)Math.sin(angle)*r;
        tint(RenderUtils.blend(a,b,Math.max(0,Math.min(1,(px-x)/w))),alpha);GL11.glVertex2f(px,py);
    }
    private static void tint(int argb,float alpha) {
        GL11.glColor4f((argb>>16&255)/255F,(argb>>8&255)/255F,(argb&255)/255F,(argb>>>24)/255F*alpha);
    }
    public static void line(float x1,float y1,float x2,float y2,float width,int color) {
        float dx=x2-x1,dy=y2-y1,length=(float)Math.sqrt(dx*dx+dy*dy);
        if(length<.01F)return;
        try(EffectState ignored=state()) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            float nx=-dy/length,ny=dx/length;
            GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
            for(int i=0;i<4;i++) {
                float offset=i==0?-width/2-.6F:i==1?-width/2:i==2?width/2:width/2+.6F;
                tint(color,i==0||i==3?0:1);
                GL11.glVertex2f(x1+nx*offset,y1+ny*offset);
                GL11.glVertex2f(x2+nx*offset,y2+ny*offset);
            }
            GL11.glEnd();
        }
        round(x1-width/2,y1-width/2,width,width,width/2,color);
        round(x2-width/2,y2-width/2,width,width,width/2,color);
    }
    public static void arc(float cx,float cy,float radius,float width,float fraction,int color) {
        if(fraction<=0)return;
        int steps=Math.max(1,(int)(96*Math.min(1,fraction)));
        try(EffectState ignored=state()) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            for(int band=0;band<3;band++) {
                GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
                for(int i=0;i<=steps;i++) {
                    double a=-Math.PI/2+Math.PI*2*fraction*i/steps;
                    for(int edge=band;edge<=band+1;edge++) {
                        float r=radius+(edge==0?-width/2-.6F:edge==1?-width/2:edge==2?width/2:width/2+.6F);
                        tint(color,edge==0||edge==3?0:1);
                        GL11.glVertex2f(cx+(float)Math.cos(a)*r,cy+(float)Math.sin(a)*r);
                    }
                }
                GL11.glEnd();
            }
        }
    }
    public static void face(ResourceLocation skin,float x,float y,float size,float radius,float hurt) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(skin);
        try(EffectState ignored=state()) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1,1-hurt*.45F,1-hurt*.45F,1);
            for(int layer=0;layer<2;layer++) {
                float u=layer==0?8:40;
                GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                GL11.glTexCoord2f((u+4)/64F,12/64F);GL11.glVertex2f(x+size/2,y+size/2);
                for(int i=0;i<=68;i++) {
                    int c=(i%68)/17;double a=Math.toRadians(-90+c*90+(i%17)*90.0/16);
                    float px=(c<2?size-radius:radius)+(float)Math.cos(a)*radius;
                    float py=(c==0||c==3?radius:size-radius)+(float)Math.sin(a)*radius;
                    GL11.glTexCoord2f((u+px/size*8)/64F,(8+py/size*8)/64F);GL11.glVertex2f(x+px,y+py);
                }
                GL11.glEnd();
            }
        }
    }
}
