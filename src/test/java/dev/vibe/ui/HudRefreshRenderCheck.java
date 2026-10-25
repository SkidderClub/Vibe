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
                if(mode.equals("Rise")||mode.equals("LiquidGlass")) {
                    if(renderer.particleCount()==0)throw new AssertionError(mode+" hit particles missing");
                    BufferedImage animated=read();
                    frame(960,540,2);world();mc.fontRendererObj.drawStringWithShadow(mode,24,26,-1);
                    renderer.draw(hud.targetHud,data,mc.fontRendererObj,x,y,w,
                            ()->glass.draw(x,y,x+w,y+h,12,2,4,.72F),true,2400);
                    save("target-"+mode.toLowerCase()+"-particles.png");
                    if(different(animated,read())<10)throw new AssertionError(mode+" particles did not move");
                    hud.targetHud.particles.setEnabled(false);
                    renderer.draw(hud.targetHud,data,mc.fontRendererObj,x,y,w,()->{},false,2500);
                    if(renderer.particleCount()!=0)throw new AssertionError("Disabled particles survived");
                    hud.targetHud.particles.setEnabled(true);
                }
            }
            verifyCompactGlass(hud,renderer,glass);
            hud.targetHud.mode.setValue("Novoline");hud.targetHud.smoothing.setEnabled(false);
            for(float hp:new float[]{0,5.7F,16.34F,20,40}) {
                TargetHudData d=new TargetHudData(null,"Jacobiissupersus",hp,20,0,16,2,354,1,data.id,data.skin);
                frame(960,540,2);world();
                renderer.draw(hud.targetHud,d,mc.fontRendererObj,24,52,180,()->{},false,3000);
                save("target-novoline-health-"+Math.round(hp*10)+".png");
            }
            verifyTargetCards(manager,hud,renderer);
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

    private static void verifyCompactGlass(HudModule hud,TargetHudRenderer renderer,dev.vibe.ui.effect.LiquidGlassRenderer glass) throws Exception {
        TargetHudSettings settings=hud.targetHud;settings.mode.setValue("LiquidGlass");settings.ping.setEnabled(true);
        settings.width.setValue(154D);settings.particles.setEnabled(true);
        if(!settings.particles.isVisible()||!settings.particleColor.isVisible()||!settings.particleLifetime.isVisible())
            throw new AssertionError("LiquidGlass particle controls are missing");
        TargetHudData sample=TargetHudData.sample();
        TargetHudData healthy=new TargetHudData(null,"N4K4N0",20,20,0,20,0,0,130,sample.id,sample.skin);
        int h=renderer.height(settings),w=renderer.width(settings,healthy,mc.fontRendererObj),x=24,y=52;
        if(h>=60||w!=154)throw new AssertionError("Glass card did not become shorter while retaining its width");
        renderer.clear();frame(960,540,2);world();
        renderer.draw(settings,healthy,mc.fontRendererObj,x,y,w,()->glass.draw(x,y,x+w,y+h,10,2,4,.72F),false,1000);
        if(renderer.particleCount()==0)throw new AssertionError("Live glass card did not emit first-hit particles");
        frame(960,540,2);world();
        renderer.draw(settings,healthy,mc.fontRendererObj,x,y,w,()->glass.draw(x,y,x+w,y+h,10,2,4,.72F),false,1200);
        save("target-liquidglass-compact.png");
        int before=renderer.particleCount();
        TargetHudData damaged=new TargetHudData(null,healthy.name,16,20,0,20,8,0,130,sample.id,sample.skin);
        renderer.draw(settings,damaged,mc.fontRendererObj,x,y,w,()->{},false,1250);
        if(renderer.particleCount()<=before)throw new AssertionError("Glass health loss did not emit a fresh hit burst");
        renderer.draw(settings,damaged,mc.fontRendererObj,x,y,w,()->{},false,2300);
        if(renderer.particleCount()!=0)throw new AssertionError("Glass hit particles did not expire");
        settings.particles.setEnabled(false);
        for(int width:new int[]{130,154,260}) {
            TargetHudData longName=new TargetHudData(null,"Long_Player_Name_123456789",40,40,6,20,0,0,999,sample.id,sample.skin);
            frame(960,540,2);world();
            renderer.draw(settings,longName,mc.fontRendererObj,x,y,width,()->glass.draw(x,y,x+width,y+h,10,2,4,.72F),false,2400);
            save("target-liquidglass-width-"+width+".png");
        }
        settings.particles.setEnabled(true);
        System.out.println("Compact LiquidGlass: original width, shorter height, long names with ping, live hit particles, expiry and controls passed.");
    }

    private static void verifyTargetCards(HudManager manager,HudModule hud,TargetHudRenderer measurements) throws Exception {
        java.lang.reflect.Method draw=HudManager.class.getDeclaredMethod("drawTargetCard",HudModule.class,TargetHudData.class,
                net.minecraft.client.gui.FontRenderer.class,int.class,int.class,int.class,float.class,boolean.class,long.class);
        draw.setAccessible(true);
        TargetHudData sample=TargetHudData.sample();
        java.util.List<TargetHudData> players=new java.util.ArrayList<TargetHudData>();
        for(int i=0;i<3;i++)players.add(new TargetHudData(null,new String[]{"First target","Second target","Third target"}[i],
                new float[]{16,7,12}[i],20,0,16,0,0,20,new java.util.UUID(0,i+1),sample.skin));
        for(String mode:hud.targetHud.mode.getModes()) {
            hud.targetHud.mode.setValue(mode);hud.targetHud.animationDuration.setValue(200D);
            int rowHeight=measurements.height(hud.targetHud);
            TargetHudCards cards=new TargetHudCards();
            for(long now:new long[]{1000,1050,1100,1200,1300,1400,1500}) {
                frame(960,540,2);world();KawaseBlur.beginHudFrame();
                java.util.List<TargetHudCards.Card> visible=cards.update(now<1300?players:Collections.emptyList(),hud.targetHud,rowHeight,4,now);
                for(TargetHudCards.Card card:visible) {
                    if(card.scale<.001F)continue;
                    int y=Math.round(30+card.offset-rowHeight*(1-card.scale)/2);
                    draw.invoke(manager,hud,card.data,mc.fontRendererObj,24,y,180,card.scale,false,now);
                }
                if(now==1100||now==1200||now==1400||now==1500)save("target-stack-"+mode.toLowerCase()+"-"+now+".png");
                if(now==1200&&visible.size()!=3)throw new AssertionError("Not all target cards appeared");
                if(now==1400&&(visible.size()!=3||visible.get(0).scale<=0||visible.get(0).scale>=1))
                    throw new AssertionError("Target cards did not animate out");
                if(now==1500&&!visible.isEmpty())throw new AssertionError("Target cards survived their exit");
                if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("Target stack graphics state: "+mode);
            }
        }
        // Alternating cards must not reset each other's interpolated health every frame.
        TargetHudRenderer renderer=new TargetHudRenderer();
        try {
            hud.targetHud.mode.setValue("Novoline");hud.targetHud.smoothing.setEnabled(true);hud.targetHud.health.setValue(0xFF11E050);
            frame(960,540,2);world();
            renderer.draw(hud.targetHud,players.get(0),mc.fontRendererObj,24,40,180,()->{},false,1000);
            renderer.draw(hud.targetHud,players.get(1),mc.fontRendererObj,24,90,180,()->{},false,1000);
            TargetHudData a=players.get(0);
            TargetHudData damaged=new TargetHudData(null,a.name,4,20,0,16,0,0,20,a.id,a.skin);
            frame(960,540,2);world();
            renderer.draw(hud.targetHud,damaged,mc.fontRendererObj,24,40,180,()->{},false,1016);
            renderer.draw(hud.targetHud,players.get(1),mc.fontRendererObj,24,90,180,()->{},false,1016);
            BufferedImage image=read();
            if((image.getRGB((24+44+60)*2,(40+22)*2)&0xFFFFFF)!=0x11E050)
                throw new AssertionError("Switching cards reset the first target's health interpolation");
            if((image.getRGB((24+44+60)*2,(90+22)*2)&0xFFFFFF)==0x11E050)
                throw new AssertionError("The second card used the first target's health");
            save("target-independent-health.png");
        } finally {renderer.close();}
        System.out.println("Three simultaneous target cards: five styles, pop in/out, animated glass coordinates and independent health state passed.");
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
