package dev.vibe.features;

import dev.vibe.Vibe;
import dev.vibe.hud.*;
import dev.vibe.module.Module;
import dev.vibe.module.Category;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.client.*;
import dev.vibe.module.impl.movement.StepModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.ClientChangesRenderCheck;
import dev.vibe.ui.clickgui.VibeClickGui;
import dev.vibe.ui.GuiInput;
import dev.vibe.ui.screen.CameraRigGui;
import dev.vibe.module.impl.movement.FreecamModule;
import dev.vibe.module.impl.meme.PocketPetsModule;
import net.minecraft.client.model.*;
import net.minecraft.entity.Entity;
import dev.vibe.ui.render.HealthGradePass;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class NewFeaturesRenderCheck {
    public static void verify(Minecraft mc,ModuleManager modules,Path out)throws Exception{
        mc.displayWidth=1280;mc.displayHeight=720;mc.gameSettings.guiScale=1;
        PathTestFixture.set(Vibe.class,Vibe.getInstance(),"config",new dev.vibe.config.VibeConfig(out.resolve("new-features-profile").toFile()));
        HudModule hud=new HudModule();modules.registerDynamic(hud);HudManager manager=new HudManager(out.resolve("new-features-hud").toFile());PathTestFixture.set(Vibe.class,Vibe.getInstance(),"hudManager",manager);
        int[] xs={28,215,465,715,970};for(String theme:new String[]{"Vibe","Skeet","LiquidGlass","Lavalamp"}){
            ClientChangesRenderCheck.frame(1280,720);for(int i=0;i<DashboardWidgets.IDS.length;i++){String id=DashboardWidgets.IDS[i];HudManager.HudElement e=manager.getElement(id);e.setTheme(theme);manager.drawPreview(e,mc.fontRendererObj);e.moveTo(xs[i],150,new ScaledResolution(mc));manager.drawPreview(e,mc.fontRendererObj);mc.fontRendererObj.drawString(DashboardWidgets.title(id),xs[i],126,0xFFE7EAF1);}
            if(GL11.glGetError()!=0)throw new AssertionError("Dashboard GL error: "+theme);save(out.resolve("dashboard-"+theme.toLowerCase(java.util.Locale.ROOT)+".png"));
        }
        HealthColorGrading grading=hud.customHealth.grading;grading.enabled.setEnabled(true);grading.exposure.setValue(1D);try(HealthGradePass pass=new HealthGradePass()){
            ClientChangesRenderCheck.frame(1280,720);Gui.drawRect(0,0,1280,720,0xFF243650);int outside=pixel(50,50);pass.render(grading,false,()->Gui.drawRect(100,100,220,180,0xFF406020));int actual=pixel(150,140);if(Math.abs((actual>>16&255)-128)>4||Math.abs((actual>>8&255)-192)>4)throw new AssertionError("Health grade exposure: "+Integer.toHexString(actual));if(pixel(50,50)!=outside)throw new AssertionError("Health grade affected the world outside its content");save(out.resolve("health-grading-isolation.png"));
            grading.preview.setValue("Original");pass.render(grading,true,()->Gui.drawRect(300,100,420,180,0xFF406020));if(pixel(350,140)!=0x406020)throw new AssertionError("Before/after Original preview was graded");
            grading.preview.setValue("Graded");grading.exposure.setValue(0D);Runnable translucent=()->{HudBarGlow.line(400,300,100,8,0xFFFF6080,1);HudShapes.round(400,300,100,8,2,0x7740FF80);};ClientChangesRenderCheck.frame(1280,720);Gui.drawRect(0,0,1280,720,0xFF243650);translucent.run();int body=pixel(450,304),halo=pixel(450,298);ClientChangesRenderCheck.frame(1280,720);Gui.drawRect(0,0,1280,720,0xFF243650);pass.render(grading,false,translucent);for(int shift:new int[]{0,8,16}){if(Math.abs((body>>shift&255)-(pixel(450,304)>>shift&255))>3)throw new AssertionError("Neutral grading changed translucent health colours");if(Math.abs((halo>>shift&255)-(pixel(450,298)>>shift&255))>3)throw new AssertionError("Health grading lost its additive glow");}save(out.resolve("health-grading-transparent-glow.png"));
        }
        ClickGuiModule style=new ClickGuiModule();modules.registerDynamic(style);StepModule step=new StepModule();modules.registerDynamic(step);for(Category c:Category.values())for(int i=0;i<5;i++)modules.registerDynamic(new Module(c.name()+" "+i,"Window theme preview",c,0){});
        style.getTheme().setValue("Wurst");VibeClickGui gui=new VibeClickGui();gui.setWorldAndResolution(mc,1280,720);ClientChangesRenderCheck.frame(1280,720);gui.drawScreen(50,40,1);save(out.resolve("wurst-overview.png"));
        Object target=null;for(Object candidate:(java.util.List<?>)read(gui,"targets"))if(read(candidate,"module")==step){target=candidate;break;}if(target==null)throw new AssertionError("Wurst module row missing");int tx=((Integer)read(target,"left")+(Integer)read(target,"right"))/2,ty=((Integer)read(target,"top")+(Integer)read(target,"bottom"))/2;GuiInput.mouseClicked(gui,tx,ty,1);GuiInput.mouseReleased(gui,tx,ty,1);ClientChangesRenderCheck.frame(1280,720);gui.drawScreen(tx,ty,1);save(out.resolve("wurst-setting-window.png"));boolean found=false;for(Object candidate:(java.util.List<?>)read(gui,"targets"))if(read(candidate,"setting")==step.mode)found=true;if(!found)throw new AssertionError("Wurst settings window did not expose native module settings");gui.onGuiClosed();
        FreecamModule freecam=new FreecamModule();modules.registerDynamic(freecam);CameraRigGui camera=new CameraRigGui(freecam,null);camera.setWorldAndResolution(mc,1280,720);ClientChangesRenderCheck.frame(1280,720);camera.drawScreen(0,0,1);save(out.resolve("camera-rig-editor.png"));camera.onGuiClosed();
        companions(mc,modules,out);
        System.out.println("Dashboard native rendering in four themes, isolated health grading and Original preview, and Wurst windows passed.");
    }
    private static Object read(Object o,String name)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void companions(Minecraft mc,ModuleManager modules,Path out)throws Exception{
        net.minecraft.client.entity.EntityPlayerSP old=mc.thePlayer;net.minecraft.client.renderer.entity.RenderManager oldManager=mc.getRenderManager();
        net.minecraft.client.entity.EntityPlayerSP player=PathTestFixture.allocate(net.minecraft.client.entity.EntityPlayerSP.class);player.ticksExisted=50;mc.thePlayer=player;PathTestFixture.set(Minecraft.class,mc,"renderManager",PathTestFixture.allocate(net.minecraft.client.renderer.entity.RenderManager.class));
        try{
            PocketPetsModule pet=new PocketPetsModule();modules.registerDynamic(pet);pet.size.setValue(2D);dev.vibe.ui.render.PocketPetRenderer renderer=new dev.vibe.ui.render.PocketPetRenderer();worldFrame();for(int i=0;i<2;i++){pet.pet.setValue(i==0?"Ghost":"Rubber Duck");pet.follower.x=pet.follower.previousX=-.9+i*1.8;renderer.render(pet,1);}save(out.resolve("pocket-pets-models.png"));
            worldFrame();String[] names={"Stop","Sit","Dab"};for(int i=0;i<3;i++){ModelPlayer model=new ModelPlayer(0,false);model.setRotationAngles(0,0,0,0,0,.0625F,player);EmotePoses.preview=names[i];EmotePoses.apply(model);GL11.glPushMatrix();GL11.glTranslatef(-1.5F+i*1.5F,1.5F,0);GL11.glScalef(-1,-1,1);GL11.glColor4f(.68F,.82F,.94F,1);for(ModelRenderer bone:new ModelRenderer[]{model.bipedHead,model.bipedBody,model.bipedLeftArm,model.bipedRightArm,model.bipedLeftLeg,model.bipedRightLeg})bone.render(.0625F);GL11.glPopMatrix();EmotePoses.before(model);}EmotePoses.preview=null;save(out.resolve("emotes-standing-sit-dab.png"));if(GL11.glGetError()!=0)throw new AssertionError("Pet or emote GL error");
        }finally{EmotePoses.preview=null;mc.thePlayer=old;PathTestFixture.set(Minecraft.class,mc,"renderManager",oldManager);}
    }
    private static void worldFrame()throws Exception{ClientChangesRenderCheck.frame(1280,720);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();org.lwjgl.util.glu.GLU.gluPerspective(38,1280F/720,.05F,50);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();org.lwjgl.util.glu.GLU.gluLookAt(3.2F,2.3F,6,0,.8F,0,0,1,0);}
    private static int pixel(int x,int y){ByteBuffer p=BufferUtils.createByteBuffer(4);GL11.glReadPixels(x,719-y,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);return (p.get(0)&255)<<16|(p.get(1)&255)<<8|p.get(2)&255;}
    private static void save(Path path)throws Exception{ByteBuffer p=BufferUtils.createByteBuffer(1280*720*4);GL11.glReadPixels(0,0,1280,720,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_RGB);for(int y=0;y<720;y++)for(int x=0;x<1280;x++){int i=((719-y)*1280+x)*4;image.setRGB(x,y,(p.get(i)&255)<<16|(p.get(i+1)&255)<<8|p.get(i+2)&255);}ImageIO.write(image,"png",path.toFile());}
}
