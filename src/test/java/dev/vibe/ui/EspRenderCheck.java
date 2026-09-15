package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.*;
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
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Real-driver checks: screen gradient masks, health units, state restoration and editor interactions. */
public final class EspRenderCheck {
    private static final int W=920,H=560;
    private static Minecraft mc;
    private static EspModule esp;
    private static Esp2DRenderer renderer;
    private static Path output;
    private static void set(Class<?> c,Object o,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    public static void main(String[] args)throws Exception {
        output=Paths.get("build/esp-render-check");Files.createDirectories(output);
        Pbuffer buffer=new Pbuffer(W,H,new PixelFormat(8,24,8),null,null);buffer.makeCurrent();
        try {
            Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);Object unsafe=f.get(null);
            mc=(Minecraft)u.getMethod("allocateInstance",Class.class).invoke(unsafe,PreviewMinecraft.class);set(Minecraft.class,null,"theMinecraft",mc);
            mc.displayWidth=W;mc.displayHeight=H;mc.gameSettings=new GameSettings();mc.gameSettings.guiScale=1;
            set(Minecraft.class,mc,"mcDataDir",output.toFile());
            IMetadataSerializer meta=new IMetadataSerializer();set(Minecraft.class,mc,"mcLanguageManager",new LanguageManager(meta,"en_US"));
            meta.registerMetadataSectionType(new TextureMetadataSectionSerializer(),TextureMetadataSection.class);meta.registerMetadataSectionType(new FontMetadataSectionSerializer(),FontMetadataSection.class);
            SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(meta);resources.reloadResourcePack(new DefaultResourcePack(Collections.<String,File>emptyMap()));
            set(Minecraft.class,mc,"mcResourceManager",resources);mc.renderEngine=new TextureManager(resources);
            OpenGlHelper.initializeTextures();
            mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.renderEngine,false);mc.fontRendererObj.onResourceManagerReload(resources);
            Vibe vibe=new Vibe();set(Vibe.class,null,"instance",vibe);esp=new EspModule();EspEditorModule editorModule=new EspEditorModule();
            ModuleManager modules=(ModuleManager)u.getMethod("allocateInstance",Class.class).invoke(unsafe,ModuleManager.class);
            set(ModuleManager.class,modules,"modules",new ArrayList<dev.vibe.module.Module>(Arrays.asList(esp,editorModule)));set(Vibe.class,vibe,"moduleManager",modules);
            esp.getModes().setValue(new LinkedHashSet<String>(Arrays.asList("2D")));renderer=new Esp2DRenderer();
            healthAndState();check("bars");gradientMask();check("gradient");textFonts();check("fonts");editor(editorModule);check("editor");
            if(GL11.glGetError()!=0)throw new AssertionError("OpenGL error after ESP checks");
            System.out.println("ESP native checks passed: bars, global gradient masks, fonts, editor selection/resizing and settings visibility.");
        } finally {buffer.destroy();}
    }
    private static Esp2DRenderer.Actor actor(float health,float max) {Esp2DRenderer.Actor a=new Esp2DRenderer.Actor("Player",health,max,14,12,"Service pistol",null,null,1);a.nativeItem="Pistol";return a;}
    private static void frame() {
        GL11.glViewport(0,0,W,H);GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,W,H,0,1000,3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glTranslated(0,0,-2000);GuiRenderState.prepare(false);
    }
    private static void healthAndState() {
        Esp2DSettings s=esp.get2D();for(Esp2DSettings.Element e:s.elements)e.enabled.setValue(false);
        s.distanceScaling.setValue(0D);s.healthBar.enabled.setValue(true);s.healthBar.width.setValue(6D);s.healthBar.background.setValue(0x00000000);s.healthBar.outline.setValue(false);s.healthBar.color.solid.setValue(0xFF00FF00);
        for(String side:new String[]{"Left","Right","Top","Bottom"}) {
            s.healthBar.position.setValue(side);frame();Esp2DRenderer.Frame f=renderer.draw(s,actor(75,150),new EspLayout.Rect(300,180,100,180),W,H,true);EspLayout.Rect r=f.elements.get("Health Bar");
            int filled=pixel((int)(r.x+r.w*.25),(int)(r.y+r.h*.75));int empty=pixel((int)(r.x+r.w*.75),(int)(r.y+r.h*.25));
            if((filled&0x00FF00)==0||(empty&0x00FF00)!=0)throw new AssertionError("GTA7 health ratio/orientation "+side);
        }
        s.healthBar.position.setValue("Left");s.healthBar.outline.setValue(true);s.healthBar.outlineColor.setValue(0xFFFF0000);s.healthBar.outlineWidth.setValue(2D);
        s.healthBar.background.setValue(0x800000FF);frame();EspLayout.Rect bar=renderer.draw(s,actor(0,150),new EspLayout.Rect(300,180,100,180),W,H,true).elements.get("Health Bar");
        int bg=pixel((int)(bar.x+bar.w/2),(int)(bar.y+bar.h/2));if((bg>>16&255)!=0||Math.abs((bg&255)-128)>2)throw new AssertionError("Outline contaminated translucent bar background");
        s.healthBar.outline.setValue(false);s.healthBar.background.setValue(0);
        frame();GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glDisable(GL11.GL_BLEND);
        renderer.draw(s,actor(75,150),new EspLayout.Rect(300,180,100,180),W,H,true);
        if(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)||GL11.glIsEnabled(GL11.GL_BLEND)||!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK))throw new AssertionError("ESP changed caller GL state");
    }
    private static void gradientMask()throws Exception {
        Esp2DSettings s=esp.get2D();s.healthBar.enabled.setValue(false);s.box.enabled.setValue(true);s.box.corners.setValue(false);s.box.width.setValue(6D);s.box.outline.setValue(false);s.box.color.mode.setValue("Global Gradient");
        s.global.direction.setValue(31D);s.global.speed.setValue(0D);EspGradient expected=new EspGradient(s.global,0);
        frame();renderer.draw(s,actor(75,150),new EspLayout.Rect(100,160,120,180),W,H,true);renderer.draw(s,actor(75,150),new EspLayout.Rect(600,160,120,180),W,H,true);
        for(int x:new int[]{100,220,600,720}){int actual=pixel(x,250),want=expected.sample(x+.5F,250+.5F,W,H);for(int shift:new int[]{0,8,16})if(Math.abs((actual>>shift&255)-(want>>shift&255))>3)throw new AssertionError("Global screen gradient mismatch at "+x+" actual="+Integer.toHexString(actual)+" want="+Integer.toHexString(want));}
        if(pixel(150,250)!=0||pixel(450,250)!=0)throw new AssertionError("Gradient painted outside element masks");
        save("global-gradient.png");
    }
    private static void textFonts()throws Exception {
        Esp2DSettings s=esp.get2D();s.name.enabled.setValue(true);s.distance.enabled.setValue(true);s.itemName.enabled.setValue(true);s.itemIcon.enabled.setValue(true);s.health.enabled.setValue(true);s.armorBar.enabled.setValue(true);s.healthBar.enabled.setValue(true);
        s.healthBar.background.setValue(0x70000000);s.healthBar.position.setValue("Left");s.healthBar.color.mode.setValue("Global Gradient");s.text.color.mode.setValue("Global Gradient");
        s.distanceScaling.setValue(.6);frame();
        int i=0;for(String font:new String[]{"Minecraft","Sans","Sans Bold"}){s.text.font.setValue(font);renderer.draw(s,actor(75,150),new EspLayout.Rect(140+i*270,180+i*30,100-i*25,180-i*45),W,H,true);i++;}
        save("fonts-distance-and-items.png");s.text.font.setValue("Minecraft");
    }
    private static void editor(EspEditorModule module)throws Exception {
        // Register only client preview items. Forge's statistics bootstrap requires a LaunchClassLoader.
        set(net.minecraft.init.Bootstrap.class,null,"alreadyRegistered",true);
        net.minecraft.block.Block.registerBlocks();net.minecraft.item.Item.registerItems();
        installItemRenderer();
        armorLayout();
        esp.get2D().itemIcon.enabled.setValue(true);esp.get2D().armor.enabled.setValue(true);
        GuiScreen parent=new GuiScreen() { };EspEditorGui gui=new EspEditorGui(module,parent);gui.mc=mc;gui.width=W;gui.height=H;set(GuiScreen.class,gui,"fontRendererObj",mc.fontRendererObj);gui.initGui();
        frame();gui.drawScreen(0,0,0);save("editor.png");check("editor initial");
        Field frameField=EspEditorGui.class.getDeclaredField("frame");frameField.setAccessible(true);Esp2DRenderer.Frame measured=(Esp2DRenderer.Frame)frameField.get(gui);
        EspLayout.Rect name=measured.elements.get("Name");gui.mouseClicked((int)(name.x+name.w/2),(int)(name.y+name.h/2),2);
        Field selected=EspEditorGui.class.getDeclaredField("selected");selected.setAccessible(true);if(selected.get(gui)!=esp.get2D().name)throw new AssertionError("Middle click did not select name");
        double before=esp.get2D().name.scale.getDouble();
        gui.mouseClicked((int)name.right()+1,(int)name.bottom()+1,0);gui.mouseClickMove((int)name.right()+28,(int)name.bottom()+12,0,100);gui.mouseReleased(0,0,0);
        if(esp.get2D().name.scale.getDouble()<=before)throw new AssertionError("Resize handle did not scale name");
        gui.mouseClicked(109+10,45+10,0);gui.mouseClicked(200+10,45+10,0);gui.mouseClicked(291+10,45+10,0);
        frame();gui.drawScreen(0,0,0);save("editor-all-modes.png");check("editor all modes");
        Method openColor=EspEditorGui.class.getDeclaredMethod("openColor",dev.vibe.setting.ColorSetting.class);openColor.setAccessible(true);
        openColor.invoke(gui,esp.get2D().global.colors.get(1));frame();gui.drawScreen(0,0,0);save("editor-color-picker.png");check("color picker");
        gui.mouseClicked(624+14+120,144+32+80,0);gui.mouseReleased(624+14+120,144+32+80,0);
        float[] hsv=java.awt.Color.RGBtoHSB(esp.get2D().global.colors.get(1).getRed(),esp.get2D().global.colors.get(1).getGreen(),esp.get2D().global.colors.get(1).getBlue(),null);
        if(Math.abs(hsv[1]-.5)>.02||Math.abs(hsv[2]-.5)>.02)throw new AssertionError("Picker saturation/brightness interaction");
        gui.keyTyped((char)0,org.lwjgl.input.Keyboard.KEY_ESCAPE);
        gui.width=460;gui.height=280;gui.initGui();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,460,280,0,1000,3000);GL11.glMatrixMode(GL11.GL_MODELVIEW);
        gui.drawScreen(0,0,0);save("editor-small.png");
        gui.keyTyped((char)0,org.lwjgl.input.Keyboard.KEY_ESCAPE);
        if(((PreviewMinecraft)mc).destination!=parent)throw new AssertionError("Editor did not return to its parent screen");
    }
    private static void installItemRenderer()throws Exception {
        // A simple baked quad runs the real RenderItem path without Forge's resource-loader bootstrap.
        int[] vertices=new int[28];float[][] points={{0,1,0,0,0},{0,0,0,0,1},{1,0,0,1,1},{1,1,0,1,0}};
        for(int v=0;v<4;v++){int i=v*7;vertices[i]=Float.floatToRawIntBits(points[v][0]);vertices[i+1]=Float.floatToRawIntBits(points[v][1]);vertices[i+2]=Float.floatToRawIntBits(points[v][2]);vertices[i+3]=-1;vertices[i+4]=Float.floatToRawIntBits(points[v][3]);vertices[i+5]=Float.floatToRawIntBits(points[v][4]);vertices[i+6]=0x007F0000;}
        java.util.List<java.util.List<net.minecraft.client.renderer.block.model.BakedQuad>> faces=new ArrayList<>();for(int i=0;i<6;i++)faces.add(Collections.emptyList());
        net.minecraft.client.resources.model.IBakedModel model=new net.minecraft.client.resources.model.SimpleBakedModel(
                Arrays.asList(new net.minecraft.client.renderer.block.model.BakedQuad(vertices,-1,net.minecraft.util.EnumFacing.SOUTH)),faces,false,false,null,net.minecraft.client.renderer.block.model.ItemCameraTransforms.DEFAULT);
        net.minecraft.client.resources.model.ModelManager manager=new net.minecraft.client.resources.model.ModelManager(new TextureMap("textures")) {
            @Override public net.minecraft.client.resources.model.IBakedModel getModel(net.minecraft.client.resources.model.ModelResourceLocation location){return model;}
            @Override public net.minecraft.client.resources.model.IBakedModel getMissingModel(){return model;}
        };
        BufferedImage texture=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=1;y<15;y++)for(int x=2;x<14;x++)texture.setRGB(x,y,0xFF82B4E8);
        mc.renderEngine.loadTexture(TextureMap.locationBlocksTexture,new DynamicTexture(texture));
        set(Minecraft.class,mc,"renderItem",new net.minecraft.client.renderer.entity.RenderItem(mc.renderEngine,manager));
    }
    private static void armorLayout()throws Exception {
        Esp2DSettings s=esp.get2D();s.armor.enabled.setValue(true);
        net.minecraft.item.ItemStack item=new net.minecraft.item.ItemStack(net.minecraft.init.Items.diamond_boots);
        Esp2DRenderer.Actor actor=new Esp2DRenderer.Actor("Player",20,20,16,12,"",item,new net.minecraft.item.ItemStack[]{item,item,item,item},1);
        for(String side:new String[]{"Left","Right","Top","Bottom"}) {
            s.armor.position.setValue(side);frame();Esp2DRenderer.Frame f=renderer.draw(s,actor,new EspLayout.Rect(400,170,100,180),W,H,true);EspLayout.Rect r=f.elements.get("Armor Display");
            boolean vertical=side.equals("Left")||side.equals("Right");if(Math.abs((vertical?r.h/r.w:r.w/r.h)-4)>0.001)throw new AssertionError("Armor orientation "+side);
            for(int i=0;i<4;i++){int x=(int)(r.x+(vertical?r.w/2:r.w*(i+.5)/4)),y=(int)(r.y+(vertical?r.h*(i+.5)/4:r.h/2));if(pixel(x,y)==0)throw new AssertionError("Missing armor icon "+side+" slot "+i);}
        }
        s.armor.position.setValue("Right");check("real item renderer");
    }
    public static final class PreviewMinecraft extends Minecraft {
        GuiScreen destination;
        private PreviewMinecraft(){super(null);}
        @Override public void displayGuiScreen(GuiScreen screen){destination=screen;}
    }
    private static void check(String stage){int error=GL11.glGetError();if(error!=0)throw new AssertionError(stage+" GL error "+error);}
    private static int pixel(int x,int y) {ByteBuffer b=BufferUtils.createByteBuffer(4);GL11.glReadPixels(x,H-1-y,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);return (b.get(0)&255)<<16|(b.get(1)&255)<<8|(b.get(2)&255);}
    private static void save(String name)throws Exception {ByteBuffer b=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(y*W+x)*4;image.setRGB(x,H-1-y,(b.get(i)&255)<<16|(b.get(i+1)&255)<<8|(b.get(i+2)&255));}ImageIO.write(image,"png",output.resolve(name).toFile());}
}
