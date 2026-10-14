package dev.vibe.extension.example;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.module.impl.movement.TestModule;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import org.junit.*;
import static org.junit.Assert.*;

public class TestModuleTest {
    private Minecraft previousMinecraft, mc;
    private Vibe previousVibe;
    private TestModule test;
    private MoveFixModule moveFix;
    @Before public void setup() throws Exception {
        previousMinecraft = Minecraft.getMinecraft(); previousVibe = Vibe.getInstance();
        set(Vibe.class, null, "instance", null);
        mc = allocate(Minecraft.class); mc.thePlayer = allocate(EntityPlayerSP.class);
        set(Minecraft.class, null, "theMinecraft", mc);
        Vibe vibe = new Vibe(); set(Vibe.class, null, "instance", vibe);
        moveFix = new MoveFixModule(); test = new TestModule();
        ModuleManager manager = allocate(ModuleManager.class);
        set(ModuleManager.class, manager, "modules", new ArrayList<Module>(Arrays.asList(moveFix, test)));
        set(Vibe.class, vibe, "moduleManager", manager);
    }
    @After public void restore() throws Exception {
        set(Minecraft.class, null, "theMinecraft", previousMinecraft); set(Vibe.class, null, "instance", previousVibe);
    }
    @Test public void testRotationRunsOnlyWhenEnabledAndReleasesOnDisable() {
        moveFix.beginRotationTick(); test.getYaw().setValue(75D); test.getPitch().setValue(30D);
        test.tick(); assertEquals(0F, moveFix.getRotationYaw(), 0);
        test.setEnabled(true); test.tick(); assertEquals(75F, moveFix.getRotationYaw(), 0); assertEquals(30F, moveFix.getRotationPitch(), 0);
        moveFix.getSmoothBackRotate().setEnabled(false);
        test.setEnabled(false); moveFix.tick(); assertEquals(0F, moveFix.getRotationYaw(), 0);
    }
    @Test public void noPlayerTickIsSafeAndLeavesTheCameraAlone() {
        mc.thePlayer.rotationYaw = 12; mc.thePlayer.rotationPitch = 6;
        moveFix.beginRotationTick(); test.setEnabled(true); test.getYaw().setValue(70D); test.tick();
        assertEquals(12F, mc.thePlayer.rotationYaw, 0); assertEquals(6F, mc.thePlayer.rotationPitch, 0);
        mc.thePlayer = null; test.tick();
    }
    @SuppressWarnings("unchecked") private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); field.setAccessible(true);
        Object unsafe = field.get(null); return (T) unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type);
    }
    private static void set(Class<?> type, Object object, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
}
