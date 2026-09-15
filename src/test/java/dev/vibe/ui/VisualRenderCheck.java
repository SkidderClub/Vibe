package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.WaifuModule;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Offscreen pixel comparisons for hostile state left by world, inventory and GUI renders. */
public final class VisualRenderCheck {
    private static Object unsafe;
    private static Method allocate;
    private static void set(Class<?> type, Object instance, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(instance, value);
    }
    private static <T> T instance(Class<T> type) throws Exception { return type.cast(allocate.invoke(unsafe, type)); }

    public static void main(String[] args) throws Exception {
        Pbuffer buffer = new Pbuffer(320, 240, new PixelFormat(8, 24, 8), null, null);
        buffer.makeCurrent();
        try {
            Class<?> type = Class.forName("sun.misc.Unsafe"); Field field = type.getDeclaredField("theUnsafe"); field.setAccessible(true);
            unsafe = field.get(null); allocate = type.getMethod("allocateInstance", Class.class);
            Minecraft mc = instance(Minecraft.class); set(Minecraft.class, null, "theMinecraft", mc);
            mc.displayWidth = 320; mc.displayHeight = 240; mc.gameSettings = new GameSettings();
            set(Minecraft.class, mc, "mcLanguageManager", new net.minecraft.client.resources.LanguageManager(new IMetadataSerializer(), "en_US"));
            File folder = Files.createTempDirectory(new File("build").toPath(), "visual-profile-").toFile();
            set(Minecraft.class, mc, "mcDataDir", folder);
            SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(new IMetadataSerializer());
            mc.renderEngine = new TextureManager(resources);
            OpenGlHelper.initializeTextures();
            File images = new File(folder, "vibe/waifu"); images.mkdirs();
            BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            for (int y=0; y<32; y++) for (int x=0; x<32; x++) image.setRGB(x,y,x<16 ? 0xFFFF4080 : 0xFF40C0FF);
            ImageIO.write(image, "png", new File(images, "check.png"));
            Vibe vibe = new Vibe(); set(Vibe.class, null, "instance", vibe);
            WaifuModule waifu = new WaifuModule(); waifu.getSelected().setValue("check.png"); waifu.getScale().setValue(1D);
            ModuleManager modules = instance(ModuleManager.class);
            set(ModuleManager.class, modules, "modules", new ArrayList<dev.vibe.module.Module>(Arrays.asList(waifu)));
            set(Vibe.class, vibe, "moduleManager", modules); waifu.setEnabled(true);
            GuiInventory gui = instance(GuiInventory.class); gui.width=320; gui.height=240;
            frame(); WaifuRenderer.draw(gui); byte[] cleanImage = pixels();
            hostile(); frame(); WaifuRenderer.draw(gui); same("Waifu colors", cleanImage, pixels());
            if (GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) throw new AssertionError("GUI inherited inventory depth testing");
            if ((cleanImage[((240-210)*320+290)*4] & 255) == 0) throw new AssertionError("Waifu fixture is empty");

            ParticlesRenderer particles = new ParticlesRenderer();
            Method drawParticle = ParticlesRenderer.class.getDeclaredMethod("drawParticle", int.class, int.class, String.class, int.class);
            drawParticle.setAccessible(true);
            frame(); GuiRenderState.prepare(false); drawParticle.invoke(particles, 100,100,"Hearts",0xFFFF4080); byte[] cleanParticle = pixels();
            hostile(); frame(); GuiRenderState.prepare(false); drawParticle.invoke(particles,100,100,"Hearts",0xFFFF4080);
            same("Particle colors", cleanParticle, pixels());

            EntityOtherPlayerMP player = instance(EntityOtherPlayerMP.class);
            DataWatcher watcher = new DataWatcher(player); watcher.addObject(0, (byte) 0); set(Entity.class,player,"dataWatcher",watcher);
            ModelPlayer model = new ModelPlayer(0, false);
            BufferedImage skinImage = new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);
            for (int y=0; y<32; y++) for (int x=0; x<64; x++) skinImage.setRGB(x,y,0xFFFFAACC);
            DynamicTexture skin = new DynamicTexture(new ImageBufferDownload().parseUserSkin(skinImage));
            for (boolean sneak : new boolean[]{false,true}) {
                watcher.updateObject(0, (byte) (sneak ? 2 : 0)); model.isSneak=sneak;
                frame(); drawModel(model,player,skin); byte[] cleanModel = pixels();
                int visible = 0; for (int i=0; i<cleanModel.length; i+=4) if ((cleanModel[i]&255)>30) visible++;
                if (visible < 100) throw new AssertionError("Empty inventory model fixture");
                hostile(); frame(); drawModel(model,player,skin); same("Inventory model, sneak="+sneak,cleanModel,pixels());
            }
            frame(); GuiRenderState.prepare(false);
            String[] languages={"English","Chinese","Russian","Japanese","Bavarian"};
            for(int i=0;i<languages.length;i++) LanguageFlags.draw(languages[i],20+i*55,60);
            byte[] flags=pixels();
            BufferedImage flagImage=new BufferedImage(320,240,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<240;y++) for(int x=0;x<320;x++) {
                int offset=((239-y)*320+x)*4;
                flagImage.setRGB(x,y,0xFF000000|((flags[offset]&255)<<16)|((flags[offset+1]&255)<<8)|(flags[offset+2]&255));
            }
            ImageIO.write(flagImage,"png",new File("build/visual-render-check/flags.png"));
            int error=GL11.glGetError(); if(error!=GL11.GL_NO_ERROR) throw new AssertionError("OpenGL error: "+error);
            System.out.println("Waifu, particles and standing/sneaking inventory model pixel checks passed: "+GL11.glGetString(GL11.GL_RENDERER));
        } finally { buffer.destroy(); }
    }
    private static void drawModel(ModelPlayer model, EntityOtherPlayerMP player, DynamicTexture texture) {
        GuiRenderState.prepare(true);
        GlStateManager.bindTexture(texture.getGlTextureId());
        GlStateManager.pushMatrix();
        GlStateManager.translate(160,210,50); GlStateManager.scale(-90,90,90); GlStateManager.rotate(180,0,0,1);
        RenderHelper.enableStandardItemLighting();
        model.render(player,0,0,0,15,0,.0625F);
        RenderHelper.disableStandardItemLighting(); GlStateManager.popMatrix();
    }
    private static void hostile() {
        // Deliberately bypass the cache, as raw OpenGL overlays do.
        GL11.glEnable(GL11.GL_LIGHTING); GL11.glEnable(GL11.GL_FOG); GL11.glColor4f(.25F,.25F,.25F,.3F);
        GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_GREATER); GL11.glDepthMask(false);
        GL11.glFrontFace(GL11.GL_CW); GL11.glEnable(GL11.GL_CULL_FACE);
        GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit); GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
    private static void frame() {
        GL11.glViewport(0,0,320,240); GL11.glDepthMask(true); GL11.glClearColor(0,0,0,1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0,320,240,0,-1000,1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
    }
    private static byte[] pixels() {
        ByteBuffer buffer=BufferUtils.createByteBuffer(320*240*4); GL11.glReadPixels(0,0,320,240,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);
        byte[] bytes=new byte[buffer.remaining()]; buffer.get(bytes); return bytes;
    }
    private static void same(String label, byte[] expected, byte[] actual) {
        if (!Arrays.equals(expected,actual)) throw new AssertionError(label+" changed after another renderer");
    }
}
