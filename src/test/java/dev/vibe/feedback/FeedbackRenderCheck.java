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
                widget.setTheme(theme); seedStack(vibe); frame(target); renderer.drawNotifications();
                save(theme.toLowerCase(java.util.Locale.ROOT) + ".png"); check();
                frame(target); renderer.drawPreview(widget, null);
                save("editor-" + theme.toLowerCase(java.util.Locale.ROOT) + ".png"); check();
            }
            for (String theme : new String[]{"Vibe", "Skeet", "LiquidGlass"}) {
                widget.setTheme(theme); vibe.getFeedback().notifications().clear();
                vibe.getFeedback().notifications().addMessage("phone.message", "ESound", "Blame Lucky. I'm buying groceries.", System.nanoTime() / 1000000L - 700);
                frame(target); renderer.drawNotifications(); save("phone-message-" + theme.toLowerCase(java.util.Locale.ROOT) + ".png"); check();
            }
            Object glass = field(HudManager.class, "liquidGlass").get(renderer);
            if (field(glass.getClass(), "failed").getBoolean(glass) || field(glass.getClass(), "program").get(glass) == null)
                throw new AssertionError("Live LiquidGlass notifications did not use the real glass shader");
            // A per-element choice wins over the legacy fallback, including after serialization.
            widget.setTheme("Skeet"); hud.getMode().setValue("Vibe");
            frame(target); renderer.drawPreview(widget, null); int selectedSkeet = surfacePixel(widget);
            widget.setTheme(null); hud.getMode().setValue("Skeet");
            frame(target); renderer.drawPreview(widget, null);
            if (surfacePixel(widget) != selectedSkeet) throw new AssertionError("Legacy theme fallback disagrees with the selected theme");
            hud.getMode().setValue("Vibe"); frame(target); renderer.drawPreview(widget, null);
            if (surfacePixel(widget) == selectedSkeet) throw new AssertionError("Theme selection did not update the surface");
            widget.setTheme("Vibe"); widget.setScale(1.35F);
            frame(target); renderer.drawPreview(widget, null); save("scaled.png"); check();
            if (widget.getWidth() != Math.round(NotificationRenderer.WIDTH * 1.35F)) throw new AssertionError("HUD editor scaling lost notification bounds");
            widget.setScale(1); mc.gameSettings.guiScale = 3;
            seedStack(vibe); frame(target); renderer.drawNotifications(); save("small-gui.png"); check();
            assertStackVisible(widget, 4);
            mc.gameSettings.guiScale = 2;
            NotificationSettings options = hud.notifications;
            options.width.setValue(240D); options.height.setValue(46D); options.gap.setValue(12D);
            options.limit.setValue(2D); options.font.setValue("Regular");
            options.icons.setEnabled(false); options.status.setEnabled(false); options.progress.setEnabled(false);
            options.shadow.setEnabled(false); options.border.setEnabled(false); options.animation.setEnabled(false);
            options.radius.setValue(14D); options.opacity.setValue(.65D);
            options.background.setValue(0xFF173249); options.text.setValue(0xFFC3E7FF);
            frame(target); renderer.drawPreview(widget, null); save("custom-minimal.png"); check();
            if (widget.getWidth() != 240 || widget.getHeight() != 104) throw new AssertionError("Custom notification dimensions/gap/limit were ignored");
            frame(target);
            vibe.getFeedback().notifications().clear();
            vibe.getFeedback().notifications().add("instant", "Instant toggle", true, System.nanoTime() / 1000000L);
            renderer.drawNotifications(); check();
            if (surfacePixel(widget) > 65) throw new AssertionError("Disabling animation did not show a fresh notification immediately");
            options.icons.setEnabled(true); options.status.setEnabled(true); options.progress.setEnabled(true);
            options.enabledColor.setValue(0xFF93B4FF); options.disabledColor.setValue(0xFFFFC38C);
            for (String theme : new String[]{"Vibe", "Skeet", "LiquidGlass"}) {
                widget.setTheme(theme); seedStack(vibe, 2); frame(target); renderer.drawNotifications();
                save("custom-" + theme.toLowerCase(java.util.Locale.ROOT) + ".png"); check();
            }
            widget.setTheme("Vibe");
            // An eight-card request at a large HUD scale must display only the newest cards that fit.
            options.limit.setValue(8D); options.height.setValue(60D); options.gap.setValue(16D); options.width.setValue(320D);
            widget.setScale(1.5F); mc.gameSettings.guiScale = 3;
            seedStack(vibe, 8); frame(target); renderer.drawNotifications(); save("large-stack-fit.png"); check();
            ScaledResolution small = new ScaledResolution(mc);
            if (widget.getLeft() < 0 || widget.getTop() < 0 || widget.getLeft() + widget.getWidth() > small.getScaledWidth()
                    || widget.getTop() + widget.getHeight() > small.getScaledHeight()) throw new AssertionError("Large stack or width overflowed the screen");
            if (widget.getHeight() != 204) throw new AssertionError("The stack did not adapt its visible count to the screen");
            for (dev.vibe.setting.Setting<?> setting : options.all) setting.resetToDefault();
            mc.gameSettings.guiScale = 2; widget.setScale(1.35F);
            frame(target); renderer.drawPreview(widget, null);
            widget.moveTo(20, 25, new ScaledResolution(mc));
            HudManager reloaded = new HudManager(new File(fixture, "other")); reloaded.fromJson(renderer.toJson());
            if (reloaded.getElement(HudManager.NOTIFICATIONS).getScale() != 1.35F) throw new AssertionError("Notification layout did not round trip");
            widget.setScale(1); options.limit.setValue(1D);
            frame(target); renderer.drawPreview(widget, null); frame(target);
            dev.vibe.ui.NeverLoseFont headings = new dev.vibe.ui.NeverLoseFont(java.awt.Font.BOLD, 20);
            String[] themes = {"Vibe", "Skeet", "LiquidGlass"};
            for (int i = 0; i < themes.length; i++) {
                widget.setTheme(themes[i]); widget.moveTo(16 + i * 210, 50, new ScaledResolution(mc));
                headings.draw(themes[i], 28 + i * 210, 29, 0xFFFFFFFF);
                seedStack(vibe); renderer.drawNotifications();
                reloaded.fromJson(renderer.toJson());
                if (!themes[i].equals(reloaded.getElement(HudManager.NOTIFICATIONS).getTheme())) throw new AssertionError("Notification theme was not saved");
            }
            saveRegion("themes-preview.png", 16, 40, 1248, 160); check();
            hud.getHudElements().toggle(HudManager.NOTIFICATIONS);
            frame(target); ByteBuffer before = pixels(); renderer.drawNotifications(); ByteBuffer after = pixels();
            for (int i = 0; i < before.capacity(); i++) if (before.get(i) != after.get(i)) throw new AssertionError("Disabled notifications still render");
            System.out.println("Toggle feedback: theme selection/fallback/persistence, real LiquidGlass shader, custom appearance, eight-card screen fitting, GUI scales 2/3, editor scale/layout, GL state and hidden widget passed.");
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
        seedStack(vibe, 4);
    }
    private static void seedStack(Vibe vibe, int count) {
        long now = System.nanoTime() / 1000000L;
        HudModule hud = vibe.getModuleManager().getModule(HudModule.class);
        vibe.getFeedback().notifications().clear();
        vibe.getFeedback().notifications().configure(hud.notifications.limit.getInt(), hud.notifications.durationMillis(), now);
        String[] names = {"Scaffold", "Scaffold", "NoHurtCam", "ToggleSound"};
        for (int i = 0; i < count; i++) vibe.getFeedback().notifications().add(names[i % 4], names[i % 4], i % 4 != 1, now - 1000 + i * 40);
    }
    private static int surfacePixel(HudManager.HudElement widget) {
        int scale = new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
        int at = ((HEIGHT - 1 - (widget.getTop() + NotificationRenderer.HEIGHT / 2) * scale) * WIDTH + (widget.getLeft() + 4) * scale) * 4;
        return pixels().get(at) & 255;
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
        saveRegion("stack-preview.png", widget.getLeft() * scale - 8, widget.getTop() * scale - 8, width, height);
    }
    private static void saveRegion(String name, int left, int top, int width, int height) throws Exception {
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(left, HEIGHT - top - height, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int at = ((height - 1 - y) * width + x) * 4;
            image.setRGB(x, y, 0xFF000000 | (pixels.get(at) & 255) << 16 | (pixels.get(at + 1) & 255) << 8 | pixels.get(at + 2) & 255);
        }
        ImageIO.write(image, "png", OUTPUT.resolve(name).toFile());
    }
    private static Field field(Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    private static <T> T create(Class<T> type) throws Exception { return type.cast(allocate.invoke(unsafe, type)); }
}
