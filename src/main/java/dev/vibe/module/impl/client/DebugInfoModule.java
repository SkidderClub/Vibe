package dev.vibe.module.impl.client;

import dev.vibe.debuginfo.DebugInfoSampler;
import dev.vibe.debuginfo.DebugInfoSnapshot;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Configurable on-screen telemetry panel showing Ping, TPS, CPS, BPS and client diagnostics. */
public final class DebugInfoModule extends Module {

    public static final String POS_TOP_LEFT = "Top Left";
    public static final String POS_TOP_RIGHT = "Top Right";
    public static final String POS_BOTTOM_LEFT = "Bottom Left";
    public static final String POS_BOTTOM_RIGHT = "Bottom Right";

    // Metric toggles (each individually configurable).
    private final BooleanSetting showPing = addSetting(new BooleanSetting("Ping", true));
    private final BooleanSetting showTps = addSetting(new BooleanSetting("TPS", true));
    private final BooleanSetting showCps = addSetting(new BooleanSetting("CPS", true));
    private final BooleanSetting showBps = addSetting(new BooleanSetting("BPS", true));
    private final BooleanSetting showFps = addSetting(new BooleanSetting("FPS", true));
    private final BooleanSetting showCoords = addSetting(new BooleanSetting("Coordinates", true));
    private final BooleanSetting showFacing = addSetting(new BooleanSetting("Facing", true));
    private final BooleanSetting showTimer = addSetting(new BooleanSetting("Timer", true));
    private final BooleanSetting showPackets = addSetting(new BooleanSetting("Packets", true));
    private final BooleanSetting showMemory = addSetting(new BooleanSetting("Memory", true));
    private final BooleanSetting showServer = addSetting(new BooleanSetting("Server", false));
    private final BooleanSetting showBiome = addSetting(new BooleanSetting("Biome", false));
    private final BooleanSetting showEntities = addSetting(new BooleanSetting("Entities", false));
    private final BooleanSetting showPlayerState = addSetting(new BooleanSetting("Player State", false));
    private final BooleanSetting showChunk = addSetting(new BooleanSetting("Chunk & Light", false));
    private final BooleanSetting showModules = addSetting(new BooleanSetting("Active Modules", false));

    // Layout & presentation settings.
    private final ModeSetting position = addSetting(new ModeSetting(
            "Position", POS_TOP_LEFT, POS_TOP_LEFT, POS_TOP_RIGHT, POS_BOTTOM_LEFT, POS_BOTTOM_RIGHT));
    private final NumberSetting offsetX = addSetting(new NumberSetting("Offset X", 8.0D, 0.0D, 400.0D, 2.0D));
    private final NumberSetting offsetY = addSetting(new NumberSetting("Offset Y", 42.0D, 0.0D, 400.0D, 2.0D));
    private final NumberSetting scale = addSetting(new NumberSetting("Scale", 1.0D, 0.6D, 1.5D, 0.05D));
    private final BooleanSetting showHeader = addSetting(new BooleanSetting("Show Header", true));
    private final BooleanSetting showBackground = addSetting(new BooleanSetting("Background", true));
    private final BooleanSetting colorCoded = addSetting(new BooleanSetting("Color Values", true));
    private final BooleanSetting hideInF3 = addSetting(new BooleanSetting("Hide In F3", true));
    private final ColorSetting accentColor = addSetting(new ColorSetting("Accent Color", 0xFF4CC9F0));
    private final NumberSetting backgroundAlpha = addSetting(
            new NumberSetting("Background Alpha", 170.0D, 0.0D, 255.0D, 5.0D, showBackground::isEnabled));

    private final DebugInfoSampler sampler = new DebugInfoSampler();

    public DebugInfoModule() {
        super("DebugInfo", "Display live ping, TPS, CPS, BPS and client telemetry on screen",
                Category.CLIENT, Keyboard.KEY_NONE);
    }

    @Override
    protected void onDisable() {
        sampler.reset();
    }

    public void tick() {
        if (!isEnabled()) {
            return;
        }
        Minecraft mc = safeMinecraft();
        if (mc != null) {
            sampler.tick(mc);
        }
    }

    public DebugInfoSnapshot sample() {
        Minecraft mc = safeMinecraft();
        return mc == null ? null : sampler.capture(mc);
    }

    public BooleanSetting getShowPing() { return showPing; }
    public BooleanSetting getShowTps() { return showTps; }
    public BooleanSetting getShowCps() { return showCps; }
    public BooleanSetting getShowBps() { return showBps; }
    public BooleanSetting getShowFps() { return showFps; }
    public BooleanSetting getShowCoords() { return showCoords; }
    public BooleanSetting getShowFacing() { return showFacing; }
    public BooleanSetting getShowTimer() { return showTimer; }
    public BooleanSetting getShowPackets() { return showPackets; }
    public BooleanSetting getShowMemory() { return showMemory; }
    public BooleanSetting getShowServer() { return showServer; }
    public BooleanSetting getShowBiome() { return showBiome; }
    public BooleanSetting getShowEntities() { return showEntities; }
    public BooleanSetting getShowPlayerState() { return showPlayerState; }
    public BooleanSetting getShowChunk() { return showChunk; }
    public BooleanSetting getShowModules() { return showModules; }

    public ModeSetting getPosition() { return position; }
    public NumberSetting getOffsetX() { return offsetX; }
    public NumberSetting getOffsetY() { return offsetY; }
    public NumberSetting getScale() { return scale; }
    public BooleanSetting getShowHeader() { return showHeader; }
    public BooleanSetting getShowBackground() { return showBackground; }
    public BooleanSetting getColorCoded() { return colorCoded; }
    public BooleanSetting getHideInF3() { return hideInF3; }
    public ColorSetting getAccentColor() { return accentColor; }
    public NumberSetting getBackgroundAlpha() { return backgroundAlpha; }

    private static Minecraft safeMinecraft() {
        try {
            return Minecraft.getMinecraft();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
