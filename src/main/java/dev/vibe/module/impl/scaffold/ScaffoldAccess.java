package dev.vibe.module.impl.scaffold;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

/** Access to the vanilla members made public by Gothaj's patched Minecraft. */
public final class ScaffoldAccess {
    private static Field rightClickDelay;
    private static Method rightClick;
    private ScaffoldAccess() { }

    public static void clearRightClickDelay() {
        try {
            if (rightClickDelay == null) {
                for (String name : new String[] {"rightClickDelayTimer", "field_71467_ac"}) {
                    try { rightClickDelay = Minecraft.class.getDeclaredField(name); break; }
                    catch (NoSuchFieldException ignored) { }
                }
                if (rightClickDelay == null) throw new NoSuchFieldException("rightClickDelayTimer");
                rightClickDelay.setAccessible(true);
            }
            rightClickDelay.setInt(Minecraft.getMinecraft(), 0);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Scaffold click-delay hook", error); }
    }

    public static void rightClickMouse() {
        try {
            if (rightClick == null) {
                for (String name : new String[] {"rightClickMouse", "func_147121_ag"}) {
                    try { rightClick = Minecraft.class.getDeclaredMethod(name); break; }
                    catch (NoSuchMethodException ignored) { }
                }
                if (rightClick == null) throw new NoSuchMethodException("rightClickMouse");
                rightClick.setAccessible(true);
            }
            rightClick.invoke(Minecraft.getMinecraft());
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Scaffold drag-click hook", error); }
    }

    // Gothaj Entity.getDistanceFromPosition, including its float sqrt return.
    public static double getDistanceFromPosition(double fromX, double fromY, double fromZ, double x, double y, double z) {
        double d0 = fromX - x, d1 = fromY - y, d2 = fromZ - z;
        return MathHelper.sqrt_double(d0 * d0 + d1 * d1 + d2 * d2);
    }
}
