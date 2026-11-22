package dev.vibe.core;

import java.io.File;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Detects the mobile Minecraft runtimes Vibe does not support. PojavLauncher
 * and FCL run an ARM Linux JVM inside an Android app sandbox, so the plain
 * {@code os.name} value only says "Linux"; the app environment and the
 * Android system directories are what identify the platform.
 */
public final class PlatformSupport {

    enum Platform { ANDROID, IOS, DESKTOP }

    private static final Platform PLATFORM = detect(System::getProperty, System::getenv, path -> new File(path).isFile());

    private PlatformSupport() {
    }

    public static boolean unsupported() {
        return PLATFORM != Platform.DESKTOP;
    }

    public static String name() {
        return PLATFORM == Platform.ANDROID ? "Android" : PLATFORM == Platform.IOS ? "iOS" : "Desktop";
    }

    public static String describe() {
        return name() + " (" + System.getProperty("os.name", "unknown") + ", "
                + System.getProperty("os.arch", "unknown") + ")";
    }

    static Platform detect(Function<String, String> property, Function<String, String> environment,
                           Predicate<String> systemFileExists) {
        String os = lower(property.apply("os.name"));
        if (os.contains("android") || lower(property.apply("java.vendor")).contains("android")
                || lower(property.apply("java.runtime.name")).contains("android")) return Platform.ANDROID;
        if (os.startsWith("ios") || os.contains("iphone") || os.contains("ipad")) return Platform.IOS;

        String javaHome = lower(property.apply("java.home"));
        String userHome = lower(property.apply("user.home"));
        if (iosSandbox(javaHome) || iosSandbox(userHome)) return Platform.IOS;
        if (!os.contains("linux")) return Platform.DESKTOP;

        // Desktop Android SDK variables and ARM architecture alone are not evidence of a mobile runtime.
        if (present(environment.apply("POJAV_RENDERER")) || present(environment.apply("POJAV_LAUNCHER"))
                || androidSandbox(javaHome) || androidSandbox(userHome) || androidSandbox(lower(environment.apply("HOME")))) {
            return Platform.ANDROID;
        }
        for (String path : lower(property.apply("java.library.path")).split(":")) {
            if (androidSandbox(path)) return Platform.ANDROID;
        }
        return systemFileExists.test("/system/build.prop") || systemFileExists.test("/system/bin/app_process")
                || systemFileExists.test("/system/bin/linker64") ? Platform.ANDROID : Platform.DESKTOP;
    }

    private static boolean androidSandbox(String path) {
        return path.startsWith("/data/data/") || path.startsWith("/data/user/")
                || path.startsWith("/storage/emulated/") || path.startsWith("/mnt/sdcard/");
    }

    private static boolean iosSandbox(String path) {
        return path.startsWith("/var/mobile/") || path.startsWith("/private/var/mobile/");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean present(String value) {
        return value != null && !value.isEmpty();
    }
}
