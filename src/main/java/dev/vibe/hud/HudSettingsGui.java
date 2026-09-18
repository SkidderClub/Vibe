package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.module.impl.HudModule;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.StringSetting;
import dev.vibe.setting.Setting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/** Compact settings surface opened from a HUD element in the HUD designer. */
public final class HudSettingsGui extends GuiScreen {
    private final HudManager manager;
    private final String element;
    private final List<Setting<?>> settings = new ArrayList<Setting<?>>();
    private final Set<MultiSelectSetting> openMulti = new HashSet<MultiSelectSetting>();
    private StringSetting editing;
    private dev.vibe.setting.ColorSetting editingColor;
    private int scroll;
    private String editBuffer = "";
    private NumberSetting draggingNumber;

    public HudSettingsGui(HudManager manager, String element) {
        this.manager = manager;
        this.element = element;
        HudModule hud = Vibe.getInstance().getModuleManager().getModule(HudModule.class);
        if (HudManager.MUSIC.equals(element)) {
            dev.vibe.module.impl.MusicModule music = Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.MusicModule.class);
            if (music != null) {
                settings.add(music.hudWidth); settings.add(music.hudScale); settings.add(music.cover);
                settings.add(music.coverBackground); settings.add(music.progress); settings.add(music.scroll); settings.add(music.hideIdle);
            }
        } else if (HudManager.WATERMARK.equals(element)) {
            settings.add(hud.getWatermarkOutline());
            settings.add(hud.getWatermarkDetails());
            settings.add(hud.getWatermarkText());
        } else if (HudManager.ARRAY_LIST.equals(element)) {
            settings.add(hud.getArrayOutline());
            settings.add(hud.getArrayStyle());
            settings.add(hud.getArrayListModules());
            settings.add(hud.getArrayPrimaryColor()); settings.add(hud.getArraySecondaryColor());
            settings.add(hud.getBackground()); settings.addAll(hud.array.all);
        } else if (HudManager.COORDINATES.equals(element)) {
            settings.add(hud.getCoordinatesOutline());
        } else if (HudManager.SCOREBOARD.equals(element)) {
            settings.add(hud.getReplaceScoreboardServer());
        } else if (HudManager.ARMOR.equals(element)) {
            settings.add(hud.getArmorDisplayMode());
        } else if (HudManager.HEALTH.equals(element)) {
            settings.add(hud.getHealthMaximumColor());
            settings.add(hud.getHealthMinimumColor());
            settings.add(hud.getHealthAbsorption());
            settings.add(hud.getHealthAbsorptionColor());
            settings.add(hud.getHealthHideFull());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (draggingNumber != null) {
            setNumberFromMouse(draggingNumber, mouseX);
        }
        drawDefaultBackground();
        int left = width / 2 - 120;
        int top = 16;
        scroll = Math.min(scroll, Math.max(0, settingsHeight() - (height - 66)));
        drawRect(left, top, left + 240, height - 12, 0xE00A1020);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.format("%s SETTINGS", dev.vibe.language.LanguageManager.translate(element.toUpperCase())), left + 10, top + 10, 0xFFFFFFFF);
        int y = top + 34 - scroll;
        try (dev.vibe.ui.GuiClip clip = new dev.vibe.ui.GuiClip(left, top + 34, 240, height - 66)) {
        for (Setting<?> setting : settings) {
            if (!setting.isVisible()) continue;
            fontRendererObj.drawStringWithShadow(setting.getName(), left + 12, y + 4, 0xFFD5E1F5);
            String state = describe(setting);
            int color = setting instanceof BooleanSetting && ((BooleanSetting) setting).isEnabled() ? 0xFF2DE2C2 : 0xFF8FA5C4;
            fontRendererObj.drawStringWithShadow(state, left + 228 - fontRendererObj.getStringWidth(state), y + 4, color);
            if (setting instanceof NumberSetting) {
                NumberSetting number = (NumberSetting) setting;
                int trackLeft = left + 12;
                int trackRight = left + 228;
                float ratio = (float) ((number.getDouble() - number.getMinimum())
                        / Math.max(0.000001D, number.getMaximum() - number.getMinimum()));
                drawRect(trackLeft, y + 18, trackRight, y + 21, 0xFF263955);
                drawRect(trackLeft, y + 18, trackLeft + Math.round((trackRight - trackLeft) * ratio), y + 21, 0xFF2DE2C2);
                y += 30;
            } else {
                y += 22;
            }
            if (setting instanceof MultiSelectSetting && openMulti.contains(setting)) {
                MultiSelectSetting multi = (MultiSelectSetting) setting;
                for (String option : multi.getOptions()) {
                    String line = (multi.isSelected(option) ? "[x] " : "[ ] ") + dev.vibe.language.LanguageManager.translate(option);
                    fontRendererObj.drawStringWithShadow(line, left + 20, y + 2, multi.isSelected(option) ? 0xFF2DE2C2 : 0xFF8FA5C4);
                    y += 14;
                }
            }
        }
        }
        fontRendererObj.drawStringWithShadow("Scroll | Click color to enter #RRGGBBAA", left + 4, height - 10, 0xFF8FA5C4);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && mouseY >= 50 && mouseY < height - 16) {
            int left = width / 2 - 120;
            int top = 16;
            int y = top + 34 - scroll;
            for (Setting<?> setting : settings) {
                if (!setting.isVisible()) continue;
                int rowHeight = setting instanceof NumberSetting ? 30 : 22;
                if (mouseX >= left && mouseX < left + 240 && mouseY >= y && mouseY < y + rowHeight) {
                    if (setting instanceof dev.vibe.setting.ColorSetting) {
                        editingColor = (dev.vibe.setting.ColorSetting) setting;
                        editing = null; editBuffer = editingColor.getHex();
                    } else if (setting instanceof BooleanSetting) ((BooleanSetting) setting).toggle();
                    else if (setting instanceof ModeSetting) ((ModeSetting) setting).cycle(false);
                    else if (setting instanceof MultiSelectSetting) { if (!openMulti.add((MultiSelectSetting) setting)) openMulti.remove(setting); }
                    else if (setting instanceof NumberSetting) {
                        draggingNumber = (NumberSetting) setting;
                        setNumberFromMouse(draggingNumber, mouseX);
                    } else if (setting instanceof StringSetting) {
                        editing = (StringSetting) setting; editBuffer = editing.getValue();
                    }
                    Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
                    dev.vibe.ui.ArrayListRenderer.applyPreset(Vibe.getInstance().getModuleManager().getModule(HudModule.class));
                    return;
                }
                y += rowHeight;
                if (setting instanceof MultiSelectSetting && openMulti.contains(setting)) {
                    MultiSelectSetting multi = (MultiSelectSetting) setting;
                    for (String option : multi.getOptions()) {
                        if (mouseX >= left && mouseX < left + 240 && mouseY >= y && mouseY < y + 14) {
                            multi.toggle(option); Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager()); return;
                        }
                        y += 14;
                    }
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && draggingNumber != null) {
            Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
            draggingNumber = null;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (editingColor != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) { editingColor = null; return; }
            if (keyCode == Keyboard.KEY_RETURN) {
                if (editingColor.setHex(editBuffer)) { editingColor = null; manager.save(); } return;
            }
            if (keyCode == Keyboard.KEY_BACK && !editBuffer.isEmpty()) editBuffer = editBuffer.substring(0,editBuffer.length()-1);
            else if ("#0123456789abcdefABCDEF".indexOf(typedChar)>=0 && editBuffer.length()<9) editBuffer += typedChar;
            return;
        }
        if (editing != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                editing.setValue(editBuffer); Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager()); editing = null; return;
            }
            if (keyCode == Keyboard.KEY_BACK && !editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
            else if (typedChar >= 32 && typedChar <= 126 && editBuffer.length() < editing.getMaxLength()) editBuffer += typedChar;
            if (editing != null) { editing.setValue(editBuffer); Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager()); }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(new HudEditorGui(manager));
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private int settingsHeight() {
        int result = 0;
        for (Setting<?> setting : settings) {
            if (!setting.isVisible()) continue;
            result += setting instanceof NumberSetting ? 30 : 22;
            if (setting instanceof MultiSelectSetting && openMulti.contains(setting)) result += ((MultiSelectSetting) setting).getOptions().size() * 14;
        }
        return result;
    }

    private void setNumberFromMouse(NumberSetting setting, int mouseX) {
        int left = width / 2 - 108;
        int right = width / 2 + 108;
        float ratio = Math.max(0.0F, Math.min(1.0F, (mouseX - left) / (float) Math.max(1, right - left)));
        setting.setValue(setting.getMinimum() + (setting.getMaximum() - setting.getMinimum()) * ratio);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) scroll = Math.max(0, Math.min(Math.max(0,settingsHeight()-(height-66)),scroll-(wheel>0?36:-36)));
    }
    private String describe(Setting<?> setting) {
        if (setting instanceof dev.vibe.setting.ColorSetting) return editingColor == setting ? editBuffer + "_" : ((dev.vibe.setting.ColorSetting)setting).getHex();
        if (setting instanceof BooleanSetting) return dev.vibe.language.LanguageManager.translate(((BooleanSetting) setting).isEnabled() ? "ON" : "OFF");
        if (setting instanceof ModeSetting) return dev.vibe.language.LanguageManager.translate(((ModeSetting) setting).getValue());
        if (setting instanceof MultiSelectSetting) return dev.vibe.language.LanguageManager.format("%s selected", ((MultiSelectSetting) setting).getValue().size());
        if (setting instanceof NumberSetting) return String.valueOf(((NumberSetting) setting).getValue());
        if (setting instanceof StringSetting) return editing == setting ? editBuffer : ((StringSetting) setting).getValue();
        return "";
    }
}
