package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.hud.*;
import dev.vibe.module.*;
import dev.vibe.module.Module;
import dev.vibe.module.impl.client.*;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.movement.*;
import dev.vibe.module.impl.world.ScaffoldModule;
import dev.vibe.module.impl.visual.AnimationsModule;
import dev.vibe.ui.clickgui.VibeClickGui;
import dev.vibe.ui.render.NovolineAnimations;
import dev.vibe.path.PathTestFixture;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Real fonts, textures, clips and transform matrices; no game window or saved user profile. */
public final class NovolineRenderCheck {
    public static void verify(Minecraft mc,ModuleManager modules,Path out)throws Exception{
        mc.displayWidth=1280;mc.displayHeight=720;
        PathTestFixture.set(Vibe.class,Vibe.getInstance(),"config",new dev.vibe.config.VibeConfig(out.resolve("novoline-config").toFile()));
        dev.vibe.camera.DroneCollisionsTest.registerBlocks();
        ClickGuiModule style=register(modules,new ClickGuiModule());style.getTheme().setValue("Novoline");
        HudModule hud=register(modules,new HudModule());HudManager manager=new HudManager(out.resolve("novoline-layout").toFile());PathTestFixture.set(Vibe.class,Vibe.getInstance(),"hudManager",manager);
        register(modules,new MoveFixModule());register(modules,new KillAuraModule());register(modules,new StepModule());register(modules,new JesusModule());register(modules,new ScaffoldModule());AnimationsModule animations=register(modules,new AnimationsModule());
        VibeClickGui gui=new VibeClickGui();gui.setWorldAndResolution(mc,1280,720);
        ClientChangesRenderCheck.frame(1280,720);gui.drawScreen(-1,-1,1);save(out.resolve("novoline-dropdown.png"));
        Object row=null;
        for(Object target:(java.util.List<?>)read(gui,"targets"))if(read(target,"module") instanceof StepModule){row=target;break;}
        if(row==null)throw new AssertionError("Novoline dropdown did not retain Vibe's Step module");
        int x=((Integer)read(row,"left")+(Integer)read(row,"right"))/2,y=((Integer)read(row,"top")+(Integer)read(row,"bottom"))/2;
        GuiInput.mouseClicked(gui,x,y,1);GuiInput.mouseReleased(gui,x,y,1);
        ClientChangesRenderCheck.frame(1280,720);gui.drawScreen(x,y,1);save(out.resolve("novoline-settings.png"));gui.onGuiClosed();
        hud.targetHud.mode.setValue("Novoline");hud.getWatermarkDetails().toggle("Time");
        ClientChangesRenderCheck.frame(1280,720);
        int i=0;for(String id:new String[]{HudManager.WATERMARK,HudManager.TARGET_HUD,HudManager.NOTIFICATIONS}){
            HudManager.HudElement element=manager.getElement(id);element.setTheme("Novoline");manager.drawPreview(element,mc.fontRendererObj);element.moveTo(70,70+i++*150,new ScaledResolution(mc));manager.drawPreview(element,mc.fontRendererObj);
        }
        if(manager.getElement(HudManager.TARGET_HUD).getHeight()!=40)throw new AssertionError("Novoline target HUD inherited generic card height");
        save(out.resolve("novoline-hud.png"));
        // Exercise both arraylist choices through its real measurement/drawing path.
        for(String font:new String[]{"Novoline Client","Novoline Vanilla"}){
            hud.array.font.setValue(font);ClientChangesRenderCheck.frame(1280,720);manager.drawPreview(manager.getElement(HudManager.ARRAY_LIST),mc.fontRendererObj);save(out.resolve(font.replace(' ','-').toLowerCase(java.util.Locale.ROOT)+".png"));
        }
        PosePlayer player=PathTestFixture.allocate(PosePlayer.class);mc.thePlayer=player;
        try{
            for(String mode:NovolineAnimations.MODES){
                animations.getBlockingStyle().setValue(mode);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();
                try{NovolineAnimations.begin(animations,.5F);if(!NovolineAnimations.transform(.3F,0))throw new AssertionError("Missing pose "+mode);
                    FloatBuffer values=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,values);for(int n=0;n<16;n++)if(!Float.isFinite(values.get(n)))throw new AssertionError("Nonfinite pose: "+mode);
                }finally{NovolineAnimations.end();GL11.glPopMatrix();}
                if(NovolineAnimations.active()||NovolineAnimations.transform(.3F,0))throw new AssertionError("Pose leaked outside native block scope");
                if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("GL error in "+mode);
            }
        }finally{mc.thePlayer=null;}
        System.out.println("Novoline dropdown, settings, watermark, TargetHUD, stacked notifications, both arraylist fonts and all 16 native block transforms passed.");
    }
    private static Object read(Object value,String name)throws Exception{java.lang.reflect.Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static <T extends Module>T register(ModuleManager modules,T module){modules.registerDynamic(module);return module;}
    private static void save(Path path)throws Exception{
        ByteBuffer bytes=BufferUtils.createByteBuffer(1280*720*4);GL11.glReadPixels(0,0,1280,720,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<720;y++)for(int x=0;x<1280;x++){int p=(y*1280+x)*4;image.setRGB(x,719-y,0xFF000000|(bytes.get(p)&255)<<16|(bytes.get(p+1)&255)<<8|(bytes.get(p+2)&255));}ImageIO.write(image,"PNG",path.toFile());
    }
    public static class PosePlayer extends EntityPlayerSP {protected PosePlayer(){super(null,null,null,null);}@Override public float getSwingProgress(float partial){return .45F;}@Override public boolean isSneaking(){return false;}}
}
