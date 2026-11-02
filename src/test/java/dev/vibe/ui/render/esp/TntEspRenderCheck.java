package dev.vibe.ui.render.esp;

import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.visual.TntESPModule;
import dev.vibe.path.PathTestFixture;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public final class TntEspRenderCheck {
    public static void verify(Minecraft mc,ModuleManager modules,Path output)throws Exception{
        dev.vibe.camera.DroneCollisionsTest.registerBlocks();mc.displayWidth=1280;mc.displayHeight=720;
        TntPredictionTest.CollisionWorld world=PathTestFixture.allocate(TntPredictionTest.CollisionWorld.class);world.loaded=true;
        PathTestFixture.set(World.class,world,"theProfiler",new Profiler());PathTestFixture.set(World.class,world,"provider",new net.minecraft.world.WorldProviderSurface());
        java.util.List<net.minecraft.entity.Entity> entities=new java.util.ArrayList<>();PathTestFixture.set(World.class,world,"loadedEntityList",entities);
        mc.theWorld=world;mc.thePlayer=PathTestFixture.allocate(EntityPlayerSP.class);mc.thePlayer.posY=65;
        RenderManager renderManager=PathTestFixture.allocate(RenderManager.class);PathTestFixture.set(Minecraft.class,mc,"renderManager",renderManager);
        EntityTNTPrimed tnt=new EntityTNTPrimed(world);tnt.setPosition(.5,67,.5);tnt.motionX=.075;tnt.fuse=53;entities.add(tnt);
        TntESPModule module=new TntESPModule();modules.registerDynamic(module);module.setEnabled(true);module.path.setEnabled(true);module.tick();
        if(module.forecasts().size()!=1)throw new AssertionError("Live TNT was not tracked");
        scene();
        TntEspRenderer renderer=new TntEspRenderer();renderer.render(new RenderWorldLastEvent(null,.5F));
        if(GL11.glIsEnabled(GL11.GL_BLEND)||!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK))throw new AssertionError("TNT ESP leaked GL state");
        BufferedImage grid=capture();ImageIO.write(grid,"png",output.resolve("tnt-esp.png").toFile());
        module.gridOpacity.setValue(0D);scene();renderer.render(new RenderWorldLastEvent(null,.5F));BufferedImage sparse=capture();int changed=0;
        for(int y=0;y<720;y++)for(int x=0;x<1280;x++){
            int a=grid.getRGB(x,y),b=sparse.getRGB(x,y);int difference=Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255));
            if(difference>12)changed++;
        }
        if(changed<1500)throw new AssertionError("Additional sphere grid did not meaningfully improve coverage: "+changed);
        module.gridOpacity.setValue(.45D);
        for(String quality:new String[]{"Low","Balanced","High"})for(double density:new double[]{4,8,12}){
            module.quality.setValue(quality);module.gridDensity.setValue(density);scene();renderer.render(new RenderWorldLastEvent(null,.5F));
        }
        module.quality.setValue("Balanced");module.gridDensity.setValue(8D);
        GL11.glFinish();long start=System.nanoTime();for(int i=0;i<120;i++)renderer.render(new RenderWorldLastEvent(null,.5F));GL11.glFinish();
        System.out.printf(java.util.Locale.ROOT,"TNT ESP: %.3f ms/render on %s%n",(System.nanoTime()-start)/120e6,GL11.glGetString(GL11.GL_RENDERER));
        renderer.close();
        module.setEnabled(false);if(!module.forecasts().isEmpty())throw new AssertionError("Disabled TNT retained forecasts");
        if(GL11.glGetError()!=0)throw new AssertionError("TNT ESP left a GL error");System.out.println("TNT depth, fuse label, prediction path and GL restoration passed.");
    }
    private static void scene(){
        GL11.glViewport(0,0,1280,720);GL11.glClearColor(.035F,.045F,.07F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GLU.gluPerspective(58,1280F/720,.1F,150);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glRotatef(12,1,0,0);GL11.glTranslatef(-2,-66,-22);
        GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_BLEND);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);
        GL11.glColor3f(.12F,.16F,.21F);GL11.glBegin(GL11.GL_QUADS);GL11.glVertex3f(-20,64,-20);GL11.glVertex3f(-20,64,20);GL11.glVertex3f(20,64,20);GL11.glVertex3f(20,64,-20);GL11.glEnd();
    }
    private static BufferedImage capture(){
        ByteBuffer pixels=BufferUtils.createByteBuffer(1280*720*4);GL11.glReadPixels(0,0,1280,720,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<720;y++)for(int x=0;x<1280;x++){int i=((719-y)*1280+x)*4;image.setRGB(x,y,(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|pixels.get(i+2)&255);}return image;
    }
}
