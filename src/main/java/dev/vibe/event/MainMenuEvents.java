package dev.vibe.event;

import dev.vibe.Vibe;
import dev.vibe.launcher.LauncherBridge;
import dev.vibe.ui.GamertagSetupGui;
import dev.vibe.ui.AccountManagerGui;
import dev.vibe.ui.MainMenuShaderManager;
import dev.vibe.ui.ShaderMenuGui;
import dev.vibe.ui.MainMenuPresentation;
import dev.vibe.ui.MenuThemesGui;
import dev.vibe.ui.VibeMenuButton;
import dev.vibe.ui.LicensesGui;
import static dev.vibe.ui.MainMenuPresentation.CHANGELOG_BUTTON_ID;
import static dev.vibe.ui.MainMenuPresentation.DISCORD_BUTTON_ID;
import static dev.vibe.ui.MainMenuPresentation.SHADER_BUTTON_ID;
import static dev.vibe.ui.MainMenuPresentation.THEMES_BUTTON_ID;
import static dev.vibe.ui.MainMenuPresentation.LICENSES_BUTTON_ID;
import java.awt.Desktop;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL20;

/** Adds Vibe branding and controls without replacing GuiMainMenu or other mods' buttons. */
public final class MainMenuEvents {
    private final MainMenuShaderManager shaders = new MainMenuShaderManager();
    private Field buttonListField;
    private MainMenuPresentation presentation;

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!(event.gui instanceof GuiMainMenu)) return;
        Minecraft mc = Minecraft.getMinecraft();
        // Apply and persist the fresh-profile default before displayGuiScreen
        // computes ScaledResolution. Existing options, including Auto, are kept.
        if (!new java.io.File(mc.mcDataDir, "options.txt").isFile()) {
            mc.gameSettings.guiScale = 2; // Normal
            mc.gameSettings.saveOptions();
        }
        if (!Vibe.getInstance().getIdentity().isConfigured()) {
            event.gui = new GamertagSetupGui(shaders, Vibe.getInstance().getIdentity());
        } else if (LauncherBridge.consumeAccountsRequest(mc.mcDataDir)) {
            // Use Vibe's own protected Microsoft/offline account flow, not a
            // second launcher implementation that would have to handle tokens.
            event.gui = new AccountManagerGui(event.gui, shaders, Vibe.getInstance().getAccountManager());
        } else if (LauncherBridge.consumeGta7Request(mc.mcDataDir)) {
            LauncherBridge.clearGta7Request(mc.mcDataDir);
            event.gui = new dev.vibe.ui.Gta7Gui(Vibe.getInstance().getModuleManager()
                    .getModule(dev.vibe.module.impl.Gta7Module.class));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMenuInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.gui instanceof GuiIngameMenu) {
            boolean exists = false;
            for (GuiButton button : event.buttonList) if (button.id == LICENSES_BUTTON_ID) exists = true;
            if (!exists) event.buttonList.add(new VibeMenuButton(new GuiButton(LICENSES_BUTTON_ID,
                    6, event.gui.height - 26, 156, 20, "Licenses & credits")));
        }
        if (!(event.gui instanceof GuiMainMenu)) return;
        presentation = new MainMenuPresentation(event.gui.width, event.gui.height);
        presentation.prepare(event.buttonList);
    }

    @SubscribeEvent
    public void onMenuDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!(event.gui instanceof GuiMainMenu)) return;
        Vibe.getInstance().updateWindowTitle();
        List<GuiButton> buttons = getButtons(event.gui);
        ScaledResolution resolution = new ScaledResolution(Minecraft.getMinecraft());
        // Own every frame, including the first frame after init/resize/return.
        // A shader failure still paints a usable Vibe menu over an opaque base.
        event.setCanceled(true);
        try {
            shaders.draw(resolution.getScaledWidth(), resolution.getScaledHeight());
        } catch (RuntimeException ignored) {
            Minecraft mc = Minecraft.getMinecraft();
            GL20.glUseProgram(0);
            mc.getFramebuffer().bindFramebuffer(true);
            mc.entityRenderer.setupOverlayRendering();
            Gui.drawRect(0, 0, resolution.getScaledWidth(), resolution.getScaledHeight(), 0xFF111218);
        }
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        if (presentation == null) presentation = new MainMenuPresentation(resolution.getScaledWidth(), resolution.getScaledHeight());
        presentation.draw(resolution.getScaledWidth(), resolution.getScaledHeight(), event.mouseX, event.mouseY);
        String loaded = Vibe.getInstance().getLoadedCountsText();
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(loaded,
                resolution.getScaledWidth() - Minecraft.getMinecraft().fontRendererObj.getStringWidth(loaded) - 9, 6, 0xFFFFFFFF);
        // Re-draw the live shared button list after the shader. Forge and other
        // mods can still add regular GuiButton instances through InitGuiEvent.
        for (GuiButton button : buttons) {
            button.drawButton(Minecraft.getMinecraft(), event.mouseX, event.mouseY);
        }
        presentation.drawOverlay(resolution.getScaledWidth(), resolution.getScaledHeight(), event.mouseX, event.mouseY);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMenuMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.gui instanceof GuiMainMenu) || presentation == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        int x = Mouse.getEventX() * event.gui.width / mc.displayWidth;
        int y = event.gui.height - Mouse.getEventY() * event.gui.height / mc.displayHeight - 1;
        if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState() && presentation.isAccountHit(x, y)) {
            mc.displayGuiScreen(new AccountManagerGui(event.gui, shaders, Vibe.getInstance().getAccountManager()));
            event.setCanceled(true);
            return;
        }
        if (presentation.mouseInput(x, y, Mouse.getEventDWheel(), Mouse.getEventButton(), Mouse.getEventButtonState())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMenuKey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        // Menus consume key events before Forge's in-world KeyInputEvent.
        dev.vibe.module.impl.ClickGuiModule click = Vibe.getInstance().getModuleManager()
                .getModule(dev.vibe.module.impl.ClickGuiModule.class);
        if (Minecraft.getMinecraft().theWorld == null && Keyboard.getEventKeyState()
                && !Keyboard.isRepeatEvent() && click != null && click.getKey() != Keyboard.KEY_NONE
                && Keyboard.getEventKey() == click.getKey()
                && (event.gui instanceof GuiMainMenu || event.gui instanceof net.minecraft.client.gui.GuiMultiplayer
                    || event.gui instanceof net.minecraft.client.gui.GuiDisconnected || event.gui instanceof GuiSelectWorld)) {
            Vibe.getInstance().openClickGui();
            event.setCanceled(true);
            return;
        }
        if (event.gui instanceof GuiMainMenu && presentation != null && Keyboard.getEventKeyState()
                && presentation.keyInput(Keyboard.getEventKey())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onMenuButton(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if ((event.gui instanceof GuiMainMenu || event.gui instanceof GuiIngameMenu) && event.button != null
                && event.button.id == LICENSES_BUTTON_ID) {
            Minecraft.getMinecraft().displayGuiScreen(new LicensesGui(event.gui, shaders));
            event.setCanceled(true);
            return;
        }
        if (!(event.gui instanceof GuiMainMenu) || event.button == null) return;
        if (event.button.id == SHADER_BUTTON_ID) {
            Minecraft.getMinecraft().displayGuiScreen(new ShaderMenuGui(event.gui, shaders));
            event.setCanceled(true);
        } else if (event.button.id == CHANGELOG_BUTTON_ID) {
            if (presentation != null) presentation.toggleChangelog();
            event.setCanceled(true);
        } else if (event.button.id == THEMES_BUTTON_ID) {
            Minecraft.getMinecraft().displayGuiScreen(new MenuThemesGui(event.gui, shaders));
            event.setCanceled(true);
        } else if (event.button.id == DISCORD_BUTTON_ID) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(new URI("https://discord.gg/ehv2UKbSvW"));
                }
            } catch (Exception ignored) {
            }
            event.setCanceled(true);
        }
    }

    @SuppressWarnings("unchecked")
    private List<GuiButton> getButtons(GuiScreen screen) {
        try {
            if (buttonListField == null) {
                for (String name : new String[] {"buttonList", "field_146292_n"}) {
                    try {
                        buttonListField = GuiScreen.class.getDeclaredField(name);
                        buttonListField.setAccessible(true);
                        break;
                    } catch (NoSuchFieldException ignored) {
                    }
                }
            }
            if (buttonListField != null) {
                Object value = buttonListField.get(screen);
                if (value instanceof List) return (List<GuiButton>) value;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }
}
