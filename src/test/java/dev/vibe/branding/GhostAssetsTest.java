package dev.vibe.branding;

import dev.vibe.core.startup.*;
import dev.vibe.hud.*;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.menu.MenuPalette;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import net.minecraftforge.fml.common.ProgressManager;
import org.junit.*;
import static org.junit.Assert.*;

public class GhostAssetsTest {
    @Rule public org.junit.rules.TemporaryFolder temporary = new org.junit.rules.TemporaryFolder();
    @Test public void aMovingIndicatorReleasesWaxWithoutEndingItsFall() throws Exception {
        Class<?> type=Class.forName("dev.vibe.core.startup.StartupDrips");
        java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor();constructor.setAccessible(true);Object motion=constructor.newInstance();
        java.lang.reflect.Method sample=type.getDeclaredMethod("sample",float.class,float.class,float.class,float.class,float.class,float.class);sample.setAccessible(true);
        java.lang.reflect.Field field=type.getDeclaredField("drops");field.setAccessible(true);
        for(int time=0;time<=500;time+=50)sample.invoke(motion,time/1000F,544F,9F,0F,272F,81F);
        float[] values=(float[])field.get(motion);int selected=-1;
        for(int i=0;i<values.length;i+=4)if(values[i+2]>.4F){selected=i;break;}
        assertTrue("No visible wax formed",selected>=0);float volume=values[selected+2]*values[selected+3];
        sample.invoke(motion,.55F,544F,9F,400F,90F,81F);
        assertTrue("The indicator cancelled its wax",values[selected+2]>.1F);
        assertTrue("Detaching destroyed the drop's volume",values[selected+2]*values[selected+3]>=volume*.9F);
        float releasedY=values[selected+1];sample.invoke(motion,1F,544F,9F,400F,90F,81F);
        assertTrue("The released drop stopped falling",values[selected+1]>releasedY+2);
    }

    @Test public void earlyStartupReadsTheSavedMenuPaletteAndToleratesInvalidPreferences() throws Exception {
        java.nio.file.Path game = temporary.newFolder("early-startup").toPath();
        assertEquals(MenuPalette.OCEAN, MenuPalette.read(game));
        java.nio.file.Path settings = game.resolve("vibe/menu.properties");
        java.nio.file.Files.createDirectories(settings.getParent());
        java.nio.file.Files.write(settings, java.util.Arrays.asList("theme=ROSE"), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(MenuPalette.ROSE, MenuPalette.read(game));
        java.nio.file.Files.write(settings, java.util.Arrays.asList("theme=unknown"), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(MenuPalette.OCEAN, MenuPalette.read(game));
    }

    @Test public void packagedArtworkHasTransparentPaddingAndFifteenNonemptyFrames() throws Exception {
        BufferedImage still = GhostArt.image("ghost-static.png"), atlas = GhostArt.image("ghost-animation.png");
        assertEquals(512, still.getWidth()); assertEquals(1024, atlas.getWidth());
        for (int frame = 0; frame < 16; frame++) {
            int opaque = 0;
            for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
                int pixel = atlas.getRGB(frame % 4 * 256 + x, frame / 4 * 256 + y);
                if ((pixel >>> 24) > 100) opaque++;
                if (x < 3 || y < 3 || x > 252 || y > 252) assertEquals("Frame edge needs transparent filtering padding", 0, pixel >>> 24);
            }
            if (frame < 15) assertTrue("Missing pose " + frame, opaque > 15000);
            else assertEquals(0, opaque);
        }
        for (int size : new int[]{16, 32, 48, 256}) {
            BufferedImage image = GhostArt.image("icon-" + size + ".png");
            assertEquals(size, image.getWidth()); assertEquals(size, image.getHeight());
            ByteBuffer rgba = GhostArt.rgba(image); assertTrue(rgba.isDirect()); assertEquals(size * size * 4, rgba.remaining());
            int x = size / 2, y = size / 2, p = image.getRGB(x, y), i = (y * size + x) * 4;
            assertEquals(p >> 16 & 255, rgba.get(i) & 255); assertEquals(p >>> 24, rgba.get(i + 3) & 255);
            assertEquals(0, image.getRGB(0, 0) >>> 24);
        }
    }

    @Test public void animationUsesTimeRatherThanRenderRateAndWrapsAcrossAllFrames() {
        for (int i = 0; i < 15; i++) assertEquals(i, GhostArt.frame(i * 100, 10));
        assertEquals(0, GhostArt.frame(1500, 10)); assertEquals(14, GhostArt.frame(-1, 10));
        assertEquals(6, GhostArt.frame(1000, 6));
    }

    @Test public void tintPreservesAlphaPinkDetailsBlackOutlinesAndPaintedShading() {
        BufferedImage source = new BufferedImage(5, 1, BufferedImage.TYPE_INT_ARGB);
        int[] colors = {0xFFFFFFFF, 0xFFAAAAAA, 0xFF080809, 0xFFF29DBB, 0x60DCDCDC};
        for (int i = 0; i < colors.length; i++) source.setRGB(i, 0, colors[i]);
        BufferedImage tinted = GhostArt.recolor(source, 0xFF4080FF);
        assertTrue((tinted.getRGB(0, 0) & 255) > (tinted.getRGB(0, 0) >> 16 & 255) * 2);
        assertTrue((tinted.getRGB(0, 0) & 255) > (tinted.getRGB(1, 0) & 255));
        assertEquals(colors[2], tinted.getRGB(2, 0)); assertEquals(colors[3], tinted.getRGB(3, 0));
        for (int i = 0; i < colors.length; i++) assertEquals(colors[i] >>> 24, tinted.getRGB(i, 0) >>> 24);
    }

    @Test public void stagesReadTheRealForgeProgressStack() throws Exception {
        // Forge's public push/pop also pump a live LaunchClassLoader window. Install a real
        // bar in its real stack directly so this check does not bootstrap the game in JUnit.
        java.lang.reflect.Constructor<ProgressManager.ProgressBar> constructor = ProgressManager.ProgressBar.class.getDeclaredConstructor(String.class, int.class);
        constructor.setAccessible(true); ProgressManager.ProgressBar bar = constructor.newInstance("Ghost asset test", 7);
        java.lang.reflect.Field stack = ProgressManager.class.getDeclaredField("bars"); stack.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.List<ProgressManager.ProgressBar> bars = (java.util.List<ProgressManager.ProgressBar>)stack.get(null);
        bars.add(bar);
        try {
            GhostStartup.Stage stage = GhostStartup.stages().get(GhostStartup.stages().size() - 1);
            assertEquals("Ghost asset test", stage.title); assertEquals(7, stage.steps); assertEquals(0, stage.step);
            // Advance the real bar without starting FML's window message pump in a unit test.
            java.lang.reflect.Field step = bar.getClass().getDeclaredField("step"); step.setAccessible(true); step.setInt(bar, 3);
            stage = GhostStartup.stages().get(GhostStartup.stages().size() - 1);
            assertEquals(3 / 7F, stage.ratio(), .0001F);
        } finally { bars.remove(bar); }
        assertEquals(0, new GhostStartup.Stage(null, null, 90, 0).ratio(), 0);
    }

    @Test public void ghostsParticipatesInHudConfigurationAndLayoutPersistence() throws Exception {
        try (PathTestFixture f = new PathTestFixture()) {
            HudModule hud = new HudModule(); f.modules.registerDynamic(hud);
            assertTrue(hud.getHudElements().hasOption(HudManager.GHOSTS));
            assertFalse(hud.getHudElements().isSelectedIgnoreCase(HudManager.GHOSTS));
            assertTrue(hud.ghosts.mode.is("Animated")); assertFalse(hud.ghosts.color.isVisible());
            hud.ghosts.override.setEnabled(true); assertTrue(hud.ghosts.color.isVisible());
            hud.ghosts.mode.setValue("Static"); assertFalse(hud.ghosts.speed.isVisible());
            HudManager source = new HudManager(temporary.newFolder("source"));
            source.getElement(HudManager.GHOSTS).setScale(1.6F);
            HudManager restored = new HudManager(temporary.newFolder("restored")); restored.fromJson(source.toJson());
            assertEquals(1.6F, restored.getElement(HudManager.GHOSTS).getScale(), .001F);
            assertNotNull(restored.getElement(HudManager.CLOCK));
            assertTrue(hud.getSettings().contains(hud.ghosts.flipX));
        }
    }
}
