package dev.vibe.teleport;

import dev.vibe.Vibe;
import dev.vibe.module.impl.movement.BasicTeleportModule;
import dev.vibe.ui.render.TeleportPortalRenderer;
import java.util.*;
import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class PortalRenderCheck {
    @SuppressWarnings("unchecked") public static void verify(Path output)throws Exception{
        BasicTeleportModule module=new BasicTeleportModule();Vibe.getInstance().getModuleManager().registerDynamic(module);
        java.lang.reflect.Field field=BasicTeleportModule.class.getDeclaredField("portals");field.setAccessible(true);List<BasicTeleportModule.Portal> portals=(List<BasicTeleportModule.Portal>)field.get(module);
        TeleportPortalRenderer renderer=new TeleportPortalRenderer();
        BufferedImage atlas=PortalSprite.read();ImageIO.write(atlas,"png",output.resolve("portal-decoded-atlas.png").toFile());
        for(int phase:new int[]{100,300,600,850}){
            portals.clear();portals.add(new BasicTeleportModule.Portal(0,0,0,0,0,1000));portals.add(new BasicTeleportModule.Portal(2.5,0,-.5,35,0,1000));
            frame();GL11.glDepthMask(true);GL11.glEnable(GL11.GL_DEPTH_TEST);renderer.render(module,0,0,0,phase);
            if(!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)||!GL11.glIsEnabled(GL11.GL_DEPTH_TEST))throw new AssertionError("Portal changed caller depth state");
            if(GL11.glGetError()!=0)throw new AssertionError("Portal OpenGL error");save(output.resolve("teleport-portals-"+phase+".png"));
        }
        renderer.close();System.out.println("Basic Teleport portal atlas, opening/spin/closing stages, two anchored endpoints and native depth state passed.");
    }
    private static void frame(){GL11.glViewport(0,0,1280,720);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDepthMask(true);GL11.glClearColor(.055F,.075F,.1F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(-3,5,-.5,4,-30,30);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();}
    private static void save(Path path)throws Exception{ByteBuffer p=BufferUtils.createByteBuffer(1280*720*4);GL11.glReadPixels(0,0,1280,720,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);BufferedImage img=new BufferedImage(1280,720,BufferedImage.TYPE_INT_RGB);for(int y=0;y<720;y++)for(int x=0;x<1280;x++){int i=((719-y)*1280+x)*4;img.setRGB(x,y,(p.get(i)&255)<<16|(p.get(i+1)&255)<<8|p.get(i+2)&255);}ImageIO.write(img,"png",path.toFile());}
}
