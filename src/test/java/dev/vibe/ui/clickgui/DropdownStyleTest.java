package dev.vibe.ui.clickgui;

import dev.vibe.config.VibeConfig;
import dev.vibe.module.impl.client.ClickGuiModule;
import dev.vibe.path.PathTestFixture;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class DropdownStyleTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void moduleSortingKeepsRegistrationOrderAndPersistsThroughProfiles() throws Exception {
        try(PathTestFixture f=new PathTestFixture()){
            PathTestFixture.set(dev.vibe.Vibe.class,f.vibe,"minecraft",f.mc);
            ClickGuiModule gui=new ClickGuiModule();f.modules.registerDynamic(gui);
            dev.vibe.module.Module zeta=new NamedModule("zeta"),alpha=new NamedModule("Alpha"),beta=new NamedModule("beta");
            java.util.List<dev.vibe.module.Module> original=java.util.Arrays.asList(zeta,alpha,beta);
            for(dev.vibe.module.Module module:original)f.modules.registerDynamic(module);
            assertTrue(gui.getModuleSorting().is("Normal"));assertSame(original,ClickGuiModule.sortedModules(original));
            java.util.List<dev.vibe.module.Module> registered=new java.util.ArrayList<>(f.modules.getModules());
            gui.getModuleSorting().setValue("Alphabetic");
            assertEquals(java.util.Arrays.asList(alpha,beta,zeta),ClickGuiModule.sortedModules(original));
            assertEquals(registered,f.modules.getModules());assertEquals(java.util.Arrays.asList(zeta,alpha,beta),original);
            VibeConfig config=new VibeConfig(temp.getRoot());assertTrue(config.save("sorting",f.modules));
            gui.getModuleSorting().setValue("Normal");assertSame(original,ClickGuiModule.sortedModules(original));
            assertTrue(config.load("sorting",f.modules));assertTrue(gui.getModuleSorting().is("Alphabetic"));
        }
    }
    private static final class NamedModule extends dev.vibe.module.Module {
        NamedModule(String name){super(name,"Sorting fixture",dev.vibe.module.Category.COMBAT,0);}
    }
    @Test public void packetStaticAndFadeUseTheSelectedColors(){
        ClickGuiModule m=new ClickGuiModule();m.getTheme().setValue("PacketV3");m.getPacketColor().setValue(0xFFFF3000);m.getPacketFadeColor().setValue(0xFF0055FF);
        m.getPacketAccent().setValue("Static");assertEquals(0xFFFF3000,DropdownStyle.accent(m,1,400,9000));
        m.getPacketAccent().setValue("Fade");assertEquals(0xFFFF3000,DropdownStyle.accent(m,0,0,0));assertEquals(0xFF0055FF,DropdownStyle.accent(m,1,0,0));
        assertNotEquals(DropdownStyle.accent(m,.2F,0,0),DropdownStyle.accent(m,.2F,0,3000));
        int a=DropdownStyle.accent(m,.4F,100,1000),b=DropdownStyle.accent(m,.4F,100,1001);
        for(int shift:new int[]{0,8,16})assertTrue(Math.abs((a>>shift&255)-(b>>shift&255))<=1);
        m.getPacketAccent().setValue("Static");m.getPacketColor().setValue(0x60112233);assertEquals(0x60112233,DropdownStyle.accent(m,0,0,0));
    }
    @Test public void fadeSpeedAndRainbowAnimate(){
        ClickGuiModule m=new ClickGuiModule();m.getTheme().setValue("PacketV3");
        int slow=DropdownStyle.accent(m,.25F,0,2000);m.getPacketFadeSpeed().setValue(2D);assertNotEquals(slow,DropdownStyle.accent(m,.25F,0,2000));
        m.getPacketAccent().setValue("Rainbow");assertNotEquals(DropdownStyle.accent(m,0,0,0),DropdownStyle.accent(m,0,0,2000));
    }
    @Test public void themeSpecificControlsAreVisibleOnlyWhenRelevant(){
        ClickGuiModule m=new ClickGuiModule();m.getTheme().setValue("PacketV3");assertTrue(m.getPacketFadeColor().isVisible());assertFalse(m.getSuizideDarkMode().isVisible());
        m.getPacketAccent().setValue("Static");assertFalse(m.getPacketFadeColor().isVisible());assertFalse(m.getPacketFadeSpeed().isVisible());
        m.getTheme().setValue("SuizideX");assertTrue(m.getSuizideDarkMode().isVisible());assertFalse(m.getPacketColor().isVisible());
        DropdownStyle light=new DropdownStyle(m,0);m.getSuizideDarkMode().setValue(true);DropdownStyle dark=new DropdownStyle(m,0);
        assertTrue(light.light);assertFalse(dark.light);assertTrue((dark.body&0xFFFFFF)<0x202020);
    }
    @Test public void themesAndColorsRoundTripThroughTheRealConfig() throws Exception {
        try(PathTestFixture f=new PathTestFixture()){
            PathTestFixture.set(dev.vibe.Vibe.class,f.vibe,"minecraft",f.mc);
            ClickGuiModule m=new ClickGuiModule();f.modules.registerDynamic(m);VibeConfig config=new VibeConfig(temp.getRoot());
            for(String name:new String[]{"PacketV3","Novoline","SuizideX"}){
                m.getTheme().setValue(name);m.getPacketColor().setValue(0xFFFF8020);m.getSuizideDarkMode().setValue(true);assertTrue(config.save("theme",f.modules));
                m.getTheme().setValue("Skeet");m.getPacketColor().setValue(0xFF000000);m.getSuizideDarkMode().setValue(false);assertTrue(config.load("theme",f.modules));
                assertTrue(m.getTheme().is(name));assertEquals(0xFFFF8020,m.getPacketColor().getArgb());assertTrue(m.getSuizideDarkMode().isEnabled());
            }
        }
    }
}
