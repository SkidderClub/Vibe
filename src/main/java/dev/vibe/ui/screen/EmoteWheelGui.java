package dev.vibe.ui.screen;

import dev.vibe.module.impl.meme.EmoteWheelModule;
import dev.vibe.features.EmotePoses;
import dev.vibe.features.WorldDraw;
import dev.vibe.ui.effect.EffectState;
import dev.vibe.ui.menu.MenuFont;
import dev.vibe.ui.menu.MenuGlass;
import dev.vibe.ui.RenderUtils;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiInventory;
import org.lwjgl.opengl.GL11;
import java.io.IOException;

/** A floating wheel leaves the world untouched; the centre previews your own skin. */
public final class EmoteWheelGui extends GuiScreen {
    private final EmoteWheelModule module;
    private final float[] focus=new float[EmoteWheelModule.EMOTES.length];
    private long last=System.nanoTime();
    public EmoteWheelGui(EmoteWheelModule module){this.module=module;}
    private float radius(){return Math.max(60,Math.min(144,Math.min(width,height)*.5F-27));}
    private int choice(int mx,int my){double x=mx-width/2.,y=my-height/2.,r=radius();if(x*x+y*y<r*r*.39||x*x+y*y>r*r)return -1;double step=Math.PI*2/focus.length,angle=Math.atan2(y,x)+Math.PI/2+step/2;angle=(angle+Math.PI*2)%(Math.PI*2);return (int)(angle/step);}
    @Override public void drawScreen(int mx,int my,float partial){
        int cx=width/2,cy=height/2,selected=choice(mx,my),accent=MenuGlass.currentPalette().accent;float radius=radius();
        long now=System.nanoTime();float dt=Math.min(.1F,(now-last)/1E9F);last=now;
        for(int i=0;i<focus.length;i++)focus[i]+=((i==selected?1:0)-focus[i])*(1-(float)Math.exp(-18*dt));
        // Only the radial controls draw a background, never the whole game window.
        try(EffectState state=new EffectState()){
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glEnable(GL11.GL_LINE_SMOOTH);
            for(int i=0;i<focus.length;i++){
                double step=Math.PI*2/focus.length,start=-Math.PI/2+(i-.5)*step+.025,end=start+step-.05;
                sector(cx,cy,radius*.63F,radius,start,end,RenderUtils.blend(0xD3121822,RenderUtils.alpha(accent,220),focus[i]));
                GL11.glLineWidth(1.3F);WorldDraw.color(RenderUtils.alpha(i==selected?accent:0xFFB5C8DE,i==selected?240:60));GL11.glBegin(GL11.GL_LINE_STRIP);
                for(int j=0;j<=24;j++){double a=start+(end-start)*j/24.;GL11.glVertex2d(cx+Math.cos(a)*radius,cy+Math.sin(a)*radius);}GL11.glEnd();
                double a=-Math.PI/2+i*step;float ix=cx+(float)Math.cos(a)*radius*.81F,iy=cy+(float)Math.sin(a)*radius*.81F;
                glyph(i,ix,iy-5,Math.max(.65F,radius/144F),i==selected?0xFFFFFFFF:0xFFC9D6E6);
            }
        }
        for(int i=0;i<focus.length;i++){double a=-Math.PI/2+i*Math.PI*2/focus.length;int xx=cx+(int)(Math.cos(a)*radius*.81),yy=cy+(int)(Math.sin(a)*radius*.81)+10;MenuFont.SMALL.centered(EmoteWheelModule.EMOTES[i],xx-34,yy,68,13,i==selected?0xFFFFFFFF:0xFFCCD5E2);}
        MenuFont.BOLD.centered("Emotes",cx-80,cy-radius-23,160,17,0xFFF4F7FC);
        String name=selected<0?module.selected():EmoteWheelModule.EMOTES[selected];
        if(mc.thePlayer!=null){EmotePoses.preview=name;try{GuiInventory.drawEntityOnScreen(cx,cy+(int)(radius*.32),Math.max(15,(int)(radius*.29)),0,0,mc.thePlayer);}finally{EmotePoses.preview=null;}}
        MenuFont.SMALL.centered(name,cx-50,cy+radius*.43F,100,13,0xFFF4F7FC);
        MenuFont.SMALL.centered("Choose an emote",cx-100,cy+radius+8,200,15,0xFFE0E7F1);
    }
    private static void sector(float x,float y,float inner,float outer,double from,double to,int color){WorldDraw.color(color);GL11.glBegin(GL11.GL_QUAD_STRIP);for(int j=0;j<=24;j++){double a=from+(to-from)*j/24.;GL11.glVertex2d(x+Math.cos(a)*inner,y+Math.sin(a)*inner);GL11.glVertex2d(x+Math.cos(a)*outer,y+Math.sin(a)*outer);}GL11.glEnd();}
    private static void glyph(int kind,float x,float y,float scale,int color){
        GL11.glPushMatrix();GL11.glTranslatef(x,y,0);GL11.glScalef(scale,scale,1);GL11.glLineWidth(1.8F);WorldDraw.color(color);
        if(kind==7){GL11.glBegin(GL11.GL_LINES);GL11.glVertex2f(-5,-5);GL11.glVertex2f(5,5);GL11.glVertex2f(5,-5);GL11.glVertex2f(-5,5);GL11.glEnd();}
        else{GL11.glBegin(GL11.GL_LINE_LOOP);for(int j=0;j<16;j++){double a=j*Math.PI/8;GL11.glVertex2d(Math.cos(a)*2.3,-8+Math.sin(a)*2.3);}GL11.glEnd();
            float[][] arms={{-8,-5,8,-5},{-7,2,7,2},{-7,0,7,-10},{-7,-9,7,-9},{-8,-4,8,0},{-5,4,6,4},{-6,0,2,-8}};
            GL11.glBegin(GL11.GL_LINES);GL11.glVertex2f(0,-4);GL11.glVertex2f(0,4);GL11.glVertex2f(0,-2);GL11.glVertex2f(arms[kind][0],arms[kind][1]);GL11.glVertex2f(0,-2);GL11.glVertex2f(arms[kind][2],arms[kind][3]);GL11.glVertex2f(0,4);GL11.glVertex2f(-4,kind==1?4:10);GL11.glVertex2f(0,4);GL11.glVertex2f(4,kind==1?4:10);GL11.glEnd();}
        GL11.glPopMatrix();
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(button==0){int i=choice(x,y);if(i>=0){module.select(EmoteWheelModule.EMOTES[i]);mc.displayGuiScreen(null);}}}
    @Override public boolean doesGuiPauseGame(){return false;}
}