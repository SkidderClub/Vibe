package dev.vibe.hud;

import dev.vibe.module.impl.meme.DabModule;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.ui.ClientChangesRenderCheck;
import dev.vibe.ui.render.HologramPose;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.GLU;

public final class MemeAdditionsRenderCheck {
    public static void verify(Minecraft mc,Path output)throws Exception{
        mc.displayWidth=1280;mc.displayHeight=720;
        BufferedImage skin=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<64;y++)for(int x=0;x<64;x++)skin.setRGB(x,y,y<16?0xFFFFD0AB:y<32?0xFF40B6C4:0xFF344663);
        ResourceLocation texture=new ResourceLocation("vibe:dab-preview");mc.renderEngine.loadTexture(texture,new DynamicTexture(skin));
        for(int age:new int[]{0,4,12,34,40}){
            ClientChangesRenderCheck.frame(1280,720);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GLU.gluPerspective(34,1280F/720,.05F,50);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GLU.gluLookAt(0,1.4F,6.3F,0,1,0,0,1,0);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);
            mc.getTextureManager().bindTexture(texture);
            for(int i=0;i<3;i++){ModelPlayer model=new ModelPlayer(0,i==1);DabModule.applyPose(model,DabModule.envelope(age));GL11.glPushMatrix();GL11.glTranslatef((i-1)*1.9F,1.5078125F,0);GL11.glRotatef(i==0?0:i==1?45:180,0,1,0);GL11.glScalef(-1,-1,1);for(ModelRenderer part:HologramPose.parts(model))part.render(.0625F);GL11.glPopMatrix();}
            save(output.resolve("dab-pose-"+age+".png"),1280,720);if(GL11.glGetError()!=0)throw new AssertionError("Dab pose GL error");
        }
        mc.renderEngine.deleteTexture(texture);ClientChangesRenderCheck.frame(1280,720);
        for(int i=0;i<2;i++){int y=160+i*130;float strength=i==0?0:.8F;HudBarGlow.line(160,y,220,4,0xFF69BDFF,strength);HudShapes.round(160,y,220,4,2,0xFF69BDFF);HudBarGlow.ring(510,y,20,3,.72F,0xFF78EAC6,strength);HudShapes.arc(510,y,20,3,.72F,0xFF78EAC6);HudBarGlow.line(640,y,180,9,0xFF73E391,strength);HudShapes.round(640,y,180,9,3,0xFF73E391);}
        int before=pixel(260,156),after=pixel(260,286);if(after==before)throw new AssertionError("Bar glow did not light pixels outside the bar");
        if(GL11.glGetError()!=0)throw new AssertionError("HUD glow GL error");save(output.resolve("hud-bar-glow-comparison.png"),1280,720);
        HudModule hud=new HudModule();if(!hud.effects.barGlow.isEnabled()||!hud.blockCounter.barGlow.isEnabled()||!hud.customHealth.barGlow.isEnabled()||!hud.targetHud.barGlow.isEnabled())throw new AssertionError("Bar glow must default on");
        System.out.println("Dab native regular/slim poses at five phases and local additive HUD line/ring/health glow passed.");
    }
    private static int pixel(int x,int y){ByteBuffer p=BufferUtils.createByteBuffer(4);GL11.glReadPixels(x,719-y,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);return (p.get(0)&255)<<16|(p.get(1)&255)<<8|p.get(2)&255;}
    private static void save(Path path,int w,int h)throws Exception{ByteBuffer p=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=((h-1-y)*w+x)*4;img.setRGB(x,y,(p.get(i)&255)<<16|(p.get(i+1)&255)<<8|p.get(i+2)&255);}ImageIO.write(img,"png",path.toFile());}
}
