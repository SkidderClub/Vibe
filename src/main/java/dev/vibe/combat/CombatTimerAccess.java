package dev.vibe.combat;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;

/** Forge 1.8.9 keeps Minecraft's timer private; Gothaj exposes the same value. */
public final class CombatTimerAccess {
    private static Field minecraftTimer;
    private static Field timerSpeed;
    private static Timer temporaryTimer;
    private static Object temporaryOwner;
    private static float temporarySpeed, baseSpeed;
    private CombatTimerAccess() { }

    public static float getSpeed() {
        return readSpeed(timer());
    }

    /** Snapshot or compare the persistent rate when managing a module's own timer. */
    public static float getBaseSpeed() {
        Timer currentTimer = timer();
        float actual = readSpeed(currentTimer);
        return hasTemporarySpeed(currentTimer, actual) ? baseSpeed : actual;
    }

    private static float readSpeed(Timer timer) {
        try {
            return timer == null ? 1.0F : timerSpeed().getFloat(timer);
        } catch (Throwable ignored) { return 1.0F; }
    }

    public static void setSpeed(float speed) {
        temporaryOwner = null;
        temporaryTimer = null;
        writeSpeed(timer(), speed);
    }

    /** A short override never becomes another module's saved baseline. A normal write supersedes it. */
    public static void setTemporarySpeed(Object owner, float speed) {
        Timer currentTimer = timer();
        if (currentTimer == null) return;
        float actual = readSpeed(currentTimer);
        if (!hasTemporarySpeed(currentTimer, actual)) baseSpeed = actual;
        temporaryTimer = currentTimer;
        temporaryOwner = owner;
        temporarySpeed = speed;
        writeSpeed(currentTimer, speed);
    }

    public static void releaseTemporarySpeed(Object owner) {
        if (temporaryOwner != owner) return;
        Timer currentTimer = timer();
        if (hasTemporarySpeed(currentTimer, readSpeed(currentTimer))) writeSpeed(currentTimer, baseSpeed);
        temporaryOwner = null;
        temporaryTimer = null;
    }

    private static boolean hasTemporarySpeed(Timer timer, float actual) {
        return timer != null && timer == temporaryTimer && Float.compare(actual, temporarySpeed) == 0;
    }

    private static void writeSpeed(Timer timer, float speed) {
        try {
            if (timer != null) timerSpeed().setFloat(timer, speed);
        } catch (Throwable ignored) { }
    }
    /** Used before saved modules are restored during client initialization. */
    public static void resetVanillaTimer() { try { setSpeed(1.0F); } catch (Throwable ignored) { } }

    private static Timer timer() {
        try {
            if (minecraftTimer == null) minecraftTimer = field(Minecraft.class, Timer.class, "timer", "field_71428_T");
            return (Timer) minecraftTimer.get(Minecraft.getMinecraft());
        } catch (Throwable ignored) { return null; }
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
