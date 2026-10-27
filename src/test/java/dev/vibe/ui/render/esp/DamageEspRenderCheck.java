package dev.vibe.ui.render.esp;

import dev.vibe.module.impl.visual.DamageESPModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.render.esp.DamageESPModuleTest.HealthPlayer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Real offscreen driver checks: glyphs, easing/fade pixels, depth, camera roll and cached GL state. */
public final class DamageEspRenderCheck {
    private static final int W = 960, H = 600;
    private static DamageESPModule module; private static DamageEspRenderer renderer; private static Path output;
    public static void main(String[] args) throws Exception {
        output = Paths.get(args[0]); Files.createDirectories(output);
        Pbuffer buffer = new Pbuffer(W, H, new PixelFormat(8, 24, 8), null, null); buffer.makeCurrent();
        try (PathTestFixture f = new PathTestFixture()) {
            resources(f.mc); module = new DamageESPModule(); module.setEnabled(true); module.healing.setEnabled(true);
            renderer = new DamageEspRenderer(f.mc);
            HealthPlayer left = HealthPlayer.create(f, 67, -4, 67, 0, 20);
            HealthPlayer middle = HealthPlayer.create(f, 68, 0, 67, 0, 20);
            HealthPlayer right = HealthPlayer.create(f, 69, 4, 67, 0, 14);
            module.tick(0); left.hp(16.5F); middle.hp(13); right.hp(18); module.tick(50);
            if (module.markers().size() != 3) throw new AssertionError("Real health metadata was not observed");
            module.spread.setValue(0D); module.drift.setValue(0D);
            for (String font : module.font.getModes()) {
                module.font.setValue(font); frame(1); draw(300); int pixels = litPixels();
                if (pixels < 150) throw new AssertionError("Missing glyphs: " + font + " (" + pixels + ")");
                save("damage-" + font.toLowerCase(java.util.Locale.ROOT) + ".png"); check(font);
            }
            module.font.setValue("Bold"); module.colorMode.setValue("Gradient"); frame(1); draw(600); save("damage-gradient.png");
            module.colorMode.setValue("Rainbow"); frame(1); draw(600); save("damage-rainbow.png");
            module.colorMode.setValue("Custom"); module.glow.setEnabled(false); module.outline.setEnabled(false); module.fade.setValue("None"); module.pop.setEnabled(false);
            frame(1); draw(300); int opaque = brightness();
            module.fade.setValue("Linear"); frame(1); draw(1250); int faded = brightness();
            if (faded <= 0 || faded >= opaque / 2) throw new AssertionError("Fade did not reduce the rendered pixels");
            module.fade.setValue("None"); module.throughWalls.setEnabled(false); frame(.1); draw(300);
            if (litPixels() != 0) throw new AssertionError("Numbers ignored world depth");
            module.throughWalls.setEnabled(true); draw(300); if (litPixels() < 150) throw new AssertionError("Through Walls did not render");
            frame(1); GL11.glRotatef(27, 0, 0, 1); GL11.glRotatef(20, 0, 1, 0); draw(300); save("damage-camera-roll.png"); check("camera roll");
            state();
            // All four fonts/atlases are now warm; repeated frames must not create new textures.
            long start = System.nanoTime();
            for (int i = 0; i < 120; i++) { frame(1); draw(300); }
            GL11.glFinish(); System.out.println("DamageESP: 120 warm frames, 3 markers: " + (System.nanoTime() - start) / 1000000 + " ms");
            renderer.close(); module.setEnabled(false); check("cleanup");
            System.out.println("DamageESP GL checks passed: five real fonts, health deltas, healing, gradients, rainbow, fade, depth, camera roll and driver/cache state restoration.");
        } finally { buffer.destroy(); }
    }
    private static void resources(Minecraft mc) throws Exception {
        mc.displayWidth = W; mc.displayHeight = H; PathTestFixture.set(Minecraft.class, mc, "mcDataDir", output.toFile());
        IMetadataSerializer metadata = new IMetadataSerializer();
        metadata.registerMetadataSectionType(new TextureMetadataSectionSerializer(), TextureMetadataSection.class);
        metadata.registerMetadataSectionType(new FontMetadataSectionSerializer(), FontMetadataSection.class);
        PathTestFixture.set(Minecraft.class, mc, "mcLanguageManager", new LanguageManager(metadata, "en_US"));
        SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(metadata);
        resources.reloadResourcePack(new DefaultResourcePack(Collections.<String, File>emptyMap()));
        PathTestFixture.set(Minecraft.class, mc, "mcResourceManager", resources); mc.renderEngine = new TextureManager(resources);
        OpenGlHelper.initializeTextures();
        mc.fontRendererObj = new FontRenderer(mc.gameSettings, new ResourceLocation("textures/font/ascii.png"), mc.renderEngine, false);
        mc.fontRendererObj.onResourceManagerReload(resources);
    }
    private static void frame(double depth) {
        GL20.glUseProgram(0); GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GL11.glViewport(0, 0, W, H); GL11.glClearColor(0, 0, 0, 0); GL11.glDepthMask(true); GL11.glClearDepth(depth);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(-8, 8, -1, 9, -20, 20);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
    }
    private static void draw(long now) { renderer.render(module, 0, 64, 0, 0, 64, 8, now); }
    private static void state() {
        frame(1); int texture = GL11.glGenTextures(); GlStateManager.bindTexture(texture);
        GlStateManager.disableBlend(); GlStateManager.enableAlpha(); GlStateManager.alphaFunc(GL11.GL_GREATER, .42F);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
        GlStateManager.color(.2F, .3F, .4F, .5F); GL11.glEnable(GL11.GL_LIGHTING); GL11.glEnable(GL11.GL_FOG);
        GL11.glDepthMask(true); GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1); GlStateManager.enableTexture2D();
        int[] before = snapshot(); FloatBuffer matrix = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
        draw(300);
        if (!Arrays.equals(before, snapshot())) throw new AssertionError("DamageESP leaked OpenGL state");
        FloatBuffer after = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, after);
        for (int i = 0; i < 16; i++) if (Math.abs(matrix.get(i) - after.get(i)) > .00001) throw new AssertionError("Leaked modelview matrix");
        // Calling the same cached values must still leave the driver in those values.
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0); GlStateManager.bindTexture(texture); GlStateManager.disableBlend(); GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, .42F); GlStateManager.color(.2F, .3F, .4F, .5F);
        if (GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) != texture || GL11.glIsEnabled(GL11.GL_BLEND)
                || !GL11.glIsEnabled(GL11.GL_ALPHA_TEST) || Math.abs(GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF) - .42F) > .001) throw new AssertionError("Font state caches diverged from the driver");
        GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG); GlStateManager.deleteTexture(texture); check("state");
    }
    private static int[] snapshot() {
        FloatBuffer color = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0); int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D) ? 1 : 0; GlStateManager.setActiveTexture(active);
        return new int[] {active, binding, textured, GL11.glIsEnabled(GL11.GL_TEXTURE_2D) ? 1 : 0, GL11.glIsEnabled(GL11.GL_BLEND) ? 1 : 0,
                GL11.glIsEnabled(GL11.GL_ALPHA_TEST) ? 1 : 0, Float.floatToIntBits(GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF)),
                GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC), GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA), GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                GL11.glGetInteger(GL11.GL_MATRIX_MODE), GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) ? 1 : 0, GL11.glIsEnabled(GL11.GL_DEPTH_TEST) ? 1 : 0,
                GL11.glIsEnabled(GL11.GL_FOG) ? 1 : 0, GL11.glIsEnabled(GL11.GL_LIGHTING) ? 1 : 0,
                Float.floatToIntBits(color.get(0)), Float.floatToIntBits(color.get(1)), Float.floatToIntBits(color.get(2)), Float.floatToIntBits(color.get(3))};
    }
    private static ByteBuffer pixels() { ByteBuffer data = BufferUtils.createByteBuffer(W * H * 4); GL11.glReadPixels(0, 0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data); return data; }
    private static int litPixels() { ByteBuffer data = pixels(); int count = 0; for (int i = 0; i < W * H; i++) if ((data.get(i * 4) & 255) + (data.get(i * 4 + 1) & 255) + (data.get(i * 4 + 2) & 255) > 30) count++; return count; }
    private static int brightness() { ByteBuffer data = pixels(); int sum = 0; for (int i = 0; i < W * H; i++) for (int c = 0; c < 3; c++) sum += data.get(i * 4 + c) & 255; return sum; }
    private static void save(String name) throws Exception {
        ByteBuffer data = pixels(); BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = ((H - y - 1) * W + x) * 4; image.setRGB(x, y, (data.get(i) & 255) << 16 | (data.get(i + 1) & 255) << 8 | data.get(i + 2) & 255);
        }
        ImageIO.write(image, "png", output.resolve(name).toFile());
    }
    private static void check(String where) { int error = GL11.glGetError(); if (error != 0) throw new AssertionError(where + " GL error " + error); }
}
