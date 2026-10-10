package dev.vibe.camera;

import dev.vibe.Vibe;
import dev.vibe.event.ClientEvents;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.movement.FreecamModule;
import dev.vibe.module.impl.visual.EspModule;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.Setting;
import dev.vibe.ui.render.FreecamHudRenderer;
import dev.vibe.ui.render.esp.EspRenderer;
import dev.vibe.ui.render.esp.ChamsRenderer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Camera controls must never move/rotate the networked entity or change its physics state. */
public class FreecamModuleTest {
    private Minecraft previousMinecraft, mc;
    private Vibe previousVibe;
    private Player player;
    private FreecamModule module;

    @Before public void setup() throws Exception {
        previousMinecraft = Minecraft.getMinecraft(); previousVibe = Vibe.getInstance();
        mc = allocate(Minecraft.class); field(Minecraft.class, "theMinecraft").set(null, mc);
        mc.gameSettings = allocate(GameSettings.class);
        mc.theWorld = allocate(WorldClient.class);
        player = allocate(Player.class); mc.thePlayer = player; player.worldObj = mc.theWorld;
        field(EntityPlayerSP.class, "mc").set(player, mc);
        player.posX = player.prevPosX = player.lastTickPosX = 10;
        player.posY = player.prevPosY = player.lastTickPosY = 64;
        player.posZ = player.prevPosZ = player.lastTickPosZ = 20;
        player.rotationYaw = 35; player.rotationPitch = 12;
        player.motionX = .7; player.motionY = .5; player.motionZ = -.6;
        player.onGround = false;
        player.capabilities = new PlayerCapabilities();
        player.movementInput = new MovementInput();
        field(Minecraft.class, "renderViewEntity").set(mc, player);
        Vibe vibe = allocate(Vibe.class); field(Vibe.class, "instance").set(null, vibe);
        ModuleManager manager = allocate(ModuleManager.class);
        module = new FreecamModule();
        field(ModuleManager.class, "modules").set(manager, new ArrayList<Module>(Collections.<Module>singletonList(module)));
        field(Vibe.class, "moduleManager").set(vibe, manager);
        module.setEnabled(true);
    }

    @After public void cleanup() throws Exception {
        field(Minecraft.class, "theMinecraft").set(null, previousMinecraft);
        field(Vibe.class, "instance").set(null, previousVibe);
    }

    @Test public void cameraControlsPreservePlayerViewEntityRotationVelocityAndPacketsEligibility() throws Exception {
        double[] original = state();
        FreecamModule.mouseLookHook(player, 300, 100);
        module.getFlight().step(.05, 1, 1, 1, false, 120, 45, 20, .35);
        player.movementInput.moveForward = 1; player.movementInput.moveStrafe = 1;
        player.movementInput.jump = player.movementInput.sneak = true;
        FreecamModule.movementInputHook(player);
        assertArrayEquals(original, state(), 0);
        assertEquals(0, player.movementInput.moveForward, 0);
        assertFalse(player.movementInput.jump);
        assertSame(player, mc.getRenderViewEntity());
        Method currentView = EntityPlayerSP.class.getDeclaredMethod("isCurrentViewEntity"); currentView.setAccessible(true);
        assertTrue("Vanilla still sends normal movement/rotation packets", (Boolean) currentView.invoke(player));
        assertEquals("Only rendering sees third-person mode", 0, mc.gameSettings.thirdPersonView);
        assertEquals(1, FreecamModule.thirdPersonHook(0));
        assertFalse(player.capabilities.isFlying);
    }

    @Test public void knockbackAndServerCorrectionsDoNotMoveTheDroneOrGetRestoredAway() {
        double cameraX = module.getFlight().x, cameraY = module.getFlight().y;
        player.motionX = -2; player.motionY = 1; player.posX += 3; player.posY += 1;
        player.rotationYaw = 80; player.rotationPitch = -15;
        module.tick(); module.frameTick(.5F);
        assertEquals(-2, player.motionX, 0);
        assertEquals(1, player.motionY, 0);
        assertEquals(13, player.posX, 0);
        assertEquals(80, player.rotationYaw, 0);
        assertEquals(-15, player.rotationPitch, 0);
        assertEquals(cameraX, module.getFlight().x, 0);
        assertEquals(cameraY, module.getFlight().y, 0);
    }

    @Test public void terrainUsesCameraForVisibilityAndPlayerForMeshOrigin() throws Exception {
        module.getFlight().x = 100; module.getFlight().y = 120; module.getFlight().z = -200;
        module.frameTick(.5F);
        assertEquals(100, FreecamModule.terrainXHook(player, player.posX), 0);
        assertEquals(120D - 1.62F, FreecamModule.terrainYHook(player, player.posY), 0);
        assertEquals(-13, FreecamModule.terrainChunkZHook(player, 1));
        Container container = allocate(Container.class);
        FreecamModule.terrainOriginHook(container, 100, 120, -200);
        assertArrayEquals(new double[] {10, 64, 20}, container.origin, 0);
        assertEquals(10, player.posX, 0);
    }

    @Test public void disablingRestoresVanillaControlsAndWorldChangesDiscardOldCamera() {
        double[] original = state();
        module.setEnabled(false);
        assertFalse(FreecamModule.cameraActiveHook());
        assertSame(player, mc.getRenderViewEntity());
        assertArrayEquals(original, state(), 0);
        FreecamModule.mouseLookHook(player, 10, 0);
        assertEquals(36.5, player.rotationYaw, .001);
        player.movementInput.moveForward = 1;
        FreecamModule.movementInputHook(player);
        assertEquals(1, player.movementInput.moveForward, 0);
        module.setEnabled(true);
        mc.theWorld = null; module.tick();
        assertFalse(module.hasCamera());
    }

    @Test public void collisionsToggleAffectsOnlyTheCameraAndCanBeSwitchedDuringFlight() throws Exception {
        DroneCollisionsTest.CollisionWorld world = DroneCollisionsTest.world();
        world.block(2, 64, 0, Blocks.stone.getDefaultState());
        mc.theWorld = world; player.worldObj = world;
        mc.gameSettings = new GameSettings(); mc.inGameHasFocus = true;
        module.tick();
        BooleanSetting collisions = null;
        for (Setting<?> setting : module.getSettings()) if (setting.getRawName().equals("Collisions"))
            collisions = (BooleanSetting) setting;
        assertNotNull("Collisions is exposed in the module GUI/config", collisions);
        assertFalse("Existing fly-through behavior is the default", collisions.isEnabled());
        double[] original = state();
        module.getFlight().reset(.5, 64.5, .5, 0); module.getFlight().velocityX = 100;
        field(FreecamModule.class, "lastFrame").setLong(module, System.nanoTime() - 1_000_000_000L);
        module.frameTick(.5F);
        assertTrue(module.getFlight().x > 3);

        collisions.setEnabled(true);
        module.getFlight().reset(.5, 64.5, .5, 0); module.getFlight().velocityX = 100;
        field(FreecamModule.class, "lastFrame").setLong(module, System.nanoTime() - 1_000_000_000L);
        module.frameTick(.5F);
        assertTrue("Swept impacts cannot tunnel through the wall", module.getFlight().x <= 1.8);
        assertTrue("Hard impacts rebound, rather than glueing the camera to a wall", module.getFlight().velocityX < 0);
        assertTrue(module.getFlight().isCrashing());
        assertArrayEquals("Block impacts never reach the player", original, state(), 0);
        assertSame(player, mc.getRenderViewEntity());

        collisions.setEnabled(false); module.getFlight().velocityX = 100;
        field(FreecamModule.class, "lastFrame").setLong(module, System.nanoTime() - 1_000_000_000L);
        module.frameTick(.5F);
        assertTrue("Turning the toggle off restores fly-through immediately", module.getFlight().x > 3);
        assertArrayEquals(original, state(), 0);
    }

    @Test public void hideHudDefaultsOnAndIsIndependentOfTheDroneOsd() throws Exception {
        BooleanSetting hide = booleanSetting("Hide HUD"), osd = booleanSetting("Drone HUD");
        assertTrue(hide.isEnabled()); assertTrue(osd.isEnabled());
        assertTrue(FreecamModule.hideHudHook()); assertTrue(module.shouldDrawDroneHud());
        double[] original = state();
        osd.setEnabled(false);
        assertTrue("Hide HUD can also provide a completely clean camera view", FreecamModule.hideHudHook());
        assertFalse(module.shouldDrawDroneHud());
        hide.setEnabled(false);
        assertFalse("Vanilla/Vibe HUD returns immediately when the toggle is off", FreecamModule.hideHudHook());
        assertFalse("The other setting remains untouched", osd.isEnabled());
        hide.setEnabled(true); osd.setEnabled(true); module.setEnabled(false);
        assertFalse(FreecamModule.hideHudHook());
        assertArrayEquals(original, state(), 0);
    }

    @Test public void hiddenHudCancelsTheWholeVanillaPassAndSkipsClientWidgets() throws Exception {
        booleanSetting("Drone HUD").setEnabled(false); // No GL is needed for this routing test.
        ClientEvents events = allocate(ClientEvents.class);
        field(ClientEvents.class, "minecraft").set(events, mc);
        field(ClientEvents.class, "freecamHudRenderer").set(events, new FreecamHudRenderer());
        field(ClientEvents.class, "espRenderer").set(events, new EspRenderer());
        RenderGameOverlayEvent base = new RenderGameOverlayEvent(.5F, null);
        RenderGameOverlayEvent.Pre pre = new OverlayPre(base);
        events.onPreAll(pre);
        assertTrue("ALL is cancelled before hotbar, health, chat, scoreboard or graphs", pre.isCanceled());
        // All other renderers/HudManager are deliberately null: no hidden widget may be reached.
        events.onHud(new RenderGameOverlayEvent.Post(base, RenderGameOverlayEvent.ElementType.TEXT));
        events.onHud(new RenderGameOverlayEvent.Post(base, RenderGameOverlayEvent.ElementType.ALL));
        events.onHotbarPost(new RenderGameOverlayEvent.Post(base, RenderGameOverlayEvent.ElementType.HOTBAR));
        assertTrue(FreecamModule.hideHudHook());
    }

    @Test public void achievementToastsRespectHideHudAndResumeNormally() throws Exception {
        AchievementHud overlay = allocate(AchievementHud.class);
        FreecamModule.achievementHudHook(overlay);
        assertEquals(0, overlay.draws);
        booleanSetting("Hide HUD").setEnabled(false);
        FreecamModule.achievementHudHook(overlay);
        assertEquals(1, overlay.draws);
        booleanSetting("Hide HUD").setEnabled(true); module.setEnabled(false);
        FreecamModule.achievementHudHook(overlay);
        assertEquals(2, overlay.draws);
    }

    @Test public void localPlayerChamsUsesRenderOnlyThirdPersonState() throws Exception {
        EspModule esp = new EspModule(); esp.setEnabled(true);
        esp.getModes().setValue(new java.util.LinkedHashSet<String>(Collections.singletonList("Chams")));
        ModuleManager manager = Vibe.getInstance().getModuleManager();
        field(ModuleManager.class, "modules").set(manager,
                new ArrayList<Module>(java.util.Arrays.<Module>asList(module, esp)));
        ChamsRenderer.beginWorld();
        try {
            assertTrue("Freecam shows Chams on the real body, even with Hide HUD on", ChamsRenderer.appliesTo(player));
            assertEquals("No actual perspective setting is changed", 0, mc.gameSettings.thirdPersonView);
            module.setEnabled(false);
            assertFalse("First-person filtering returns after Freecam is disabled", ChamsRenderer.appliesTo(player));
            mc.gameSettings.thirdPersonView = 1;
            assertTrue("Normal third-person Chams is unchanged", ChamsRenderer.appliesTo(player));
        } finally { ChamsRenderer.endWorld(); }
    }

    private BooleanSetting booleanSetting(String name) {
        for (Setting<?> setting : module.getSettings()) if (setting.getRawName().equals(name)) return (BooleanSetting) setting;
        throw new AssertionError("Missing setting " + name);
    }

    private double[] state() {
        return new double[] {player.posX, player.posY, player.posZ, player.motionX, player.motionY, player.motionZ,
                player.rotationYaw, player.rotationPitch, player.prevRotationYaw, player.prevRotationPitch,
                player.rotationYawHead, player.renderYawOffset, player.onGround ? 1 : 0, player.noClip ? 1 : 0};
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field(unsafe, "theUnsafe").get(null), type));
    }
    public static class Player extends EntityPlayerSP {
        private Player() { super(null, null, null, null); }
        @Override public float getEyeHeight() { return 1.62F; }
        @Override public boolean isEntityAlive() { return !isDead; }
    }
    /** Forge normally installs this behavior with EventSubclassTransformer at launch. */
    public static class OverlayPre extends RenderGameOverlayEvent.Pre {
        public OverlayPre(RenderGameOverlayEvent base) { super(base, RenderGameOverlayEvent.ElementType.ALL); }
        @Override public boolean isCancelable() { return true; }
    }
    public static class AchievementHud extends net.minecraft.client.gui.achievement.GuiAchievement {
        int draws;
        private AchievementHud() { super(null); }
        @Override public void updateAchievementWindow() { draws++; }
    }
    public static class Container extends ChunkRenderContainer {
        double[] origin;
        @Override public void initialize(double x, double y, double z) { origin = new double[] {x, y, z}; }
        @Override public void renderChunkLayer(net.minecraft.util.EnumWorldBlockLayer layer) { }
    }
}
