package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.cosmetic.CosmeticaAccessory;
import dev.vibe.cosmetic.CosmeticPreset;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.module.impl.CosmeticsEditorModule;
import java.io.IOException;
import java.util.Locale;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Per-accessory controls matching the adjustable fields exposed by Cosmetica. */
public final class CosmeticAccessoryOptionsGui extends GuiScreen {
    private final CosmeticsEditorModule module;
    private final CosmeticPreset preset;
    private final CosmeticaAccessory accessory;
    private final CosmeticPresetEditGui parent;
    private final CosmeticPreviewRenderer preview = new CosmeticPreviewRenderer();
    private int left, top, right, bottom, draggingAxis = -1;
    private float previewYaw;
    private int lastMouseX;
    private boolean offsetsChanged;

    public CosmeticAccessoryOptionsGui(CosmeticsEditorModule module, CosmeticPreset preset, CosmeticaAccessory accessory, CosmeticPresetEditGui parent) {
        this.module = module; this.preset = preset; this.accessory = accessory; this.parent = parent;
    }

    @Override public void initGui() {
        int width = Math.min(this.width - 18, 680), height = Math.min(this.height - 24, 430);
        left = (this.width - width) / 2; right = left + width; top = Math.max(12, (this.height - height) / 2); bottom = top + height;
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // GuiScreen#mouseClickMove is not delivered consistently at every
        // scaled-resolution/frame-rate combination in 1.8.9. Poll the held
        // button here as well so every slider tracks the cursor continuously.
        if (draggingAxis >= 0 && Mouse.isButtonDown(0)) {
            setAxis(draggingAxis, mouseX);
        }
        SkeetEditorStyle.backdrop(this, BlurModule.COSMETICS_EDITOR, partialTicks);
        SkeetEditorStyle.window(left, top, right, bottom, "Accessory settings", accessory.getName() + " • " + accessory.getAttachment());
        SkeetEditorStyle.panel(left + 14, top + 30, left + 248, bottom - 14, "Preview");
        SkeetEditorStyle.panel(left + 262, top + 30, right - 14, bottom - 14, "Cosmetica controls");
        preview.draw(preset, left + 131, top + 286, 82, previewYaw);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Drag preview to rotate"), left + 59, top + 310, SkeetEditorStyle.MUTED);
        drawSwitch(left + 28, top + 340, "Enabled", accessory.isEnabled());
        drawSwitch(left + 28, top + 366, "Mirrored", accessory.isMirrored());

        int controlX = left + 278;
        slider(controlX, top + 60, "X offset", accessory.getOffsetX(), accessory.getMinOffsetX(), accessory.getMaxOffsetX(), 0);
        slider(controlX, top + 106, "Y offset", accessory.getOffsetY(), accessory.getMinOffsetY(), accessory.getMaxOffsetY(), 1);
        slider(controlX, top + 152, "Z offset", accessory.getOffsetZ(), accessory.getMinOffsetZ(), accessory.getMaxOffsetZ(), 2);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Visibility overrides"), controlX, top + 207, SkeetEditorStyle.MUTED);
        drawSwitch(controlX, top + 224, "Hide with helmet", accessory.hasVisibilityFlag(CosmeticaAccessory.HIDE_WITH_HELMET));
        drawSwitch(controlX, top + 248, "Hide with chestplate", accessory.hasVisibilityFlag(CosmeticaAccessory.HIDE_WITH_CHESTPLATE));
        drawSwitch(controlX, top + 272, "Hide with leggings", accessory.hasVisibilityFlag(CosmeticaAccessory.HIDE_WITH_LEGGINGS));
        drawSwitch(controlX, top + 296, "Hide with boots", accessory.hasVisibilityFlag(CosmeticaAccessory.HIDE_WITH_BOOTS));
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Cloak, elytra and parrot flags are unavailable in 1.8.9."), controlX, top + 328, SkeetEditorStyle.MUTED);
        SkeetEditorStyle.button(controlX, bottom - 42, right - 28, bottom - 20, "SAVE & BACK", true);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void slider(int x, int y, String label, float value, float min, float max, int axis) {
        fontRendererObj.drawStringWithShadow(label, x, y, SkeetEditorStyle.TEXT);
        String valueText = min == max ? "fixed " + number(value) : number(value) + "  [" + number(min) + " .. " + number(max) + "]";
        fontRendererObj.drawStringWithShadow(valueText, x, y + 12, SkeetEditorStyle.MUTED);
        int trackLeft = x + 126, trackRight = right - 32, trackTop = y + 8;
        SkeetEditorStyle.input(trackLeft, trackTop, trackRight, trackTop + 11);
        if (min == max) return;
        float progress = (value - min) / (max - min);
        Gui.drawRect(trackLeft + 1, trackTop + 1, trackLeft + 1 + (int) ((trackRight - trackLeft - 2) * progress), trackTop + 10, SkeetEditorStyle.accent(axis * 0.23F));
        int thumb = trackLeft + (int) ((trackRight - trackLeft - 2) * progress);
        Gui.drawRect(thumb - 1, trackTop - 2, thumb + 2, trackTop + 13, SkeetEditorStyle.TEXT);
    }

    private void drawSwitch(int x, int y, String label, boolean enabled) {
        SkeetEditorStyle.row(x, y, x + 208, y + 20, enabled, false);
        fontRendererObj.drawStringWithShadow(label, x + 7, y + 6, SkeetEditorStyle.TEXT);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate(enabled ? "ON" : "OFF"), x + 174, y + 6, enabled ? 0xFF43D89C : 0xFFFF657A);
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        int previewLeft = left + 26, previewTop = top + 48;
        if (button == 0 && hit(previewLeft, previewTop, 210, 250, mouseX, mouseY)) { lastMouseX = mouseX; draggingAxis = -2; return; }
        if (button == 0 && hit(left + 28, top + 340, 208, 20, mouseX, mouseY)) { accessory.setEnabled(!accessory.isEnabled()); save(); return; }
        if (button == 0 && hit(left + 28, top + 366, 208, 20, mouseX, mouseY)) { accessory.setMirrored(!accessory.isMirrored()); save(); return; }
        int controls = left + 278;
        if (button == 0 && clickSlider(controls, top + 60, 0, mouseX, mouseY)) return;
        if (button == 0 && clickSlider(controls, top + 106, 1, mouseX, mouseY)) return;
        if (button == 0 && clickSlider(controls, top + 152, 2, mouseX, mouseY)) return;
        if (button == 0 && hit(controls, top + 224, 208, 20, mouseX, mouseY)) { flag(CosmeticaAccessory.HIDE_WITH_HELMET); return; }
        if (button == 0 && hit(controls, top + 248, 208, 20, mouseX, mouseY)) { flag(CosmeticaAccessory.HIDE_WITH_CHESTPLATE); return; }
        if (button == 0 && hit(controls, top + 272, 208, 20, mouseX, mouseY)) { flag(CosmeticaAccessory.HIDE_WITH_LEGGINGS); return; }
        if (button == 0 && hit(controls, top + 296, 208, 20, mouseX, mouseY)) { flag(CosmeticaAccessory.HIDE_WITH_BOOTS); return; }
        if (button == 0 && hit(controls, bottom - 42, right - controls - 28, 22, mouseX, mouseY)) { save(); mc.displayGuiScreen(parent); return; }
        super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickSlider(int x, int y, int axis, int mouseX, int mouseY) {
        boolean adjustable = axis == 0 ? accessory.isAdjustableX() : axis == 1 ? accessory.isAdjustableY() : accessory.isAdjustableZ();
        if (!adjustable || !hit(x + 126, y + 6, right - x - 158, 16, mouseX, mouseY)) return false;
        draggingAxis = axis; setAxis(axis, mouseX); return true;
    }

    @Override protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if (button != 0) return;
        if (draggingAxis == -2) { previewYaw += mouseX - lastMouseX; lastMouseX = mouseX; }
        else if (draggingAxis >= 0) setAxis(draggingAxis, mouseX);
    }

    @Override protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (draggingAxis >= 0 && offsetsChanged) save();
        offsetsChanged = false;
        draggingAxis = -1;
        super.mouseReleased(mouseX, mouseY, state);
    }
    @Override protected void keyTyped(char character, int keyCode) throws IOException { if (keyCode == Keyboard.KEY_ESCAPE) { save(); mc.displayGuiScreen(parent); return; } super.keyTyped(character, keyCode); }
    @Override public void onGuiClosed() { if (module.isEnabled() && !(mc.currentScreen instanceof CosmeticPresetEditGui)) module.setEnabled(false); super.onGuiClosed(); }
    @Override public boolean doesGuiPauseGame() { return false; }

    private void setAxis(int axis, int mouseX) {
        int leftTrack = left + 278 + 126, rightTrack = right - 32;
        float progress = Math.max(0.0F, Math.min(1.0F, (mouseX - leftTrack) / (float) Math.max(1, rightTrack - leftTrack)));
        float before = axis == 0 ? accessory.getOffsetX() : axis == 1 ? accessory.getOffsetY() : accessory.getOffsetZ();
        if (axis == 0) accessory.setOffsetX(accessory.getMinOffsetX() + (accessory.getMaxOffsetX() - accessory.getMinOffsetX()) * progress);
        else if (axis == 1) accessory.setOffsetY(accessory.getMinOffsetY() + (accessory.getMaxOffsetY() - accessory.getMinOffsetY()) * progress);
        else accessory.setOffsetZ(accessory.getMinOffsetZ() + (accessory.getMaxOffsetZ() - accessory.getMinOffsetZ()) * progress);
        float after = axis == 0 ? accessory.getOffsetX() : axis == 1 ? accessory.getOffsetY() : accessory.getOffsetZ();
        if (Math.abs(after - before) > 0.00001F) offsetsChanged = true;
    }
    private void flag(int flag) { accessory.setVisibilityFlag(flag, !accessory.hasVisibilityFlag(flag)); save(); }
    private void save() { Vibe.getInstance().getCosmeticPresetManager().save(); }
    private static String number(float value) { return String.format(Locale.ROOT, "%.2f", value); }
    private static boolean hit(int x, int y, int width, int height, int mouseX, int mouseY) { return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height; }
}
