package dev.vibe.module.impl.movement;

import dev.vibe.Vibe;
import dev.vibe.camera.DroneCollisions;
import dev.vibe.camera.FpvFlight;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.achievement.GuiAchievement;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovementInput;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;

/** FPV flight of a render camera, with no fake entity or player-state transport. */
public final class FreecamModule extends Module {
    private final ModeSetting flightMode = addSetting(new ModeSetting("Flight Mode", "Acro", "Acro", "Angle"));
    private final NumberSetting rates = addSetting(new NumberSetting("Turn Rate", 120, 30, 360, 5));
    private final NumberSetting maxTilt = addSetting(new NumberSetting("Max Tilt", 45, 10, 75, 1,
            () -> flightMode.is("Angle")));
    private final NumberSetting thrust = addSetting(new NumberSetting("Thrust", 20, 12, 40, 1));
    private final NumberSetting drag = addSetting(new NumberSetting("Air Drag", 0.35, 0.05, 2, 0.05));
    private final BooleanSetting collisions = addSetting(new BooleanSetting("Collisions", false));
    private final NumberSetting cameraTilt = addSetting(new NumberSetting("Camera Tilt", 15, 0, 50, 1));
    private final NumberSetting fov = addSetting(new NumberSetting("Drone FOV", 105, 60, 130, 1));
    private final BooleanSetting droneHud = addSetting(new BooleanSetting("Drone HUD", true));
    private final BooleanSetting hideHud = addSetting(new BooleanSetting("Hide HUD", true));
    private final FpvFlight flight = new FpvFlight();
    private final FloatBuffer viewMatrix = BufferUtils.createFloatBuffer(16);
    private WorldClient cameraWorld;
    private EntityPlayerSP cameraPlayer;
    private DroneCollisions cameraCollisions;
    private long lastFrame;
    private double framePartialTicks;

    public FreecamModule() {
        super("Freecam", "Fly the third-person camera as an FPV drone; WASD pitch/roll, mouse steer, Space/Shift thrust",
                Category.MOVEMENT, Keyboard.KEY_NONE);
    }

    @Override protected void onEnable() { tick(); }
    @Override protected void onDisable() { clearCamera(); }

    /** Only establishes/resets private camera state when a world/player changes. */
    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.isDead) {
            clearCamera();
            return;
        }
        if (cameraWorld == mc.theWorld && cameraPlayer == mc.thePlayer) return;
        cameraWorld = mc.theWorld;
        cameraPlayer = mc.thePlayer;
        cameraCollisions = new DroneCollisions(cameraWorld);
        double yaw = Math.toRadians(cameraPlayer.rotationYaw);
        // Start just behind the body, like a detached third-person camera.
        flight.reset(cameraPlayer.posX + Math.sin(yaw) * 2.5,
                cameraPlayer.posY + cameraPlayer.getEyeHeight() + 0.5,
                cameraPlayer.posZ - Math.cos(yaw) * 2.5, cameraPlayer.rotationYaw);
        lastFrame = System.nanoTime();
    }

    public void frameTick(float partialTicks) {
        tick();
        framePartialTicks = partialTicks;
        if (!hasCamera()) return;
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        double dt = Math.min(0.05, Math.max(0, (now - lastFrame) * 1.0e-9));
        lastFrame = now;
        if (mc.currentScreen != null || !mc.inGameHasFocus || mc.isGamePaused()) return;
        flight.step(dt, key(mc.gameSettings.keyBindForward) - key(mc.gameSettings.keyBindBack),
                key(mc.gameSettings.keyBindRight) - key(mc.gameSettings.keyBindLeft),
                key(mc.gameSettings.keyBindJump) - key(mc.gameSettings.keyBindSneak),
                flightMode.is("Angle"), rates.getDouble(), maxTilt.getDouble(), thrust.getDouble(), drag.getDouble(),
                collisions.isEnabled() ? cameraCollisions : null);
    }

    private void clearCamera() {
        cameraWorld = null;
        cameraPlayer = null;
        cameraCollisions = null;
        lastFrame = 0;
    }

    public boolean hasCamera() {
        Minecraft mc = Minecraft.getMinecraft();
        return isEnabled() && cameraPlayer != null && cameraPlayer == mc.thePlayer
                && cameraWorld == mc.theWorld && !cameraPlayer.isDead
                && mc.getRenderViewEntity() == cameraPlayer;
    }

    private static FreecamModule active() {
        Vibe vibe = Vibe.getInstance();
        FreecamModule module = vibe == null || vibe.getModuleManager() == null ? null
                : vibe.getModuleManager().getModule(FreecamModule.class);
        return module != null && module.hasCamera() ? module : null;
    }

    /** Replaces orientCamera only. Everything else keeps the real player as its view entity. */
    public static boolean orientCameraHook(float partialTicks) {
        FreecamModule module = active();
        if (module == null) return false;
        module.framePartialTicks = partialTicks;
        module.viewMatrix.clear();
        module.viewMatrix.put(module.flight.viewMatrix(module.cameraTilt.getDouble())).flip();
        GlStateManager.multMatrix(module.viewMatrix);
        Entity player = module.cameraPlayer;
        // World meshes/entities remain relative to vanilla's player origin.
        // The offset lives exclusively in the model-view matrix.
        GlStateManager.translate(interpolate(player.lastTickPosX, player.posX, partialTicks) - module.flight.x,
                interpolate(player.lastTickPosY, player.posY, partialTicks) - module.flight.y,
                interpolate(player.lastTickPosZ, player.posZ, partialTicks) - module.flight.z);
        return true;
    }

    /** Called in the renderer's mouse path before vanilla can write player yaw/pitch. */
    public static void mouseLookHook(Object entity, float yaw, float pitch) {
        FreecamModule module = active();
        if (module != null && entity == module.cameraPlayer) {
            module.flight.look(yaw * 0.15, -pitch * 0.15, module.flightMode.is("Angle"));
        } else {
            ((Entity) entity).setAngles(yaw, pitch);
        }
    }

    /** Divert control input only; normal player ticks, gravity and knockback remain active. */
    public static void movementInputHook(Object entity) {
        FreecamModule module = active();
        if (module == null || entity != module.cameraPlayer) return;
        MovementInput input = module.cameraPlayer.movementInput;
        input.moveForward = input.moveStrafe = 0;
        input.jump = input.sneak = false;
    }

    public static boolean cameraActiveHook() { return active() != null; }

    public static boolean hideHudHook() {
        FreecamModule module = active();
        return module != null && module.hideHud.isEnabled();
    }

    /** Achievement toasts are rendered after GuiIngame, outside overlay events. */
    public static void achievementHudHook(Object overlay) {
        if (!hideHudHook()) ((GuiAchievement) overlay).updateAchievementWindow();
    }

    /** The sky is camera-centred, unlike world meshes rendered relative to the player. */
    public static void renderSkyHook(Object renderer, float partialTicks, int pass) {
        FreecamModule module = active();
        if (module == null) {
            ((RenderGlobal) renderer).renderSky(partialTicks, pass);
            return;
        }
        GlStateManager.pushMatrix();
        try {
            Entity player = module.cameraPlayer;
            GlStateManager.translate(module.flight.x - interpolate(player.lastTickPosX, player.posX, partialTicks),
                    module.flight.y - interpolate(player.lastTickPosY, player.posY, partialTicks),
                    module.flight.z - interpolate(player.lastTickPosZ, player.posZ, partialTicks));
            ((RenderGlobal) renderer).renderSky(partialTicks, pass);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    /** Render-only equivalent of third-person mode, so the real body is drawn. */
    public static int thirdPersonHook(int vanilla) { return active() == null ? vanilla : 1; }

    public static float fovHook(float vanilla) {
        FreecamModule module = active();
        return module == null ? vanilla : module.fov.getFloat();
    }

    public static float yawHook(Object entity, float vanilla) {
        FreecamModule module = active();
        return module == null || entity != module.cameraPlayer ? vanilla
                : (float) module.flight.cameraYaw(module.cameraTilt.getDouble());
    }

    public static float pitchHook(Object entity, float vanilla) {
        FreecamModule module = active();
        return module == null || entity != module.cameraPlayer ? vanilla
                : (float) module.flight.cameraPitch(module.cameraTilt.getDouble());
    }

    /** Values used only by terrain visibility/chunk selection, never by entity updates. */
    public static double terrainXHook(Object entity, double vanilla) {
        FreecamModule module = active();
        return module == null || entity != module.cameraPlayer ? vanilla : module.flight.x;
    }

    public static double terrainYHook(Object entity, double vanilla) {
        FreecamModule module = active();
        return module == null || entity != module.cameraPlayer ? vanilla
                : module.flight.y - module.cameraPlayer.getEyeHeight();
    }

    public static double terrainZHook(Object entity, double vanilla) {
        FreecamModule module = active();
        return module == null || entity != module.cameraPlayer ? vanilla : module.flight.z;
    }

    public static int terrainChunkXHook(Object entity, int vanilla) {
        return (int) Math.floor(terrainXHook(entity, vanilla * 16.0) / 16.0);
    }

    public static int terrainChunkYHook(Object entity, int vanilla) {
        return (int) Math.floor(terrainYHook(entity, vanilla * 16.0) / 16.0);
    }

    public static int terrainChunkZHook(Object entity, int vanilla) {
        return (int) Math.floor(terrainZHook(entity, vanilla * 16.0) / 16.0);
    }

    /** Terrain selection follows the drone, but mesh translation retains vanilla's render origin. */
    public static void terrainOriginHook(Object container, double x, double y, double z) {
        FreecamModule module = active();
        if (module != null) {
            Entity player = module.cameraPlayer;
            double partial = module.framePartialTicks;
            x = interpolate(player.lastTickPosX, player.posX, partial);
            y = interpolate(player.lastTickPosY, player.posY, partial);
            z = interpolate(player.lastTickPosZ, player.posZ, partial);
        }
        ((ChunkRenderContainer) container).initialize(x, y, z);
    }

    private static double interpolate(double previous, double current, double partial) {
        return previous + (current - previous) * partial;
    }

    private static int key(net.minecraft.client.settings.KeyBinding binding) { return binding.isKeyDown() ? 1 : 0; }
    public boolean shouldDrawDroneHud() { return hasCamera() && droneHud.isEnabled(); }
    public FpvFlight getFlight() { return flight; }
    public ModeSetting getFlightMode() { return flightMode; }
    public double getCameraTilt() { return cameraTilt.getDouble(); }
    public double getDroneFov() { return fov.getDouble(); }
    public boolean hasCollisions() { return collisions.isEnabled(); }
}
