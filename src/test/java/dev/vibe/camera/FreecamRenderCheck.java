package dev.vibe.camera;

import dev.vibe.Vibe;
import dev.vibe.event.ClientEvents;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.movement.FreecamModule;
import dev.vibe.module.impl.visual.EspModule;
import dev.vibe.module.impl.visual.Esp2DSettings;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.Setting;
import dev.vibe.ui.render.FreecamHudRenderer;
import dev.vibe.ui.render.esp.EspRenderer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.GLU;

/** Real offscreen GL camera transform, culling, world-origin invariance and FPV OSD checks. */
public final class FreecamRenderCheck {
    private static Minecraft mc;
    private static FreecamModule module;
    private static ClientEvents events;
    private static EspModule esp;
    private static EspRenderer espRenderer;
    private static Path output;

    public static void main(String[] args) throws Exception {
        output = Paths.get(args[0]); Files.createDirectories(output);
        Pbuffer buffer = new Pbuffer(960, 540, new PixelFormat(8, 24, 8), null, null);
        buffer.makeCurrent();
        try {
            initialize();
            module.getFlight().reset(10, 67, 12, 0);
            camera(960, 540);
            float[] before = matrix();
            double[] vertexBefore = transform(before, 10, 65, 20);
            Frustum frustum = new Frustum();
            frustum.setPosition(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            if (!frustum.isBoundingBoxInFrustum(new AxisAlignedBB(9.7, 64, 19.7, 10.3, 65.8, 20.3)))
                throw new AssertionError("Real player body was culled from the detached camera");
            mc.thePlayer.posX = mc.thePlayer.prevPosX = mc.thePlayer.lastTickPosX = 25;
            mc.thePlayer.posY = mc.thePlayer.prevPosY = mc.thePlayer.lastTickPosY = 70;
            mc.thePlayer.posZ = mc.thePlayer.prevPosZ = mc.thePlayer.lastTickPosZ = -4;
            camera(960, 540);
            double[] vertexAfter = transform(matrix(), 10, 65, 20);
            for (int i = 0; i < 3; i++) if (Math.abs(vertexBefore[i] - vertexAfter[i]) > 1e-4)
                throw new AssertionError("Knockback moved camera/world projection on axis " + i);
            // Reset the body and capture the actual HUD over a simple world fixture.
            mc.thePlayer.posX = mc.thePlayer.prevPosX = mc.thePlayer.lastTickPosX = 10;
            mc.thePlayer.posY = mc.thePlayer.prevPosY = mc.thePlayer.lastTickPosY = 64;
            mc.thePlayer.posZ = mc.thePlayer.prevPosZ = mc.thePlayer.lastTickPosZ = 20;
            scene(960, 540, "fpv-level.png");
            module.getFlight().step(.1, 0, 1, 0, false, 250, 45, 20, .35);
            scene(960, 540, "fpv-banked.png");
            scene(400, 300, "fpv-compact.png");
            scene(320, 180, "fpv-small.png");
            module.getFlight().look(0, 20, false);
            module.getFlight().velocityX = -3; module.getFlight().velocityY = 1.5; module.getFlight().velocityZ = 18;
            module.getFlight().homeX = 3; module.getFlight().homeY = 65; module.getFlight().homeZ = -18;
            module.getFlight().flightTime = 193; module.getFlight().distanceTravelled = 1240; module.getFlight().maxSpeed = 22;
            scene(960, 540, "fpv-cruise.png");
            esp.setEnabled(true);
            esp.getModes().setValue(new LinkedHashSet<String>(Arrays.asList("2D")));
            Esp2DSettings appearance = esp.get2D();
            for (Esp2DSettings.Element element : appearance.elements) element.enabled.setValue(false);
            appearance.box.enabled.setValue(true); appearance.box.color.solid.setValue(0xFFFF00FF);
            appearance.box.width.setValue(3D); appearance.box.corners.setValue(false);
            appearance.box.rounding.setValue(0D); appearance.distanceScaling.setValue(0D);
            module.getFlight().reset(10, 67, 12, 0);
            module.getFlight().look(0, 20, false);
            scene(960, 540, "fpv-esp.png");
            droneHud(false);
            scene(960, 540, "fpv-esp-without-osd.png");
            // A second camera pose catches stale/player-origin projection assumptions.
            module.getFlight().reset(14, 66, 13, 30);
            module.getFlight().look(0, 15, false);
            module.getFlight().step(.1, 0, 1, 0, false, 180, 45, 20, .35);
            scene(960, 540, "fpv-esp-banked.png");
            esp.setEnabled(false); droneHud(true);
            Sky sky = allocate(Sky.class);
            camera(960, 540);
            float[] worldMatrix = matrix();
            FreecamModule.renderSkyHook(sky, .5F, 0);
            for (int i = 0; i < 16; i++) {
                if (Math.abs(worldMatrix[i] - matrix()[i]) > 1e-5)
                    throw new AssertionError("Sky altered the world camera matrix");
                double expected = i >= 12 && i <= 14 ? 0 : worldMatrix[i];
                if (Math.abs(sky.view[i] - expected) > 1e-4)
                    throw new AssertionError("Sky is not camera-centred on matrix element " + i);
            }
            module.setEnabled(false);
            if (FreecamModule.orientCameraHook(.5F)) throw new AssertionError("Camera still overrides vanilla after disable");
            FreecamModule.renderSkyHook(sky, .5F, 0);
            for (int i = 0; i < 16; i++) if (sky.view[i] != worldMatrix[i])
                throw new AssertionError("Sky did not revert to vanilla after disable");
            System.out.println("FPV camera, body frustum, knockback, sky, Hide HUD with projected ESP (including OSD off), responsive OSD and disable GL checks passed.");
        } finally { buffer.destroy(); }
    }

    private static void initialize() throws Exception {
        mc = allocate(Minecraft.class); set(Minecraft.class, null, "theMinecraft", mc);
        mc.gameSettings = new GameSettings(); mc.gameSettings.guiScale = 1;
        mc.theWorld = allocate(WorldClient.class);
        mc.thePlayer = allocate(FreecamModuleTest.Player.class);
        mc.thePlayer.posX = mc.thePlayer.prevPosX = mc.thePlayer.lastTickPosX = 10;
        mc.thePlayer.posY = mc.thePlayer.prevPosY = mc.thePlayer.lastTickPosY = 64;
        mc.thePlayer.posZ = mc.thePlayer.prevPosZ = mc.thePlayer.lastTickPosZ = 20;
        set(Minecraft.class, mc, "renderViewEntity", mc.thePlayer);
        set(Minecraft.class, mc, "mcDataDir", output.toFile());
        IMetadataSerializer meta = new IMetadataSerializer();
        meta.registerMetadataSectionType(new TextureMetadataSectionSerializer(), TextureMetadataSection.class);
        meta.registerMetadataSectionType(new FontMetadataSectionSerializer(), FontMetadataSection.class);
        set(Minecraft.class, mc, "mcLanguageManager", new net.minecraft.client.resources.LanguageManager(meta, "en_US"));
        SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(meta);
        resources.reloadResourcePack(new DefaultResourcePack(Collections.<String, File>emptyMap()));
        resources.reloadResourcePack(new FolderResourcePack(new File("src/main/resources")));
        set(Minecraft.class, mc, "mcResourceManager", resources);
        mc.renderEngine = new net.minecraft.client.renderer.texture.TextureManager(resources);
        OpenGlHelper.initializeTextures();
        mc.fontRendererObj = new FontRenderer(mc.gameSettings, new ResourceLocation("textures/font/ascii.png"), mc.renderEngine, false);
        mc.fontRendererObj.onResourceManagerReload(resources);
        mc.entityRenderer = allocate(EntityRenderer.class);
        set(EntityRenderer.class, mc.entityRenderer, "mc", mc);
        Vibe vibe = allocate(Vibe.class); set(Vibe.class, null, "instance", vibe);
        ModuleManager manager = allocate(ModuleManager.class);
        module = new FreecamModule();
        esp = new EspModule();
        set(ModuleManager.class, manager, "modules", new ArrayList<Module>(Arrays.<Module>asList(module, esp)));
        set(Vibe.class, vibe, "moduleManager", manager);
        module.setEnabled(true);
        events = allocate(ClientEvents.class);
        set(ClientEvents.class, events, "minecraft", mc);
        set(ClientEvents.class, events, "freecamHudRenderer", new FreecamHudRenderer());
        espRenderer = new EspRenderer();
        set(ClientEvents.class, events, "espRenderer", espRenderer);
    }

    private static void camera(int width, int height) {
        mc.displayWidth = width; mc.displayHeight = height;
        GlStateManager.viewport(0, 0, width, height);
        GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.loadIdentity();
        GLU.gluPerspective(105, (float) width / height, .05F, 256);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.loadIdentity();
        if (!FreecamModule.orientCameraHook(.5F)) throw new AssertionError("Camera did not activate");
    }

    private static void scene(int width, int height, String name) throws Exception {
        camera(width, height);
        GlStateManager.clearColor(.15F, .25F, .33F, 1); GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.enableDepth(); GlStateManager.disableTexture2D(); GlStateManager.disableCull();
        GlStateManager.pushMatrix();
        GlStateManager.translate(-mc.thePlayer.posX, -mc.thePlayer.posY, -mc.thePlayer.posZ);
        GlStateManager.color(.28F, .39F, .31F, 1);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(-100, 63.9, -100); GL11.glVertex3d(-100, 63.9, 100);
        GL11.glVertex3d(100, 63.9, 100); GL11.glVertex3d(100, 63.9, -100);
        GL11.glEnd();
        GlStateManager.color(.55F, .65F, .56F, 1);
        GL11.glBegin(GL11.GL_LINES);
        for (int i = -30; i < 60; i += 2) {
            GL11.glVertex3d(i, 64, -30); GL11.glVertex3d(i, 64, 60);
            GL11.glVertex3d(-30, 64, i); GL11.glVertex3d(60, 64, i);
        }
        GL11.glEnd();
        // A physical player-sized body marker, drawn at the real player's world position.
        dev.vibe.ui.WorldRenderUtils.box(new AxisAlignedBB(9.7, 64, 19.7, 10.3, 65.8, 20.3), 0xFFFFCE80, 0xFFE29F52, 2);
        GlStateManager.popMatrix();
        espRenderer.beginLocalFrame();
        espRenderer.captureLocalActor(new AxisAlignedBB(9.7, 64, 19.7, 10.3, 65.8, 20.3)
                        .offset(-mc.thePlayer.posX, -mc.thePlayer.posY, -mc.thePlayer.posZ),
                "Real body", 20, 20, 8, "", esp);
        if (esp.isEnabled() && !espRenderer.hasOverlay()) throw new AssertionError("Drone projection lost the ESP target");
        RenderGameOverlayEvent base = new RenderGameOverlayEvent(.5F, new net.minecraft.client.gui.ScaledResolution(mc));
        RenderGameOverlayEvent.Pre pre = new FreecamModuleTest.OverlayPre(base);
        events.onPreAll(pre); // Actual exclusive HUD routing from an existing 3D world projection.
        if (!pre.isCanceled()) throw new AssertionError("Hide HUD did not cancel the vanilla hotbar/HUD pass");
        events.onHud(new RenderGameOverlayEvent.Post(base, RenderGameOverlayEvent.ElementType.TEXT));
        if (!GL11.glIsEnabled(GL11.GL_TEXTURE_2D) || GL11.glIsEnabled(GL11.GL_DEPTH_TEST))
            throw new AssertionError("Exclusive OSD did not leave a valid GUI state");
        if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("FPV OSD GL error");
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = (y * width + x) * 4;
            image.setRGB(x, height - 1 - y, 0xFF000000 | (pixels.get(i) & 255) << 16 | (pixels.get(i+1) & 255) << 8 | (pixels.get(i+2) & 255));
        }
        boolean centredReticle = false;
        for (int y = height / 2 - 1; y <= height / 2 + 1; y++) {
            int pixel = image.getRGB(width / 2 - 10, y);
            if ((pixel >> 16 & 255) >= 220 && (pixel >> 8 & 255) >= 240 && (pixel & 255) >= 220) centredReticle = true;
        }
        if (module.shouldDrawDroneHud() && !centredReticle)
            throw new AssertionError("FPV reticle is not at the optical centre: " + width + "x" + height);
        if (esp.isEnabled()) {
            int espPixels = 0;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int color = image.getRGB(x, y);
                if ((color >> 16 & 255) > 220 && (color >> 8 & 255) < 30 && (color & 255) > 220) espPixels++;
            }
            if (espPixels < 80) throw new AssertionError("Hide HUD suppressed projected ESP: " + name);
        }
        ImageIO.write(image, "png", output.resolve(name).toFile());
    }

    private static void droneHud(boolean enabled) {
        for (Setting<?> setting : module.getSettings()) if (setting.getRawName().equals("Drone HUD"))
            ((BooleanSetting) setting).setEnabled(enabled);
    }

    private static float[] matrix() {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
        float[] matrix = new float[16]; buffer.get(matrix); return matrix;
    }
    public static class Sky extends RenderGlobal {
        float[] view;
        private Sky() { super(null); }
        @Override public void renderSky(float partialTicks, int pass) { view = matrix(); }
    }
    private static double[] transform(float[] matrix, double x, double y, double z) {
        x -= mc.thePlayer.posX; y -= mc.thePlayer.posY; z -= mc.thePlayer.posZ;
        return new double[] {matrix[0]*x+matrix[4]*y+matrix[8]*z+matrix[12],
                matrix[1]*x+matrix[5]*y+matrix[9]*z+matrix[13], matrix[2]*x+matrix[6]*y+matrix[10]*z+matrix[14]};
    }
    private static void set(Class<?> type, Object object, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe"); Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }
}
