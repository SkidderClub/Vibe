package dev.vibe.core;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static dev.vibe.core.PlatformSupport.Platform.ANDROID;
import static dev.vibe.core.PlatformSupport.Platform.DESKTOP;
import static dev.vibe.core.PlatformSupport.Platform.IOS;
import static org.junit.Assert.assertEquals;

public class PlatformSupportTest {
    private final Map<String, String> properties = new HashMap<>();
    private final Map<String, String> environment = new HashMap<>();

    @Test public void desktopLaunchSurvivesAndroidSdkVariablesAndArmArchitecture() {
        properties.put("os.name", "Linux");
        properties.put("os.arch", "aarch64");
        properties.put("java.home", "/opt/data/user/jdk");
        environment.put("ANDROID_ROOT", "/opt/android-sdk");
        environment.put("ANDROID_DATA", "/home/dev/.android");
        assertEquals(DESKTOP, detect());

        properties.put("os.name", "Windows 11");
        environment.put("POJAV_RENDERER", "test");
        assertEquals(DESKTOP, detect());
    }

    @Test public void detectsAndroidLaunchersWithStandardLinuxJvmNames() {
        properties.put("os.name", "Linux");
        environment.put("POJAV_RENDERER", "opengles");
        assertEquals(ANDROID, detect());

        environment.clear();
        properties.put("java.home", "/data/user/0/com.tungsten.fcl/files/jdk");
        assertEquals(ANDROID, detect());

        properties.remove("java.home");
        assertEquals(ANDROID, PlatformSupport.detect(properties::get, environment::get, "/system/bin/app_process"::equals));
    }

    @Test public void detectsIosEvenWhenTheLauncherReportsAMacOsJvm() {
        properties.put("os.name", "iOS");
        assertEquals(IOS, detect());

        properties.put("os.name", "Mac OS X");
        assertEquals(DESKTOP, detect());
        properties.put("user.home", "/private/var/mobile/Containers/Data/Application/minecraft");
        assertEquals(IOS, detect());
    }

    private PlatformSupport.Platform detect() {
        return PlatformSupport.detect(properties::get, environment::get, ignored -> false);
    }
}
