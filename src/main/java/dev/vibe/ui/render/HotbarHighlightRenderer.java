package dev.vibe.ui.render;

import dev.vibe.ui.effect.EffectState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** A HUD overlay must remain visible above the depth left behind by item rendering. */
public final class HotbarHighlightRenderer {
    private HotbarHighlightRenderer() { }
    public static void draw(int x,int y,int size,int color){
        try(EffectState ignored=new EffectState()){
            GL20.glUseProgram(0);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(770,771);GL11.glEnable(GL11.GL_LINE_SMOOTH);GL11.glLineWidth(1.5F);
            GL11.glColor4f((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,(color>>>24)/255F);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for(int corner=0;corner<4;corner++)for(int i=0;i<=6;i++){
                double angle=Math.toRadians(-90+corner*90+i*15);float cx=x+(corner<2?size-2:2),cy=y+(corner==0||corner==3?2:size-2);
                GL11.glVertex2d(cx+Math.cos(angle)*2,cy+Math.sin(angle)*2);
            }
            GL11.glEnd();
        }
    }
}
