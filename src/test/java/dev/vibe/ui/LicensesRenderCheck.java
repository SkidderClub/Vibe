package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.identity.ClientIdentity;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.LanguageModule;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.*;

/** Uses a real offscreen GL context; never opens a game window, browser or user profile. */
public final class LicensesRenderCheck {
    private static void set(Class<?> type, Object instance, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(instance, value);
    }
    private static int value(LicensesGui gui, String name) throws Exception {
        Field field = LicensesGui.class.getDeclaredField(name); field.setAccessible(true); return field.getInt(gui);
    }

    public static void main(String[] args) throws Exception {
        Path output = Paths.get("build/licenses-render-check"); Files.createDirectories(output);
        Pbuffer buffer = new Pbuffer(1280, 720, new PixelFormat(8, 24, 8), null, null);
        buffer.makeCurrent();
        try {
            Class<?> type = Class.forName("sun.misc.Unsafe"); Field field = type.getDeclaredField("theUnsafe"); field.setAccessible(true);
            Object unsafe = field.get(null); Method allocate = type.getMethod("allocateInstance", Class.class);
            Minecraft mc = (Minecraft) allocate.invoke(unsafe, Minecraft.class); set(Minecraft.class, null, "theMinecraft", mc);
            mc.gameSettings = new GameSettings(); mc.gameSettings.guiScale = 1;
            set(Minecraft.class, mc, "mcDataDir", output.toFile());
            IMetadataSerializer meta = new IMetadataSerializer();
            meta.registerMetadataSectionType(new TextureMetadataSectionSerializer(), TextureMetadataSection.class);
            meta.registerMetadataSectionType(new FontMetadataSectionSerializer(), FontMetadataSection.class);
            set(Minecraft.class, mc, "mcLanguageManager", new net.minecraft.client.resources.LanguageManager(meta, "en_US"));
            SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(meta);
            resources.reloadResourcePack(new DefaultResourcePack(Collections.<String, File>emptyMap()));
            set(Minecraft.class, mc, "mcResourceManager", resources); mc.renderEngine = new TextureManager(resources);
            OpenGlHelper.initializeTextures();
            mc.entityRenderer = (EntityRenderer) allocate.invoke(unsafe, EntityRenderer.class);
            set(EntityRenderer.class, mc.entityRenderer, "mc", mc);
            mc.fontRendererObj = new FontRenderer(mc.gameSettings, new ResourceLocation("textures/font/ascii.png"), mc.renderEngine, false);
            mc.fontRendererObj.onResourceManagerReload(resources);
            Vibe vibe = new Vibe(); set(Vibe.class, null, "instance", vibe);
            ModuleManager modules = (ModuleManager) allocate.invoke(unsafe, ModuleManager.class);
            LanguageModule language = new LanguageModule();
            set(ModuleManager.class, modules, "modules", new ArrayList<dev.vibe.module.Module>(Arrays.asList(language)));
            set(Vibe.class, vibe, "moduleManager", modules);
            MainMenuShaderManager shaders = new MainMenuShaderManager();
            shaders.getPresets();
            shaders.setEnabled(true);
            shaders.select("prestige.frag");

            for (LicenseDocuments.Page page : LicenseDocuments.Page.values()) if (page.path != null) {
                String canonical = new String(Files.readAllBytes(Paths.get(page.path)), StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
                if (!canonical.equals(LicenseDocuments.read(page).trim())) throw new AssertionError("Incomplete offline document: " + page);
            }

            for (int[] size : new int[][] {{320, 240}, {640, 360}, {960, 540}}) {
                mc.displayWidth = size[0]; mc.displayHeight = size[1];
                LicensesGui gui = new LicensesGui(null, shaders); gui.setWorldAndResolution(mc, size[0], size[1]);
                assertButtons(buttons(gui), size[0], size[1]);
                List<GuiButton> menu = new ArrayList<GuiButton>();
                for (int id : new int[] {1, 2, 6, 0, 4}) menu.add(new GuiButton(id, 0, 0, "Menu"));
                new MainMenuPresentation(size[0], size[1]).prepare(menu);
                assertButtons(menu, size[0], size[1]);
                assertLicenseButton(menu);
                GuiIngameMenu pause = new GuiIngameMenu(); pause.width = size[0]; pause.height = size[1];
                List<GuiButton> pauseButtons = new ArrayList<GuiButton>();
                dev.vibe.event.MainMenuEvents events = new dev.vibe.event.MainMenuEvents();
                net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent.Post init =
                        new net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent.Post(pause, pauseButtons);
                events.onMenuInit(init); events.onMenuInit(init);
                assertLicenseButton(pauseButtons);
                assertButtons(pauseButtons, size[0], size[1]);
                render(gui, output.resolve("credits-" + size[0] + ".png"));
                if (!shaders.getLastError().isEmpty()) throw new AssertionError(shaders.getLastError());
                GamertagSetupGui welcome = new GamertagSetupGui(shaders, new ClientIdentity(output.toFile()));
                welcome.setWorldAndResolution(mc, size[0], size[1]);
                assertButtons(buttons(welcome), size[0], size[1]);
                render(welcome, output.resolve("welcome-" + size[0] + ".png"));
                welcome.onGuiClosed();
                for (LicenseDocuments.Page page : LicenseDocuments.Page.values()) {
                    gui.actionPerformed(find(gui, 10 + page.ordinal()));
                    gui.keyTyped('\0', Keyboard.KEY_END);
                    render(gui, page == LicenseDocuments.Page.GPL && size[0] == 320 ? output.resolve("gpl-end-320.png") : null);
                    if (page == LicenseDocuments.Page.GPL) {
                        int end = value(gui, "scroll");
                        if (end < 1000) throw new AssertionError("GPL text cannot be scrolled to its end");
                        gui.keyTyped('\0', Keyboard.KEY_HOME);
                        if (value(gui, "scroll") != 0) throw new AssertionError("Home did not reset scrolling");
                        int x = value(gui, "left") + value(gui, "panelWidth") - 17;
                        gui.mouseClicked(x, value(gui, "contentTop") + 1, 0);
                        gui.mouseClickMove(x, value(gui, "contentBottom") + 1000, 0, 1);
                        gui.mouseReleased(x, 0, 0);
                        if (value(gui, "scroll") != end) throw new AssertionError("Scrollbar did not clamp at the end");
                    }
                }
                gui.actionPerformed(find(gui, 10));
                if (value(gui, "scroll") != 0) throw new AssertionError("Changing a page retained the old scroll position");
            }
            // Vibe's language selection does not force Minecraft's Unicode font mode.
            // Use the normal font choice and a common 2x GUI scale for localized screenshots.
            mc.displayWidth = 1280; mc.displayHeight = 720; mc.gameSettings.guiScale = 2;
            for (String selection : new String[] {"Chinese", "Russian", "Japanese", "Bavarian"}) {
                language.getLanguage().setValue(selection);
                LicensesGui gui = new LicensesGui(null, shaders); gui.setWorldAndResolution(mc, 640, 360);
                render(gui, output.resolve("credits-" + selection.toLowerCase(java.util.Locale.ROOT) + ".png"));
                GamertagSetupGui welcome = new GamertagSetupGui(shaders, new ClientIdentity(output.toFile()));
                welcome.setWorldAndResolution(mc, 640, 360);
                render(welcome, output.resolve("welcome-" + selection.toLowerCase(java.util.Locale.ROOT) + ".png"));
                welcome.onGuiClosed();
            }
            System.out.println("Offline document fidelity, all eight pages, scrolling, menu layout and localized rendering passed.");
        } finally { buffer.destroy(); }
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> buttons(GuiScreen gui) throws Exception {
        Field field = GuiScreen.class.getDeclaredField("buttonList"); field.setAccessible(true);
        return (List<GuiButton>) field.get(gui);
    }

    private static GuiButton find(LicensesGui gui, int id) throws Exception {
        for (GuiButton button : buttons(gui)) if (button.id == id) return button;
        throw new AssertionError("Missing button: " + id);
    }

    private static void assertButtons(List<GuiButton> buttons, int width, int height) {
        for (int i = 0; i < buttons.size(); i++) {
            GuiButton a = buttons.get(i);
            if (a.xPosition < 0 || a.yPosition < 0 || a.xPosition + a.width > width || a.yPosition + a.height > height)
                throw new AssertionError("Button outside screen: " + a.id);
            for (int j = i + 1; j < buttons.size(); j++) {
                GuiButton b = buttons.get(j);
                if (a.xPosition < b.xPosition + b.width && a.xPosition + a.width > b.xPosition
                        && a.yPosition < b.yPosition + b.height && a.yPosition + a.height > b.yPosition)
                    throw new AssertionError("Overlapping buttons: " + a.id + ", " + b.id);
            }
        }
    }

    private static void assertLicenseButton(List<GuiButton> buttons) {
        int count = 0;
        for (GuiButton button : buttons) if (button.id == MainMenuPresentation.LICENSES_BUTTON_ID) count++;
        if (count != 1) throw new AssertionError("Expected one visible license entry, found " + count);
    }

    private static void render(GuiScreen gui, Path output) throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        int displayWidth = mc.displayWidth, displayHeight = mc.displayHeight;
        Framebuffer target = mc.getFramebuffer();
        if (target == null || target.framebufferWidth != displayWidth || target.framebufferHeight != displayHeight) {
            if (target != null) target.deleteFramebuffer();
            target = new Framebuffer(displayWidth, displayHeight, true);
            set(Minecraft.class, mc, "framebufferMc", target);
        }
        target.bindFramebuffer(true);
        int setupError = GL11.glGetError();
        if (setupError != GL11.GL_NO_ERROR) throw new AssertionError("Framebuffer setup GL error: " + setupError);
        GL11.glViewport(0, 0, displayWidth, displayHeight); GL11.glDepthMask(true);
        GL11.glClearColor(0, 0, 0, 1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0, gui.width, gui.height, 0, -1000, 1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
        gui.drawScreen(-1, -1, 0);
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) throw new AssertionError(gui.getClass().getSimpleName() + " GL error: " + error);
        if (output == null) return;
        ByteBuffer pixels = BufferUtils.createByteBuffer(displayWidth * displayHeight * 4);
        GL11.glReadPixels(0, 0, displayWidth, displayHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        // Match Minecraft's final window blit. Reading only the GUI framebuffer
        // misses a stale blend cache left by the welcome screen's language flags.
        Framebuffer presented = new Framebuffer(displayWidth, displayHeight, false);
        try {
            presented.bindFramebuffer(true);
            target.framebufferRender(displayWidth, displayHeight);
            ByteBuffer finalPixels = BufferUtils.createByteBuffer(displayWidth * displayHeight * 4);
            GL11.glReadPixels(0, 0, displayWidth, displayHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, finalPixels);
            for (int i = 0; i < pixels.capacity(); i++) {
                if (i % 4 != 3 && pixels.get(i) != finalPixels.get(i)) {
                    throw new AssertionError(gui.getClass().getSimpleName() + " colors changed during framebuffer presentation at byte " + i);
                }
            }
        } finally {
            presented.deleteFramebuffer();
            target.bindFramebuffer(true);
        }
        BufferedImage image = new BufferedImage(displayWidth, displayHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < displayHeight; y++) for (int x = 0; x < displayWidth; x++) {
            int at = ((displayHeight - 1 - y) * displayWidth + x) * 4;
            image.setRGB(x, y, 0xFF000000 | (pixels.get(at) & 255) << 16 | (pixels.get(at + 1) & 255) << 8 | (pixels.get(at + 2) & 255));
        }
        ImageIO.write(image, "png", output.toFile());
    }
}
