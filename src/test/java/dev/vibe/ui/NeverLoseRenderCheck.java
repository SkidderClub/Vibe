package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.config.VibeConfig;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.AimAssistModule;
import dev.vibe.module.impl.ClickGuiModule;
import dev.vibe.module.impl.TargetsModule;
import dev.vibe.module.impl.WTapModule;
import dev.vibe.module.impl.ReachModule;
import dev.vibe.setting.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.*;

/** Exercises the actual renderer/input against temporary profiles, without starting Minecraft. */
public final class NeverLoseRenderCheck {
    private static final Path OUTPUT = Paths.get("build/neverlose-render-check");
    private static Minecraft mc;

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        Pbuffer gl = new Pbuffer(1280, 960, new PixelFormat(8, 24, 8), null, null);
        gl.makeCurrent();
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeType.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null); Method allocate = unsafeType.getMethod("allocateInstance", Class.class);
            mc = (Minecraft) allocate.invoke(unsafe, Minecraft.class); set(Minecraft.class, null, "theMinecraft", mc);
            mc.gameSettings = new GameSettings(); mc.gameSettings.guiScale = 1;
            Path profile = Files.createTempDirectory(OUTPUT, "profile-"); set(Minecraft.class, mc, "mcDataDir", profile.toFile());
            IMetadataSerializer meta = new IMetadataSerializer();
            set(Minecraft.class, mc, "mcLanguageManager", new net.minecraft.client.resources.LanguageManager(meta, "en_US"));
            mc.renderEngine = new TextureManager(new SimpleReloadableResourceManager(meta));
            OpenGlHelper.initializeTextures();
            Vibe vibe = new Vibe(); set(Vibe.class, null, "instance", vibe);
            ModuleManager manager = (ModuleManager) allocate.invoke(unsafe, ModuleManager.class);
            Fixture main = new Fixture("Aim Assist", Category.COMBAT, true);
            Fixture trigger = new Fixture("Triggerbot", Category.COMBAT, false);
            Fixture other = new Fixture("Other", Category.COMBAT, false);
            Fixture visual = new Fixture("Visual test", Category.VISUAL, true);
            ClickGuiModule clickGui = new ClickGuiModule(); clickGui.getTheme().setValue("NeverLose");
            List<Module> modules = new ArrayList<Module>(Arrays.<Module>asList(main, trigger, other, visual, clickGui));
            for (int i = 0; i < 10; i++) modules.add(new Fixture("Extra " + i, Category.COMBAT, false));
            set(ModuleManager.class, manager, "modules", modules); set(Vibe.class, vibe, "moduleManager", manager);
            VibeConfig config = new VibeConfig(profile.toFile()); set(Vibe.class, vibe, "config", config);
            main.setEnabled(true); config.save("preview", manager);

            NeverLoseWorkspace workspace = new NeverLoseWorkspace();
            render(workspace, 800, 540, 1, "neverlose-desktop.png");
            assertContentHitsClipped(workspace);
            // Settings cards intentionally start collapsed. Open the fixture
            // through the same fold affordance a player uses before checking
            // the controls inside it.
            click(workspace, hit(workspace, "FOLD", main), 0);
            render(workspace, 800, 540, 1, null);
            Object toggle = hit(workspace, "BOOLEAN", main.enabled);
            click(workspace, toggle, 0); check(!main.enabled.isEnabled(), "Boolean control did not toggle");
            render(workspace, 800, 540, 1, null);
            Object mode = hit(workspace, "DROPDOWN", main.mode);
            click(workspace, mode, 0); render(workspace, 800, 540, 1, "neverlose-dropdown.png");
            Object popup = get(workspace, "popup");
            workspace.click(num(popup, "x") + 14, num(popup, "y") + 4 + 22 + 8, 0);
            check(main.mode.is("Adaptive"), "Dropdown did not select the clicked mode");
            render(workspace, 800, 540, 1, null);
            Object multi = hit(workspace, "DROPDOWN", main.multi); click(workspace, multi, 0);
            popup = get(workspace, "popup"); workspace.click(num(popup, "x") + 12, num(popup, "y") + 4 + 22 + 8, 0);
            check(main.multi.isSelected("Chest"), "Multi-selection did not update");
            check(get(workspace, "popup") != null, "Multi-selection closed after one choice");
            workspace.key('\0', Keyboard.KEY_ESCAPE);
            render(workspace, 800, 540, 1, null);
            Object number = hit(workspace, "SLIDER", main.number);
            click(workspace, number, 0); frame(800, 540, 1); workspace.draw(800, 540, num(number, "x") + num(number, "w") + 100, num(number, "y")); workspace.release(0);
            check(main.number.getDouble() == main.number.getMaximum(), "Number slider failed to clamp");
            render(workspace, 800, 540, 1, null);
            Object range = hit(workspace, "SLIDER", main.range);
            workspace.click(num(range, "x") + 2, num(range, "y") + 5, 0);
            frame(800, 540, 1); workspace.draw(800, 540, num(range, "x") + num(range, "w") + 100, num(range, "y")); workspace.release(0);
            check(main.range.getMin() == main.range.getMax(), "Range handle crossed the other handle");
            render(workspace, 800, 540, 1, null);
            click(workspace, hit(workspace, "COLOR", main.color), 0);
            render(workspace, 800, 540, 1, "neverlose-color.png");
            int cx = num(workspace, "colorX"), cy = num(workspace, "colorY");
            workspace.click(cx + 40, cy + 60, 0); workspace.release(0);
            check(main.color.getArgb() != 0xFF3984FF, "Color picker did not update");
            workspace.key('\0', Keyboard.KEY_ESCAPE);
            render(workspace, 800, 540, 1, null);
            click(workspace, hit(workspace, "TEXT", main.string), 0);
            workspace.key('!', Keyboard.KEY_1); workspace.key('\0', Keyboard.KEY_RETURN);
            check(main.string.getValue().endsWith("!"), "Text entry did not persist");
            click(workspace, hit(workspace, "BIND", main), 0); workspace.key('k', Keyboard.KEY_K);
            check(main.getKey() == Keyboard.KEY_K, "Key capture failed");
            click(workspace, hit(workspace, "SEARCH", null), 0);
            for (char c : "Visual test".toCharArray()) workspace.key(c, 0);
            render(workspace, 800, 540, 1, "neverlose-search.png");
            check(hit(workspace, "MODULE", visual) != null, "Search did not find another category");
            check(num(workspace, "scroll") == 0, "Search kept stale scroll");
            workspace.key('\0', Keyboard.KEY_RETURN);
            click(workspace, hit(workspace, "CATEGORY", Category.COMBAT), 0);
            render(workspace, 800, 540, 1, null);
            workspace.wheel(num(workspace, "contentLeft") + 15, num(workspace, "contentTop") + 30, -120);
            render(workspace, 800, 540, 1, "neverlose-scroll.png");
            check(num(workspace, "scroll") > 0, "Content did not scroll"); assertContentHitsClipped(workspace);
            int oldX = num(workspace, "x"), oldY = num(workspace, "y");
            workspace.click(oldX + 45, oldY + 5, 0); frame(800, 540, 1); workspace.draw(800, 540, 799, 539); workspace.release(0);
            check(num(workspace, "x") + num(workspace, "w") <= 800 && num(workspace, "y") + num(workspace, "h") <= 540, "Window drag escaped screen");
            workspace.close();

            for (int[] size : new int[][] {{320, 240, 1}, {640, 360, 1}, {640, 480, 2}}) {
                workspace = new NeverLoseWorkspace();
                render(workspace, size[0], size[1], size[2], "neverlose-" + size[0] + "x" + size[1] + "-scale" + size[2] + ".png");
                assertContentHitsClipped(workspace);
                for (Category category : Category.values()) {
                    Object tab = hit(workspace, "CATEGORY", category);
                    check(num(tab, "y") + num(tab, "h") <= num(workspace, "y") + num(workspace, "h") - 43, "Sidebar tabs overlap profile at small resolution");
                }
                click(workspace, hit(workspace, "CATEGORY", Category.CLIENT), 0);
                render(workspace, size[0], size[1], size[2], null);
                check(get(workspace, "category") == Category.CLIENT, "Small window category cannot be selected");
                if (find(workspace, "DROPDOWN", clickGui.getTheme()) == null) {
                    click(workspace, hit(workspace, "FOLD", clickGui), 0);
                    render(workspace, size[0], size[1], size[2], null);
                }
                click(workspace, hit(workspace, "DROPDOWN", clickGui.getTheme()), 0);
                popup = get(workspace, "popup");
                check(num(popup, "x") >= 0 && num(popup, "x") + num(popup, "w") <= size[0], "Popup outside small window");
                workspace.key('\0', Keyboard.KEY_ESCAPE);
            }
            // An open menu must not survive with obsolete coordinates after resizing.
            render(workspace, 800, 540, 1, null);
            if (find(workspace, "DROPDOWN", clickGui.getTheme()) == null) {
                click(workspace, hit(workspace, "FOLD", clickGui), 0);
                render(workspace, 800, 540, 1, null);
            }
            click(workspace, hit(workspace, "DROPDOWN", clickGui.getTheme()), 0);
            render(workspace, 320, 240, 1, null);
            check(get(workspace, "popup") == null, "Resize kept popup at obsolete coordinates");
            // Real Vibe modules exercise conditional rows and realistic label lengths.
            set(ModuleManager.class, manager, "modules", new ArrayList<Module>(Arrays.<Module>asList(new TargetsModule(), new AimAssistModule(), new WTapModule(), new ReachModule(), clickGui)));
            workspace = new NeverLoseWorkspace(); set(NeverLoseWorkspace.class, workspace, "x", -1); set(NeverLoseWorkspace.class, workspace, "scroll", 0);
            render(workspace, 800, 540, 1, "neverlose-real-modules.png");
            frame(800, 540, 1);
            VibeClickGui host = new VibeClickGui(); host.setWorldAndResolution(mc, 800, 540); host.drawScreen(0, 0, 0);
            NeverLoseWorkspace embedded = (NeverLoseWorkspace) get(host, "neverLose");
            Object tab = hit(embedded, "CATEGORY", Category.CLIENT);
            host.mouseClicked(num(tab, "x") + 5, num(tab, "y") + 5, 0); host.drawScreen(0, 0, 0);
            Object theme = hit(embedded, "DROPDOWN", clickGui.getTheme());
            host.mouseClicked(num(theme, "x") + 5, num(theme, "y") + 5, 0);
            popup = get(embedded, "popup");
            int skeetIndex = clickGui.getTheme().getModes().indexOf("Skeet");
            host.mouseClicked(num(popup, "x") + 10, num(popup, "y") + 4 + skeetIndex * 22 + 5, 0);
            check(clickGui.getTheme().is("Skeet"), "Host did not route theme dropdown input");
            clickGui.getTheme().setValue("NeverLose");
            checkWindowResizing(clickGui);
            GuiRenderState.prepare(false); NeverLoseFont.REGULAR.draw("Deutsch / Русский / 日本語 / 中文", 12, 12, 0xFFFFFFFF);
            check(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error in NeverLose rendering");
            check(Files.isRegularFile(profile.resolve("vibe/configs/preview.json")), "Settings were not saved to isolated profile");
            System.out.println("NeverLose render, clipping, search, settings, binding and scale checks passed: " + GL11.glGetString(GL11.GL_RENDERER));
        } finally { gl.destroy(); }
    }

    private static void checkWindowResizing(ClickGuiModule clickGui) throws Exception {
        NeverLoseWorkspace gui = new NeverLoseWorkspace();
        set(NeverLoseWorkspace.class, gui, "x", 20); set(NeverLoseWorkspace.class, gui, "y", 20);
        render(gui, 900, 650, 1, null);
        int initialWidth = num(gui, "w"), initialHeight = num(gui, "h");
        gui.click(num(gui, "x") + initialWidth - 5, num(gui, "y") + initialHeight - 5, 1);
        frame(900, 650, 1); gui.draw(900, 650, 400, 300);
        check(num(gui, "w") == initialWidth && num(gui, "h") == initialHeight, "Right click resized NeverLose");
        click(gui, hit(gui, "CATEGORY", Category.CLIENT), 0); render(gui, 900, 650, 1, null);
        click(gui, hit(gui, "DROPDOWN", clickGui.getTheme()), 0);
        resize(gui, 480, 360);
        check(get(gui, "popup") == null, "Window resizing kept stale dropdown coordinates");
        render(gui, 900, 650, 1, "neverlose-resized-small.png"); assertContentHitsClipped(gui);
        check(num(gui, "w") == 480 && num(gui, "h") == 360, "NeverLose did not shrink to dragged size");
        // Input targets must follow the new layout immediately.
        click(gui, hit(gui, "DROPDOWN", clickGui.getTheme()), 0);
        check(get(gui, "popup") != null, "Settings were not clickable after resizing"); gui.key('\0', Keyboard.KEY_ESCAPE);
        resize(gui, 1, 1);
        check(num(gui, "w") == 320 && num(gui, "h") == 240, "NeverLose minimum size failed");
        render(gui, 900, 650, 1, null); assertContentHitsClipped(gui);
        resize(gui, 5000, 5000);
        check(num(gui, "x") + num(gui, "w") <= 900 && num(gui, "y") + num(gui, "h") <= 650, "NeverLose resize escaped viewport");
        resize(gui, 740, 520);
        click(gui, hit(gui, "CATEGORY", Category.COMBAT), 0);
        render(gui, 900, 650, 1, "neverlose-resized-large.png"); assertContentHitsClipped(gui);
        gui.close(); gui = new NeverLoseWorkspace(); render(gui, 900, 650, 1, null);
        check(num(gui, "w") == 740 && num(gui, "h") == 520, "NeverLose forgot its window size");
        render(gui, 320, 240, 1, null); assertContentHitsClipped(gui);
        check(num(gui, "x") + num(gui, "w") <= 320 && num(gui, "y") + num(gui, "h") <= 240, "Saved NeverLose size escaped smaller screen");
        render(gui, 900, 650, 1, null);
        check(num(gui, "w") == 740 && num(gui, "h") == 520, "A smaller screen discarded the preferred size");
        gui.click(num(gui, "x") + num(gui, "w") - 5, num(gui, "y") + num(gui, "h") - 5, 0);
        render(gui, 640, 480, 2, "neverlose-resized-scale2.png");
        check(!(Boolean) get(gui, "resizing"), "GUI scale change left resize capture active");
    }

    private static void resize(NeverLoseWorkspace gui, int width, int height) throws Exception {
        gui.click(num(gui, "x") + num(gui, "w") - 5, num(gui, "y") + num(gui, "h") - 5, 0);
        frame(900, 650, 1); gui.draw(900, 650, num(gui, "x") + width - 5, num(gui, "y") + height - 5); gui.release(0);
    }

    private static void render(NeverLoseWorkspace workspace, int width, int height, int scale, String filename) throws Exception {
        frame(width, height, scale); workspace.draw(width, height, 0, 0);
        if (filename == null) return;
        int pw = width * scale, ph = height * scale;
        ByteBuffer pixels = BufferUtils.createByteBuffer(pw * ph * 4); GL11.glReadPixels(0, 0, pw, ph, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < ph; y++) for (int x = 0; x < pw; x++) {
            int offset = ((ph - y - 1) * pw + x) * 4;
            image.setRGB(x, y, 0xFF000000 | ((pixels.get(offset) & 255) << 16) | ((pixels.get(offset + 1) & 255) << 8) | (pixels.get(offset + 2) & 255));
        }
        ImageIO.write(image, "png", OUTPUT.resolve(filename).toFile());
    }

    private static void frame(int width, int height, int scale) {
        mc.displayWidth = width * scale; mc.displayHeight = height * scale; mc.gameSettings.guiScale = scale;
        GL11.glViewport(0, 0, width * scale, height * scale); GL11.glDepthMask(true); GL11.glClearColor(.035F, .045F, .06F, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0, width, height, 0, -1000, 1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
    }

    private static void assertContentHitsClipped(NeverLoseWorkspace workspace) throws Exception {
        for (Object hit : (List<?>) get(workspace, "hits")) {
            String kind = get(hit, "kind").toString();
            if (Arrays.asList("MODULE", "FOLD", "BIND", "BOOLEAN", "SLIDER", "DROPDOWN", "TEXT", "COLOR").contains(kind)) {
                check(num(hit, "y") >= num(workspace, "contentTop") && num(hit, "y") + num(hit, "h") <= num(workspace, "contentBottom"), "Hidden control receives clicks");
            }
        }
    }
    private static Object hit(NeverLoseWorkspace workspace, String kind, Object value) throws Exception {
        Object result = find(workspace, kind, value);
        if (result != null) return result;
        throw new AssertionError("Missing control: " + kind + " / " + value);
    }
    private static Object find(NeverLoseWorkspace workspace, String kind, Object value) throws Exception {
        for (Object hit : (List<?>) get(workspace, "hits")) if (get(hit, "kind").toString().equals(kind) && get(hit, "value") == value) return hit;
        return null;
    }
    private static void click(NeverLoseWorkspace workspace, Object hit, int button) throws Exception { workspace.click(num(hit, "x") + num(hit, "w") / 2, num(hit, "y") + num(hit, "h") / 2, button); }
    private static int num(Object object, String name) throws Exception { return ((Number) get(object, name)).intValue(); }
    private static Object get(Object object, String name) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private static void set(Class<?> type, Object instance, String name, Object value) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(instance, value); }
    private static void check(boolean success, String message) { if (!success) throw new AssertionError(message); }

    private static final class Fixture extends Module {
        final BooleanSetting enabled;
        final ModeSetting mode;
        final MultiSelectSetting multi;
        final NumberSetting number;
        final RangeSetting range;
        final ColorSetting color;
        final StringSetting string;
        Fixture(String name, Category category, boolean full) {
            super(name, "Configure targeting, activation and prediction for this module.", category, Keyboard.KEY_NONE);
            enabled = addSetting(new BooleanSetting("Prediction", true));
            mode = addSetting(new ModeSetting("Activation", "Assisted", "Assisted", "Adaptive", "Always"));
            multi = addSetting(new MultiSelectSetting("Hitboxes", Arrays.asList("Head", "Chest", "Stomach"), Collections.singletonList("Head")));
            number = addSetting(new NumberSetting("Field of View", 90, 0, 180, 1));
            range = full ? addSetting(new RangeSetting("Reaction Time", 80, 150, 0, 300, 10)) : null;
            color = full ? addSetting(new ColorSetting("Accent", 0xFF3984FF)) : null;
            string = full ? addSetting(new StringSetting("Profile Note", "Default")) : null;
        }
    }
}
