package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.StringSetting;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.lwjgl.input.Keyboard;

public final class HudModule extends Module {

    // Profiles written before selector initialization was fixed can contain
    // only this original selection, even though newer modules are enabled.
    private static final List<String> LEGACY_ARRAY_LIST_OPTIONS = Arrays.asList(
            "ESP", "TargetESP", "Hitmarker", "Skeletal", "Cosmetics", "ChestESP", "FullBright", "FOV Changer", "CustomCrosshair", "Animations", "Targets", "AimAssist", "Velocity", "BHop", "Fly",
            "LeftClicker", "Sprint", "NoJumpDelay", "NoSlow", "Eagle", "FastPlace", "HUD Editor",
            "HUD", "NameProtect", "ClickGUI", "Blur", "Waifu", "Particles", "QOL");
    private static final List<String> LEGACY_ARRAY_LIST_DEFAULTS = Arrays.asList(
            "ESP", "TargetESP", "Hitmarker", "Skeletal", "Cosmetics", "ChestESP", "FullBright", "FOV Changer", "CustomCrosshair", "Animations", "AimAssist", "Velocity", "BHop", "Fly",
            "LeftClicker", "Sprint", "NoJumpDelay", "NoSlow", "Eagle", "FastPlace", "HUD Editor", "NameProtect", "ClickGUI");

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Vibe", "Vibe", "Skeet", "LiquidGlass"));

    private final MultiSelectSetting hudElements = addSetting(new MultiSelectSetting("HUD Elements",
                        Arrays.asList("watermark", "arraylist", "coordinates", "scoreboard", "clock", "sessioninfo", "motiongraph", "stalker", "armor", "inventory", "health", "cps", "cpsgraph"),
            Arrays.asList("watermark", "arraylist", "coordinates", "scoreboard")));

    private final BooleanSetting replaceScoreboardServer = addSetting(new BooleanSetting("Replace Scoreboard Server", false,
            () -> true));
    private final MultiSelectSetting watermarkDetails = addSetting(new MultiSelectSetting("Watermark Details",
            Arrays.asList("Version", "FPS", "Username"), Arrays.asList("Version", "FPS"), () -> true));
    private final StringSetting watermarkText = addSetting(new StringSetting("Watermark Text", "VIBE", 24, () -> true));
    private final ModeSetting arrayStyle = addSetting(new ModeSetting("Array Style", "Cards", () -> true, "Cards", "Compact", "Minimal"));
    private final MultiSelectSetting arrayListModules = addSetting(new MultiSelectSetting("ArrayList Modules",
            LEGACY_ARRAY_LIST_OPTIONS, LEGACY_ARRAY_LIST_DEFAULTS, () -> true));
    private final BooleanSetting arrayOutline = addSetting(new BooleanSetting("ArrayList Outline", true, () -> true));
        private final BooleanSetting watermarkOutline = addSetting(new BooleanSetting("Watermark Outline", true, () -> true));
    private final BooleanSetting coordinatesOutline = addSetting(new BooleanSetting("Coordinates Outline", true, () -> true));
    private final ModeSetting armorDisplayMode = addSetting(new ModeSetting("Armor Display", "Horizontal", () -> true,
            "Horizontal", "Vertical"));
    private final ColorSetting healthMaximumColor = addSetting(new ColorSetting("Health Max Color", 0xFF5BE8A6,
            () -> true));
    private final ColorSetting healthMinimumColor = addSetting(new ColorSetting("Health Min Color", 0xFFFF5B6E,
            () -> true));
    private final BooleanSetting healthAbsorption = addSetting(new BooleanSetting("Health Absorption", true, () -> true));
    private final ColorSetting healthAbsorptionColor = addSetting(new ColorSetting("Absorption Color", 0xFFFFC857,
            () -> healthAbsorption.isEnabled()));
    private final BooleanSetting healthHideFull = addSetting(new BooleanSetting("Hide Health When Full", false, () -> true));
    private final ColorSetting arrayPrimaryColor = addSetting(new ColorSetting("ArrayList Primary Color", 0xFF2DE2C2,
            () -> true));
    private final ColorSetting arraySecondaryColor = addSetting(new ColorSetting("ArrayList Secondary Color", 0xFFA855F7,
            () -> true));
    private final ColorSetting accent = addSetting(new ColorSetting("Accent", 0xFF2DE2C2));
    private final ColorSetting background = addSetting(new ColorSetting("Array Background", 0xD9141E34,
            () -> true));

    public final dev.vibe.hud.ArrayListSettings array = new dev.vibe.hud.ArrayListSettings(this::addSetting);

    public HudModule() {
        super("HUD", "Customizable Vibe HUD", Category.CLIENT, Keyboard.KEY_NONE);
        setEnabled(true);
    }

    public MultiSelectSetting getHudElements() { return hudElements; }
    public ModeSetting getMode() { return mode; }
    public BooleanSetting getReplaceScoreboardServer() { return replaceScoreboardServer; }
    public MultiSelectSetting getWatermarkDetails() { return watermarkDetails; }
    public StringSetting getWatermarkText() { return watermarkText; }
    public ModeSetting getArrayStyle() { return arrayStyle; }
    public MultiSelectSetting getArrayListModules() { return arrayListModules; }
    public BooleanSetting getArrayOutline() { return arrayOutline; }
        public BooleanSetting getWatermarkOutline() { return watermarkOutline; }
        public BooleanSetting getCoordinatesOutline() { return coordinatesOutline; }
    public ModeSetting getArmorDisplayMode() { return armorDisplayMode; }
    public ColorSetting getHealthMaximumColor() { return healthMaximumColor; }
    public ColorSetting getHealthMinimumColor() { return healthMinimumColor; }
    public BooleanSetting getHealthAbsorption() { return healthAbsorption; }
    public ColorSetting getHealthAbsorptionColor() { return healthAbsorptionColor; }
    public BooleanSetting getHealthHideFull() { return healthHideFull; }
    public ColorSetting getArrayPrimaryColor() { return arrayPrimaryColor; }
    public ColorSetting getArraySecondaryColor() { return arraySecondaryColor; }
    public ColorSetting getAccent() { return accent; }
    public ColorSetting getBackground() { return background; }

    /** Recover the incomplete stock selection in pre-format-3 profiles. */
    public void migrateLegacyArrayListModules() {
        Set<String> selected = arrayListModules.copyValue();
        // Restrict recovery to the exact old default. Custom filters, including
        // deliberately empty selections, must keep their saved values.
        if (!selected.equals(new LinkedHashSet<String>(LEGACY_ARRAY_LIST_DEFAULTS))) return;
        for (String option : arrayListModules.getOptions()) {
            if (!LEGACY_ARRAY_LIST_OPTIONS.contains(option)) selected.add(option);
        }
        arrayListModules.setValue(selected);
    }

    /** Populate the user-facing selector for every newly registered module. */
    public void synchronizeArrayListModules() {
        if (dev.vibe.Vibe.getInstance() == null || dev.vibe.Vibe.getInstance().getModuleManager() == null) return;
        synchronizeArrayListModules(dev.vibe.Vibe.getInstance().getModuleManager().getModules());
    }

    /** Initialize built-in defaults before loading a profile; also accept later script modules. */
    public void synchronizeArrayListModules(Iterable<Module> modules) {
        Set<String> selected = null;
        for (Module module : modules) {
            if (!arrayListModules.getOptions().contains(module.getRawName())) {
                arrayListModules.addOption(module.getRawName());
                // A profile may already contain a newly registered name. Toggling
                // it here used to hide saved entries on every other client start.
                if (selected == null) selected = arrayListModules.copyValue();
                selected.add(module.getRawName());
            }
        }
        if (selected != null) arrayListModules.setValue(selected);
    }
}
