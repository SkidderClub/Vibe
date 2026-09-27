package dev.vibe.game.gta8;

import org.junit.Test;

import static org.junit.Assert.*;

/** Checks that the daylight calibration stays physically plausible through a day. */
public class Gta8AtmosphereTest {
    @Test public void daylightIsBalanced() {
        Gta8Atmosphere a = new Gta8Atmosphere();
        Gta8Weather w = new Gta8Weather();
        w.set(Gta8Weather.Type.CLEAR);
        a.update(13, w);
        assertTrue("Noon sun should dominate the sky ambient", Gta8Atmosphere.luma(a.lightColor) > 2.5 * Gta8Atmosphere.luma(a.skyAmbient));
        assertTrue(a.sunDir[1] > .8);
        assertEquals(0, a.night, .01);
        a.update(23, w);
        assertTrue(a.night > .95);
        a.update(6.5, w);
        assertTrue("The sun rises around 6:00", a.sunDir[1] > 0);
        a.update(20.5, w);
        assertTrue("The sun sets around 20:00", a.sunDir[1] < 0);
    }
}
