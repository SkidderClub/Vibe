package dev.vibe.hud;

import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.ui.ClientChangesRenderCheck;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Transparent atlas pixels must survive caller state left by other HUD renderers. */
public final class HealthTransparencyRenderCheck {
    public static void verify(Minecraft mc,ModuleManager modules,Path output)throws Exception{
        mc.displayWidth=960;mc.displayHeight=540;mc.gameSettings.guiScale=1;
        HudModule hud=new HudModule();modules.registerDynamic(hud);HudManager manager=new HudManager(output.resolve("health-transparency-fixture").toFile());
        HudManager.HudElement e=manager.getElement(HudManager.CUSTOM_HEALTH);HudOverlayRenderer renderer=new HudOverlayRenderer();ScaledResolution res=new ScaledResolution(mc);
        frame();renderer.health(manager,hud,e,res,true);e.moveTo(80,140,res);
        for(String mode:hud.customHealth.mode.getModes())for(boolean numbers:new boolean[]{false,true}){
            hud.customHealth.mode.setValue(mode);hud.customHealth.numbers.setEnabled(numbers);
            frame();renderer.health(manager,hud,e,res,true);int[] expected=region(e);
            frame();GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glBlendFunc(GL11.GL_ONE,GL11.GL_ZERO);
            renderer.health(manager,hud,e,res,true);int[] actual=region(e);
            int differences=0;for(int i=0;i<expected.length;i++)if(expected[i]!=actual[i])differences++;
            if(mode.equals("Fancy")&&!numbers)save(output.resolve("health-transparency.png"));
            if(differences!=0)throw new AssertionError(mode+" health has "+differences+" changed pixels after disabled blend/alpha state");
            if(GL11.glGetError()!=0)throw new AssertionError("Health GL error");
        }
        System.out.println("Health transparency passed: all three modes, hunger/armour icons, values on/off and poisoned blend/alpha state.");
    }
    private static void frame()throws Exception{ClientChangesRenderCheck.frame(960,540);}
    private static int[] region(HudManager.HudElement e){int w=e.getWidth(),h=e.getHeight();ByteBuffer data=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(e.getLeft(),540-e.getTop()-h,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);int[] rgb=new int[w*h];for(int i=0;i<rgb.length;i++)rgb[i]=(data.get(i*4)&255)<<16|(data.get(i*4+1)&255)<<8|data.get(i*4+2)&255;return rgb;}
    private static void save(Path file)throws Exception{ByteBuffer data=BufferUtils.createByteBuffer(960*540*4);GL11.glReadPixels(0,0,960,540,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);BufferedImage image=new BufferedImage(960,540,BufferedImage.TYPE_INT_RGB);for(int y=0;y<540;y++)for(int x=0;x<960;x++){int i=((539-y)*960+x)*4;image.setRGB(x,y,(data.get(i)&255)<<16|(data.get(i+1)&255)<<8|data.get(i+2)&255);}ImageIO.write(image,"png",file.toFile());}
}
