package dev.vibe.ui.screen;

import dev.vibe.aiaura.*;
import dev.vibe.module.impl.combat.AIAuraModule;
import dev.vibe.path.PathTestFixture;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.Collections;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Native fonts, native player rig and real GL pixels; no game window, server or real profile. */
public final class AIAuraRecorderRenderCheck {
    private static final int W=960,H=600;
    public static void main(String[] args)throws Exception{
        Path output=Paths.get(args[0]);Files.createDirectories(output);Pbuffer buffer=new Pbuffer(W,H,new PixelFormat(8,24,8),null,null);buffer.makeCurrent();
        try(PathTestFixture f=new PathTestFixture()){
            Minecraft mc=f.mc;mc.displayWidth=W;mc.displayHeight=H;mc.gameSettings.guiScale=1;OpenGlHelper.initializeTextures();PathTestFixture.set(Minecraft.class,mc,"mcDataDir",output.toFile());
            IMetadataSerializer metadata=new IMetadataSerializer();metadata.registerMetadataSectionType(new TextureMetadataSectionSerializer(),TextureMetadataSection.class);metadata.registerMetadataSectionType(new FontMetadataSectionSerializer(),FontMetadataSection.class);
            PathTestFixture.set(Minecraft.class,mc,"mcLanguageManager",new LanguageManager(metadata,"en_US"));SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(metadata);resources.reloadResourcePack(new DefaultResourcePack(Collections.<String,File>emptyMap()));PathTestFixture.set(Minecraft.class,mc,"mcResourceManager",resources);mc.renderEngine=new TextureManager(resources);mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.renderEngine,false);mc.fontRendererObj.onResourceManagerReload(resources);
            AIAuraModule module=new AIAuraModule();f.modules.registerDynamic(module);AuraLearning learning=new AuraLearning(Files.createTempDirectory(output,"isolated-profile-"));PathTestFixture.set(AIAuraModule.class,module,"learning",learning);
            AIAuraRecorderGui gui=new AIAuraRecorderGui(module,null);gui.setWorldAndResolution(mc,W,H);frame(gui,W,H);save(output.resolve("dashboard-empty.png"),W,H);
            gui.start(false);RecorderArena arena=(RecorderArena)get(AIAuraRecorderGui.class,gui,"arena");
            frame(gui,W,H);save(output.resolve("hand-idle.png"),W,H);check(skinPixels(W,H)>500,"The native first-person arm was not visible");
            arena.attackDown();arena.releaseAttack();for(int i=0;i<4;i++)arena.tick(0,1,1);frame(gui,W,H);save(output.resolve("hand-swing.png"),W,H);
            int originalBind=mc.gameSettings.keyBindTogglePerspective.getKeyCode();mc.gameSettings.keyBindTogglePerspective.setKeyCode(org.lwjgl.input.Keyboard.KEY_P);gui.perspectivePressed(originalBind);check(arena.perspective==0,"Recorder ignored the configured perspective binding");gui.perspectivePressed(org.lwjgl.input.Keyboard.KEY_P);check(arena.perspective==1&&mc.gameSettings.thirdPersonView==0,"Perspective switching changed the outer game settings");frame(gui,W,H);save(output.resolve("perspective-rear.png"),W,H);
            gui.perspectivePressed(org.lwjgl.input.Keyboard.KEY_P);check(arena.perspective==2,"Front perspective was not selected");frame(gui,W,H);save(output.resolve("perspective-front.png"),W,H);gui.perspectivePressed(org.lwjgl.input.Keyboard.KEY_P);mc.gameSettings.keyBindTogglePerspective.setKeyCode(originalBind);
            for(int i=0;i<80;i++)arena.tick(0,1,1);frame(gui,W,H);save(output.resolve("arena-manual.png"),W,H);check(arena.wave>0&&!arena.enemies.isEmpty(),"Arena did not spawn native rigs");
            int recorded=learning.frames();gui.pause();check(learning.frames()==recorded+1,"Pause did not commit pending manual frame");check(Files.exists(output.resolve("vibe/aiaura"))==false,"Check touched the module's normal profile");
            frame(gui,W,H);save(output.resolve("dashboard-paused.png"),W,H);
            for(AuraDataset.Frame row:AuraFixtures.demonstrations().frames)learning.append(row);PathTestFixture.set(AuraLearning.class,learning,"model",AuraFixtures.policy());frame(gui,W,H);save(output.resolve("dashboard-ready.png"),W,H);
            int before=learning.frames();gui.start(true);arena=(RecorderArena)get(AIAuraRecorderGui.class,gui,"arena");for(int i=0;i<80;i++)arena.tick(0,1,1);frame(gui,W,H);save(output.resolve("arena-preview.png"),W,H);gui.pause();check(before==learning.frames(),"Preview polluted manual training data");
            for(int[] size:new int[][]{{427,240},{320,200}}){int w=size[0],h=size[1];mc.displayWidth=w;mc.displayHeight=h;gui.setWorldAndResolution(mc,w,h);frame(gui,w,h);save(output.resolve("dashboard-"+w+".png"),w,h);gui.start(false);arena=(RecorderArena)get(AIAuraRecorderGui.class,gui,"arena");for(int i=0;i<35;i++)arena.tick(0,1,1);frame(gui,w,h);save(output.resolve("arena-"+w+".png"),w,h);gui.pause();}
            mc.displayWidth=W;mc.displayHeight=H;mc.inGameHasFocus=false;mc.currentScreen=new GuiScreen(){};int saved=learning.frames();
            module.reset.setEnabled(true);module.tickMenus();check(mc.currentScreen instanceof GuiYesNo,"Reset skipped the warning dialog");frame(mc.currentScreen,W,H);save(output.resolve("reset-confirmation.png"),W,H);callback((GuiYesNo)mc.currentScreen).confirmClicked(false,0);check(learning.frames()==saved&&learning.model()!=null,"Cancelling reset deleted data");
            module.reset.setEnabled(true);module.tickMenus();callback((GuiYesNo)mc.currentScreen).confirmClicked(true,0);check(learning.frames()==0&&learning.model()==null,"Confirming reset did not clear AI data");
            check(GL11.glGetError()==0,"Recorder left a GL error");gui.onGuiClosed();
            System.out.println("AIAura GPU checks passed: native fonts/player rigs, manual/preview scenes, local persistence, preview isolation and 960/427/320 pixel layouts.");
        }finally{buffer.destroy();}
    }
    private static Object get(Class<?> type,Object object,String name)throws Exception{java.lang.reflect.Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static int skinPixels(int w,int h){ByteBuffer pixels=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);int count=0;for(int y=0;y<h/2;y++)for(int x=w/2;x<w;x++){int i=(y*w+x)*4,r=pixels.get(i)&255,g=pixels.get(i+1)&255,b=pixels.get(i+2)&255;if(r>g*1.1&&g>b*1.08&&r>80)count++;}return count;}
    private static GuiYesNoCallback callback(GuiYesNo dialog)throws Exception{for(java.lang.reflect.Field field:GuiYesNo.class.getDeclaredFields())if(GuiYesNoCallback.class.isAssignableFrom(field.getType())){field.setAccessible(true);return (GuiYesNoCallback)field.get(dialog);}throw new AssertionError("Missing confirmation callback");}
    private static void frame(GuiScreen gui,int w,int h){GL11.glViewport(0,0,w,h);GL11.glClearColor(1,1,1,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,w,h,0,-1000,1000);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();gui.drawScreen(0,0,1);GL11.glFinish();}
    private static void save(Path path,int w,int h)throws Exception{ByteBuffer pixels=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);int contrasted=0,bright=0;long luminance=0;for(int y=0;y<h;y++)for(int x=0;x<w;x++){int p=((h-1-y)*w+x)*4,r=pixels.get(p)&255,g=pixels.get(p+1)&255,b=pixels.get(p+2)&255;image.setRGB(x,y,0xFF000000|r<<16|g<<8|b);luminance+=(r*21+g*72+b*7)/100;if(Math.max(r,Math.max(g,b))>60)contrasted++;if(r>190&&g>190&&b>190)bright++;}check(contrasted>100,"Rendered frame had no readable geometry or text");check(luminance/(double)(w*h)<80,"Recorder/arena became too bright");check(bright<w*h*.08,"Large bright surfaces returned");ImageIO.write(image,"png",path.toFile());}
    private static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
}
