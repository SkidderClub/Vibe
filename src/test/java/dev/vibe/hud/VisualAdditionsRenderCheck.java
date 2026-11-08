package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.path.PathTestFixture;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.module.impl.visual.*;
import dev.vibe.ui.render.*;
import java.util.*;
import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.*;

public final class VisualAdditionsRenderCheck {
    private static final int W=1280,H=720;
    @SuppressWarnings("unchecked") public static void verify(Minecraft mc,Path out)throws Exception{
        mc.displayWidth=W;mc.displayHeight=H;mc.gameSettings.guiScale=1;
        HudModule hud=new HudModule();Vibe.getInstance().getModuleManager().registerDynamic(hud);HudManager manager=new HudManager(out.resolve("new-visuals-layout").toFile());PathTestFixture.set(Vibe.class,Vibe.getInstance(),"hudManager",manager);
        HudManager.HudElement e=manager.getElement(HudManager.KEYSTROKES);e.setBounds(0,0,78,105);e.moveTo(50,50,new ScaledResolution(mc));
        for(String theme:new String[]{"Vibe","Skeet","LiquidGlass","Lavalamp"}){e.setTheme(theme);flat();
            for(int flags=0;flags<8;flags++){hud.keystrokes.leftClick.setEnabled((flags&1)!=0);hud.keystrokes.rightClick.setEnabled((flags&2)!=0);hud.keystrokes.space.setEnabled((flags&4)!=0);e.moveTo(40+flags*145,120,new ScaledResolution(mc));manager.drawPreview(e,mc.fontRendererObj);
                int rows=2+((flags&3)!=0?1:0)+((flags&4)!=0?1:0);if(e.getHeight()!=rows*24+(rows-1)*3)throw new AssertionError("Optional keystrokes rows did not compact: "+flags);
            }save(out.resolve("keystrokes-"+theme.toLowerCase(Locale.ROOT)+".png"));
        }
        int old=mc.gameSettings.keyBindForward.getKeyCode();mc.gameSettings.keyBindForward.setKeyCode(org.lwjgl.input.Keyboard.KEY_UP);flat();manager.drawPreview(e,mc.fontRendererObj);save(out.resolve("keystrokes-rebound.png"));mc.gameSettings.keyBindForward.setKeyCode(old);
        MotionBlurRenderer blur=new MotionBlurRenderer();flat();GL11.glClearColor(0,0,1,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);blur.composite(W,H,.5F);
        GL11.glClearColor(1,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);blur.composite(W,H,.5F);
        int rgb=pixel(W/2,H/2);if(Math.abs((rgb>>16&255)-128)>3||Math.abs((rgb&255)-128)>3)throw new AssertionError("Motion history did not blend: "+Integer.toHexString(rgb));
        if(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)||!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK))throw new AssertionError("Motion blur changed depth state");
        Gui.drawRect(20,20,80,40,0xFFFFFFFF);if(pixel(30,30)!=0xFFFFFF)throw new AssertionError("HUD was blurred after compositing");save(out.resolve("motion-blur-sharp-hud.png"));
        blur.reset();GL11.glClearColor(0,1,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);blur.composite(W,H,.9F);if(pixel(W/2,H/2)!=0x00FF00)throw new AssertionError("History survived reset");blur.composite(640,360,.9F);blur.close();
        KillEffectsModule kills=new KillEffectsModule();Vibe.getInstance().getModuleManager().registerDynamic(kills);kills.setEnabled(true);kills.throughWalls.setEnabled(true);
        ResourceLocation skin=new ResourceLocation("vibe:kill-preview");BufferedImage pixels=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<64;y++)for(int x=0;x<64;x++)pixels.setRGB(x,y,y<16?0xFFFFD5AF:y<32?0xFF35AEB6:0xFF314678);mc.renderEngine.loadTexture(skin,new DynamicTexture(pixels));
        Matrix4f root=new Matrix4f();root.translate(new Vector3f(0,1.5078125F,0));root.rotate((float)Math.PI,new Vector3f(0,1,0));root.scale(new Vector3f(-1,-1,1));HologramPose pose=new HologramPose(new ModelPlayer(0,false),root,skin,false,0);
        java.lang.reflect.Field field=KillEffectsModule.class.getDeclaredField("deaths");field.setAccessible(true);List<KillEffectsModule.Death> deaths=(List<KillEffectsModule.Death>)field.get(kills);
        KillEffectsRenderer renderer=new KillEffectsRenderer();
        for(String mode:kills.mode.getModes())for(float phase:new float[]{.12F,.42F,.72F}){
            deaths.clear();deaths.add(new KillEffectsModule.Death(UUID.randomUUID(),0,0,0,0,0,3200,mode,pose,1234));world();renderer.render(kills,0,0,0,(long)(3200*phase));
            if(GL11.glGetError()!=0)throw new AssertionError("Kill mode GL error: "+mode);
            save(out.resolve("kill-"+mode.toLowerCase(Locale.ROOT).replace(' ','-')+"-"+Math.round(phase*100)+".png"));
        }
        renderer.close();kills.setEnabled(false);System.out.println("KillEffects: five native mesh/model modes at three animation stages; temporal blur pixels/reset/depth state; all eight Keystrokes layouts in four themes passed.");
    }
    private static void flat(){GL11.glViewport(0,0,W,H);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glClearColor(.025F,.035F,.05F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,W,H,0,-100,100);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();}
    private static void world(){flat();GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GLU.gluPerspective(58,W/(float)H,.05F,100);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GLU.gluLookAt(9,7,13,0,3,0,0,1,0);}
    private static int pixel(int x,int y){ByteBuffer p=BufferUtils.createByteBuffer(4);GL11.glReadPixels(x,H-1-y,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);return (p.get(0)&255)<<16|(p.get(1)&255)<<8|p.get(2)&255;}
    private static void save(Path path)throws Exception{ByteBuffer p=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);BufferedImage img=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=((H-1-y)*W+x)*4;img.setRGB(x,y,(p.get(i)&255)<<16|(p.get(i+1)&255)<<8|p.get(i+2)&255);}ImageIO.write(img,"png",path.toFile());}
}
