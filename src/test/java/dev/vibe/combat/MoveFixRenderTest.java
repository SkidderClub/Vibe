package dev.vibe.combat;

import dev.vibe.module.impl.MoveFixModule;
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
        moveFix.tick();
        moveFix.beginPlayerRender(player);
        assertEquals(5.0F, headAt(0.5F), 0.001F);
        assertEquals(4.0F, pitchAt(0.5F), 0.001F);
        moveFix.endPlayerRender(player);

        moveFix.beginRotationTick();
        float[] vanilla = pose();
        moveFix.beginPlayerRender(player);
        assertArrayEquals(vanilla, pose(), 0.0F);
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
