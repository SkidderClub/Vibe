package dev.vibe.input;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.client.RawMouseInputModule;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MouseHelper;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import static org.junit.Assert.*;

public class RawMouseInputTest {

    private static final class FakeMouseDevice implements RawMouseDevice {
        private final String name;
        private boolean connected = true;
        private float nextDx;
        private float nextDy;
        private float lastDx;
        private float lastDy;
        private int pollCount;
        private int queueSize;

        FakeMouseDevice(String name) {
            this.name = name;
        }

        void move(float dx, float dy) {
            this.nextDx += dx;
            this.nextDy += dy;
        }

        void disconnect() {
            this.connected = false;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean poll() {
            pollCount++;
            if (!connected) {
                lastDx = 0.0F;
                lastDy = 0.0F;
                return false;
            }
            lastDx = nextDx;
            lastDy = nextDy;
            nextDx = 0.0F;
            nextDy = 0.0F;
            return true;
        }

        @Override
        public float getDeltaX() {
            return lastDx;
        }

        @Override
        public float getDeltaY() {
            return lastDy;
        }

        @Override
        public void setEventQueueSize(int size) {
            this.queueSize = size;
        }
    }

    private static final class StubMouseHelper extends MouseHelper {
        int nextVanillaDx;
        int nextVanillaDy;
        int grabCount;
        int ungrabCount;
        int xyChangeCount;

        @Override
        public void grabMouseCursor() {
            grabCount++;
            deltaX = 0;
            deltaY = 0;
        }

        @Override
        public void ungrabMouseCursor() {
            ungrabCount++;
        }

        @Override
        public void mouseXYChange() {
            xyChangeCount++;
            deltaX = nextVanillaDx;
            deltaY = nextVanillaDy;
        }
    }

    @Test
    public void rawMouseHelperFeedsAccumulatedDeltasAndInvertsYAxis() {
        FakeMouseDevice mouse = new FakeMouseDevice("Gaming Mouse");
        AtomicBoolean capturing = new AtomicBoolean(true);
        RawMouseInputController controller = new RawMouseInputController(
                (backend, refresh) -> Collections.<RawMouseDevice>singletonList(mouse),
                capturing::get);
        controller.scanNow(RawMouseProvider.Backend.AUTO, false, false);
        // Prime capture transition (first poll after scan flushes stale driver events).
        controller.pollOnce();

        StubMouseHelper vanilla = new StubMouseHelper();
        vanilla.nextVanillaDx = 99;
        vanilla.nextVanillaDy = 99;
        AtomicInteger lwjglDrains = new AtomicInteger();
        RawMouseHelper helper = new RawMouseHelper(vanilla, controller, () -> true, lwjglDrains::incrementAndGet);

        mouse.move(12.0F, 5.0F);
        controller.pollOnce();
        mouse.move(-3.0F, -9.0F);
        controller.pollOnce();

        helper.mouseXYChange();
        assertEquals("X delta should pass through right-positive", 9, helper.deltaX);
        assertEquals("Y delta should be negated from screen-down to Minecraft-up", 4, helper.deltaY);
        assertEquals("LWJGL should be drained while raw input is active", 1, lwjglDrains.get());
        assertEquals("Vanilla delegate should not be invoked when raw input is active", 0, vanilla.xyChangeCount);

        // Next frame without movement yields zero.
        helper.mouseXYChange();
        assertEquals(0, helper.deltaX);
        assertEquals(0, helper.deltaY);
    }

    @Test
    public void allMiceModeSumsMovementFromMultipleDevices() {
        FakeMouseDevice primary = new FakeMouseDevice("Primary Mouse");
        FakeMouseDevice touchpad = new FakeMouseDevice("Touchpad");
        RawMouseInputController controller = new RawMouseInputController(
                (backend, refresh) -> Arrays.<RawMouseDevice>asList(primary, touchpad),
                () -> true);
        controller.setLockActive(false);
        controller.scanNow(RawMouseProvider.Backend.AUTO, false, false);
        controller.pollOnce();

        primary.move(7.0F, -2.0F);
        touchpad.move(3.0F, 5.0F);
        controller.pollOnce();

        int[] out = new int[2];
        assertTrue(controller.consumeDelta(out));
        assertEquals(10, out[0]);
        assertEquals(3, out[1]);
        assertEquals(2, controller.getActiveDeviceCount());
    }

    @Test
    public void lockActiveLocksToMovingMouseIgnoresOthersAndFailsOverOnDisconnect() {
        FakeMouseDevice phantom = new FakeMouseDevice("Virtual Mouse");
        FakeMouseDevice gaming = new FakeMouseDevice("Superlight");
        RawMouseInputController controller = new RawMouseInputController(
                (backend, refresh) -> Arrays.<RawMouseDevice>asList(phantom, gaming),
                () -> true);
        controller.setLockActive(true);
        controller.scanNow(RawMouseProvider.Backend.AUTO, false, false);

        // Stale movement during discarded first poll must NOT lock onto phantom.
        phantom.move(40.0F, 40.0F);
        controller.pollOnce();
        assertEquals("2 mice", controller.getActiveDeviceName());

        // Real movement from gaming mouse locks onto gaming mouse.
        gaming.move(6.0F, 2.0F);
        controller.pollOnce();
        assertEquals("Superlight", controller.getActiveDeviceName());

        // Subsequent noise from phantom mouse is ignored while still polled/drained.
        int phantomPollsBefore = phantom.pollCount;
        phantom.move(100.0F, 100.0F);
        gaming.move(4.0F, -1.0F);
        controller.pollOnce();
        assertTrue("Other devices must still be drained", phantom.pollCount > phantomPollsBefore);

        int[] out = new int[2];
        assertTrue(controller.consumeDelta(out));
        assertEquals(10, out[0]);
        assertEquals(1, out[1]);

        // Disconnecting locked mouse unlocks and allows failover to remaining mouse.
        gaming.disconnect();
        phantom.move(5.0F, 3.0F);
        controller.pollOnce();
        assertEquals(1, controller.getActiveDeviceCount());
        assertEquals("Virtual Mouse", controller.getActiveDeviceName());
        assertTrue(controller.consumeDelta(out));
        assertEquals(5, out[0]);
        assertEquals(3, out[1]);
    }

    @Test
    public void preservesFractionalRemainderAndIgnoresNonFiniteValues() {
        FakeMouseDevice mouse = new FakeMouseDevice("High-Res Mouse");
        RawMouseInputController controller = new RawMouseInputController(
                (backend, refresh) -> Collections.<RawMouseDevice>singletonList(mouse),
                () -> true);
        controller.scanNow(RawMouseProvider.Backend.AUTO, false, false);
        controller.pollOnce();

        int[] out = new int[2];
        mouse.move(0.6F, 0.4F);
        controller.pollOnce();
        assertTrue(controller.consumeDelta(out));
        assertEquals(0, out[0]);
        assertEquals(0, out[1]);

        mouse.move(0.6F, 0.7F);
        controller.pollOnce();
        assertTrue(controller.consumeDelta(out));
        assertEquals(1, out[0]);
        assertEquals(1, out[1]);

        mouse.move(0.8F, Float.NaN);
        controller.pollOnce();
        assertTrue(controller.consumeDelta(out));
        assertEquals(1, out[0]);
        assertEquals(0, out[1]);
    }

    @Test
    public void discardsMovementWhileUngrabbedAndFlushesOnGrabTransitions() {
        FakeMouseDevice mouse = new FakeMouseDevice("Mouse");
        AtomicBoolean capturing = new AtomicBoolean(false);
        RawMouseInputController controller = new RawMouseInputController(
                (backend, refresh) -> Collections.<RawMouseDevice>singletonList(mouse),
                capturing::get);
        controller.scanNow(RawMouseProvider.Backend.AUTO, false, false);

        // Moving around in menus/alt-tab must be drained and discarded.
        mouse.move(500.0F, -300.0F);
        controller.pollOnce();

        StubMouseHelper vanilla = new StubMouseHelper();
        AtomicInteger lwjglDrains = new AtomicInteger();
        RawMouseHelper helper = new RawMouseHelper(vanilla, controller, () -> true, lwjglDrains::incrementAndGet);

        // Re-entering the game grabs cursor and flushes any residual deltas.
        capturing.set(true);
        helper.grabMouseCursor();
        assertEquals(1, vanilla.grabCount);
        assertEquals(1, lwjglDrains.get());

        // First poll after grab transition discards residual edge motion.
        mouse.move(25.0F, 25.0F);
        controller.pollOnce();
        helper.mouseXYChange();
        assertEquals(0, helper.deltaX);
        assertEquals(0, helper.deltaY);

        // Subsequent normal in-game movement applies cleanly.
        mouse.move(8.0F, -6.0F);
        controller.pollOnce();
        helper.mouseXYChange();
        assertEquals(8, helper.deltaX);
        assertEquals(6, helper.deltaY);

        // Ungrabbing delegates and clears pending deltas.
        mouse.move(30.0F, 30.0F);
        controller.pollOnce();
        helper.ungrabMouseCursor();
        assertEquals(1, vanilla.ungrabCount);
        capturing.set(false);
        controller.pollOnce();
        int[] out = new int[2];
        assertTrue(controller.consumeDelta(out));
        assertEquals(0, out[0]);
        assertEquals(0, out[1]);
    }

    @Test
    public void fallsBackToVanillaWhenNoRawMouseIsAvailable() {
        RawMouseInputController controller = new RawMouseInputController(
                (backend, refresh) -> Collections.<RawMouseDevice>emptyList(),
                () -> true);
        controller.scanNow(RawMouseProvider.Backend.AUTO, false, false);

        StubMouseHelper vanilla = new StubMouseHelper();
        vanilla.nextVanillaDx = 14;
        vanilla.nextVanillaDy = -7;

        AtomicBoolean fallbackEnabled = new AtomicBoolean(true);
        RawMouseHelper helper = new RawMouseHelper(vanilla, controller, fallbackEnabled::get, () -> { });

        helper.mouseXYChange();
        assertEquals(14, helper.deltaX);
        assertEquals(-7, helper.deltaY);
        assertEquals(1, vanilla.xyChangeCount);

        fallbackEnabled.set(false);
        helper.mouseXYChange();
        assertEquals(0, helper.deltaX);
        assertEquals(0, helper.deltaY);
        assertEquals(1, vanilla.xyChangeCount);
    }

    @Test
    public void moduleIsRegisteredInClientCategoryAndInstallsRestoresMouseHelper() throws Exception {
        assertTrue("ModuleManager constructor must register RawMouseInputModule",
                moduleManagerConstructs("dev/vibe/module/impl/client/RawMouseInputModule"));

        Minecraft previousMinecraft = Minecraft.getMinecraft();
        try {
            Minecraft mc = allocate(Minecraft.class);
            StubMouseHelper originalHelper = new StubMouseHelper();
            mc.mouseHelper = originalHelper;
            setField(Minecraft.class, null, "theMinecraft", mc);

            List<RawMouseProvider.Backend> seenBackends = new ArrayList<RawMouseProvider.Backend>();
            FakeMouseDevice fake = new FakeMouseDevice("Test Mouse");
            RawMouseInputModule module = new RawMouseInputModule((backend, refresh) -> {
                seenBackends.add(backend);
                return Collections.<RawMouseDevice>singletonList(fake);
            });

            ModuleManager manager = allocate(ModuleManager.class);
            setField(ModuleManager.class, manager, "modules",
                    new ArrayList<Module>(Collections.<Module>singletonList(module)));
            assertSame("RawMouseInput must be discoverable by id", module, manager.getModule("rawmouseinput"));
            assertSame(module, manager.getModule(RawMouseInputModule.class));
            assertEquals(Category.CLIENT, module.getCategory());
            assertEquals("RawMouseInput", module.getRawName());

            assertEquals(RawMouseInputModule.MODE_ALL_MICE, module.getMouseSelection().getValue());
            assertEquals(RawMouseProvider.Backend.AUTO.getLabel(), module.getBackend().getValue());
            assertEquals(1, module.getPollDelay().getInt());
            assertTrue(module.getVanillaFallback().isEnabled());
            assertFalse(module.getRescanMouse().isEnabled());

            module.setEnabled(true);
            try {
                assertTrue("Enabling should install RawMouseHelper", mc.mouseHelper instanceof RawMouseHelper);
                assertSame("RawMouseHelper should delegate to original MouseHelper",
                        originalHelper, ((RawMouseHelper) mc.mouseHelper).getDelegate());

                module.getMouseSelection().setValue(RawMouseInputModule.MODE_LOCK_ACTIVE);
                module.getPollDelay().setValue(2.0D);
                module.tick();
                assertTrue(module.getController().isLockActive());
                assertEquals(2, module.getController().getPollDelayMs());

                // One-shot Rescan Mouse setting resets itself to false immediately.
                module.getRescanMouse().setEnabled(true);
                assertFalse(module.getRescanMouse().isEnabled());
            } finally {
                module.setEnabled(false);
                assertSame("Disabling must restore the original MouseHelper", originalHelper, mc.mouseHelper);
                module.shutdown();
            }
            assertFalse(module.getController().isRunning());
        } finally {
            setField(Minecraft.class, null, "theMinecraft", previousMinecraft);
        }
    }

    private static boolean moduleManagerConstructs(String internalClassName) throws Exception {
        try (InputStream stream = ModuleManager.class.getResourceAsStream("/dev/vibe/module/ModuleManager.class")) {
            assertNotNull("ModuleManager bytecode should be readable", stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            for (Object item : node.methods) {
                MethodNode method = (MethodNode) item;
                if (!"<init>".equals(method.name)) {
                    continue;
                }
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.NEW && insn instanceof TypeInsnNode
                            && internalClassName.equals(((TypeInsnNode) insn).desc)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        return (T) unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type);
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
