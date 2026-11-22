package dev.vibe.ui.menu;

import dev.vibe.core.PlatformSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/**
 * Startup gate for mobile launchers such as PojavLauncher and FCL. Vibe does
 * not support Android or iOS, so the title screen is replaced by this notice
 * before the player can reach any Vibe menu.
 */
public final class UnsupportedPlatformGui extends GuiScreen {

    private static boolean shown;

    /** Called once per client tick; shows the notice as soon as any screen is ready. */
    public static void tick(Minecraft minecraft) {
        if (shown || !PlatformSupport.unsupported() || minecraft.theWorld != null) {
            return;
        }
        // Launchers and other mods may replace GuiMainMenu, so do not match a
        // specific title-screen class here.
        if (minecraft.currentScreen == null || minecraft.currentScreen instanceof UnsupportedPlatformGui) {
            return;
        }
        shown = true;
        org.apache.logging.log4j.LogManager.getLogger("Vibe")
                .warn("Unsupported platform detected: {}", PlatformSupport.describe());
        minecraft.displayGuiScreen(new UnsupportedPlatformGui());
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int left = width / 2 - 100;
        int top = height / 2 + 52;
        buttonList.add(new GuiButton(0, left, top, 200, 20, "退出游戏 / Quit game"));
        buttonList.add(new GuiButton(1, left, top + 24, 200, 20, "仍要继续 / Continue anyway"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0xFF070A10);
        drawCenteredString(fontRendererObj, "Vibe", width / 2, height / 2 - 96, 0xFFFFFFFF);
        drawCenteredString(fontRendererObj, "此客户端不支持 Android 或 iOS。", width / 2, height / 2 - 68, 0xFFFF6B6B);
        drawCenteredString(fontRendererObj, "Vibe does not support Android or iOS.", width / 2, height / 2 - 54, 0xFFFF6B6B);
        drawCenteredString(fontRendererObj, "Detected: " + PlatformSupport.describe(), width / 2, height / 2 - 28, 0xFF9AA4B2);
        drawCenteredString(fontRendererObj, "请在 Windows、macOS 或 Linux 上运行。", width / 2, height / 2 - 6, 0xFF9AA4B2);
        drawCenteredString(fontRendererObj, "Please run Vibe on Windows, macOS or Linux.", width / 2, height / 2 + 8, 0xFF9AA4B2);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            Minecraft.getMinecraft().shutdown();
            return;
        }
        Minecraft.getMinecraft().displayGuiScreen(null);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == 1) {
            Minecraft.getMinecraft().displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
