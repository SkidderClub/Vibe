package dev.vibe.combat;

import dev.vibe.aiaura.MouseMath;
import dev.vibe.module.impl.movement.MoveFixModule;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the real render scope and interpolation endpoints without a GL window. */
public class MoveFixRenderTest {
    @org.junit.BeforeClass public static void bootstrap()throws Exception{dev.vibe.camera.DroneCollisionsTest.registerBlocks();}
    private Minecraft minecraft;
    private Minecraft previousMinecraft;
    private EntityPlayerSP player;
    private MoveFixModule moveFix;
    private Field singleton;

    @Before public void setUp() throws Exception {
        singleton = Minecraft.class.getDeclaredField("theMinecraft");
        singleton.setAccessible(true);
        previousMinecraft = (Minecraft) singleton.get(null);
        minecraft = allocate(Minecraft.class);
        minecraft.gameSettings = allocate(GameSettings.class);
        minecraft.gameSettings.thirdPersonView = 1;
        player = allocate(EntityPlayerSP.class);
        minecraft.thePlayer = player;
        singleton.set(null, minecraft);
        moveFix = new MoveFixModule();
    }

    @After public void tearDown() throws Exception {
        if (singleton != null) singleton.set(null, previousMinecraft);
    }

    @Test public void interpolatesEveryFrameAndRestoresAllCameraAndModelFields() throws Exception {
        player.rotationYaw = 10;
        player.prevRotationYaw = 8;
        player.rotationPitch = 6;
        player.prevRotationPitch = 4;
        player.rotationYawHead = 12;
        player.prevRotationYawHead = 9;
        player.renderYawOffset = 5;
        player.prevRenderYawOffset = 3;
        float[] camera = pose();
        moveFix.beginRotationTick();
        moveFix.setFakeRotation("aura", 80, 40);
        moveFix.beginPlayerRender(player);
        assertEquals(44.5F, headAt(0.5F), 0.001F);
        assertEquals(22.0F, pitchAt(0.5F), 0.001F);
        moveFix.endPlayerRender(player);
        assertArrayEquals(camera, pose(), 0.0F);

        moveFix.beginRotationTick();
        moveFix.setFakeRotation("test", 100, 45);
        moveFix.setFakeRotation("aura", 120, 50);
        // Multiple producers and multiple renders in a tick retain the same
        // previous tick, so neither causes a jump to the newest endpoint.
        for (int perspective = 1; perspective <= 2; perspective++) {
            minecraft.gameSettings.thirdPersonView = perspective;
            for (float partial : new float[] {0, 0.25F, 0.5F, 0.75F, 1}) {
                moveFix.beginPlayerRender(player);
                assertEquals(80 + 40 * partial, headAt(partial), 0.001F);
                assertEquals(headAt(partial), bodyAt(partial), 0.001F);
                assertEquals(40 + 10 * partial, pitchAt(partial), 0.001F);
                moveFix.endPlayerRender(player);
                assertArrayEquals(camera, pose(), 0.0F);
            }
        }
    }

    @Test public void wrapBoundaryIsSmoothAndPacketsKeepTheFullTickRotation() throws Exception {
        moveFix.beginRotationTick();
        moveFix.setFakeRotation("aura", 179, 20);
        moveFix.beginRotationTick();
        moveFix.setFakeRotation("aura", -179, 30);
        moveFix.beginPlayerRender(player);
        assertEquals(180.0F, headAt(0.5F), 0.001F);
        assertEquals(25.0F, pitchAt(0.5F), 0.001F);
        moveFix.endPlayerRender(player);

        moveFix.beginPacketRotation(player);
        assertEquals(181.0F, player.rotationYaw, 0.001F);
        assertEquals(30.0F, player.rotationPitch, 0.001F);
        moveFix.endPacketRotation(player);
        assertEquals(0.0F, player.rotationYaw, 0.0F);
        assertEquals(0.0F, player.rotationPitch, 0.0F);
    }

    @Test public void finalReturnTickBlendsIntoTheVanillaPose() throws Exception {
        moveFix.beginRotationTick();
        moveFix.setFakeRotation("aura", 10, 8);
        moveFix.beginRotationTick();
        moveFix.clearFakeRotation("aura");
        moveFix.getRotateBackSpeed().setRange(20, 20);
        float lastYaw = 10, lastPitch = 8;
        moveFix.tick();
        for (int i = 0; i < 20 && (moveFix.getRotationYaw() != 0 || moveFix.getRotationPitch() != 0); i++) {   // the turn back eases in
            lastYaw = moveFix.getRotationYaw();
            lastPitch = moveFix.getRotationPitch();
            moveFix.beginRotationTick();
            moveFix.tick();
        }
        moveFix.beginPlayerRender(player);
        assertEquals(lastYaw / 2, headAt(0.5F), 0.001F);
        assertEquals(lastPitch / 2, pitchAt(0.5F), 0.001F);
        moveFix.endPlayerRender(player);

        moveFix.beginRotationTick();
        float[] vanilla = pose();
        moveFix.beginPlayerRender(player);
        assertArrayEquals(vanilla, pose(), 0.0F);
    }

    @Test public void smoothBackRotateTurnsBackAtItsSpeedOrReturnsAtOnce() throws Exception {
        moveFix.beginRotationTick();
        moveFix.setFakeRotation("scaffold", 100, 40);
        moveFix.clearFakeRotation("scaffold");
        moveFix.getRotateBackSpeed().setRange(30, 30);
        assertTrue(moveFix.getRotateBackSpeed().isVisible());
        moveFix.tick();
        // Nyx's Smooth back: a third of its speed the first tick, gathering pace, in whole mouse counts
        assertEquals(90.0F, moveFix.getRotationYaw(), 0.01F);
        assertEquals(30.0F, moveFix.getRotationPitch(), 0.01F);
        float step = MouseMath.step(minecraft.gameSettings.mouseSensitivity), turned = 100.0F - moveFix.getRotationYaw();
        assertEquals("whole mouse counts", Math.round(turned / step), turned / step, 0.01F);
        float before = moveFix.getRotationYaw();
        moveFix.tick();
        assertEquals("then quicker", 20.0F, before - moveFix.getRotationYaw(), 0.01F);

        moveFix.getSmoothBackRotate().setEnabled(false);
        assertFalse("The speed only applies to a smooth return", moveFix.getRotateBackSpeed().isVisible());
        moveFix.tick();
        assertEquals(0.0F, moveFix.getRotationYaw(), 0.0F);
        assertEquals(0.0F, moveFix.getRotationPitch(), 0.0F);
    }

    @Test public void aTurnBackAfterAWholeTurnRoundYouHandsOverWithoutTheYawJumping() {
        moveFix.beginRotationTick();
        for (float yaw = 0; yaw <= 370; yaw += 10) moveFix.setFakeRotation("aura", yaw, 0);   // followed someone round past your back
        moveFix.clearFakeRotation("aura");
        float sent = moveFix.getRotationYaw();
        for (int i = 0; i < 40; i++) {
            moveFix.tick();
            assertTrue("a mouse's yaw only adds up", Math.abs(moveFix.getRotationYaw() - sent) < 15F);
            sent = moveFix.getRotationYaw();
        }
        assertFalse(moveFix.ownsRotation("aura"));
        assertEquals("the camera put on the same turn", 360F, player.rotationYaw, 0.01F);
    }

    @Test public void silentKeysStayUntilTheWayYouSteerIsThirtyDegreesOffThem() {
        net.minecraft.util.MovementInput input = new net.minecraft.util.MovementInput();
        float[] strafes = new float[3];
        float[] fakes = {20F, 25F, 35F};                 // the camera at 0, W held: they are 20, 25 and 35 degrees off W
        for (int i = 0; i < fakes.length; i++) {
            moveFix.setFakeRotation("aura", fakes[i], 0, "Silent");
            input.moveForward = 1F;
            input.moveStrafe = 0F;
            moveFix.correctInjectedInput(input);
            strafes[i] = input.moveStrafe;
        }
        assertEquals("W", 0F, strafes[0], 0F);
        assertEquals("past 22.5 it is still W", 0F, strafes[1], 0F);
        assertNotEquals("past 30 it turns", 0F, strafes[2], 0F);
    }

    @Test public void changingPlayerDiscardsPreviousWorldRotation() throws Exception {
        moveFix.beginRotationTick();
        moveFix.setFakeRotation("aura", 100, 50);
        moveFix.beginRotationTick();
        player = allocate(EntityPlayerSP.class);
        player.rotationYaw = 20;
        player.prevRotationYaw = 18;
        minecraft.thePlayer = player;
        moveFix.beginRotationTick();
        float[] vanilla = pose();
        moveFix.beginPlayerRender(player);
        assertArrayEquals(vanilla, pose(), 0.0F);
        assertEquals(20.0F, moveFix.getRotationYaw(), 0.0F);
    }

    @Test public void interruptedRenderIsRestoredBeforeTheNextTick() throws Exception {
        moveFix.beginRotationTick();
        moveFix.setFakeRotation("aura", 60, 30);
        float[] vanilla = pose();
        moveFix.beginPlayerRender(player);
        // Simulate a Forge event cancellation that omits RenderPlayer.Post.
        moveFix.beginRotationTick();
        assertArrayEquals(vanilla, pose(), 0.0F);
        moveFix.setFakeRotation("aura", 80, 40);
        moveFix.beginPlayerRender(player);
        assertEquals(70.0F, headAt(0.5F), 0.001F);
        moveFix.endPlayerRender(player);
    }

    private float headAt(float partial) {
        return player.prevRotationYawHead + RotationMath.difference(player.rotationYawHead, player.prevRotationYawHead) * partial;
    }
    @Test public void disablingAProducerKeepsTheSmoothReturnButDropsForcedInput(){
        moveFix.beginRotationTick();moveFix.setFakeRotation("aura",100,40,"Silent");moveFix.setForcedMovement("aura",1,0,true);
        moveFix.getRotateBackSpeed().setRange(10,10);moveFix.releaseOwnedState("aura");
        assertFalse(moveFix.ownsRotation("aura"));assertFalse(moveFix.hasForcedMovement());assertEquals(100,moveFix.getRotationYaw(),0);
        moveFix.tick();assertEquals(100-10/3F,moveFix.getRotationYaw(),.01);assertEquals(40-10/3F,moveFix.getRotationPitch(),.01);
        for(int i=0;i<40;i++)moveFix.tick();assertEquals(0,moveFix.getRotationYaw(),0);
        moveFix.setFakeRotation("other",75,20);moveFix.releaseOwnedState("aura");assertEquals(75,moveFix.getRotationYaw(),0);
        moveFix.getSmoothBackRotate().setEnabled(false);moveFix.releaseOwnedState("other");assertEquals(0,moveFix.getRotationYaw(),0);
    }

    @Test public void forcedMovementSprintsInServerSpaceAndResetsOnRespawn() throws Exception {
        moveFix.beginRotationTick();
        moveFix.getCorrectMovement().setValue("Prevent Backwards Sprinting");
        player.movementInput=new net.minecraft.util.MovementInput();
        moveFix.installInputHook();
        moveFix.setFakeRotation("PitBot",180,0);
        moveFix.setForcedMovement("PitBot",1,0,true);
        player.movementInput.updatePlayerMoveState();
        java.lang.reflect.Method sprint=MoveFixModule.class.getDeclaredMethod("sprintForward",float.class,Object.class);
        sprint.setAccessible(true);
        assertEquals(1F,(Float)sprint.invoke(moveFix,1F,player),0F);
        assertEquals(1F,player.movementInput.moveForward,0F);
        assertTrue(player.movementInput.jump);
        moveFix.clearForcedMovement("Other");
        player.movementInput.updatePlayerMoveState();
        assertEquals(1F,player.movementInput.moveForward,0F);
        minecraft.thePlayer=allocate(EntityPlayerSP.class);
        minecraft.thePlayer.movementInput=new net.minecraft.util.MovementInput();
        moveFix.beginRotationTick();moveFix.installInputHook();
        minecraft.thePlayer.movementInput.updatePlayerMoveState();
        assertEquals(0F,minecraft.thePlayer.movementInput.moveForward,0F);
        assertFalse(minecraft.thePlayer.movementInput.jump);
    }

    private float bodyAt(float partial) {
        return player.prevRenderYawOffset + RotationMath.difference(player.renderYawOffset, player.prevRenderYawOffset) * partial;
    }

    private float pitchAt(float partial) {
        return player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partial;
    }

    private float[] pose() {
        return new float[] {player.rotationYaw, player.prevRotationYaw, player.rotationPitch, player.prevRotationPitch,
                player.rotationYawHead, player.prevRotationYawHead, player.renderYawOffset, player.prevRenderYawOffset};
    }

    private <T> T allocate(Class<T> type) throws Exception {
        // These fixtures need only rotation fields, not Minecraft's window,
        // renderer constructors, network connection or a running world.
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(unsafeClass.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }
}
