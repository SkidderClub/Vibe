package dev.vibe.debuginfo;

import dev.vibe.module.impl.client.DebugInfoModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Immutable telemetry snapshot for one frame of the Debug Information HUD.
 * Keeps formatting and color-threshold logic pure and independent of OpenGL.
 */
public final class DebugInfoSnapshot {

    public static final int COLOR_NEUTRAL = 0xFFF1F5F9;
    public static final int COLOR_MUTED = 0xFF94A3B8;
    public static final int COLOR_GOOD = 0xFF6EE7B7;
    public static final int COLOR_WARN = 0xFFFCD34D;
    public static final int COLOR_BAD = 0xFFF87171;
    public static final int COLOR_ACCENT = 0xFF7DD3FC;

    private static final String[] CARDINALS = {
        "S (+Z)", "SW (-X/+Z)", "W (-X)", "NW (-X/-Z)",
        "N (-Z)", "NE (+X/-Z)", "E (+X)", "SE (+X/+Z)"
    };

    public final boolean singleplayer;
    public final int pingMs;
    public final int pingJitterMs;
    public final double tps;
    public final int leftCps;
    public final int rightCps;
    public final double bps;
    public final int fps;
    public final double posX;
    public final double posY;
    public final double posZ;
    public final float yaw;
    public final float pitch;
    public final float timerSpeed;
    public final int txPackets;
    public final int rxPackets;
    public final long usedMemoryMb;
    public final long maxMemoryMb;
    public final String serverAddress;
    public final String biomeName;
    public final int loadedEntities;
    public final int loadedPlayers;
    public final boolean onGround;
    public final double motionY;
    public final int hurtTime;
    public final int chunkX;
    public final int chunkZ;
    public final int blockLight;
    public final int skyLight;
    public final int enabledModules;
    public final int totalModules;

    public DebugInfoSnapshot(
            boolean singleplayer,
            int pingMs,
            int pingJitterMs,
            double tps,
            int leftCps,
            int rightCps,
            double bps,
            int fps,
            double posX,
            double posY,
            double posZ,
            float yaw,
            float pitch,
            float timerSpeed,
            int txPackets,
            int rxPackets,
            long usedMemoryMb,
            long maxMemoryMb,
            String serverAddress,
            String biomeName,
            int loadedEntities,
            int loadedPlayers,
            boolean onGround,
            double motionY,
            int hurtTime,
            int chunkX,
            int chunkZ,
            int blockLight,
            int skyLight,
            int enabledModules,
            int totalModules) {
        this.singleplayer = singleplayer;
        this.pingMs = pingMs;
        this.pingJitterMs = pingJitterMs;
        this.tps = tps;
        this.leftCps = Math.max(0, leftCps);
        this.rightCps = Math.max(0, rightCps);
        this.bps = Double.isFinite(bps) ? Math.max(0.0D, bps) : 0.0D;
        this.fps = Math.max(0, fps);
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.timerSpeed = Float.isFinite(timerSpeed) ? timerSpeed : 1.0F;
        this.txPackets = Math.max(0, txPackets);
        this.rxPackets = Math.max(0, rxPackets);
        this.usedMemoryMb = Math.max(0L, usedMemoryMb);
        this.maxMemoryMb = Math.max(1L, maxMemoryMb);
        this.serverAddress = serverAddress == null ? "" : serverAddress;
        this.biomeName = biomeName == null ? "" : biomeName;
        this.loadedEntities = Math.max(0, loadedEntities);
        this.loadedPlayers = Math.max(0, loadedPlayers);
        this.onGround = onGround;
        this.motionY = Double.isFinite(motionY) ? motionY : 0.0D;
        this.hurtTime = Math.max(0, hurtTime);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blockLight = Math.max(0, Math.min(15, blockLight));
        this.skyLight = Math.max(0, Math.min(15, skyLight));
        this.enabledModules = Math.max(0, enabledModules);
        this.totalModules = Math.max(0, totalModules);
    }

    /** Builds the ordered list of enabled telemetry rows for display. */
    public List<DebugInfoEntry> buildEntries(DebugInfoModule module) {
        List<DebugInfoEntry> entries = new ArrayList<DebugInfoEntry>();
        if (module == null) {
            return entries;
        }
        boolean colored = module.getColorCoded().isEnabled();

        if (module.getShowPing().isEnabled()) {
            entries.add(new DebugInfoEntry("PING", formatPing(),
                    colored ? pingColor(singleplayer, pingMs) : COLOR_NEUTRAL));
        }
        if (module.getShowTps().isEnabled()) {
            entries.add(new DebugInfoEntry("TPS", formatTps(),
                    colored ? tpsColor(tps) : COLOR_NEUTRAL));
        }
        if (module.getShowCps().isEnabled()) {
            entries.add(new DebugInfoEntry("CPS", leftCps + " | " + rightCps, COLOR_NEUTRAL));
        }
        if (module.getShowBps().isEnabled()) {
            entries.add(new DebugInfoEntry("BPS", format("%.2f b/s", bps), COLOR_NEUTRAL));
        }
        if (module.getShowFps().isEnabled()) {
            entries.add(new DebugInfoEntry("FPS", fps + " fps",
                    colored ? fpsColor(fps) : COLOR_NEUTRAL));
        }
        if (module.getShowCoords().isEnabled()) {
            entries.add(new DebugInfoEntry("XYZ", format("%.1f, %.1f, %.1f", posX, posY, posZ), COLOR_NEUTRAL));
        }
        if (module.getShowFacing().isEnabled()) {
            float wrappedYaw = wrapDegrees(yaw);
            entries.add(new DebugInfoEntry("FACING",
                    cardinalDirection(wrappedYaw) + format(" (%.1f / %.1f)", wrappedYaw, pitch),
                    COLOR_NEUTRAL));
        }
        if (module.getShowTimer().isEnabled()) {
            entries.add(new DebugInfoEntry("TIMER", format("%.2fx", timerSpeed),
                    colored ? timerColor(timerSpeed) : COLOR_NEUTRAL));
        }
        if (module.getShowPackets().isEnabled()) {
            entries.add(new DebugInfoEntry("PACKETS",
                    txPackets + " TX / " + rxPackets + " RX", COLOR_NEUTRAL));
        }
        if (module.getShowMemory().isEnabled()) {
            int percent = (int) Math.min(100L, Math.round(usedMemoryMb * 100.0D / maxMemoryMb));
            entries.add(new DebugInfoEntry("RAM",
                    usedMemoryMb + " / " + maxMemoryMb + " MB (" + percent + "%)",
                    colored ? memoryColor(percent) : COLOR_NEUTRAL));
        }
        if (module.getShowServer().isEnabled()) {
            String host = singleplayer ? "Singleplayer"
                    : (serverAddress.isEmpty() ? "Unknown" : serverAddress);
            entries.add(new DebugInfoEntry("SERVER", host, COLOR_NEUTRAL));
        }
        if (module.getShowBiome().isEnabled()) {
            entries.add(new DebugInfoEntry("BIOME", biomeName.isEmpty() ? "--" : biomeName, COLOR_NEUTRAL));
        }
        if (module.getShowEntities().isEnabled()) {
            entries.add(new DebugInfoEntry("ENTITIES",
                    loadedEntities + " (" + loadedPlayers + " players)", COLOR_NEUTRAL));
        }
        if (module.getShowPlayerState().isEnabled()) {
            String state = (onGround ? "Ground" : "Air")
                    + format(" | Y %+.2f", motionY)
                    + (hurtTime > 0 ? " | Hurt " + hurtTime : "");
            int color = colored && hurtTime > 0 ? COLOR_BAD : COLOR_NEUTRAL;
            entries.add(new DebugInfoEntry("STATE", state, color));
        }
        if (module.getShowChunk().isEnabled()) {
            entries.add(new DebugInfoEntry("CHUNK",
                    chunkX + ", " + chunkZ + " | Light " + skyLight + "/" + blockLight, COLOR_NEUTRAL));
        }
        if (module.getShowModules().isEnabled()) {
            entries.add(new DebugInfoEntry("MODULES", enabledModules + " / " + totalModules, COLOR_NEUTRAL));
        }
        return entries;
    }

    private String formatPing() {
        if (singleplayer) {
            return "Local";
        }
        if (pingMs < 0) {
            return "--";
        }
        if (pingJitterMs > 0) {
            return pingMs + " ms (±" + pingJitterMs + ")";
        }
        return pingMs + " ms";
    }

    private String formatTps() {
        if (Double.isNaN(tps) || Double.isInfinite(tps)) {
            return "--";
        }
        return format("%.1f", Math.max(0.0D, Math.min(20.0D, tps)));
    }

    public static int pingColor(boolean singleplayer, int pingMs) {
        if (singleplayer) {
            return COLOR_GOOD;
        }
        if (pingMs < 0) {
            return COLOR_MUTED;
        }
        if (pingMs <= 80) {
            return COLOR_GOOD;
        }
        if (pingMs <= 160) {
            return COLOR_WARN;
        }
        return COLOR_BAD;
    }

    public static int tpsColor(double tps) {
        if (Double.isNaN(tps) || Double.isInfinite(tps)) {
            return COLOR_MUTED;
        }
        if (tps >= 18.0D) {
            return COLOR_GOOD;
        }
        if (tps >= 14.0D) {
            return COLOR_WARN;
        }
        return COLOR_BAD;
    }

    public static int fpsColor(int fps) {
        if (fps >= 60) {
            return COLOR_GOOD;
        }
        if (fps >= 30) {
            return COLOR_WARN;
        }
        return COLOR_BAD;
    }

    public static int memoryColor(int percent) {
        if (percent < 70) {
            return COLOR_GOOD;
        }
        if (percent < 85) {
            return COLOR_WARN;
        }
        return COLOR_BAD;
    }

    public static int timerColor(float timerSpeed) {
        if (Math.abs(timerSpeed - 1.0F) < 0.01F) {
            return COLOR_NEUTRAL;
        }
        return timerSpeed > 1.0F ? COLOR_ACCENT : COLOR_WARN;
    }

    public static String cardinalDirection(float yaw) {
        float normalized = ((yaw % 360.0F) + 360.0F) % 360.0F;
        int index = ((int) Math.floor((normalized + 22.5F) / 45.0F)) & 7;
        return CARDINALS[index];
    }

    public static float wrapDegrees(float value) {
        float wrapped = value % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    private static String format(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }
}
