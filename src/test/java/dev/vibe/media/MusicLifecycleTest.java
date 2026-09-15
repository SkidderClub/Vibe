package dev.vibe.media;

import dev.vibe.module.Module;
import dev.vibe.module.impl.MusicModule;
import dev.vibe.setting.BooleanSetting;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class MusicLifecycleTest {
    @Test public void togglingPreparesTheFirstHudFrameAndReleasesTheService() {
        MusicModule music = new MusicModule();
        music.systemMedia.setEnabled(false);
        music.tick();
        assertNull(music.service());
        MusicService previous = null;
        try {
            for (int i = 0; i < 5; i++) {
                music.toggle();
                assertTrue(music.isEnabled());
                MusicService current = music.service();
                assertNotNull("The HUD may render before the first tick", current);
                assertNotSame(previous, current);
                assertNotNull(current.track());
                music.tick();
                assertSame(current, music.service());
                music.toggle();
                music.tick();
                assertFalse(music.isEnabled());
                assertNull(music.service());
                previous = current;
            }
        } finally { music.setEnabled(false); }
    }

    @Test public void unavailableMediaClassesDisableMusicBeforeTheHudCanUseThem() throws Exception {
        for (String missing : new String[]{"MediaTrack", "AudioSpectrum"}) {
            MediaLoader loader = new MediaLoader();
            loader.missing = "dev.vibe.media." + missing;
            Module music = (Module) loader.loadClass(MusicModule.class.getName()).newInstance();
            try {
                music.toggle();
                assertFalse("A failed startup must not leave Music enabled", music.isEnabled());
                assertNull(music.getClass().getMethod("service").invoke(music));
                music.getClass().getMethod("tick").invoke(music);
                assertFalse(music.isEnabled());
            } finally { music.setEnabled(false); }
        }
    }

    @Test public void unavailableBridgeDuringAnUpdateReleasesTheStartedService() throws Exception {
        MediaLoader loader = new MediaLoader();
        loader.missing = "dev.vibe.media.WindowsMediaBridge";
        Module music = (Module) loader.loadClass(MusicModule.class.getName()).newInstance();
        BooleanSetting systemMedia = (BooleanSetting) music.getClass().getField("systemMedia").get(music);
        systemMedia.setEnabled(false);
        try {
            music.toggle();
            assertTrue(music.isEnabled());
            assertNotNull(music.getClass().getMethod("service").invoke(music));
            systemMedia.setEnabled(true);
            music.getClass().getMethod("tick").invoke(music);
            assertFalse(music.isEnabled());
            assertNull(music.getClass().getMethod("service").invoke(music));
        } finally { music.setEnabled(false); }
    }

    /** Reproduces LaunchClassLoader refusing a media class, without replacing user JARs. */
    private static final class MediaLoader extends ClassLoader {
        String missing;
        MediaLoader() { super(MusicLifecycleTest.class.getClassLoader()); }

        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(missing)) throw new ClassNotFoundException(name);
            if (!name.equals(MusicModule.class.getName()) && !name.startsWith("dev.vibe.media."))
                return super.loadClass(name, resolve);
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                try (InputStream input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (input == null) throw new ClassNotFoundException(name);
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    byte[] bytes = output.toByteArray();
                    type = defineClass(name, bytes, 0, bytes.length);
                } catch (IOException failure) { throw new ClassNotFoundException(name, failure); }
            }
            if (resolve) resolveClass(type);
            return type;
        }
    }
}
