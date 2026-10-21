package dev.vibe.feedback;

import dev.vibe.Vibe;
import dev.vibe.hud.HudManager;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.module.impl.client.ToggleSoundModule;
import dev.vibe.module.impl.visual.NoHurtCamModule;
import dev.vibe.ui.GuiRenderState;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Real GL checks of the actual HUD widget, without opening a game or playing audio. */
public final class FeedbackRenderCheck {
    private static final Path OUTPUT = Paths.get("build/feedback-render-check");
    private static final int WIDTH = 1280, HEIGHT = 720;
    private static Object unsafe;
    private static Method allocate;
    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        Pbuffer buffer = new Pbuffer(WIDTH, HEIGHT, new PixelFormat(8, 24, 8), null, null); buffer.makeCurrent();
        Framebuffer target = null;
        try {
            Class<?> type = Class.forName("sun.misc.Unsafe"); unsafe = field(type, "theUnsafe").get(null); allocate = type.getMethod("allocateInstance", Class.class);
            Minecraft mc = create(Minecraft.class); field(Minecraft.class, "theMinecraft").set(null, mc);
            mc.displayWidth = WIDTH; mc.displayHeight = HEIGHT; mc.gameSettings = new GameSettings(); mc.gameSettings.guiScale = 2;
            File fixture = OUTPUT.resolve("fixture").toFile(); field(Minecraft.class, "mcDataDir").set(mc, fixture);
            IMetadataSerializer metadata = new IMetadataSerializer();
            metadata.registerMetadataSectionType(new TextureMetadataSectionSerializer(), TextureMetadataSection.class);
            field(Minecraft.class, "mcLanguageManager").set(mc, new net.minecraft.client.resources.LanguageManager(metadata, "en_US"));
            SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(metadata);
            resources.reloadResourcePack(new DefaultResourcePack(Collections.<String, File>emptyMap()));
            resources.reloadResourcePack(new FolderResourcePack(new File("src/main/resources")));
            field(Minecraft.class, "mcResourceManager").set(mc, resources); mc.renderEngine = new TextureManager(resources);
            OpenGlHelper.initializeTextures(); target = new Framebuffer(WIDTH, HEIGHT, true); field(Minecraft.class, "framebufferMc").set(mc, target);
            Vibe vibe = new Vibe(); field(Vibe.class, "instance").set(null, vibe);
            ModuleManager manager = create(ModuleManager.class); field(ModuleManager.class, "modules").set(manager, new ArrayList<Module>());
            field(Vibe.class, "moduleManager").set(vibe, manager);
            HudModule hud = new HudModule(); NoHurtCamModule camera = new NoHurtCamModule(); ToggleSoundModule sound = new ToggleSoundModule();
            manager.registerDynamic(hud); manager.registerDynamic(camera); manager.registerDynamic(sound);
            HudManager renderer = new HudManager(new File(fixture, "config")); field(Vibe.class, "hudManager").set(vibe, renderer);
            vibe.getFeedback().start(); camera.setEnabled(true); camera.setEnabled(false); sound.setEnabled(true);
            List<ModuleNotifications.Entry> entries = vibe.getFeedback().notifications().active(System.nanoTime() / 1000000L);
            if (entries.size() != 3 || !entries.get(0).enabled || entries.get(1).enabled || !entries.get(2).enabled)
                throw new AssertionError("Rapid enable/disable transitions must remain separate cards");
            seedStack(vibe);
            frame(target); renderer.drawNotifications(); save("live.png"); check();
            HudManager.HudElement widget = renderer.getElement(HudManager.NOTIFICATIONS);
            if (widget.getWidth() != NotificationRenderer.WIDTH || widget.getHeight() != 4 * NotificationRenderer.STRIDE - NotificationRenderer.GAP)
                throw new AssertionError("Toast bounds do not include all four cards");
            assertStackVisible(widget, 4);
            savePreview(widget);
            vibe.getFeedback().notifications().clear();
            vibe.getFeedback().notifications().add("scaffold", "Scaffold", false, System.nanoTime() / 1000000L - 700);
            frame(target); renderer.drawNotifications(); save("single.png"); check();
            if (widget.getHeight() != NotificationRenderer.HEIGHT) throw new AssertionError("Single-card bounds are wrong");
            for (String theme : new String[]{"Vibe", "Skeet", "LiquidGlass"}) {
                widget.setTheme(theme); frame(target); renderer.drawPreview(widget, null);
                save(theme.toLowerCase(java.util.Locale.ROOT) + ".png"); check();
            }
            widget.setTheme("Vibe"); widget.setScale(1.35F);
            frame(target); renderer.drawPreview(widget, null); save("scaled.png"); check();
            if (widget.getWidth() != Math.round(NotificationRenderer.WIDTH * 1.35F)) throw new AssertionError("HUD editor scaling lost notification bounds");
            widget.setScale(1); mc.gameSettings.guiScale = 3;
            seedStack(vibe); frame(target); renderer.drawNotifications(); save("small-gui.png"); check();
            assertStackVisible(widget, 4);
            mc.gameSettings.guiScale = 2; widget.setScale(1.35F);
            frame(target); renderer.drawPreview(widget, null);
            widget.moveTo(20, 25, new ScaledResolution(mc));
            HudManager reloaded = new HudManager(new File(fixture, "other")); reloaded.fromJson(renderer.toJson());
            if (reloaded.getElement(HudManager.NOTIFICATIONS).getScale() != 1.35F) throw new AssertionError("Notification layout did not round trip");
            hud.getHudElements().toggle(HudManager.NOTIFICATIONS);
            frame(target); ByteBuffer before = pixels(); renderer.drawNotifications(); ByteBuffer after = pixels();
            for (int i = 0; i < before.capacity(); i++) if (before.get(i) != after.get(i)) throw new AssertionError("Disabled notifications still render");
            System.out.println("Toggle feedback: four simultaneous cards, repeated module toggles, GUI scales 2/3, all HUD themes, editor scale/layout, GL state and hidden widget passed.");
        } finally { if (target != null) target.deleteFramebuffer(); buffer.destroy(); }
    }
    private static void frame(Framebuffer target) {
        target.bindFramebuffer(true); GL11.glDepthMask(true); GL11.glClearColor(.08F, .13F, .19F, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        ScaledResolution resolution = new ScaledResolution(Minecraft.getMinecraft());
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
        GL11.glOrtho(0, resolution.getScaledWidth_double(), resolution.getScaledHeight_double(), 0, -1000, 1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); GuiRenderState.prepare(false);
        // A noisy stone-coloured backdrop reveals weak contrast and antialiasing at game GUI scale.
        Random random = new Random(67);
        for (int y = 0; y < resolution.getScaledHeight(); y += 4) for (int x = 0; x < resolution.getScaledWidth(); x += 4) {
            int shade = 76 + random.nextInt(32) + ((x / 32 + y / 32) % 2) * 12;
            Gui.drawRect(x, y, x + 4, y + 4, 0xFF000000 | shade << 16 | (shade - 3) << 8 | shade - 8);
        }
    }
    private static void seedStack(Vibe vibe) {
        long now = System.nanoTime() / 1000000L;
        vibe.getFeedback().notifications().clear();
        vibe.getFeedback().notifications().add("scaffold", "Scaffold", true, now - 1000);
        vibe.getFeedback().notifications().add("scaffold", "Scaffold", false, now - 900);
        vibe.getFeedback().notifications().add("nohurtcam", "NoHurtCam", true, now - 800);
        vibe.getFeedback().notifications().add("togglesound", "ToggleSound", true, now - 700);
    }
    private static void assertStackVisible(HudManager.HudElement widget, int count) {
        ScaledResolution resolution = new ScaledResolution(Minecraft.getMinecraft());
        if (widget.getTop() < 0 || widget.getTop() + widget.getHeight() > resolution.getScaledHeight())
            throw new AssertionError("Stack is outside the screen");
        ByteBuffer image = pixels();
        int scale = resolution.getScaleFactor();
        for (int i = 0; i < count; i++) {
            int x = (widget.getLeft() + 4) * scale;
            int y = (widget.getTop() + i * NotificationRenderer.STRIDE + NotificationRenderer.HEIGHT / 2) * scale;
            int at = ((HEIGHT - 1 - y) * WIDTH + x) * 4;
            if ((image.get(at) & 255) > 65) throw new AssertionError("Card " + i + " was not rendered at its stack position");
        }
    }
    private static void check() {
        if (GL11.glGetError() != GL11.GL_NO_ERROR || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != 0
                || GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) throw new AssertionError("Notification GL state leaked");
    }
    private static ByteBuffer pixels() {
        ByteBuffer pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4); GL11.glReadPixels(0, 0, WIDTH, HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels); return pixels;
    }
    private static void save(String name) throws Exception {
        ByteBuffer pixels = pixels(); BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int at = ((HEIGHT - 1 - y) * WIDTH + x) * 4;
            image.setRGB(x, y, 0xFF000000 | (pixels.get(at) & 255) << 16 | (pixels.get(at + 1) & 255) << 8 | pixels.get(at + 2) & 255);
        }
        ImageIO.write(image, "png", OUTPUT.resolve(name).toFile());
    }
    private static void savePreview(HudManager.HudElement widget) throws Exception {
        int scale = new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
        int width = widget.getWidth() * scale + 16, height = widget.getHeight() * scale + 20;
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(widget.getLeft() * scale - 8, HEIGHT - widget.getTop() * scale - height + 8,
                width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int at = ((height - 1 - y) * width + x) * 4;
            image.setRGB(x, y, 0xFF000000 | (pixels.get(at) & 255) << 16 | (pixels.get(at + 1) & 255) << 8 | pixels.get(at + 2) & 255);
        }
        ImageIO.write(image, "png", OUTPUT.resolve("stack-preview.png").toFile());
    }
    private static Field field(Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    private static <T> T create(Class<T> type) throws Exception { return type.cast(allocate.invoke(unsafe, type)); }
}
