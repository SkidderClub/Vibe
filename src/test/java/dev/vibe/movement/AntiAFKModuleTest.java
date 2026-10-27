package dev.vibe.movement;

import dev.vibe.input.IdleActionSchedule;
import dev.vibe.core.FreecamTransformer;
import dev.vibe.module.impl.movement.*;
import dev.vibe.path.PathTestFixture;
import java.util.*;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.*;
import org.junit.*;
import org.lwjgl.input.Keyboard;
import org.objectweb.asm.*;
import static org.junit.Assert.*;

public class AntiAFKModuleTest {
    private PathTestFixture f; private AntiAFKModule anti; private FreecamModule camera; private MoveFixModule fix;
    @Before public void setup() throws Exception {
        f = new PathTestFixture(); f.player.onGround = true; f.player.inventory = new InventoryPlayer(f.player);
        f.player.setEntityBoundingBox(new AxisAlignedBB(.2, 64, .2, .8, 65.8, .8));
        f.player.movementInput = new MovementInputFromOptions(f.mc.gameSettings); f.mc.inGameHasFocus = true;
        fix = new MoveFixModule(); camera = new FreecamModule(); anti = new AntiAFKModule();
        f.modules.registerDynamic(fix); f.modules.registerDynamic(camera); f.modules.registerDynamic(anti);
        fix.installInputHook(); anti.idleDelay.setValue(0D); anti.actionDelay.setRange(250, 250); anti.setEnabled(true);
    }
    @After public void cleanup() throws Exception {
        anti.setEnabled(false); camera.setEnabled(false);
        for (KeyBinding key : f.mc.gameSettings.keyBindings) { KeyBinding.setKeyBindState(key.getKeyCode(), false); while (key.isPressed()) { } }
        f.close();
    }
    private void mode(String name) { anti.modes.setValue(new LinkedHashSet<>(Collections.singletonList(name))); }
    private MovementInput input() { f.player.movementInput.updatePlayerMoveState(); return f.player.movementInput; }
    @Test public void firstActionWaitsForIdleThenUsesTheSeparateActionInterval() {
        mode("Move"); anti.idleDelay.setValue(2D); anti.tick(1000); assertEquals(0, input().moveStrafe, 0);
        anti.tick(2999); assertEquals(0, input().moveStrafe, 0);
        anti.tick(3000); assertEquals(1, input().moveStrafe, 0); anti.tickEnd(); anti.tickEnd();
        anti.tick(3249); assertEquals(0, input().moveStrafe, 0);
        anti.tick(3250); assertEquals(-1, input().moveStrafe, 0);
    }
    @Test public void selectedActionsCycleAndDoNotInventHeldKeys() {
        anti.modes.setValue(new LinkedHashSet<>(Arrays.asList("Move", "Jump", "Rotate")));
        anti.tick(0); assertEquals(1, input().moveStrafe, 0); assertFalse(f.mc.gameSettings.keyBindRight.isKeyDown());
        anti.tickEnd(); anti.tickEnd(); anti.tick(250); assertTrue(input().jump); assertFalse(f.mc.gameSettings.keyBindJump.isKeyDown());
        anti.tickEnd(); float yaw = f.player.rotationYaw; anti.tick(500);
        assertEquals(anti.rotation.getFloat(), Math.abs(f.player.rotationYaw - yaw), .001);
        assertEquals(0, input().moveStrafe, 0); assertFalse(input().jump);
    }
    @Test public void moveFixCorrectsBothNormalAndFreecamBodyMovement() {
        mode("Move"); fix.getMode().setValue("Silent"); fix.setFakeRotation("test", 90, 0);
        anti.tick(0); MovementInput input = input(); assertEquals(-1, input.moveForward, .001); assertEquals(0, input.moveStrafe, .001);
        anti.tickEnd(); anti.tickEnd(); camera.setEnabled(true); camera.tick();
        KeyBinding.setKeyBindState(f.mc.gameSettings.keyBindForward.getKeyCode(), true);
        anti.tick(250); input = input(); FreecamModule.movementInputHook(f.player);
        assertEquals(1, input.moveForward, .001); assertEquals(0, input.moveStrafe, .001);
        assertTrue("Camera retains its physical controls", f.mc.gameSettings.keyBindForward.isKeyDown());
    }
    @Test public void freecamCameraMovementDoesNotResetBodyIdleButHotbarInputDoes() {
        mode("Jump"); anti.idleDelay.setValue(2D); camera.setEnabled(true); camera.tick();
        anti.tick(0); anti.keyboardInput(f.mc.gameSettings.keyBindForward.getKeyCode());
        anti.mouseInput(-1, 0, 100, 80); FreecamModule.mouseLookHook(f.player, 90, 25);
        anti.tick(2000); input(); FreecamModule.movementInputHook(f.player); assertTrue(f.player.movementInput.jump);
        anti.tickEnd(); anti.keyboardInput(Keyboard.KEY_2); anti.tick(2250); input();
        FreecamModule.movementInputHook(f.player); assertFalse(f.player.movementInput.jump);
    }
    @Test public void hotbarUsesTheVanillaQueuedKeyPressInsteadOfSpoofingSlots() {
        mode("Switch Items"); f.player.inventory.currentItem = 3; anti.tick(0);
        assertEquals(3, f.player.inventory.currentItem); assertTrue(f.mc.gameSettings.keyBindsHotbar[4].isPressed());
        assertFalse(f.mc.gameSettings.keyBindsHotbar[4].isPressed());
    }
    @Test public void clickUsesVanillaPressAndFreecamOnlyAllowsThatBodyClick() {
        mode("Left Click"); camera.setEnabled(true); camera.tick();
        assertTrue(AntiAFKModule.bodyClickBlockingHook(true)); anti.tick(0); AntiAFKModule.prepareClickHook();
        assertTrue(f.mc.gameSettings.keyBindAttack.isPressed()); assertFalse(f.mc.gameSettings.keyBindAttack.isPressed());
        assertTrue(f.mc.gameSettings.keyBindAttack.isKeyDown()); assertFalse(AntiAFKModule.bodyClickBlockingHook(true));
        anti.tickEnd(); assertFalse(f.mc.gameSettings.keyBindAttack.isKeyDown()); assertTrue(AntiAFKModule.bodyClickBlockingHook(true));
    }
    @Test public void unboundHotbarKeysUseTheNormalInventoryScrollPathAndWrap() {
        mode("Switch Items"); f.player.inventory.currentItem = 8; f.mc.gameSettings.keyBindsHotbar[0].setKeyCode(Keyboard.KEY_NONE);
        anti.tick(0); assertEquals(0, f.player.inventory.currentItem);
    }
    @Test public void realInputMenusDisableAndWorldChangesCancelActions() {
        mode("Move"); anti.tick(0); assertNotEquals(0, input().moveStrafe, 0); anti.keyboardInput(Keyboard.KEY_E); assertEquals(0, input().moveStrafe, 0);
        anti.tick(250); f.mc.currentScreen = new net.minecraft.client.gui.GuiScreen() { }; anti.tick(500); assertEquals(0, input().moveStrafe, 0);
        f.mc.currentScreen = null; anti.tick(750); anti.setEnabled(false); assertEquals(0, input().moveStrafe, 0);
        anti.setEnabled(true); anti.tick(1000); f.mc.theWorld = null; anti.tick(1250); assertEquals(0, input().moveStrafe, 0);
    }
    @Test public void freecamAlwaysRunsTheVanillaDigReleaseAfterAnAutomatedClick() throws Exception {
        mode("Left Click"); camera.setEnabled(true); camera.tick();
        // Execute the actual transformer on a controller-dispatch fixture. Its
        // body records the input that vanilla's damage/reset branch receives.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "AntiAFKDigDispatch", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "held", "Z", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "calls", "I", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "sendClickBlockToController", "(Z)V", null, null);
        method.visitCode(); method.visitVarInsn(Opcodes.ALOAD, 0); method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitFieldInsn(Opcodes.PUTFIELD, "AntiAFKDigDispatch", "held", "Z");
        method.visitVarInsn(Opcodes.ALOAD, 0); method.visitInsn(Opcodes.DUP); method.visitFieldInsn(Opcodes.GETFIELD, "AntiAFKDigDispatch", "calls", "I");
        method.visitInsn(Opcodes.ICONST_1); method.visitInsn(Opcodes.IADD); method.visitFieldInsn(Opcodes.PUTFIELD, "AntiAFKDigDispatch", "calls", "I");
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
        final byte[] bytes = new FreecamTransformer().transform("net.minecraft.client.Minecraft", "net.minecraft.client.Minecraft", writer.toByteArray());
        Class<?> type = new ClassLoader(getClass().getClassLoader()) { Class<?> define() { return defineClass("AntiAFKDigDispatch", bytes, 0, bytes.length); } }.define();
        Object dispatch = PathTestFixture.allocate(type); java.lang.reflect.Method send = type.getMethod("sendClickBlockToController", boolean.class);
        send.invoke(dispatch, true); assertFalse(type.getField("held").getBoolean(dispatch)); assertEquals(1, type.getField("calls").getInt(dispatch));
        anti.tick(0); AntiAFKModule.prepareClickHook(); send.invoke(dispatch, true); assertTrue(type.getField("held").getBoolean(dispatch));
        anti.tickEnd(); send.invoke(dispatch, false); assertFalse(type.getField("held").getBoolean(dispatch));
        send.invoke(dispatch, true); assertFalse(type.getField("held").getBoolean(dispatch)); assertEquals(4, type.getField("calls").getInt(dispatch));
    }
    @Test public void boundedRandomScheduleNeverCountsItsOwnActionsAsHumanInput() {
        IdleActionSchedule schedule = new IdleActionSchedule(); Random random = new Random(67); schedule.userInput(100);
        assertFalse(schedule.ready(1099, 1000)); assertTrue(schedule.ready(1100, 1000));
        long time = 1100;
        for (int i = 0; i < 500; i++) {
            schedule.acted(time, 250, 700, random); assertFalse(schedule.ready(time + 249, 1000)); assertTrue(schedule.ready(time + 700, 1000)); time += 700;
        }
        schedule.userInput(time); assertFalse(schedule.ready(time + 999, 1000)); assertTrue(schedule.ready(time + 1000, 1000));
    }
}
