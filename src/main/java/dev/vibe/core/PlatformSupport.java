package dev.vibe.core;

import java.io.File;
import java.util.Locale;

/**
 * Detects the mobile Minecraft runtimes Vibe does not support. PojavLauncher
 * and FCL run an ARM Linux JVM inside an Android app sandbox, so the plain
 * {@code os.name} value only says "Linux"; the app environment and the
 * Android system directories are what identify the platform.
 */
public final class PlatformSupport {

    private static final boolean ANDROID = detectAndroid();
    private static final boolean IOS = detectIos();

    private PlatformSupport() {
    }

    public static boolean unsupported() {
        return ANDROID || IOS;
    }

    public static String name() {
        return ANDROID ? "Android" : IOS ? "iOS" : "Desktop";
    }

    public static String describe() {
        return name() + " (" + System.getProperty("os.name", "unknown") + ", "
                + System.getProperty("os.arch", "unknown") + ")";
    }

    private static boolean detectAndroid() {
        String os = property("os.name");
        String arch = property("os.arch");
        if (os.contains("android") || property("java.vendor").contains("android")
                || property("java.runtime.name").contains("android")) {
            return true;
        }
        if (env("ANDROID_ROOT") || env("ANDROID_DATA")) {
            return true;
        }
        if (env("POJAV_RENDERER") || env("POJAV_LAUNCHER")) {
            return true;
        }
        if (new File("/system/build.prop").isFile()) {
            return true;
        }
        // Last resort for launchers that hide the Android environment: an ARM
        // Linux JVM is only found on Android in practice, and Android always
        // has /system mounted.
        return os.contains("linux")
                && (arch.startsWith("aarch64") || arch.startsWith("arm"))
                && new File("/system").isDirectory();
    }

    private static boolean detectIos() {
        String os = property("os.name");
        if (os.contains("ios") || os.contains("iphone") || os.contains("ipad")) {
            return true;
        }
        // iOS apps are sandboxed under /var/mobile; macOS homes never are.
        String home = System.getProperty("user.home", "");
        String envHome = System.getenv("HOME");
        return home.contains("/var/mobile/")
                || (envHome != null && envHome.contains("/var/mobile/"));
    }

    private static String property(String key) {
        String value = System.getProperty(key, "");
        return value.toLowerCase(Locale.ROOT);
    }

    private static boolean env(String key) {
        String value = System.getenv(key);
        return value != null && !value.isEmpty();
    }
}
