package dev.vibe.module.impl.client;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

public final class ClickGuiModule extends Module {

    // Each style renders through VibeClickGui's shared Module/Setting model,
    // so switching the look never changes bindings, values, or config data.
    private final ModeSetting theme = addSetting(new ModeSetting("Theme", "Skeet", "Skeet", "Futuristic", "NeverLose", "Augustus", "Xanax", "PacketV3", "Novoline", "SuizideX"));
    private final ColorSetting primaryColor = addSetting(new ColorSetting("Primary Color", 0xFF2DE2C2, () -> !isDropdownTheme()));
    private final ColorSetting secondaryColor = addSetting(new ColorSetting("Secondary Color", 0xFFA855F7, () -> !isDropdownTheme()));
    private final BooleanSetting augustusRoundedCorners = addSetting(new BooleanSetting("Rounded Corners", true, () -> theme.is("Augustus")));
    private final NumberSetting augustusBackgroundAlpha = addSetting(new NumberSetting("Background Alpha", 200, 0, 255, 1, () -> theme.is("Augustus")));
    private final ModeSetting packetAccent = addSetting(new ModeSetting("Packet Color Mode", "Fade", () -> theme.is("PacketV3"), "Fade", "Static", "Rainbow"));
    private final ColorSetting packetColor = addSetting(new ColorSetting("Packet Color", 0xFF00B9DF, () -> theme.is("PacketV3") && !packetAccent.is("Rainbow")));
    private final ColorSetting packetFadeColor = addSetting(new ColorSetting("Packet Fade Color", 0xFF4149CE, () -> theme.is("PacketV3") && packetAccent.is("Fade")));
    private final NumberSetting packetFadeSpeed = addSetting(new NumberSetting("Packet Fade Speed", .6, .1, 3, .1, () -> theme.is("PacketV3") && !packetAccent.is("Static")));
    private final ColorSetting novolineColor = addSetting(new ColorSetting("Novoline Color", 0xFF8A76FA, () -> theme.is("Novoline")));
    private final BooleanSetting novolineGlow = addSetting(new BooleanSetting("Novoline Glow", true, () -> theme.is("Novoline")));
    private final BooleanSetting suizideDarkMode = addSetting(new BooleanSetting("SuizideX Dark Mode", false, () -> theme.is("SuizideX")));
    private final ColorSetting suizideColor = addSetting(new ColorSetting("SuizideX Color", 0xFFFF5038, () -> theme.is("SuizideX")));
    private final ColorSetting suizideFadeColor = addSetting(new ColorSetting("SuizideX Fade Color", 0xFFE91476, () -> theme.is("SuizideX")));

    public ClickGuiModule() {
        super("ClickGUI", "Open the Vibe module manager", Category.CLIENT, Keyboard.KEY_RSHIFT);
    }

    @Override
    public void toggle() {
        Vibe.getInstance().openClickGui();
    }

    public ModeSetting getTheme() { return theme; }
    private boolean isDropdownTheme(){return theme.is("PacketV3")||theme.is("Novoline")||theme.is("SuizideX");}
    public ColorSetting getPrimaryColor() { return primaryColor; }
    public ColorSetting getSecondaryColor() { return secondaryColor; }
    public BooleanSetting getAugustusRoundedCorners() { return augustusRoundedCorners; }
    public NumberSetting getAugustusBackgroundAlpha() { return augustusBackgroundAlpha; }
    public ModeSetting getPacketAccent() { return packetAccent; }
    public ColorSetting getPacketColor() { return packetColor; }
    public ColorSetting getPacketFadeColor() { return packetFadeColor; }
    public NumberSetting getPacketFadeSpeed() { return packetFadeSpeed; }
    public ColorSetting getNovolineColor() { return novolineColor; }
    public BooleanSetting getNovolineGlow() { return novolineGlow; }
    public BooleanSetting getSuizideDarkMode() { return suizideDarkMode; }
    public ColorSetting getSuizideColor() { return suizideColor; }
    public ColorSetting getSuizideFadeColor() { return suizideFadeColor; }
}
