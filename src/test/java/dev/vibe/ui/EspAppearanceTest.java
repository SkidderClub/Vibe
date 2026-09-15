package dev.vibe.ui;

import dev.vibe.module.impl.*;
import dev.vibe.setting.Setting;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EspAppearanceTest {
    @Test public void disabledModesElementsAndInheritedTextHideDetails() {
        EspModule esp=new EspModule();Esp2DSettings s=esp.get2D();
        assertFalse(s.name.scale.isVisible());esp.getModes().toggle("2D");assertTrue(s.name.scale.isVisible());
        assertFalse(s.name.text.font.isVisible());s.name.useDefaultText.setValue(false);assertTrue(s.name.text.font.isVisible());
        s.name.enabled.setValue(false);assertFalse(s.name.scale.isVisible());assertFalse(s.name.text.font.isVisible());
        assertFalse(esp.getSkeletal().getColor().isVisible());esp.getModes().toggle("Skeletal");assertTrue(esp.getSkeletal().getColor().isVisible());
        assertFalse(s.global.count.isVisible());s.box.color.mode.setValue("Global Gradient");assertTrue(s.global.count.isVisible());
        s.box.enabled.setValue(false);assertFalse(s.global.count.isVisible());
        for(Esp2DSettings.Element e:s.elements)e.enabled.setValue(false);assertFalse(s.text.font.isVisible());
        Set<String> keys=new HashSet<String>();for(Setting<?> setting:esp.getSettings())assertTrue("Duplicate persisted setting "+setting.getRawName(),keys.add(setting.getRawName()));
    }
    @Test public void gradientSortsStopsHonorsAlphaAndDirectionAndLoopsContinuously() {
        EspModule esp=new EspModule();Esp2DSettings.Gradient g=esp.get2D().global;
        g.direction.setValue(0D);g.count.setValue(3D);
        g.positions.get(0).setValue(1D);g.colors.get(0).setValue(0x000000FF);
        g.positions.get(1).setValue(0D);g.colors.get(1).setValue(0xFFFF0000);
        g.positions.get(2).setValue(.5);g.colors.get(2).setValue(0x8000FF00);
        EspGradient gradient=new EspGradient(g,0);
        assertEquals(0xFFFF0000,gradient.sample(0,50,100,100));assertEquals(0x000000FF,gradient.sample(100,50,100,100));
        assertEquals(0x8000FF00,gradient.sample(50,50,100,100));
        g.direction.setValue(90D);assertEquals(0x8000FF00,new EspGradient(g,0).sample(15,50,100,100));
        assertEquals(EspGradient.triangle(-.001F),EspGradient.triangle(.001F),.00001);
        g.speed.setValue(.2);assertEquals(new EspGradient(g,0).sample(25,50,100,100),new EspGradient(g,10).sample(25,50,100,100));
    }
}
