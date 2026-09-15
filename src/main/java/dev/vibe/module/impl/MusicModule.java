package dev.vibe.module.impl;

import dev.vibe.media.MusicService;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

public final class MusicModule extends Module {
    private static final Logger LOGGER = LogManager.getLogger(MusicModule.class);
    public final BooleanSetting hud = addSetting(new BooleanSetting("Media HUD", true));
    public final BooleanSetting systemMedia = addSetting(new BooleanSetting("System Media", true));
    public final StringSetting owners = addSetting(new StringSetting("Player Priority", "Spotify,Chrome,Firefox", 160, () -> systemMedia.isEnabled()));
    public final NumberSetting hudWidth = addSetting(new NumberSetting("HUD Width", 270, 180, 500, 5, () -> hud.isEnabled()));
    public final NumberSetting hudScale = addSetting(new NumberSetting("HUD Scale", 1, .5, 2, .05, () -> hud.isEnabled()));
    public final BooleanSetting cover = addSetting(new BooleanSetting("Cover Art", true, () -> hud.isEnabled()));
    public final BooleanSetting coverBackground = addSetting(new BooleanSetting("Blurred Cover Background", true, () -> hud.isEnabled()));
    public final BooleanSetting progress = addSetting(new BooleanSetting("Track Progress", true, () -> hud.isEnabled()));
    public final BooleanSetting scroll = addSetting(new BooleanSetting("Scroll Long Titles", true, () -> hud.isEnabled()));
    public final BooleanSetting hideIdle = addSetting(new BooleanSetting("Hide When Idle", false, () -> hud.isEnabled()));
    public final ColorSetting background = addSetting(new ColorSetting("HUD Background", 0xC51A202A, () -> hud.isEnabled()));
    public final ColorSetting text = addSetting(new ColorSetting("HUD Text", 0xFFF5F7FF, () -> hud.isEnabled()));
    public final ColorSetting accent = addSetting(new ColorSetting("HUD Accent", 0xFF7DE2CE, () -> hud.isEnabled()));
    public final BooleanSetting radio = addSetting(new BooleanSetting("Radio", false));
    public final ModeSetting station = addSetting(new ModeSetting("Radio Station", "Groove Salad", () -> radio.isEnabled(), "Groove Salad", "Drone Zone", "Secret Agent", "Custom"));
    public final StringSetting radioUrl = addSetting(new StringSetting("Radio URL", "", 2048, () -> radio.isEnabled() && station.is("Custom")));
    public final NumberSetting volume = addSetting(new NumberSetting("Radio Volume", 45, 0, 100, 1, () -> radio.isEnabled()));
    public final BooleanSetting reconnect = addSetting(new BooleanSetting("Radio Reconnect", true, () -> radio.isEnabled()));
    public final BooleanSetting visualizer = addSetting(new BooleanSetting("Volume Display", false));
    public final ModeSetting audioSource = addSetting(new ModeSetting("Audio Source", "Auto", () -> visualizer.isEnabled(), "Auto", "System", "Radio"));
    public final ModeSetting style = addSetting(new ModeSetting("Wave Style", "Waves", () -> visualizer.isEnabled(), "Waves", "Bars", "Line"));
    public final NumberSetting bands = addSetting(new NumberSetting("Wave Detail", 64, 16, 128, 4, () -> visualizer.isEnabled()));
    public final NumberSetting waveHeight = addSetting(new NumberSetting("Wave Height", 90, 10, 300, 5, () -> visualizer.isEnabled()));
    public final NumberSetting waveWidth = addSetting(new NumberSetting("Wave Width", 100, 10, 100, 1, () -> visualizer.isEnabled()));
    public final NumberSetting bottom = addSetting(new NumberSetting("Bottom Offset", 0, 0, 220, 1, () -> visualizer.isEnabled()));
    public final NumberSetting gain = addSetting(new NumberSetting("Sensitivity", 2, .1, 12, .1, () -> visualizer.isEnabled()));
    public final NumberSetting smoothing = addSetting(new NumberSetting("Wave Smoothing", 70, 0, 95, 1, () -> visualizer.isEnabled()));
    public final NumberSetting minHz = addSetting(new NumberSetting("Minimum Frequency", 35, 20, 2000, 5, () -> visualizer.isEnabled()));
    public final NumberSetting maxHz = addSetting(new NumberSetting("Maximum Frequency", 16000, 2000, 22000, 100, () -> visualizer.isEnabled()));
    public final NumberSetting waveOpacity = addSetting(new NumberSetting("Wave Opacity", 75, 0, 100, 1, () -> visualizer.isEnabled()));
    public final NumberSetting lineWidth = addSetting(new NumberSetting("Wave Line Width", 2, 1, 6, .5, () -> visualizer.isEnabled()));
    public final NumberSetting gap = addSetting(new NumberSetting("Bar Gap", 2, 0, 12, .5, () -> visualizer.isEnabled() && style.is("Bars")));
    public final NumberSetting layers = addSetting(new NumberSetting("Wave Layers", 3, 1, 5, 1, () -> visualizer.isEnabled() && style.is("Waves")));
    public final BooleanSetting mirror = addSetting(new BooleanSetting("Mirror Spectrum", true, () -> visualizer.isEnabled()));
    public final BooleanSetting peaks = addSetting(new BooleanSetting("Peak Markers", true, () -> visualizer.isEnabled() && style.is("Bars")));
    public final ModeSetting waveColor = addSetting(new ModeSetting("Wave Color Mode", "Gradient", () -> visualizer.isEnabled(), "Solid", "Gradient", "Rainbow"));
    public final ColorSetting first = addSetting(new ColorSetting("Wave Color", 0xFF69DEDC, () -> visualizer.isEnabled()));
    public final ColorSetting second = addSetting(new ColorSetting("Wave Second Color", 0xFF9A75ED, () -> visualizer.isEnabled() && waveColor.is("Gradient")));
    public final NumberSetting rainbowSpeed = addSetting(new NumberSetting("Wave Rainbow Speed", 1, 0, 5, .05, () -> visualizer.isEnabled() && waveColor.is("Rainbow")));
    private MusicService service;
    private boolean failed;

    public MusicModule() { super("Music", "Media cover and title, internet radio and audio-reactive waves", Category.CLIENT, Keyboard.KEY_NONE); }
    @Override protected void onEnable() {
        failed = false;
        // A HUD frame can arrive before the next client tick. Resolve the media
        // classes here, inside the failure boundary, before exposing that frame.
        tick();
    }
    public void tick() {
        if (!isEnabled()) return;
        try {
            if (service == null) service = new MusicService();
            service.update(this);
        } catch (RuntimeException | LinkageError failure) {
            failed = true;
            LOGGER.error("[Vibe] Music disabled after a media service failure", failure);
            setEnabled(false);
        }
    }
    public MusicService service() { return service; }
    @Override public String getDescription() {
        return failed ? "Music is unavailable. Restart the client; details are in latest.log." : super.getDescription();
    }
    @Override protected void onDisable() {
        MusicService current = service;
        service = null;
        if (current != null) try {
            current.close();
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("[Vibe] Could not fully stop the music service", failure);
        }
    }
    public String stationUrl() {
        if (station.is("Custom")) return radioUrl.getValue().trim();
        String id = station.is("Drone Zone") ? "dronezone" : station.is("Secret Agent") ? "secretagent" : "groovesalad";
        return "https://somafm.com/" + id + ".pls";
    }
}
