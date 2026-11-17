package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.hud.*;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.client.*;
import dev.vibe.module.impl.combat.*;
import dev.vibe.module.impl.movement.*;
import dev.vibe.module.impl.visual.*;
import dev.vibe.module.impl.world.ScaffoldModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.clickgui.VibeClickGui;
import dev.vibe.ui.render.*;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Offscreen GL with supplied fonts, real controls and the existing native item-pose hook. */
public final class DiabloRenderCheck {
    public static void verify(Minecraft mc,ModuleManager modules,Path out)throws Exception{
        mc.displayWidth=1280;mc.displayHeight=720;
        PathTestFixture.set(Vibe.class,Vibe.getInstance(),"config",new dev.vibe.config.VibeConfig(out.resolve("diablo-config").toFile()));
        dev.vibe.camera.DroneCollisionsTest.registerBlocks();
        ClickGuiModule style=register(modules,new ClickGuiModule());style.getTheme().setValue("Diablo");
        CriticalsModule criticals=register(modules,new CriticalsModule());
        register(modules,new KillAuraModule());register(modules,new MoveFixModule());register(modules,new StepModule());
        register(modules,new LongJumpModule());register(modules,new ScaffoldModule());register(modules,new TargetEspModule());
        register(modules,new KillsayModule());register(modules,new dev.vibe.module.impl.meme.DabModule());
        HudModule hud=register(modules,new HudModule());AnimationsModule animations=register(modules,new AnimationsModule());
        VibeClickGui gui=new VibeClickGui();gui.setWorldAndResolution(mc,1280,720);draw(gui,1);save(out.resolve("diablo-dropdown.png"));
        Object workspace=read(gui,"dropdown");List<?> panels=(List<?>)read(workspace,"panels");
        check(panels.size()==dev.vibe.module.Category.values().length,"Diablo replaced Vibe's categories");
        check((Integer)read(workspace,"panelWidth")==100,"Source panel width");
        click(gui,find(gui,"module",criticals),1);
        for(int i=0;i<24;i++){Thread.sleep(12);draw(gui,1);}
        Object mode=find(gui,"setting",criticals.mode);String original=criticals.mode.getValue();
        click(gui,mode,0);check(!criticals.mode.getValue().equals(original),"Mode did not cycle forward");
        click(gui,mode,1);check(criticals.mode.getValue().equals(original),"Mode did not cycle backward");
        save(out.resolve("diablo-settings.png"));gui.onGuiClosed();
        mc.gameSettings.guiScale=2;VibeClickGui scaled=new VibeClickGui();scaled.setWorldAndResolution(mc,640,360);draw(scaled,2);
        ByteBuffer glyphs=BufferUtils.createByteBuffer(160*30*4);GL11.glReadPixels(28,720-72,160,30,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,glyphs);
        int visible=0;for(int i=0;i<glyphs.capacity();i+=4)if((glyphs.get(i)&255)>100&&(glyphs.get(i+1)&255)>100&&(glyphs.get(i+2)&255)>100)visible++;
        check(visible>12,"Scale 2 clipped the module text");save(out.resolve("diablo-gui-scale-2.png"));scaled.onGuiClosed();mc.gameSettings.guiScale=1;
        TargetHudRenderer renderer=new TargetHudRenderer();TargetHudSettings settings=hud.targetHud;settings.mode.setValue("Diablo");
        TargetHudData sample=TargetHudData.sample();check(renderer.height(settings)==45&&renderer.width(settings,sample,mc.fontRendererObj)==150,"Generic TargetHUD size");
        ClientChangesRenderCheck.frame(1280,720);
        for(int i=0;i<3;i++){
            float health=i==0?20:i==1?13.2F:2;
            TargetHudData data=new TargetHudData(null,"Alex",health,20,10,20,0,0,30,UUID.randomUUID(),sample.skin);
            GL11.glPushMatrix();GL11.glTranslatef(80,80+i*175,0);GL11.glScalef(3,3,1);
            renderer.draw(settings,data,mc.fontRendererObj,0,0,150,()->{},true,System.nanoTime()/1000000);GL11.glPopMatrix();
        }
        save(out.resolve("diablo-targethud.png"));
        mc.thePlayer=PathTestFixture.allocate(NovolineRenderCheck.PosePlayer.class);
        try{
            float[] previous=null;
            for(String name:new String[]{"Diablo","ExihiDiablo"}){
                animations.getBlockingStyle().setValue(name);GL11.glPushMatrix();GL11.glLoadIdentity();float[] matrix=new float[16];
                try{NovolineAnimations.begin(animations,.5F);check(NovolineAnimations.transform(.3F,0),"Missing native pose "+name);
                    FloatBuffer values=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,values);values.get(matrix);for(float value:matrix)check(Float.isFinite(value),"Nonfinite pose "+name);
                }finally{NovolineAnimations.end();GL11.glPopMatrix();}
                if(previous!=null)check(!Arrays.equals(previous,matrix),"Identical Diablo and Exhi transforms");previous=matrix;
                check(!NovolineAnimations.active()&&!NovolineAnimations.transform(.3F,0),"Pose leaked outside block scope");
            }
        }finally{mc.thePlayer=null;}
        ClientChangesRenderCheck.frame(1280,720);GL11.glLineWidth(1.5F);boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GL11.glPushMatrix();GL11.glTranslatef(400,360,0);GL11.glScalef(70,70,1);GL11.glRotatef(55,1,0,0);
        new DiabloTargetMarker().draw(2,0,1,1.8F,0xFF9000C4,0xFF9000C4,1000);GL11.glPopMatrix();
        check(GL11.glGetFloat(GL11.GL_LINE_WIDTH)==1.5F&&GL11.glIsEnabled(GL11.GL_DEPTH_TEST)==depth,"Marker leaked GL state");
        save(out.resolve("diablo-target-marker.png"));check(GL11.glGetError()==0,"Diablo rendering GL error");
        System.out.println("Diablo rendering passed: category panels, source fonts/controls, mode cycling, GUI scales 1/2, 150x45 TargetHUD, marker, both block poses and GL state.");
    }
    private static void draw(VibeClickGui gui,int scale)throws Exception{ClientChangesRenderCheck.frame(1280,720);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,1280/scale,720/scale,0,-1000,1000);GL11.glMatrixMode(GL11.GL_MODELVIEW);gui.drawScreen(-1,-1,1);}
    private static Object find(VibeClickGui gui,String field,Object expected)throws Exception{for(Object target:(List<?>)read(gui,"targets"))if(read(target,field)==expected)return target;throw new AssertionError("Missing control "+expected);}
    private static void click(VibeClickGui gui,Object row,int button)throws Exception{int x=((Integer)read(row,"left")+(Integer)read(row,"right"))/2,y=((Integer)read(row,"top")+(Integer)read(row,"bottom"))/2;GuiInput.mouseClicked(gui,x,y,button);GuiInput.mouseReleased(gui,x,y,button);}
    private static Object read(Object value,String name)throws Exception{java.lang.reflect.Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static <T extends Module>T register(ModuleManager modules,T module){modules.registerDynamic(module);return module;}
    private static void check(boolean result,String message){if(!result)throw new AssertionError(message);}
    private static void save(Path path)throws Exception{ByteBuffer bytes=BufferUtils.createByteBuffer(1280*720*4);GL11.glReadPixels(0,0,1280,720,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<720;y++)for(int x=0;x<1280;x++){int p=(y*1280+x)*4;image.setRGB(x,719-y,0xFF000000|(bytes.get(p)&255)<<16|(bytes.get(p+1)&255)<<8|(bytes.get(p+2)&255));}ImageIO.write(image,"PNG",path.toFile());}
}
