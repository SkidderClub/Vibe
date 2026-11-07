package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.module.impl.client.*;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Native OpenGL previews, warnings, custom font colors and clipped Island morphs. */
public final class HudAdditionsRenderCheck {
    public static void verify(Minecraft mc,HudManager manager,HudModule hud,Path out)throws Exception{
        HudManager.HudElement block=manager.getElement(HudManager.BLOCK_COUNTER),island=manager.getElement(HudManager.DYNAMIC_ISLAND),watermark=manager.getElement(HudManager.WATERMARK);
        hud.getWatermarkDetails().setValue(new LinkedHashSet<>(hud.getWatermarkDetails().getOptions()));hud.watermark.client.mode.setValue("Fade");hud.watermark.client.glow.setEnabled(true);hud.watermark.separator.setValue("Bullet");
        for(String theme:new String[]{"Vibe","Skeet","LiquidGlass","Lavalamp"}){
            block.setTheme(theme);island.setTheme(theme);watermark.setTheme(theme);block.setScale(1);island.setScale(1);watermark.setScale(1);
            manager.beginEditorPreview(true);
            try{
                String[] views={"Idle","Notification","TargetHud","Tab"};int[][] sizes=new int[4][2];
                for(int i=0;i<views.length;i++){hud.island.preview.setValue(views[i]);manager.drawPreview(island,mc.fontRendererObj);sizes[i][0]=island.getWidth();sizes[i][1]=island.getHeight();}
                frame(1280,720);
                block.moveTo(40,65,new ScaledResolution(mc));manager.drawPreview(block,mc.fontRendererObj);
                int[][] positions={{270,65},{440,65},{680,65},{440,150}};
                for(int i=0;i<views.length;i++){
                    hud.island.preview.setValue(views[i]);island.setBounds(0,0,sizes[i][0],sizes[i][1]);
                    island.moveTo(positions[i][0],positions[i][1],new ScaledResolution(mc));manager.drawPreview(island,mc.fontRendererObj);
                    if(island.getLeft()<0||island.getLeft()+island.getWidth()>1280)throw new AssertionError("Island escaped viewport");
                }
                watermark.moveTo(40,420,new ScaledResolution(mc));manager.drawPreview(watermark,mc.fontRendererObj);
            }finally{manager.endEditorPreview();}
            save(out.resolve("hud-additions-"+theme.toLowerCase(Locale.ROOT)+".png"));
        }
        // All tags must wrap instead of escaping at a compact GUI scale.
        mc.gameSettings.guiScale=3;ScaledResolution res=new ScaledResolution(mc);frame(res.getScaledWidth(),res.getScaledHeight());
        watermark.setScale(1.25F);manager.drawPreview(watermark,mc.fontRendererObj);
        if(watermark.getWidth()>res.getScaledWidth())throw new AssertionError("Full watermark escaped compact viewport");
        save(out.resolve("watermark-all-tags-scaled.png"));watermark.setScale(1);mc.gameSettings.guiScale=1;
        warnings(mc,manager,hud,out);ownName(mc,out);morphs(mc,manager,hud,out);
        hud.island.preview.setValue("Idle");hud.getWatermarkDetails().setValue(new LinkedHashSet<>(Arrays.asList("Version","FPS")));
        if(GL11.glGetError()!=0)throw new AssertionError("New HUD elements left an OpenGL error");
        System.out.println("Block Counter themes/warnings, Island views/morph clipping, compact watermark and protected-name colors/glow passed.");
    }
    private static void warnings(Minecraft mc,HudManager manager,HudModule hud,Path out)throws Exception{
        net.minecraft.client.entity.EntityPlayerSP player=mc.thePlayer;net.minecraft.client.multiplayer.WorldClient world=mc.theWorld;
        try{
            mc.theWorld=PathTestFixture.allocate(net.minecraft.client.multiplayer.WorldClient.class);
            mc.thePlayer=PathTestFixture.allocate(dev.vibe.inventory.AutomationFixture.Player.class);mc.thePlayer.worldObj=mc.theWorld;
            mc.thePlayer.inventory=new net.minecraft.entity.player.InventoryPlayer(mc.thePlayer);
            hud.blockCounter.scaffold.setEnabled(false);hud.blockCounter.eagle.setEnabled(false);hud.blockCounter.animate.setEnabled(false);
            HudManager.HudElement e=manager.getElement(HudManager.BLOCK_COUNTER);e.setTheme("Vibe");BlockCounterRenderer renderer=new BlockCounterRenderer();
            frame(1280,720);ScaledResolution res=new ScaledResolution(mc);
            int x=40;for(int count:new int[]{128,24,4}){
                mc.thePlayer.inventory.mainInventory[0]=new ItemStack(Blocks.stone,Math.min(64,count));mc.thePlayer.inventory.mainInventory[1]=count>64?new ItemStack(Blocks.stone,count-64):null;
                e.moveTo(x,80,res);renderer.draw(manager,hud,e,res,false);x+=160;
            }
            save(out.resolve("block-counter-depletion-warnings.png"));
        }finally{mc.thePlayer=player;mc.theWorld=world;hud.blockCounter.scaffold.setEnabled(true);hud.blockCounter.animate.setEnabled(true);}
    }
    private static void ownName(Minecraft mc,Path out)throws Exception{
        NameProtectModule m=new NameProtectModule();Vibe.getInstance().getModuleManager().registerDynamic(m);
        for(Setting<?> s:m.getSettings()){if(s.getRawName().equals("Protected Name"))((StringSetting)s).setValue("VibeUser");if(s.getRawName().equals("Override Color"))((BooleanSetting)s).setEnabled(true);}
        m.nameStyle.glow.setEnabled(true);
        frame(1280,720);
        int y=40;for(String mode:new String[]{"Static","Fade","Rainbow"}){
            m.nameStyle.mode.setValue(mode);NameProtectRenderer.drawAt(mc.fontRendererObj,"Before <VibeUser> after",40,y,0xFFFFFFFF,true,1600);
            dev.vibe.ui.NeverLoseFont.BOLD.draw("VibeUser",300,y,0xFFFFFFFF);y+=40;
        }
        save(out.resolve("nameprotect-native-and-smooth-font.png"));
        // Execute the actual transformed FontRenderer, with the helper in its loader.
        ClassLoader loader=new ClassLoader(HudAdditionsRenderCheck.class.getClassLoader()){
            @Override protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!name.equals("net.minecraft.client.gui.FontRenderer")&&!name.equals("dev.vibe.hud.NameProtectRenderer")&&!name.equals("dev.vibe.hud.TextGlowScope"))return super.loadClass(name,resolve);
                Class<?> type=findLoadedClass(name);
                if(type==null)try(InputStream in=getParent().getResourceAsStream(name.replace('.','/')+".class")){
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))!=-1;)bytes.write(buffer,0,n);
                    byte[] code=bytes.toByteArray();if(name.endsWith("FontRenderer"))code=new dev.vibe.core.FontLocalizationTransformer().transform(name,name,code);
                    type=defineClass(name,code,0,code.length);
                }catch(Exception e){throw new ClassNotFoundException(name,e);}
                if(resolve)resolveClass(type);return type;
            }
        };
        Class<?> type=loader.loadClass("net.minecraft.client.gui.FontRenderer");Object font=type.getConstructor(net.minecraft.client.settings.GameSettings.class,ResourceLocation.class,net.minecraft.client.renderer.texture.TextureManager.class,boolean.class).newInstance(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.renderEngine,false);
        type.getMethod("onResourceManagerReload",net.minecraft.client.resources.IResourceManager.class).invoke(font,mc.getResourceManager());
        frame(1280,720);Object end=type.getMethod("drawString",String.class,float.class,float.class,int.class,boolean.class).invoke(font,"<VibeUser> hello",40F,40F,-1,true);
        if(!end.equals(41+mc.fontRendererObj.getStringWidth("<VibeUser> hello")))throw new AssertionError("Protected-name core hook changed text metrics");
        save(out.resolve("nameprotect-transformed-font.png"));
    }
    private static void morphs(Minecraft mc,HudManager manager,HudModule hud,Path out)throws Exception{
        HudManager.HudElement e=manager.getElement(HudManager.DYNAMIC_ISLAND);e.setTheme("Lavalamp");hud.island.preview.setValue("Tab");e.setScale(1.5F);
        frame(1280,720);manager.beginEditorPreview(true);try{manager.drawPreview(e,mc.fontRendererObj);}finally{manager.endEditorPreview();}
        save(out.resolve("island-tab-scaled.png"));e.setScale(1);
        net.minecraft.client.multiplayer.WorldClient world=mc.theWorld;
        Object oldFeedback=Vibe.getInstance().getFeedback();
        try{
            mc.theWorld=PathTestFixture.allocate(net.minecraft.client.multiplayer.WorldClient.class);
            PathTestFixture.set(net.minecraft.world.World.class,mc.theWorld,"worldScoreboard",new net.minecraft.scoreboard.Scoreboard());
            dev.vibe.feedback.ModuleFeedback feedback=new dev.vibe.feedback.ModuleFeedback();PathTestFixture.set(Vibe.class,Vibe.getInstance(),"feedback",feedback);
            HudOverlayRenderer overlays=new HudOverlayRenderer();DynamicIslandRenderer renderer=new DynamicIslandRenderer();ScaledResolution res=new ScaledResolution(mc);
            e.moveTo(550,40,res);hud.island.duration.setValue(300D);
            frame(1280,720);renderer.draw(manager,hud,e,res,overlays,null,false);int idle=e.getWidth();
            feedback.notifications().add("scaffold","Scaffold",true,System.nanoTime()/1000000);
            renderer.draw(manager,hud,e,res,overlays,null,false);Thread.sleep(120);frame(1280,720);renderer.draw(manager,hud,e,res,overlays,null,false);
            if(e.getWidth()<=idle||e.getHeight()<=hud.island.idleHeight.getInt())throw new AssertionError("Notification did not expand the Island smoothly");
            save(out.resolve("island-opening-notification.png"));
            PathTestFixture.set(HudOverlayRenderer.class,overlays,"visible",true);
            renderer.draw(manager,hud,e,res,overlays,null,false);Thread.sleep(120);frame(1280,720);renderer.draw(manager,hud,e,res,overlays,null,false);
            ByteBuffer pixel=BufferUtils.createByteBuffer(4);int xx=e.getLeft()+e.getWidth()/2,yy=e.getTop()+e.getHeight()+12;
            GL11.glReadPixels(xx,719-yy,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
            if((pixel.get(0)&255)>30||(pixel.get(1)&255)>35)throw new AssertionError("Morph content escaped the growing Island");
            save(out.resolve("island-opening-tab.png"));
            PathTestFixture.set(HudOverlayRenderer.class,overlays,"visible",false);feedback.notifications().clear();
            renderer.draw(manager,hud,e,res,overlays,TargetHudData.sample(),false);Thread.sleep(330);frame(1280,720);renderer.draw(manager,hud,e,res,overlays,TargetHudData.sample(),false);
            if(e.getHeight()!=48)throw new AssertionError("Island did not settle on Target HUD");save(out.resolve("island-live-target.png"));
        }finally{mc.theWorld=world;PathTestFixture.set(Vibe.class,Vibe.getInstance(),"feedback",oldFeedback);}
    }
    private static void frame(int w,int h){GL11.glViewport(0,0,1280,720);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glClearColor(.045F,.055F,.075F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,w,h,0,-1000,1000);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();}
    private static void save(Path file)throws Exception{ByteBuffer pixels=BufferUtils.createByteBuffer(1280*720*4);GL11.glReadPixels(0,0,1280,720,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_RGB);for(int y=0;y<720;y++)for(int x=0;x<1280;x++){int i=((719-y)*1280+x)*4;image.setRGB(x,y,(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|pixels.get(i+2)&255);}ImageIO.write(image,"png",file.toFile());}
}
