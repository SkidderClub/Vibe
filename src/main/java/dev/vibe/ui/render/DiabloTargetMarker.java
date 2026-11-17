package dev.vibe.ui.render;

import dev.vibe.ui.RenderUtils;
import dev.vibe.ui.effect.EffectState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Diablo KillAura.Visualise: the 45-vertex, radius-two GL_LINES sweep. */
public final class DiabloTargetMarker {
    private long started;private boolean reversed;private double percentage;
    public double height(float height,long now){
        int amount=Math.max(1,(int)height*1000);
        if(started==0||(reversed?percentage==0:percentage==100)){reversed=!reversed;started=now;}
        percentage=Math.max(0,Math.min(100,(now-started)/(amount/2D)*100));if(reversed)percentage=100-percentage;
        return amount/100D*percentage/1000*3-1;
    }
    public void draw(double x,double y,double z,float targetHeight,int first,int second,long now){
        double offset=height(targetHeight,now);
        try(EffectState state=new EffectState()){
            GL20.glUseProgram(0);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(770,771);GL11.glEnable(GL11.GL_LINE_SMOOTH);GL11.glLineWidth(12.75F);
            GL11.glBegin(GL11.GL_LINES);
            for(int i=0;i<45;i++){
                int color=RenderUtils.blend(first,second,i/45F);
                GL11.glColor4f((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,(color>>>24)/255F);
                // Vibe already has the camera matrix: subtract/translate the viewer only once.
                double angle=Math.PI*2*i/45;GL11.glVertex3d(x+2*Math.sin(angle),y+offset,z+2*Math.cos(angle));
            }
            GL11.glEnd();
        }
    }
}
