package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.module.impl.NesEmulatorModule;
import dev.vibe.nes.NesRuntime;
import java.io.IOException;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Standalone built-in NES player and RetroArch launcher. */
public final class NesEmulatorGui extends GuiScreen {
    private final NesEmulatorModule module;
    private final NesRuntime runtime = new NesRuntime();
    private final ParticlesRenderer particles = new ParticlesRenderer();
    private DynamicTexture screenTexture;
    private ResourceLocation screenLocation;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public NesEmulatorGui(NesEmulatorModule module) { this.module = module; }

    @Override public void initGui() {
        panelWidth = Math.min(Math.max(620, width - 18), 820);
        panelHeight = Math.min(Math.max(380, height - 26), 620);
        left = (width - panelWidth) / 2;
        top = Math.max(12, (height - panelHeight) / 2);
        if (screenTexture == null) {
            screenTexture = new DynamicTexture(NesRuntime.WIDTH, NesRuntime.HEIGHT);
            screenLocation = mc.getTextureManager().getDynamicTextureLocation("vibe_nes", screenTexture);
        }
        startSelectedRom();
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        SkeetEditorStyle.backdrop(this, BlurModule.NES_EMULATOR, partialTicks);
        SkeetEditorStyle.window(left, top, left + panelWidth, top + panelHeight, "Retro emulator", "local ROMs • built-in NES or installed RetroArch");

        int scale = displayScale();
        int drawWidth = NesRuntime.WIDTH * scale;
        int drawHeight = NesRuntime.HEIGHT * scale;
        int screenLeft = left + 18;
        int screenTop = top + 54;
        SkeetEditorStyle.panel(screenLeft - 5, screenTop - 5, screenLeft + drawWidth + 5, screenTop + drawHeight + 5, null);
        runtime.upload(screenTexture);
        if (screenLocation != null) {
            // GUI accent animations may leave a translucent GL colour behind.
            // Reset the texture state explicitly so the ROM framebuffer is
            // always rendered at its original colour and opacity.
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GL11.glDisable(GL11.GL_BLEND);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(screenLocation);
            Gui.drawScaledCustomSizeModalRect(screenLeft, screenTop, 0, 0, NesRuntime.WIDTH, NesRuntime.HEIGHT,
                    drawWidth, drawHeight, NesRuntime.WIDTH, NesRuntime.HEIGHT);
            GL11.glPopAttrib();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        int side = screenLeft + drawWidth + 25;
        int right = left + panelWidth - 17;
        SkeetEditorStyle.panel(side - 5, top + 52, right + 5, top + panelHeight - 20, "Cartridge");
        drawChoice("ROM", module.getRom().getValue(), side, top + 75, right);
        drawChoice("System", module.getSystem().getValue(), side, top + 112, right);
        drawChoice("Backend", module.getBackend().getValue(), side, top + 149, right);
        button(side, top + 188, right, module.getBackend().is("RetroArch") ? "OPEN WITH RETROARCH" : "START / RESTART", 0xFF2DE2C2);
        button(side, top + 214, right, "OPEN ROM FOLDER", 0xFF536FAD);
        fontRendererObj.drawSplitString(runtime.getStatus(), side, top + 248, Math.max(60, right - side), RenderUtils.TEXT);
        if (module.getRom().is("None")) {
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Add owned ROM files to the ROM folder,"), side, top + 280, RenderUtils.MUTED);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("then click the ROM row to select one."), side, top + 292, RenderUtils.MUTED);
        }
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("CONTROLS"), side, top + panelHeight - 91, SkeetEditorStyle.MUTED);
        fontRendererObj.drawStringWithShadow("A  Z       B  X", side, top + panelHeight - 76, RenderUtils.TEXT);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("SELECT  Shift     START  Enter"), side, top + panelHeight - 62, RenderUtils.TEXT);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("D-Pad  Arrow keys"), side, top + panelHeight - 48, RenderUtils.TEXT);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawChoice(String label, String value, int x, int y, int right) {
        fontRendererObj.drawStringWithShadow(label, x, y, RenderUtils.MUTED);
        SkeetEditorStyle.row(x, y + 11, right, y + 31, false, false);
        fontRendererObj.drawStringWithShadow(value, x + 7, y + 17, SkeetEditorStyle.TEXT);
        fontRendererObj.drawStringWithShadow("‹ ›", right - 21, y + 17, SkeetEditorStyle.accent(0.1F));
    }

    private void button(int x, int y, int right, String text, int color) {
        SkeetEditorStyle.button(x, y, right, y + 20, text, color != 0xFFFF6A82);
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            // Use the exact same constrained scale as drawScreen.  Without
            // this, small display sizes could put the clickable controls to
            // the right of their visible location.
            int side = left + 18 + displayScale() * NesRuntime.WIDTH + 25;
            int right = left + panelWidth - 17;
            if (hit(side, top + 72, right, top + 108, mouseX, mouseY)) { module.refreshRoms(); module.getRom().cycle(false); startSelectedRom(); return; }
            if (hit(side, top + 109, right, top + 145, mouseX, mouseY)) { module.getSystem().cycle(false); startSelectedRom(); return; }
            if (hit(side, top + 146, right, top + 182, mouseX, mouseY)) { module.getBackend().cycle(false); startSelectedRom(); return; }
            if (hit(side, top + 188, right, top + 208, mouseX, mouseY)) { startSelectedRom(); return; }
            if (hit(side, top + 214, right, top + 234, mouseX, mouseY)) { module.openFolder(); return; }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override public void handleKeyboardInput() throws IOException {
        super.handleKeyboardInput();
        int key = Keyboard.getEventKey();
        if (key == Keyboard.KEY_NONE) return;
        int button = nesButton(key);
        if (button >= 0) runtime.setButton(button, Keyboard.getEventKeyState());
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; }
        super.keyTyped(typedChar, keyCode);
    }

    private int nesButton(int key) {
        if (key == Keyboard.KEY_Z) return 0;
        if (key == Keyboard.KEY_X) return 1;
        if (key == Keyboard.KEY_LSHIFT || key == Keyboard.KEY_RSHIFT) return 2;
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) return 3;
        if (key == Keyboard.KEY_UP) return 4;
        if (key == Keyboard.KEY_DOWN) return 5;
        if (key == Keyboard.KEY_LEFT) return 6;
        if (key == Keyboard.KEY_RIGHT) return 7;
        return -1;
    }

    private void startSelectedRom() {
        if (module.getBackend().is("RetroArch")) {
            runtime.stop();
            runtime.showStatus(module.launchRetroArch());
        } else if (!module.canUseBuiltIn()) {
            runtime.stop();
            runtime.showStatus("Built-in playback supports NES/FDS. Choose RetroArch for " + module.selectedSystem() + ".");
        } else runtime.start(module.getSelectedRom(), module.getRegion().is("PAL"), module.getPresentationFps().getInt());
    }

    private int displayScale() {
        int requested = Math.max(1, Math.min(module.getScale().getInt(), 2));
        // There must be room for the framebuffer, its border and the title
        // strip. Scale two is deliberately downgraded on compact GUIs.
        return NesRuntime.HEIGHT * requested <= panelHeight - 72 ? requested : 1;
    }
    private boolean hit(int left, int top, int right, int bottom, int mouseX, int mouseY) { return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom; }
    @Override public void onGuiClosed() { runtime.stop(); if (module.isEnabled()) module.setEnabled(false); super.onGuiClosed(); }
    @Override public boolean doesGuiPauseGame() { return false; }
}
