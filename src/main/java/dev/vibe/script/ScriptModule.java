package dev.vibe.script;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.Setting;
import java.util.LinkedHashMap;
import java.util.Map;
import org.lwjgl.input.Keyboard;

/** A normal Vibe module backed by one compiled Raven-compatible source file. */
public final class ScriptModule extends Module {
    private final Map<String, Setting<?>> scriptSettings = new LinkedHashMap<String, Setting<?>>();
    private final ScriptRuntime runtime;
    private final String scriptName;

    ScriptModule(ScriptRuntime runtime, String scriptName) {
        super(scriptName, "Local Raven BS compatible script", Category.SCRIPTS, Keyboard.KEY_NONE);
        this.runtime = runtime;
        this.scriptName = scriptName;
    }

    @Override protected void onEnable() { runtime.invoke(scriptName, "onEnable"); }
    @Override protected void onDisable() { runtime.invoke(scriptName, "onDisable"); }

    public Setting<?> getScriptSetting(String name) { return scriptSettings.get(key(name)); }

    public void registerBoolean(String name, boolean value) {
        if (getScriptSetting(name) != null) return;
        BooleanSetting setting = addSetting(new BooleanSetting(name, value));
        scriptSettings.put(key(name), setting);
    }

    public void registerNumber(String name, double value, double minimum, double maximum, double increment) {
        if (getScriptSetting(name) != null) return;
        NumberSetting setting = addSetting(new NumberSetting(name, value, minimum, maximum, increment));
        scriptSettings.put(key(name), setting);
    }

    public void registerMode(String name, int defaultIndex, String[] values) {
        if (getScriptSetting(name) != null || values == null || values.length == 0) return;
        int index = Math.max(0, Math.min(values.length - 1, defaultIndex));
        ModeSetting setting = addSetting(new ModeSetting(name, values[index], values));
        scriptSettings.put(key(name), setting);
    }

    public void registerColor(String name, int red, int green, int blue, int alpha) {
        if (getScriptSetting(name) != null) return;
        ColorSetting setting = addSetting(new ColorSetting(name,
                ((Math.max(0, Math.min(255, alpha)) & 255) << 24)
                        | ((Math.max(0, Math.min(255, red)) & 255) << 16)
                        | ((Math.max(0, Math.min(255, green)) & 255) << 8)
                        | (Math.max(0, Math.min(255, blue)) & 255)));
        scriptSettings.put(key(name), setting);
    }

    public boolean getButton(String name) {
        Setting<?> setting = getScriptSetting(name);
        return setting instanceof BooleanSetting && ((BooleanSetting) setting).isEnabled();
    }

    public void setButton(String name, boolean value) {
        Setting<?> setting = getScriptSetting(name);
        if (setting instanceof BooleanSetting) ((BooleanSetting) setting).setEnabled(value);
    }

    public double getSlider(String name) {
        Setting<?> setting = getScriptSetting(name);
        if (setting instanceof NumberSetting) return ((NumberSetting) setting).getDouble();
        if (setting instanceof ModeSetting) return ((ModeSetting) setting).getModes().indexOf(((ModeSetting) setting).getValue());
        return 0.0D;
    }

    public void setSlider(String name, double value) {
        Setting<?> setting = getScriptSetting(name);
        if (setting instanceof NumberSetting) ((NumberSetting) setting).setValue(value);
        if (setting instanceof ModeSetting) {
            ModeSetting mode = (ModeSetting) setting;
            int index = Math.max(0, Math.min(mode.getModes().size() - 1, (int) Math.round(value)));
            mode.setValue(mode.getModes().get(index));
        }
    }

    public int getColor(String name) {
        Setting<?> setting = getScriptSetting(name);
        return setting instanceof ColorSetting ? ((ColorSetting) setting).getArgb() : 0xFFFFFFFF;
    }

    public void setColor(String name, int red, int green, int blue, int alpha) {
        Setting<?> setting = getScriptSetting(name);
        if (setting instanceof ColorSetting) ((ColorSetting) setting).setRgba(red, green, blue, alpha);
    }

    private static String key(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
