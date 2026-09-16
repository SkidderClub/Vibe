package dev.vibe.ui;

import dev.vibe.module.impl.*;
import dev.vibe.setting.Setting;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EspAppearanceTest {
    @Test public void profilesAreIndependentAndAllColorsSupportContext(){
        EspModule esp=new EspModule();esp.getModes().toggle("2D");esp.getModes().toggle("Chams");
        assertEquals(0,esp.resolvedProfile(1));
        esp.getFriendsProfile().getUsePlayerDefaults().setValue(false);
        esp.getEditProfile().setValue("Friends");
        assertEquals(1,esp.resolvedProfile(1));assertTrue(esp.get2D(1).name.scale.isVisible());assertFalse(esp.get2D().name.scale.isVisible());
        esp.get2D(1).name.scale.setValue(2D);assertEquals(1,esp.get2D().name.scale.getDouble(),0);
        esp.getChams(1,true).getColor().getEntityMode().setValue("Team");
        dev.vibe.setting.ColorSetting color=esp.getChams(1,true).getColor();
        color.setValue(0x80112233);assertEquals(0x8055AAFF,color.resolve(0xFF55AAFF,false));
        assertEquals(0x80112233,color.resolve(0,false));
        color.getHurtColor().setValue(0xCCFF0000);color.getHurtOverride().setValue(true);
        assertEquals(0xCCFF0000,color.resolve(0xFF55AAFF,true));
        assertNotEquals(color,esp.getChams(2,true).getColor());
        for(int p=0;p<3;p++)for(Esp2DSettings.Element e:esp.get2D(p).elements)assertFalse(e.backgroundEnabled.isEnabled());
        assertTrue(EspLayout.scale(2,1)<EspLayout.scale(10,1));
        assertEquals(EspLayout.scale(10,1)*2,EspLayout.scale(20,1),.00001);
    }
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
