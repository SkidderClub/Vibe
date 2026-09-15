package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.MultiSelectSetting;
import java.util.Arrays;
import java.util.Collections;
import org.lwjgl.input.Keyboard;
import dev.vibe.setting.NumberSetting;

/** Global switchboard for Vibe blur surfaces. */
public final class BlurModule extends Module {
    public static final String ARRAY_LIST = "arraylist";
    public static final String WATERMARK = "watermark";
    public static final String COORDINATES = "coordinates";
    public static final String SCOREBOARD = "scoreboard";
    public static final String CLICK_GUI = "clickgui";
    public static final String INVENTORY_EDITOR = "inventoryeditor";
    public static final String FRIEND_EDITOR = "friendeditor";
    public static final String CONFIG_EDITOR = "configeditor";
    public static final String KEYBIND_EDITOR = "keybindeditor";
    public static final String ESP_EDITOR = "espeditor";
    public static final String CLOCK = "clock";
    public static final String SESSION_INFO = "sessioninfo";
    public static final String MOTION_GRAPH = "motiongraph";
    public static final String STALKER = "stalker";
    public static final String ARMOR = "armor";
    public static final String INVENTORY = "inventory";
    public static final String HEALTH = "health";
    public static final String CPS = "cps";
    public static final String CPS_GRAPH = "cpsgraph";
    public static final String NES_EMULATOR = "nesemulator";
    public static final String COSMETICS_EDITOR = "cosmeticseditor";
    public static final String SCRIPTS_EDITOR = "scriptseditor";
    public static final String GTA7 = "gta7";
    public static final String MEME_GAMES = "memegames";

    private final MultiSelectSetting elements = addSetting(new MultiSelectSetting("Blur Elements",
            Arrays.asList(ARRAY_LIST, WATERMARK, COORDINATES, SCOREBOARD, CLOCK, SESSION_INFO, MOTION_GRAPH, STALKER, ARMOR, INVENTORY, HEALTH, CPS, CPS_GRAPH,
                    CLICK_GUI, INVENTORY_EDITOR, FRIEND_EDITOR, CONFIG_EDITOR, KEYBIND_EDITOR, ESP_EDITOR, NES_EMULATOR, COSMETICS_EDITOR, SCRIPTS_EDITOR, GTA7, MEME_GAMES),
            // Vibe's HUD and utility surfaces opt in by default. Health stays
            // deliberately crisp so its crosshair-adjacent readout never
            // receives an unnecessary fullscreen blur pass.
            Arrays.asList(ARRAY_LIST, WATERMARK, COORDINATES, SCOREBOARD, CLOCK, SESSION_INFO, MOTION_GRAPH, STALKER, ARMOR, INVENTORY, CPS_GRAPH,
                    CLICK_GUI, INVENTORY_EDITOR, FRIEND_EDITOR, CONFIG_EDITOR, KEYBIND_EDITOR, ESP_EDITOR, NES_EMULATOR, COSMETICS_EDITOR, SCRIPTS_EDITOR, GTA7, MEME_GAMES)));
    private final NumberSetting strength = addSetting(new NumberSetting("Strength", 4.0D, 1.0D, 8.0D, 1.0D));

    public BlurModule() {
        super("Blur", "Globally control Vibe blur surfaces", Category.CLIENT, Keyboard.KEY_NONE);
    }

    public MultiSelectSetting getElements() {
        return elements;
    }
    public NumberSetting getStrength() { return strength; }
}
