package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.hud.*;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.path.PathTestFixture;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Collections;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Pixel captures for target styles, health updates, particles, clocks and the game-view editor. */
public final class HudRefreshRenderCheck {
    private static Minecraft mc;
    private static Path output;
    public static void verify(Minecraft client,ModuleManager modules,Path directory) throws Exception {
        mc=client;output=directory;
        dev.vibe.camera.DroneCollisionsTest.registerBlocks();
        dev.vibe.ui.render.esp.EspRenderCheck.installItemRenderer(mc);
        HudModule hud=new HudModule();modules.registerDynamic(hud);
        HudManager manager=new HudManager(output.resolve("hud-refresh-fixture").toFile());
        PathTestFixture.set(Vibe.class,Vibe.getInstance(),"hudManager",manager);
        TargetHudData data=TargetHudData.sample();
        TargetHudRenderer renderer=new TargetHudRenderer();
        try(dev.vibe.ui.effect.LiquidGlassRenderer glass=new dev.vibe.ui.effect.LiquidGlassRenderer()) {
            for(String mode:hud.targetHud.mode.getModes()) {
                hud.targetHud.mode.setValue(mode);hud.targetHud.width.setValue(180D);
                hud.targetHud.health.setValue(mode.equals("Novoline")?0xFFEF7BDA:mode.equals("Tenacity")?0xFF36DDBD:0xFF91A2FF);
                frame(960,540,2);world();
                mc.fontRendererObj.drawStringWithShadow(mode,24,26,0xFFFFFFFF);
                int x=24,y=52,w=renderer.width(hud.targetHud,data,mc.fontRendererObj),h=renderer.height(hud.targetHud);
                renderer.clear();
                renderer.draw(hud.targetHud,data,mc.fontRendererObj,x,y,w,
                        ()->glass.draw(x,y,x+w,y+h,12,2,4,.72F),true,2000);
                frame(960,540,2);world();mc.fontRendererObj.drawStringWithShadow(mode,24,26,0xFFFFFFFF);
                renderer.draw(hud.targetHud,data,mc.fontRendererObj,x,y,w,
                        ()->glass.draw(x,y,x+w,y+h,12,2,4,.72F),true,2250);
                save("target-hud-"+mode.toLowerCase()+".png");
                if(mode.equals("Rise")) {
                    if(renderer.particleCount()==0)throw new AssertionError("Rise hit particles missing");
                    BufferedImage animated=read();
                    frame(960,540,2);world();mc.fontRendererObj.drawStringWithShadow(mode,24,26,-1);
                    renderer.draw(hud.targetHud,data,mc.fontRendererObj,x,y,w,()->{},true,2400);
                    save("target-rise-particles.png");
                    if(different(animated,read())<10)throw new AssertionError("Rise particles did not move");
                    hud.targetHud.particles.setEnabled(false);
                    renderer.draw(hud.targetHud,data,mc.fontRendererObj,x,y,w,()->{},false,2500);
                    if(renderer.particleCount()!=0)throw new AssertionError("Disabled particles survived");
                    hud.targetHud.particles.setEnabled(true);
                }
            }
            hud.targetHud.mode.setValue("Novoline");hud.targetHud.smoothing.setEnabled(false);
            for(float hp:new float[]{0,5.7F,16.34F,20,40}) {
                TargetHudData d=new TargetHudData(null,"Jacobiissupersus",hp,20,0,16,2,354,1,data.id,data.skin);
                frame(960,540,2);world();
                renderer.draw(hud.targetHud,d,mc.fontRendererObj,24,52,180,()->{},false,3000);
                save("target-novoline-health-"+Math.round(hp*10)+".png");
            }
            hud.clock.display.setValue("Analog");hud.clock.diameter.setValue(104D);hud.clock.date.setEnabled(true);
            frame(960,540,2);world();
            ClockRenderer.draw(hud.clock,mc.fontRendererObj,30,40,ZonedDateTime.parse("2026-10-24T10:10:30.500+02:00[Europe/Berlin]"));
            save("hud-analog-clock.png");
            hud.clock.diameter.setValue(56D);frame(960,540,1);
            manager.drawPreview(manager.getElement(HudManager.CLOCK),mc.fontRendererObj);
            if(manager.getElement(HudManager.CLOCK).getWidth()<mc.fontRendererObj.getStringWidth(ClockRenderer.date(ZonedDateTime.now())))
                throw new AssertionError("Small clock bounds omit the date");
            hud.clock.diameter.setValue(104D);
            verifyEditorPixels(manager,hud);
            for(int guiScale:new int[]{1,2,3}) {
                frame(960,540,guiScale);world();
                hud.targetHud.mode.setValue("LiquidGlass");hud.targetHud.width.setValue(168D);
                hud.getHudElements().setValue(new java.util.LinkedHashSet<String>(java.util.Arrays.asList(HudManager.WATERMARK,HudManager.TARGET_HUD,HudManager.CLOCK)));
                manager.getElement(HudManager.TARGET_HUD).setScale(1.25F);
                ScaledResolution res=new ScaledResolution(mc);
                HudEditorGui editor=new HudEditorGui(manager);
                editor.setWorldAndResolution(mc,res.getScaledWidth(),res.getScaledHeight());
                // Supply a real framebuffer image to the same capture used by onPreAll.
                mc.theWorld=PathTestFixture.allocate(net.minecraft.client.multiplayer.WorldClient.class);
                editor.captureGameView();mc.theWorld=null;
                frame(960,540,guiScale);editor.drawScreen(-1,-1,0);
                if(GL11.glGetInteger(0x8CA6)!=mc.getFramebuffer().framebufferObject)throw new AssertionError("Preview escaped its framebuffer");
                for(String id:hud.getHudElements().getValue()) {
                    HudManager.HudElement e=manager.getElement(id);
                    if(e.getWidth()<2||e.getHeight()<2)throw new AssertionError("Missing editor bounds: "+id);
                }
                HudManager.HudElement target=manager.getElement(HudManager.TARGET_HUD);
                if(target.getLeft()<0||target.getTop()<0||target.getLeft()+target.getWidth()>res.getScaledWidth()
                        ||target.getTop()+target.getHeight()>res.getScaledHeight())
                    throw new AssertionError("Scaled Target HUD clipped at GUI scale "+guiScale);
                save("hud-editor-world-scale-"+guiScale+".png");
                if(guiScale==1) {
                    mc.gameSettings.fboEnable=false;frame(960,540,1);editor.drawScreen(-1,-1,0);
                    save("hud-editor-world-unbuffered.png");mc.gameSettings.fboEnable=true;
                }
                editor.onGuiClosed();
                if(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST))throw new AssertionError("Editor leaked clipping");
            }
        } finally {renderer.close();}
        if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("New HUD GL error");
        System.out.println("Five target styles, health extremes, Rise hit particles, analog clock, captured world editor, widget scale, GUI scales 1/2/3 and framebuffer fallback passed.");
    }

    private static void verifyEditorPixels(HudManager manager,HudModule hud) throws Exception {
        HudManager.HudElement element=manager.getElement(HudManager.WATERMARK);
        element.setTheme("LiquidGlass");
        java.lang.reflect.Method draw=HudManager.class.getDeclaredMethod("drawWatermark",HudModule.class,
                ScaledResolution.class,net.minecraft.client.gui.FontRenderer.class);
        java.lang.reflect.Method scale=HudManager.class.getDeclaredMethod("renderScaled",HudManager.HudElement.class,
                ScaledResolution.class,Runnable.class);
        draw.setAccessible(true);scale.setAccessible(true);
        for(int guiScale:new int[]{1,2,3})for(float widgetScale:new float[]{1,1.5F}) {
            element.setScale(widgetScale);
            frame(960,540,guiScale);world();
            ScaledResolution resolution=new ScaledResolution(mc);
            scale.invoke(manager,element,resolution,(Runnable)()-> {
                try {draw.invoke(manager,hud,resolution,mc.fontRendererObj);}
                catch(Exception e) {throw new RuntimeException(e);}
            });
            BufferedImage actual=read();
            frame(960,540,guiScale);world();
            manager.beginEditorPreview(true);
            try {manager.drawPreview(element,mc.fontRendererObj);}finally {manager.endEditorPreview();}
            if(different(actual,read())!=0)throw new AssertionError("Editor differs from actual glass HUD at GUI "+guiScale+", widget "+widgetScale);
        }
        element.setTheme("Vibe");element.setScale(1);
    }
    private static void frame(int w,int h,int scale) throws Exception {
        mc.displayWidth=w;mc.displayHeight=h;mc.gameSettings.guiScale=scale;
        ClientChangesRenderCheck.frame(w,h);mc.entityRenderer.setupOverlayRendering();
    }
    private static void world() {
        ScaledResolution r=new ScaledResolution(mc);int w=r.getScaledWidth(),h=r.getScaledHeight();
        Gui.drawRect(0,0,w,h,0xFF91B8CE);
        Gui.drawRect(0,h/2,w,h,0xFF7D9864);
        for(int z=0;z<12;z++) {
            int y=h/2+z*z*h/300;
            Gui.drawRect(0,y,w,y+1,0xFF68824F);
        }
        for(int x=0;x<w;x+=24)Gui.drawRect(x,h/2,x+12,h,0x18607842);
        for(int x=20;x<w;x+=100) {
            Gui.drawRect(x,38,x+8,h/2+15,0xFF72634B);
            Gui.drawRect(x-17,22,x+26,68,0xFF567C54);
        }
        Gui.drawRect(w/2-3,h/2,w/2+4,h/2+1,0xBFFFFFFF);
        Gui.drawRect(w/2,h/2-3,w/2+1,h/2+4,0xBFFFFFFF);
    }
    private static BufferedImage read() {
        int w=mc.displayWidth,h=mc.displayHeight;ByteBuffer b=BufferUtils.createByteBuffer(w*h*4);
        GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);
        BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {int i=((h-1-y)*w+x)*4;image.setRGB(x,y,0xFF000000|(b.get(i)&255)<<16|(b.get(i+1)&255)<<8|(b.get(i+2)&255));}
        return image;
    }
    private static void save(String name) throws Exception {ImageIO.write(read(),"png",output.resolve(name).toFile());}
    private static int different(BufferedImage a,BufferedImage b) {int n=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))n++;return n;}
}
