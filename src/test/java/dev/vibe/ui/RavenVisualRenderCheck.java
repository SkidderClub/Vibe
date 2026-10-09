package dev.vibe.ui;

import dev.vibe.module.impl.visual.SaturationModule;
import dev.vibe.ui.effect.SaturationRenderer;
import java.io.File;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.Collections;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Checks the real vanilla saturation shader in an offscreen Java 8 context. */
public final class RavenVisualRenderCheck {
    public static void main(String[] arguments) throws Exception {
        Pbuffer context = new Pbuffer(128,128,new PixelFormat(8,24,8),null,null);
        context.makeCurrent();
        try {
            Minecraft mc = allocate(Minecraft.class);
            set(Minecraft.class,null,"theMinecraft",mc);
            mc.displayWidth = mc.displayHeight = 128;
            mc.gameSettings = new GameSettings();
            mc.gameSettings.fboEnable = true;
            mc.entityRenderer = allocate(EntityRenderer.class);
            mc.theWorld = allocate(WorldClient.class);
            SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(new IMetadataSerializer());
            resources.reloadResourcePack(new DefaultResourcePack(Collections.<String,File>emptyMap()));
            set(Minecraft.class,mc,"mcResourceManager",resources);
            mc.renderEngine = new TextureManager(resources);
            OpenGlHelper.initializeTextures();
            net.minecraft.client.shader.ShaderLinkHelper.setNewStaticShaderLinkHelper();
            if (!OpenGlHelper.shadersSupported) throw new AssertionError("Offscreen driver has no shader support");
            Framebuffer target = new Framebuffer(128,128,true);
            set(Minecraft.class,mc,"framebufferMc",target);
            SaturationModule module = new SaturationModule();
            module.getSaturation().setValue(0D);
            module.setEnabled(true);
            if (!SaturationRenderer.shaderActiveHook(false)) throw new AssertionError("Shader did not load");
            Object nativeGroup = SaturationRenderer.shaderGroupHook(null);
            Object otherGroup = new Object();
            if (nativeGroup == null || SaturationRenderer.shaderGroupHook(otherGroup) != otherGroup)
                throw new AssertionError("Vanilla shader coexistence failed");
            clear(target);
            SaturationRenderer.render(0F);
            target.bindFramebuffer(true);
            int[] grey = pixel();
            if (Math.abs(grey[0]-grey[1])>2 || Math.abs(grey[1]-grey[2])>2 || grey[0]<10)
                throw new AssertionError("Saturation=0 did not render greyscale: "+java.util.Arrays.toString(grey));
            module.getSaturation().setValue(1D);
            module.tick();
            clear(target);
            SaturationRenderer.render(0F);
            target.bindFramebuffer(true);
            int[] color = pixel();
            if (color[0]-color[1]<50 || color[1]-color[2]<10)
                throw new AssertionError("Saturation=1 did not preserve colour: "+java.util.Arrays.toString(color));
            mc.displayWidth = mc.displayHeight = 64;
            target.createBindFramebuffer(64,64);
            SaturationRenderer.resizeHook(64,64);
            clear(target);
            SaturationRenderer.render(.5F);
            target.bindFramebuffer(true);
            int[] resized = pixel();
            if (resized[0]-resized[1]<50) throw new AssertionError("Resized shader output failed");
            module.setEnabled(false);
            if (SaturationRenderer.shaderActiveHook(false) || SaturationRenderer.shaderGroupHook(null)!=null)
                throw new AssertionError("Shader was not removed on disable");
            if (GL11.glGetError()!=GL11.GL_NO_ERROR) throw new AssertionError("OpenGL error");
            target.deleteFramebuffer();
            System.out.println("Raven saturation: shader compilation, greyscale/colour pixels, resize, coexistence and disable passed.");
        } finally { context.destroy(); }
    }
    private static void clear(Framebuffer target) {
        target.bindFramebuffer(true);
        GL11.glClearColor(.8F,.2F,.1F,1F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
    }
    private static int[] pixel() {
        ByteBuffer rgba = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(32,32,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,rgba);
        return new int[]{rgba.get(0)&255,rgba.get(1)&255,rgba.get(2)&255};
    }
    private static void set(Class<?> type,Object object,String name,Object value)throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object,value);
    }
    private static <T> T allocate(Class<T> type)throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),type));
    }
}
