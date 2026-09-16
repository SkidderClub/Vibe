package dev.vibe.ui;

import dev.vibe.game.battlefront.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import static dev.vibe.game.battlefront.BattlefrontContent.*;

/** Cached terrain relief, routes and floor plans shared by the radar and tactical atlas. */
public final class BattlefrontMapTexture {
    private BattlefrontWorld world;private DynamicTexture texture;
    public static BufferedImage rasterize(BattlefrontWorld world){
        final int size=512;final double edge=BattlefrontWorld.TERRAIN_EDGE,step=edge*2/size;
        BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);boolean forest=world.scenario==Scenario.ENDOR;
        for(int z=0;z<size;z++)for(int x=0;x<size;x++){
            double wx=(x+.5)*step-edge,wz=(z+.5)*step-edge,h=world.height(wx,wz),hx=world.height(wx+step,wz),hz=world.height(wx,wz+step);
            double light=Math.max(-20,Math.min(26,(h-hx)*15+(h-hz)*10+h*.28));
            int r=(forest?41:81),g=(forest?66:67),b=(forest?58:52);
            if(world.onTrail(wx,wz,2.5)){r+=25;g+=22;b+=17;}
            if(Math.floor(h/3)!=Math.floor(hx/3)||Math.floor(h/3)!=Math.floor(hz/3))light-=12;
            if(Math.abs(wx)>BattlefrontWorld.LIMIT||Math.abs(wz)>BattlefrontWorld.LIMIT){r=21;g=28;b=32;light=0;}
            image.setRGB(x,z,0xFF000000|clamp(r+light)<<16|clamp(g+light)<<8|clamp(b+light));
        }
        Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        for(BattlefrontArchitecture.Building building:world.buildings){
            int x=(int)((building.x-building.w+edge)/step),y=(int)((building.z-building.d+edge)/step),w=Math.max(3,(int)(building.w*2/step)),h=Math.max(3,(int)(building.d*2/step));
            g.setColor(new Color(17,24,27,180));g.fillRect(x+2,y+3,w,h);g.setColor(new Color(102,125,117));g.fillRect(x,y,w,h);g.setColor(new Color(181,197,179));g.drawRect(x,y,w,h);
            // The doorway faces south, matching the navigable world geometry.
            g.setColor(new Color(224,212,166));g.fillRect(x+w/2-2,y+h-1,4,2);
        }
        g.dispose();return image;
    }
    private static int clamp(double n){return (int)Math.max(0,Math.min(255,n));}
    public void draw(BattlefrontWorld world,double x,double y,double size,double centerX,double centerZ,double radius){
        if(this.world!=world){close();this.world=world;texture=new DynamicTexture(rasterize(world));texture.setBlurMipmap(true,false);}
        double edge=BattlefrontWorld.TERRAIN_EDGE,left=Math.max(-edge,centerX-radius),right=Math.min(edge,centerX+radius),top=Math.max(-edge,centerZ-radius),bottom=Math.min(edge,centerZ+radius);
        if(right<=left||bottom<=top)return;
        double x0=x+(left-centerX+radius)*size/(radius*2),x1=x+(right-centerX+radius)*size/(radius*2),y0=y+(top-centerZ+radius)*size/(radius*2),y1=y+(bottom-centerZ+radius)*size/(radius*2);
        GlStateManager.enableTexture2D();GlStateManager.enableBlend();GlStateManager.color(1,1,1,1);GlStateManager.bindTexture(texture.getGlTextureId());
        GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2d((left+edge)/(2*edge),(top+edge)/(2*edge));GL11.glVertex2d(x0,y0);
        GL11.glTexCoord2d((left+edge)/(2*edge),(bottom+edge)/(2*edge));GL11.glVertex2d(x0,y1);
        GL11.glTexCoord2d((right+edge)/(2*edge),(bottom+edge)/(2*edge));GL11.glVertex2d(x1,y1);
        GL11.glTexCoord2d((right+edge)/(2*edge),(top+edge)/(2*edge));GL11.glVertex2d(x1,y0);GL11.glEnd();
    }
    public void close(){if(texture!=null){texture.deleteGlTexture();texture=null;}world=null;}
}
