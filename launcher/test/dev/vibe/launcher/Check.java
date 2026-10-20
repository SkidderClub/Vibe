package dev.vibe.launcher;

import java.util.Objects;

/** Minimal assertions for the dependency-free launcher test run. */
public final class Check {
    public interface Body { void run() throws Exception; }

    private int passed, failed;

    public void test(String name, Body body) {
        try {
            body.run();
            passed++;
            System.out.println("  ok    " + name);
        } catch (Throwable error) {
            failed++;
            System.out.println("  FAIL  " + name + ": " + error);
            error.printStackTrace(System.out);
        }
    }

    public static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
    }

    public static void isTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static <T extends Throwable> T fails(Class<T> type, Body body) {
        try {
            body.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) return type.cast(error);
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + error, error);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }

    int passed() { return passed; }
    int failed() { return failed; }
}
