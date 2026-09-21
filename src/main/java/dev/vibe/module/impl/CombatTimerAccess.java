package dev.vibe.module.impl;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;

/** Forge 1.8.9 keeps Minecraft's timer private; Gothaj exposes the same value. */
public final class CombatTimerAccess {
    private static Field minecraftTimer;
    private static Field timerSpeed;
    private CombatTimerAccess() { }

    static float getSpeed() {
        try {
            Timer timer = timer();
            return timer == null ? 1.0F : timerSpeed().getFloat(timer);
        } catch (Throwable ignored) { return 1.0F; }
    }
    static void setSpeed(float speed) {
        try {
            Timer timer = timer();
            if (timer != null) timerSpeed().setFloat(timer, speed);
        } catch (Throwable ignored) { }
    }
    /** Used before saved modules are restored during client initialization. */
    public static void resetVanillaTimer() { try { setSpeed(1.0F); } catch (Throwable ignored) { } }

    private static Timer timer() {
        try {
            if (minecraftTimer == null) minecraftTimer = field(Minecraft.class, Timer.class, "timer", "field_71428_T");
            return (Timer) minecraftTimer.get(Minecraft.getMinecraft());
        } catch (ReflectiveOperationException ignored) { return null; }
    }
    private static Field timerSpeed() {
        if (timerSpeed == null) timerSpeed = field(Timer.class, Float.TYPE, "timerSpeed", "field_74278_d");
        return timerSpeed;
    }
    private static Field field(Class<?> type, Class<?> fieldType, String first, String second) {
        for (String name : new String[] {first, second}) {
            try { Field value = type.getDeclaredField(name); value.setAccessible(true); return value; }
            catch (NoSuchFieldException ignored) { }
        }
        for (Field value : type.getDeclaredFields()) if (value.getType() == fieldType) { value.setAccessible(true); return value; }
        throw new IllegalStateException("Missing " + fieldType.getName() + " field on " + type.getName());
    }
}
