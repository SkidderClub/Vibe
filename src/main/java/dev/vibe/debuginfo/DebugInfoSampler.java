package dev.vibe.debuginfo;

import dev.vibe.Vibe;
import dev.vibe.combat.CombatTimerAccess;
import dev.vibe.hud.ServerTickRate;
import dev.vibe.input.ClickStats;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.biome.BiomeGenBase;

/** Collects tick-aligned movement/ping state and builds live {@link DebugInfoSnapshot}s. */
public final class DebugInfoSampler {

    private static final long PING_SAMPLE_INTERVAL_MS = 500L;

    private WorldClient lastWorld;
    private EntityPlayerSP lastPlayer;
    private double lastX;
    private double lastZ;
    private boolean hasPositionSample;
    private double currentBps;

    private int lastPing = -1;
    private int currentJitter;
    private long nextPingSampleMs;

    public void reset() {
        lastWorld = null;
        lastPlayer = null;
        hasPositionSample = false;
        currentBps = 0.0D;
        lastPing = -1;
        currentJitter = 0;
        nextPingSampleMs = 0L;
    }

    /** Updates tick-rate telemetry (BPS and ping jitter) once per client tick. */
    public void tick(Minecraft mc) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            reset();
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        WorldClient world = mc.theWorld;
        float timerSpeed = CombatTimerAccess.getSpeed();

        if (!hasPositionSample || world != lastWorld || player != lastPlayer) {
            lastWorld = world;
            lastPlayer = player;
            lastX = player.posX;
            lastZ = player.posZ;
            hasPositionSample = true;
            currentBps = 0.0D;
        } else {
            currentBps = computeHorizontalBps(lastX, lastZ, player.posX, player.posZ, timerSpeed);
            lastX = player.posX;
            lastZ = player.posZ;
        }

        long nowMs = System.nanoTime() / 1_000_000L;
        if (nowMs >= nextPingSampleMs) {
            nextPingSampleMs = nowMs + PING_SAMPLE_INTERVAL_MS;
            int ping = readPing(mc);
            if (ping >= 0) {
                if (lastPing >= 0) {
                    currentJitter = Math.abs(ping - lastPing);
                }
                lastPing = ping;
            } else {
                lastPing = -1;
                currentJitter = 0;
            }
        }
    }

    public DebugInfoSnapshot capture(Minecraft mc) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            return null;
        }
        EntityPlayerSP player = mc.thePlayer;
        WorldClient world = mc.theWorld;
        boolean singleplayer = mc.isSingleplayer();
        long nowMs = System.nanoTime() / 1_000_000L;

        NetHandlerPlayClient netHandler = mc.getNetHandler();
        Object channel = (netHandler == null || netHandler.getNetworkManager() == null)
                ? null : netHandler.getNetworkManager().channel();

        int ping = singleplayer ? 0 : readPing(mc);
        double tps = singleplayer ? 20.0D : ServerTickRate.value(channel, nowMs);
        int leftCps = ClickStats.leftCps();
        int rightCps = ClickStats.rightCps();
        int fps = Minecraft.getDebugFPS();
        float timerSpeed = CombatTimerAccess.getSpeed();
        int txPackets = PacketTrafficTracker.outboundPerSecond(channel, nowMs);
        int rxPackets = PacketTrafficTracker.inboundPerSecond(channel, nowMs);

        Runtime runtime = Runtime.getRuntime();
        long usedMb = Math.max(0L, (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L));
        long maxMb = Math.max(1L, runtime.maxMemory() / (1024L * 1024L));

        ServerData serverData = mc.getCurrentServerData();
        String serverAddress = singleplayer ? "Singleplayer"
                : (serverData != null && serverData.serverIP != null ? serverData.serverIP : "");

        BlockPos pos = new BlockPos(
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.posY),
                MathHelper.floor_double(player.posZ));
        String biomeName = "";
        int blockLight = 0;
        int skyLight = 0;
        if (world.isBlockLoaded(pos)) {
            BiomeGenBase biome = world.getBiomeGenForCoords(pos);
            if (biome != null && biome.biomeName != null) {
                biomeName = biome.biomeName;
            }
            blockLight = world.getLightFor(EnumSkyBlock.BLOCK, pos);
            skyLight = world.getLightFor(EnumSkyBlock.SKY, pos);
        }

        int loadedEntities = world.loadedEntityList == null ? 0 : world.loadedEntityList.size();
        int loadedPlayers = world.playerEntities == null ? 0 : world.playerEntities.size();

        int enabledModules = 0;
        int totalModules = 0;
        Vibe vibe = Vibe.getInstance();
        if (vibe != null && vibe.getModuleManager() != null) {
            ModuleManager manager = vibe.getModuleManager();
            totalModules = manager.getModules().size();
            for (Module candidate : manager.getModules()) {
                if (candidate.isEnabled()) {
                    enabledModules++;
                }
            }
        }

        return new DebugInfoSnapshot(
                singleplayer,
                ping,
                singleplayer ? 0 : currentJitter,
                tps,
                leftCps,
                rightCps,
                currentBps,
                fps,
                player.posX,
                player.posY,
                player.posZ,
                player.rotationYaw,
                player.rotationPitch,
                timerSpeed,
                txPackets,
                rxPackets,
                usedMb,
                maxMb,
                serverAddress,
                biomeName,
                loadedEntities,
                loadedPlayers,
                player.onGround,
                player.motionY,
                player.hurtTime,
                pos.getX() >> 4,
                pos.getZ() >> 4,
                blockLight,
                skyLight,
                enabledModules,
                totalModules);
    }

    /** Horizontal blocks-per-second calculation for one client tick. */
    public static double computeHorizontalBps(double prevX, double prevZ, double x, double z, float timerSpeed) {
        double safeTimer = Float.isFinite(timerSpeed) && timerSpeed > 0.0F ? timerSpeed : 1.0F;
        double distance = Math.hypot(x - prevX, z - prevZ);
        return Double.isFinite(distance) ? distance * 20.0D * safeTimer : 0.0D;
    }

    private static int readPing(Minecraft mc) {
        if (mc.getNetHandler() == null || mc.thePlayer == null) {
            return -1;
        }
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (info == null) {
            return -1;
        }
        return info.getResponseTime();
    }
}
