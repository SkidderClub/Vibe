package dev.vibe.ui;

import dev.vibe.module.impl.BedEspModule;
import dev.vibe.module.impl.CuteVisualsModule;
import org.junit.Test;
import static org.junit.Assert.*;

public class VisualSettingsTest {
    @Test public void maximumBedDistanceIsUnlimitedAndHidesFade() {
        BedEspModule beds = new BedEspModule();
        assertTrue(beds.isUnlimited());
        assertFalse(beds.getFadeAlpha().isVisible());
        assertEquals(1, beds.distanceAlpha(10000), 0);
        beds.getViewDistance().setValue(100D);
        assertTrue(beds.getFadeAlpha().isVisible());
        assertEquals(.5, beds.distanceAlpha(50), .00001);
        assertEquals(0, beds.distanceAlpha(101), 0);
        beds.getFadeAlpha().setEnabled(false);
        assertEquals(1, beds.distanceAlpha(100), 0);
        assertEquals(0, beds.distanceAlpha(101), 0);
    }
    @Test public void loveIsUnchangedAndSingleColorsHaveDarkerVariants() {
        CuteVisualsModule cute = new CuteVisualsModule();
        assertArrayEquals(new double[] {1,.4,.8,1}, cute.palette(1,.4,.8,.5F), 0);
        for (String preset : new String[] {"Red","Green","Blue","Yellow","Gray","Purple","LightBlue"}) {
            cute.getColorPreset().setValue(preset);
            double[] light = cute.palette(0,0,0,0), dark = cute.palette(0,0,0,1);
            for (int i=0; i<3; i++) assertEquals(light[i] * .76, dark[i], 1.0/255);
        }
    }
    @Test public void pairedAndCustomColorsRetainBothEndpointsAndAlpha() {
        CuteVisualsModule cute = new CuteVisualsModule();
        cute.getColorPreset().setValue("Bavaria");
        assertArrayEquals(new double[] {1,1,1,1}, cute.palette(0,0,0,1), 0);
        cute.getColorPreset().setValue("Custom");
        cute.getPrimaryColor().setRgba(255,0,0,128);
        cute.getSecondaryColor().setRgba(0,0,255,64);
        assertTrue(cute.getPrimaryColor().isVisible());
        assertArrayEquals(new double[]{1,0,0,128.0/255}, cute.palette(0,0,0,0), .00001);
        assertArrayEquals(new double[]{0,0,1,64.0/255}, cute.palette(0,0,0,1), .00001);
    }
}
