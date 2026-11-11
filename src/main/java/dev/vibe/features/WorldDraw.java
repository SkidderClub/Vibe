package dev.vibe.features;

import dev.vibe.ui.effect.EffectState;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;

public final class WorldDraw {
    private WorldDraw(){}
    public static void color(int c){GL11.glColor4f((c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,(c>>>24)/255F);}
    public static void lines(List<Vec3> points,int c,float width,boolean glow){if(points.size()<2)return;Minecraft mc=Minecraft.getMinecraft();if(mc.getRenderManager()==null)return;
        try(EffectState state=new EffectState()){GL11.glPushMatrix();try{GL11.glTranslated(-mc.getRenderManager().viewerPosX,-mc.getRenderManager().viewerPosY,-mc.getRenderManager().viewerPosZ);
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glDepthMask(false);GL11.glEnable(GL11.GL_LINE_SMOOTH);
            if(glow){GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);draw(points,(c&0xFFFFFF)|((c>>>24)/4<<24),width+3);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);}draw(points,c,width);
        }finally{GL11.glPopMatrix();}}
    }
    private static void draw(List<Vec3> points,int c,float width){color(c);GL11.glLineWidth(width);GL11.glBegin(GL11.GL_LINE_STRIP);for(Vec3 p:points)GL11.glVertex3d(p.xCoord,p.yCoord,p.zCoord);GL11.glEnd();}
    public static void box(AxisAlignedBB b,int c,float width,boolean glow){
        lines(java.util.Arrays.asList(new Vec3(b.minX,b.minY,b.minZ),new Vec3(b.maxX,b.minY,b.minZ),new Vec3(b.maxX,b.minY,b.maxZ),new Vec3(b.minX,b.minY,b.maxZ),new Vec3(b.minX,b.minY,b.minZ)),c,width,glow);
        lines(java.util.Arrays.asList(new Vec3(b.minX,b.maxY,b.minZ),new Vec3(b.maxX,b.maxY,b.minZ),new Vec3(b.maxX,b.maxY,b.maxZ),new Vec3(b.minX,b.maxY,b.maxZ),new Vec3(b.minX,b.maxY,b.minZ)),c,width,glow);
        for(int x=0;x<2;x++)for(int z=0;z<2;z++)lines(java.util.Arrays.asList(new Vec3(x==0?b.minX:b.maxX,b.minY,z==0?b.minZ:b.maxZ),new Vec3(x==0?b.minX:b.maxX,b.maxY,z==0?b.minZ:b.maxZ)),c,width,glow);
    }
}
