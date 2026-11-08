package dev.vibe.companion;

import dev.vibe.Vibe;
import dev.vibe.path.PathTestFixture;
import dev.vibe.module.impl.meme.GirlfriendModule;
import dev.vibe.ui.render.CompanionRenderer;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.GLU;

public final class CompanionRenderCheck {
    public static void verify(Minecraft mc,Path out)throws Exception{
        mc.displayWidth=1280;mc.displayHeight=720;GirlfriendModule m=new GirlfriendModule();Vibe.getInstance().getModuleManager().registerDynamic(m);
        EntityOtherPlayerMP npc=PathTestFixture.allocate(EntityOtherPlayerMP.class);DataWatcher watcher=new DataWatcher(npc);watcher.addObject(0,(byte)0);PathTestFixture.set(Entity.class,npc,"dataWatcher",watcher);npc.ticksExisted=30;npc.limbSwing=1;npc.prevLimbSwingAmount=npc.limbSwingAmount=.25F;
        CompanionRenderer renderer=new CompanionRenderer();frame();
        for(int i=0;i<3;i++)renderer.render(m,npc,-1.8+i*1.8,0,0,new float[]{0,55,180}[i],0,0,.5F);
        save(out.resolve("girlfriend-model-front-side-back.png"));ImageIO.write(CompanionSkin.create(m.hairColor.getArgb(),m.outfitColor.getArgb(),m.accentColor.getArgb(),m.eyeColor.getArgb(),m.outfit.getValue()),"png",out.resolve("girlfriend-skin.png").toFile());
        frame();GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GLU.gluLookAt(0,4.4F,3.2F,0,1.3F,0,0,1,0);renderer.render(m,npc,0,0,0,0,0,0,.5F);save(out.resolve("girlfriend-scalp-above.png"));
        for(String style:new String[]{"Goth","Chic","Casual"}){m.outfit.setValue(style);frame();renderer.render(m,npc,0,0,0,40,0,0,.5F);save(out.resolve("girlfriend-outfit-"+style.toLowerCase(java.util.Locale.ROOT)+".png"));}
        m.outfit.setValue("Casual");frame();renderer.render(m,npc,0,0,0,0,0,0,.5F);save(out.resolve("girlfriend-casual-refined-face.png"));m.outfit.setValue("Chic");
        m.hairColor.setValue(0xFFAF874E);m.outfitColor.setValue(0xFF7D588F);frame();renderer.render(m,npc,0,0,0,0,15,0,.5F);save(out.resolve("girlfriend-custom-colors.png"));
        npc.ticksExisted=94;frame();renderer.render(m,npc,0,0,0,0,0,0,.5F);save(out.resolve("girlfriend-blinking.png"));
        if(GL11.glGetError()!=0)throw new AssertionError("Companion left a GL error");renderer.close();System.out.println("Girlfriend: native slim model, hair/accessories, three viewpoints, animation and custom skin colors rendered on "+GL11.glGetString(GL11.GL_RENDERER));
    }
    private static void frame(){GL11.glViewport(0,0,1280,720);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glClearColor(.035F,.045F,.06F,1);GL11.glDepthMask(true);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GLU.gluPerspective(32,1280F/720,.05F,50);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GLU.gluLookAt(0,1.5F,6.5F,0,.9F,0,0,1,0);}
    private static void save(Path path)throws Exception{ByteBuffer p=BufferUtils.createByteBuffer(1280*720*4);GL11.glReadPixels(0,0,1280,720,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);BufferedImage img=new BufferedImage(1280,720,BufferedImage.TYPE_INT_RGB);for(int y=0;y<720;y++)for(int x=0;x<1280;x++){int i=((719-y)*1280+x)*4;img.setRGB(x,y,(p.get(i)&255)<<16|(p.get(i+1)&255)<<8|p.get(i+2)&255);}ImageIO.write(img,"png",path.toFile());}
}
