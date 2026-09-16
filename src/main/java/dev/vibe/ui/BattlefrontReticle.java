package dev.vibe.ui;

import dev.vibe.game.battlefront.BattlefrontGame;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import static dev.vibe.game.battlefront.BattlefrontContent.*;

/** Geometric reticle and separate, fading hit confirmations. No font glyphs. */
public final class BattlefrontReticle {
    private BattlefrontReticle(){}
    public static void draw(BattlefrontGame game,double x,double y){
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_LINE_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glPushMatrix();
        try{
            GL11.glTranslated(x,y,0);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glEnable(GL11.GL_LINE_SMOOTH);
            Weapon weapon=game.progress.weapon();boolean precision=weapon==Weapon.DLT19X&&game.aiming,spread=weapon==Weapon.BOWCASTER;
            double gap=(game.aiming?3.1:6.0)+(weapon==Weapon.DC15LE&&!game.aiming?1.5:0)+game.recoil*(game.aiming?2.5:4);
            int ink=game.reload>0?0xAAB7C5CA:0xF5F3FCFD;
            if(spread){for(int i=0;i<4;i++)arc(gap+3,i*90+13,i*90+77,ink,1.45f);}
            else if(precision){for(int s:new int[]{-1,1}){stroke(s*10,-3,s*10,3,ink,1.1f);stroke(-3,s*10,3,s*10,ink,1.1f);}}
            else{double length=game.aiming?3.4:5.1;for(int s:new int[]{-1,1}){stroke(s*gap,0,s*(gap+length),0,ink,1.35f);stroke(0,s*gap,0,s*(gap+length),ink,1.35f);}}
            disk(0,0,2.0,0xD0081118);disk(0,0,game.aiming?.8:1.05,ink);
            if(game.reload>0){arc(18,-90,-90+360*game.reloadProgress(),0xEAEBC58D,1.8f);}
            if(game.hitMarker>0){
                double alpha=Math.min(1,game.hitMarker/.12),duration=game.lastHitKill?.36:game.lastHitHead?.26:.20;
                double inner=8+2*Math.min(1,game.hitMarker/duration),outer=inner+(game.lastHitKill?7:5);
                int color=((int)(255*alpha)<<24)|(game.lastHitKill?0xFF788E:game.lastHitHead?0xFFD082:0xF3FCFD);
                for(int sx:new int[]{-1,1})for(int sy:new int[]{-1,1}){stroke(sx*inner,sy*inner,sx*outer,sy*outer,color,game.lastHitKill?2.25f:1.8f);
                    if(game.lastHitKill)stroke(sx*(outer-3),sy*(outer+2),sx*(outer+2),sy*(outer-3),color,1.4f);}
                if(game.lastHitHead){stroke(-3,-20,0,-23,color,1.6f);stroke(0,-23,3,-20,color,1.6f);}
            }
        }finally{GL11.glPopMatrix();GL11.glPopAttrib();GlStateManager.resetColor();}
    }
    private static void color(int c){GL11.glColor4d((c>>16&255)/255.0,(c>>8&255)/255.0,(c&255)/255.0,(c>>>24)/255.0);}
    private static void line(double x,double y,double X,double Y){GL11.glBegin(GL11.GL_LINES);GL11.glVertex2d(x,y);GL11.glVertex2d(X,Y);GL11.glEnd();}
    private static void stroke(double x,double y,double X,double Y,int c,float width){color((c&0xFF000000)|0x071017);GL11.glLineWidth(width+2.2f);line(x,y,X,Y);color(c);GL11.glLineWidth(width);line(x,y,X,Y);}
    private static void arc(double r,double from,double to,int c,float width){if(to<=from)return;for(int pass=0;pass<2;pass++){color(pass==0?(c&0xFF000000)|0x071017:c);GL11.glLineWidth(pass==0?width+2:width);GL11.glBegin(GL11.GL_LINE_STRIP);int count=Math.max(3,(int)((to-from)/6));for(int i=0;i<=count;i++){double a=Math.toRadians(from+(to-from)*i/count);GL11.glVertex2d(Math.cos(a)*r,Math.sin(a)*r);}GL11.glEnd();}}
    private static void disk(double x,double y,double radius,int c){color(c);GL11.glBegin(GL11.GL_TRIANGLE_FAN);GL11.glVertex2d(x,y);for(int i=0;i<=20;i++){double a=i*Math.PI/10;GL11.glVertex2d(x+Math.cos(a)*radius,y+Math.sin(a)*radius);}GL11.glEnd();}
}
