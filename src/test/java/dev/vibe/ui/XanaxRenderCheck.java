package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.config.VibeConfig;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.*;
import dev.vibe.setting.*;
import java.awt.image.BufferedImage;
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

/** Real GL rendering and input, with all config writes confined to a temporary profile. */
public final class XanaxRenderCheck {
    private static final Path OUTPUT = Paths.get("build/xanax-render-check");
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
            checkScriptsGlyphs();
            Vibe vibe = new Vibe(); set(Vibe.class, null, "instance", vibe);
            ModuleManager manager = (ModuleManager) allocate.invoke(unsafe, ModuleManager.class);
            Fixture main = new Fixture("KillAura"), other = new Fixture("AutoClicker");
            ClickGuiModule theme = new ClickGuiModule(); theme.getTheme().setValue("Xanax");
            List<Module> modules = new ArrayList<Module>(Arrays.<Module>asList(main, other, theme));
            for (String name : new String[] {"BowAimbot", "Criticals", "Velocity", "AntiBot", "Teams", "Reach"}) modules.add(new Fixture(name));
            set(ModuleManager.class, manager, "modules", modules); set(Vibe.class, vibe, "moduleManager", manager);
            VibeConfig config = new VibeConfig(profile.toFile()); set(Vibe.class, vibe, "config", config);
            main.setKey(Keyboard.KEY_R); main.setEnabled(true); config.save("Gomme", manager);
            XanaxWorkspace gui = new XanaxWorkspace();
            render(gui, 786, 638, 1, "xanax-desktop.png");
            clipped(gui);
            // Module selection must not toggle on right click; left/middle have separate actions.
            click(gui, hit(gui, "MODULE", other), 1); render(gui, 786, 638, 1, null);
            check(!other.isEnabled() && get(gui, "selected") == other, "Right click toggled module instead of selecting it");
            click(gui, hit(gui, "MODULE", other), 0); render(gui, 786, 638, 1, null);
            check(other.isEnabled(), "Module toggle failed");
            click(gui, hit(gui, "MODULE", main), 1); render(gui, 786, 638, 1, null);
            click(gui, hit(gui, "MODULE", main), 2); gui.key('k', Keyboard.KEY_K);
            check(main.getKey() == Keyboard.KEY_K, "Key capture failed");
            render(gui, 786, 638, 1, null);
            click(gui, hit(gui, "BOOLEAN", main.smartBlock), 0); render(gui, 786, 638, 1, null);
            check(!main.smartBlock.isEnabled() && !hasHit(gui, "DROPDOWN", main.mode), "Conditional setting remained clickable");
            click(gui, hit(gui, "BOOLEAN", main.smartBlock), 0); render(gui, 786, 638, 1, null);
            click(gui, hit(gui, "DROPDOWN", main.mode), 0); render(gui, 786, 638, 1, "xanax-dropdown.png");
            choose(gui, 1); check(main.mode.is("Packet"), "Mode dropdown failed");
            render(gui, 786, 638, 1, null);
            click(gui, hit(gui, "DROPDOWN", main.targets), 0); choose(gui, 1);
            check(main.targets.isSelected("Mobs") && get(gui, "popup") != null, "Multi-select failed");
            gui.key('\0', Keyboard.KEY_ESCAPE); render(gui, 786, 638, 1, null);
            Object number = hit(gui, "SLIDER", main.range);
            click(gui, number, 0); frame(786, 638, 1); gui.draw(786, 638, num(number, "x") + num(number, "w") + 500, num(number, "y")); gui.release(0);
            check(main.range.getDouble() == main.range.getMaximum(), "Slider did not clamp to its maximum");
            render(gui, 786, 638, 1, null);
            Object pair = hit(gui, "SLIDER", main.cps);
            click(gui, pair, 0); frame(786, 638, 1); gui.draw(786, 638, num(pair, "x") + num(pair, "w") + 500, num(pair, "y")); gui.release(0);
            check(main.cps.getMin() == main.cps.getMax(), "Range bounds crossed");
            render(gui, 786, 638, 1, null);
            click(gui, hit(gui, "COLOR", main.color), 0); render(gui, 786, 638, 1, "xanax-color.png");
            gui.click(num(gui, "colorX") + 60, num(gui, "colorY") + 70, 0); gui.release(0);
            check(main.color.getArgb() != 0xFFB7352B, "Color picker did not change the color");
            gui.key('\0', Keyboard.KEY_ESCAPE); render(gui, 786, 638, 1, null);
            click(gui, hit(gui, "TEXT", main.note), 0); gui.key('!', Keyboard.KEY_1); gui.key('\0', Keyboard.KEY_RETURN);
            check(main.note.getValue().equals("Default!"), "String editing failed");
            // All config buttons use the real JSON persistence layer.
            render(gui, 786, 638, 1, null); action(gui, "Create");
            for (char c : "test-profile".toCharArray()) gui.key(c, 0);
            gui.key('\0', Keyboard.KEY_RETURN);
            check(config.list().contains("test-profile"), "Create did not write profile");
            render(gui, 786, 638, 1, null); action(gui, "Save");
            main.range.setValue(1.0); set(Module.class, main, "key", Keyboard.KEY_P);
            render(gui, 786, 638, 1, null); click(gui, hit(gui, "KEYBINDS", null), 0); action(gui, "Load");
            check(main.range.getDouble() == main.range.getMaximum() && main.getKey() == Keyboard.KEY_P, "Profile load or preserve-keybinds option failed");
            render(gui, 786, 638, 1, null); action(gui, "Delete");
            check(config.list().contains("test-profile"), "Delete skipped confirmation");
            render(gui, 786, 638, 1, null); action(gui, "Delete"); config.save(manager);
            check(!config.list().contains("test-profile"), "Autosave recreated deleted profile");
            render(gui, 786, 638, 1, null); action(gui, "Refresh");
            // Enough content to exercise independently clipped panes and draggable scrollbars.
            for (int i = 0; i < 30; i++) modules.add(new Fixture("Extra " + i));
            render(gui, 786, 638, 1, null);
            Object mp = get(gui, "modulesPane"), sp = get(gui, "settingsPane");
            gui.wheel(num(mp, "x") + 20, num(mp, "y") + 20, -120);
            render(gui, 786, 638, 1, "xanax-scroll.png"); clipped(gui);
            check(num(mp, "scroll") > 0 && num(sp, "scroll") == 0, "Module scroll leaked into settings");
            int moduleScroll = num(mp, "scroll");
            gui.wheel(num(sp, "x") + 20, num(sp, "y") + 20, -120);
            render(gui, 786, 638, 1, null); clipped(gui);
            check(num(sp, "scroll") > 0 && num(mp, "scroll") == moduleScroll, "Settings scroll leaked into modules");
            click(gui, hit(gui, "SCROLL", mp), 0); frame(786, 638, 1); gui.draw(786, 638, num(mp, "x"), 637); gui.release(0);
            check(num(mp, "scroll") == num(mp, "maxScroll"), "Scrollbar drag did not reach end");
            int oldX = num(gui, "x"), oldY = num(gui, "y");
            gui.click(oldX + 40, oldY + 10, 0); frame(786, 638, 1); gui.draw(786, 638, -100, -100); gui.release(0);
            check(num(gui, "x") == 0 && num(gui, "y") == 0, "Window drag escaped screen");
            render(gui, 786, 638, 1, null);
            gui.click(num(gui, "configX") + 40, num(gui, "configY") + 10, 0); frame(786, 638, 1); gui.draw(786, 638, 900, 900); gui.release(0);
            check(num(gui, "configX") + 198 <= 786 && num(gui, "configY") + num(gui, "configHeight") <= 638, "Config drag escaped screen");
            gui.close();
            for (int[] size : new int[][] {{320, 240, 1}, {640, 360, 1}, {640, 480, 2}}) {
                gui = new XanaxWorkspace();
                render(gui, size[0], size[1], size[2], "xanax-" + size[0] + "x" + size[1] + "-scale" + size[2] + ".png"); clipped(gui);
                click(gui, hit(gui, "CATEGORY", Category.CLIENT), 0); render(gui, size[0], size[1], size[2], null);
                click(gui, hit(gui, "DROPDOWN", theme.getTheme()), 0);
                Object popup = get(gui, "popup");
                check(num(popup, "x") >= 0 && num(popup, "x") + num(popup, "w") <= size[0]
                        && num(popup, "y") + num(popup, "h") <= size[1], "Popup escaped small screen");
                gui.key('\0', Keyboard.KEY_ESCAPE);
                click(gui, hit(gui, "CONFIGS", null), 0); render(gui, size[0], size[1], size[2], "xanax-configs-" + size[0] + ".png");
                for (String button : new String[] {"Load", "Save", "Create", "Delete", "Folder", "Refresh"}) {
                    Object control = hit(gui, "ACTION", button);
                    check(num(control, "y") >= 0 && num(control, "y") + num(control, "h") <= size[1], "Config button outside small screen");
                }
                gui.key('\0', Keyboard.KEY_ESCAPE); gui.close();
            }
            // An open dropdown is discarded after a resize; host routes both theme transitions.
            gui = new XanaxWorkspace(); render(gui, 786, 638, 1, null);
            check(num(gui, "configX") >= num(gui, "x") + num(gui, "w"), "Reopening at a wider resolution kept configs over the main window");
            click(gui, hit(gui, "CATEGORY", Category.CLIENT), 0); render(gui, 786, 638, 1, null);
            click(gui, hit(gui, "DROPDOWN", theme.getTheme()), 0); render(gui, 320, 240, 1, null);
            check(get(gui, "popup") == null, "Resize kept stale dropdown coordinates");
            frame(786, 638, 1);
            VibeClickGui host = new VibeClickGui(); host.setWorldAndResolution(mc, 786, 638); host.drawScreen(0, 0, 0);
            XanaxWorkspace embedded = (XanaxWorkspace) get(host, "xanax");
            Object tab = hit(embedded, "CATEGORY", Category.CLIENT); host.mouseClicked(num(tab, "x") + 5, num(tab, "y") + 5, 0); host.drawScreen(0, 0, 0);
            Object dropdown = hit(embedded, "DROPDOWN", theme.getTheme()); host.mouseClicked(num(dropdown, "x") + 5, num(dropdown, "y") + 5, 0);
            Object popup = get(embedded, "popup");
            host.mouseClicked(num(popup, "x") + 10, num(popup, "y") + 2 + theme.getTheme().getModes().indexOf("NeverLose") * 20 + 5, 0);
            check(theme.getTheme().is("NeverLose"), "Host failed to switch away from Xanax"); host.drawScreen(0, 0, 0);
            NeverLoseWorkspace nl = (NeverLoseWorkspace) get(host, "neverLose");
            tab = hit(nl, "CATEGORY", Category.CLIENT); host.mouseClicked(num(tab, "x") + 5, num(tab, "y") + 5, 0); host.drawScreen(0, 0, 0);
            if (!hasHit(nl, "DROPDOWN", theme.getTheme())) {
                Object fold = hit(nl, "FOLD", theme); host.mouseClicked(num(fold, "x") + 5, num(fold, "y") + 5, 0); host.drawScreen(0, 0, 0);
            }
            dropdown = hit(nl, "DROPDOWN", theme.getTheme()); host.mouseClicked(num(dropdown, "x") + 5, num(dropdown, "y") + 5, 0); popup = get(nl, "popup");
            host.mouseClicked(num(popup, "x") + 10, num(popup, "y") + 4 + theme.getTheme().getModes().indexOf("Xanax") * 22 + 5, 0);
            check(theme.getTheme().is("Xanax"), "Host failed to switch into Xanax"); host.drawScreen(0, 0, 0);
            // Inspect a final preview with real module names and conditional settings.
            set(ModuleManager.class, manager, "modules", new ArrayList<Module>(Arrays.<Module>asList(new AimAssistModule(), new TargetsModule(), new WTapModule(), new ReachModule(), theme)));
            gui = new XanaxWorkspace(); render(gui, 786, 638, 1, null); click(gui, hit(gui, "CATEGORY", Category.COMBAT), 0);
            render(gui, 786, 638, 1, "xanax-real-modules.png");
            checkWindowResizing(theme);
            check(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), "Renderer leaked scissor state");
            check(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error in Xanax renderer");
            System.out.println("Xanax rendering, settings, profiles, clipping, dragging, scale and theme switching passed: " + GL11.glGetString(GL11.GL_RENDERER));
        } finally { gl.destroy(); }
    }

    private static void checkWindowResizing(ClickGuiModule theme) throws Exception {
        XanaxWorkspace gui = new XanaxWorkspace(); render(gui, 1000, 700, 1, null);
        // Position both windows explicitly so growing the main window cannot hide its grip.
        gui.click(num(gui, "x") + 40, num(gui, "y") + 10, 0);
        frame(1000, 700, 1); gui.draw(1000, 700, 60, 30); gui.release(0);
        gui.click(num(gui, "configX") + 40, num(gui, "configY") + 10, 0);
        frame(1000, 700, 1); gui.draw(1000, 700, 820, 30); gui.release(0);
        render(gui, 1000, 700, 1, null);
        int initialWidth = num(gui, "w"), initialHeight = num(gui, "h");
        gui.click(num(gui, "x") + initialWidth - 5, num(gui, "y") + initialHeight - 5, 1);
        frame(1000, 700, 1); gui.draw(1000, 700, 400, 300);
        check(num(gui, "w") == initialWidth && num(gui, "h") == initialHeight, "Right click resized Xanax");
        click(gui, hit(gui, "CATEGORY", Category.CLIENT), 0); render(gui, 1000, 700, 1, null);
        click(gui, hit(gui, "DROPDOWN", theme.getTheme()), 0);
        resize(gui, false, 420, 360);
        check(get(gui, "popup") == null, "Resizing kept a stale Xanax dropdown");
        render(gui, 1000, 700, 1, "xanax-resized-small.png"); clipped(gui);
        check(num(gui, "w") == 420 && num(gui, "h") == 360, "Xanax did not shrink to dragged size");
        click(gui, hit(gui, "DROPDOWN", theme.getTheme()), 0); check(get(gui, "popup") != null, "Resized settings are not clickable"); gui.key('\0', Keyboard.KEY_ESCAPE);
        resize(gui, false, 1, 1);
        check(num(gui, "w") == 300 && num(gui, "h") == 220, "Xanax minimum size failed");
        render(gui, 1000, 700, 1, null); clipped(gui);
        resize(gui, false, 5000, 5000);
        check(num(gui, "x") + num(gui, "w") <= 1000 && num(gui, "y") + num(gui, "h") <= 700, "Xanax resize escaped viewport");
        resize(gui, false, 740, 520); render(gui, 1000, 700, 1, null);
        // Move the config window before widening it, then verify button bounds expand with it.
        gui.click(num(gui, "configX") + 40, num(gui, "configY") + 10, 0);
        frame(1000, 700, 1); gui.draw(1000, 700, 720, 30); gui.release(0);
        render(gui, 1000, 700, 1, null);
        resize(gui, true, 300, 320);
        render(gui, 1000, 700, 1, null);
        check(num(gui, "configWidth") == 300 && num(gui, "configHeight") == 320, "Config window did not resize");
        check(num(hit(gui, "ACTION", "Load"), "w") == 280, "Config buttons kept their old width");
        resize(gui, true, 1, 1); check(num(gui, "configWidth") == 180 && num(gui, "configHeight") == 220, "Config minimum size failed");
        resize(gui, true, 5000, 5000);
        check(num(gui, "configX") + num(gui, "configWidth") <= 1000 && num(gui, "configY") + num(gui, "configHeight") <= 700, "Config resize escaped viewport");
        resize(gui, true, 300, 320);
        render(gui, 1000, 700, 1, "xanax-resized-large.png"); clipped(gui);
        gui.close(); gui = new XanaxWorkspace(); render(gui, 1000, 700, 1, null);
        check(num(gui, "w") == 740 && num(gui, "h") == 520 && num(gui, "configWidth") == 300 && num(gui, "configHeight") == 320, "Xanax forgot window sizes");
        render(gui, 320, 240, 1, null); clipped(gui);
        check(num(gui, "x") + num(gui, "w") <= 320 && num(gui, "y") + num(gui, "h") <= 240, "Saved Xanax size escaped smaller screen");
        render(gui, 1000, 700, 1, null);
        check(num(gui, "w") == 740 && num(gui, "h") == 520, "Smaller screen discarded preferred Xanax size");
        gui.click(num(gui, "x") + num(gui, "w") - 5, num(gui, "y") + num(gui, "h") - 5, 0);
        render(gui, 640, 480, 2, "xanax-resized-scale2.png");
        check(num(gui, "resizing") == 0, "GUI scale change left Xanax resize capture active");
    }

    private static void resize(XanaxWorkspace gui, boolean config, int width, int height) throws Exception {
        String xf = config ? "configX" : "x", yf = config ? "configY" : "y";
        gui.click(num(gui, xf) + num(gui, config ? "configWidth" : "w") - 5, num(gui, yf) + num(gui, config ? "configHeight" : "h") - 5, 0);
        frame(1000, 700, 1); gui.draw(1000, 700, num(gui, xf) + width - 5, num(gui, yf) + height - 5); gui.release(0);
    }

    private static void checkScriptsGlyphs() throws Exception {
        NeverLoseFont font = new NeverLoseFont(java.awt.Font.BOLD, 24);
        for (int scale : new int[] {1, 2, 3}) {
            frame(320, 240, scale);
            GL11.glClearColor(0, 0, 0, 1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GuiRenderState.prepare(false); font.draw("SCRIPTS", 24, 24, 0xFFFFFFFF);
            // These rows precede the glyphs. The old atlas leaked a dash from a neighbouring cell here.
            ByteBuffer strip = BufferUtils.createByteBuffer(100 * 3 * scale * scale * 4);
            GL11.glReadPixels(20 * scale, (240 - 25) * scale, 100 * scale, 3 * scale, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, strip);
            for (int i = 0; i < strip.capacity(); i += 4)
                check((strip.get(i) & 255) <= 1, "Glyph atlas leaked pixels above SCRIPTS at scale " + scale);
            ByteBuffer glyphs = BufferUtils.createByteBuffer(100 * 18 * scale * scale * 4);
            GL11.glReadPixels(20 * scale, (240 - 43) * scale, 100 * scale, 18 * scale, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, glyphs);
            int visible = 0;
            for (int i = 0; i < glyphs.capacity(); i += 4) if ((glyphs.get(i) & 255) > 32) visible++;
            check(visible > 60 * scale * scale, "Scripts text disappeared while fixing atlas bleed");
        }
    }

    private static void render(XanaxWorkspace gui, int width, int height, int scale, String filename) throws Exception {
        frame(width, height, scale); gui.draw(width, height, 0, 0);
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
        GL11.glViewport(0, 0, width * scale, height * scale); GL11.glDepthMask(true); GL11.glClearColor(.12F, .15F, .16F, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0, width, height, 0, -1000, 1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
    }
    private static void clipped(XanaxWorkspace gui) throws Exception {
        for (Object hit : (List<?>) get(gui, "hits")) {
            String kind = get(hit, "kind").toString();
            if (!Arrays.asList("MODULE", "BOOLEAN", "SLIDER", "STEP", "DROPDOWN", "TEXT", "COLOR").contains(kind)) continue;
            Object pane = get(gui, kind.equals("MODULE") ? "modulesPane" : "settingsPane");
            check(num(hit, "y") >= num(pane, "y") + 12 && num(hit, "y") + num(hit, "h") <= num(pane, "y") + num(pane, "h") - 8, "Clipped row receives clicks");
        }
    }
    private static void choose(XanaxWorkspace gui, int index) throws Exception { Object p = get(gui, "popup"); gui.click(num(p, "x") + 12, num(p, "y") + 2 + index * 20 + 5, 0); }
    private static void action(XanaxWorkspace gui, String name) throws Exception { click(gui, hit(gui, "ACTION", name), 0); }
    private static Object hit(Object gui, String kind, Object value) throws Exception {
        for (Object hit : (List<?>) get(gui, "hits")) if (get(hit, "kind").toString().equals(kind) && (get(hit, "value") == value || value instanceof String && value.equals(get(hit, "value")))) return hit;
        throw new AssertionError("Missing control: " + kind + " / " + value);
    }
    private static boolean hasHit(Object gui, String kind, Object value) throws Exception { try { hit(gui, kind, value); return true; } catch (AssertionError absent) { return false; } }
    private static void click(XanaxWorkspace gui, Object hit, int button) throws Exception { gui.click(num(hit, "x") + num(hit, "w") / 2, num(hit, "y") + num(hit, "h") / 2, button); }
    private static int num(Object object, String name) throws Exception { return ((Number) get(object, name)).intValue(); }
    private static Object get(Object object, String name) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private static void set(Class<?> type, Object instance, String name, Object value) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(instance, value); }
    private static void check(boolean success, String message) { if (!success) throw new AssertionError(message); }

    private static final class Fixture extends Module {
        final NumberSetting range;
        final BooleanSetting smartBlock;
        final ModeSetting mode;
        final MultiSelectSetting targets;
        final RangeSetting cps;
        final ColorSetting color;
        final StringSetting note;
        Fixture(String name) {
            super(name, "Preview module", Category.COMBAT, Keyboard.KEY_NONE);
            range = addSetting(new NumberSetting("Range", 3.92, 1, 6, .01));
            addSetting(new NumberSetting("BlockSlowdown", .5, 0, 1, .05));
            smartBlock = addSetting(new BooleanSetting("SmartBlock", true));
            mode = addSetting(new ModeSetting("Block-Mode", "Normal", () -> smartBlock.isEnabled(), "Normal", "Packet", "Watchdog"));
            addSetting(new BooleanSetting("Unblock", true)); addSetting(new BooleanSetting("Crush", false));
            addSetting(new BooleanSetting("Correct Movement", true)); addSetting(new BooleanSetting("Prediction", true));
            addSetting(new NumberSetting("Self Predict", 1, 0, 3, .1)); addSetting(new NumberSetting("Target Predict", 2, 0, 3, .1));
            targets = addSetting(new MultiSelectSetting("Targets", Arrays.asList("Players", "Mobs", "Animals"), Collections.singletonList("Players")));
            cps = addSetting(new RangeSetting("CPS", 8, 12, 1, 20, 1));
            color = addSetting(new ColorSetting("ESP Color", 0xFFB7352B));
            note = addSetting(new StringSetting("Note", "Default"));
            for (String label : new String[] {"Heuristics", "Raytrace", "ForceAim", "Wallbang", "MultiAura", "ESP", "TargetMonitor"}) addSetting(new BooleanSetting(label, false));
        }
    }
}
