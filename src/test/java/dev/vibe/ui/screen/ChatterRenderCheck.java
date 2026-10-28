package dev.vibe.ui.screen;

import dev.vibe.chatter.*;
import dev.vibe.chatter.ChatterData.Password;
import dev.vibe.module.impl.client.ChatterModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.GuiRenderState;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Actual GUI controls and driver rendering, with fake credentials in an isolated build directory. */
public final class ChatterRenderCheck {
    private static final int W=960,H=600;
    private static ChatterGui gui;private static Path output;private static Minecraft mc;
    private static ChatterEngine engine;
    public static void main(String[] args)throws Exception {
        output=Paths.get(args[0]);Files.createDirectories(output);Pbuffer buffer=new Pbuffer(W,H,new PixelFormat(8,24,8),null,null);buffer.makeCurrent();
        try(PathTestFixture fixture=new PathTestFixture()) {
            mc=fixture.mc;resources();Path vault=Files.createTempDirectory(output,"gui-fixture-");engine=new ChatterEngine(new ChatterStore(vault));
            engine.data().passwords.add(new Password("DemoPlayer","play.example.org","fake_demo_pass_42"));
            engine.data().passwords.add(new Password("OtherUser","another.example.org","another_fake_42"));engine.save();
            ChatterModule module=new ChatterModule();PathTestFixture.set(ChatterModule.class,module,"engine",engine);fixture.modules.registerDynamic(module);
            gui=new ChatterGui(module,null);gui.setWorldAndResolution(mc,W,H);render("rules-960.png",W,H);
            fields().get("trigger").setText("/reg*please");click(292,383);assertion(engine.data().rules.get(0).trigger.equals("/reg*please"),"Save rule was not connected");
            fields().get("trigger").setText("");click(260,103);click(274,302);assertion(fields().get("trigger").getText().equals("%name%"),"Placeholder insertion lost the focused trigger");
            click(397,383);render(null,W,H);click(100,211);fields().get("test").setText("<Alex> Player are you using cheats?");render("leave-preview-960.png",W,H);
            click(640,9);render("passwords-960.png",W,H);click(110,148);render("password-selected-960.png",W,H);
            String secret=fields().get("password").getText();assertion(secret.equals("fake_demo_pass_42"),"Account selection failed");
            render(null,W,H);assertion(fields().get("password").getText().equals(secret),"Masking changed the saved password");
            click(620,219);render(null,W,H);click(620,219);render(null,W,H);
            fields().get("password").setText("replacement_pass_24");click(310,355);
            assertion(engine.expand("%pass%","DemoPlayer","play.example.org").equals("replacement_pass_24"),"Password editor did not save");
            click(575,74);render(null,W,H);fields().get("length").setText("24");fields().get("letters").setText("8");fields().get("digits").setText("4");
            click(478,174);render(null,W,H);click(330,370);
            assertion(engine.data().generation.length==24&&engine.data().generation.symbols&&engine.data().generation.lettersLength==8,"Generation controls were not saved");
            render("generation-960.png",W,H);
            gui.setWorldAndResolution(mc,427,240);render("generation-427.png",427,240);
            gui.setWorldAndResolution(mc,320,240);render("generation-320.png",320,240);
            gui.setWorldAndResolution(mc,W,H);render(null,W,H);click(120,74);render(null,W,H);click(300,103);fields().get("trigger").setText("unsaved draft");
            click(640,9);assertion(fields().containsKey("trigger"),"Navigation discarded an unsaved draft");click(397,383);render(null,W,H);click(640,9);render(null,W,H);
            assertion(fields().containsKey("password"),"Settings wheel did not open the password manager");
            ChatterEngine reload=new ChatterEngine(new ChatterStore(vault));assertion(reload.data().generation.length==24,"UI persistence failed");
            gui.onGuiClosed();if(GL11.glGetError()!=0)throw new AssertionError("Chatter rendering left a GL error");
            System.out.println("Chatter GUI checks passed: Skeet rules, literal wildcard preview, focused placeholder insertion, password masking/editing, generation settings, draft protection, wheel navigation, three sizes and private persistence.");
        }finally{buffer.destroy();}
    }
    @SuppressWarnings("unchecked") private static Map<String,GuiTextField> fields()throws Exception{return (Map<String,GuiTextField>)get("fields");}
    private static Object get(String name)throws Exception{Field field=ChatterGui.class.getDeclaredField(name);field.setAccessible(true);return field.get(gui);}
    private static void click(int x,int y)throws Exception{
        float scale=(Float)get("scale");int left=(Integer)get("left"),top=(Integer)get("top");
        Method click=ChatterGui.class.getDeclaredMethod("mouseClicked",int.class,int.class,int.class);click.setAccessible(true);click.invoke(gui,Math.round((left+x)*scale),Math.round((top+y)*scale),0);
    }
    private static void resources()throws Exception {
        mc.displayWidth=W;mc.displayHeight=H;mc.gameSettings.guiScale=1;PathTestFixture.set(Minecraft.class,mc,"mcDataDir",output.toFile());
        IMetadataSerializer metadata=new IMetadataSerializer();metadata.registerMetadataSectionType(new TextureMetadataSectionSerializer(),TextureMetadataSection.class);metadata.registerMetadataSectionType(new FontMetadataSectionSerializer(),FontMetadataSection.class);
        PathTestFixture.set(Minecraft.class,mc,"mcLanguageManager",new LanguageManager(metadata,"en_US"));
        SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(metadata);resources.reloadResourcePack(new DefaultResourcePack(Collections.<String,File>emptyMap()));
        PathTestFixture.set(Minecraft.class,mc,"mcResourceManager",resources);mc.renderEngine=new TextureManager(resources);OpenGlHelper.initializeTextures();
        mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.renderEngine,false);mc.fontRendererObj.onResourceManagerReload(resources);
        mc.setServerData(new ServerData("Demo","play.example.org",false));
    }
    private static void render(String name,int width,int height)throws Exception {
        int pixelWidth=Math.min(W,Math.round(width*H/(float)height)),pixelHeight=Math.round(height*pixelWidth/(float)width);
        GL11.glViewport(0,0,pixelWidth,pixelHeight);GL11.glClearColor(.03F,.03F,.04F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,width,height,0,1000,3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glTranslated(0,0,-2000);GuiRenderState.prepare(false);
        gui.drawScreen(-1,-1,0);
        if(name!=null){ByteBuffer pixels=BufferUtils.createByteBuffer(pixelWidth*pixelHeight*4);GL11.glReadPixels(0,0,pixelWidth,pixelHeight,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);BufferedImage image=new BufferedImage(pixelWidth,pixelHeight,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<pixelHeight;y++)for(int x=0;x<pixelWidth;x++){int i=((pixelHeight-y-1)*pixelWidth+x)*4;image.setRGB(x,y,(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|pixels.get(i+2)&255);}ImageIO.write(image,"png",output.resolve(name).toFile());}
        if(GL11.glGetError()!=0)throw new AssertionError("GL error rendering "+name);
    }
    private static void assertion(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
