package dev.vibe.hud;

import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;

public final class HudEditorGui extends GuiScreen {

    private final HudManager manager;
    private HudManager.HudElement dragged;
    private int offsetX;
    private int offsetY;

    public HudEditorGui(HudManager manager) {
        this.manager = manager;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("VIBE HUD LAYOUT"), 9, 9, 0xFFFFFFFF);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Drag elements • ESC to save and return"), 9, 21, 0xFF8FA5C4);
        for (String id : manager.getElementIds()) {
            if (manager.isEnabled(id)) manager.drawPreview(manager.getElement(id), fontRendererObj);
        }
        if (dragged != null) {
            ScaledResolution resolution = new ScaledResolution(mc);
            dragged.moveTo(mouseX - offsetX, mouseY - offsetY, resolution);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            for (String id : manager.getElementIds()) {
                if (!manager.isEnabled(id)) continue;
                HudManager.HudElement element = manager.getElement(id);
                if (mouseX >= element.getLeft() - 3 && mouseX <= element.getLeft() + Math.max(110, element.getWidth()) + 3
                        && mouseY >= element.getTop() - 3 && mouseY <= element.getTop() + Math.max(22, element.getHeight()) + 3) {
                    dragged = element;
                    offsetX = mouseX - element.getLeft();
                    offsetY = mouseY - element.getTop();
                    break;
                }
            }
        } else if (mouseButton == 2) {
            for (String id : manager.getElementIds()) {
                if (!manager.isEnabled(id)) continue;
                HudManager.HudElement element = manager.getElement(id);
                if (mouseX >= element.getLeft() - 3 && mouseX <= element.getLeft() + Math.max(110, element.getWidth()) + 3
                        && mouseY >= element.getTop() - 3 && mouseY <= element.getTop() + Math.max(22, element.getHeight()) + 3) {
                    manager.openSettings(id);
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && dragged != null) {
            dragged = null;
            manager.save();
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_H) {
            manager.save();
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
