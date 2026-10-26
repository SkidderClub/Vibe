package dev.vibe.config;

import dev.vibe.module.impl.visual.*;
import dev.vibe.path.PathTestFixture;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class FogPaletteTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void fogSharesCuteVisualsPresetsAndCustomEndpoints() {
        FogModule fog=new FogModule();CuteVisualsModule cute=new CuteVisualsModule();
        fog.color.setValue(0xFF123456);fog.secondaryColor.setValue(0xFFABCDEF);
        cute.getPrimaryColor().setValue(0xFF123456);cute.getSecondaryColor().setValue(0xFFABCDEF);
        for(String preset:cute.getColorPreset().getModes()){
            assertTrue(fog.tint.getModes().contains(preset));if(preset.equals("Love"))continue;
            fog.tint.setValue(preset);cute.getColorPreset().setValue(preset);
            assertRgb(fog.primaryColor(),cute.palette(1,0,0,0));assertRgb(fog.secondaryColor(),cute.palette(1,0,0,1));assertTrue(fog.twoTone());
        }
        fog.tint.setValue("Love");assertFalse(fog.twoTone());assertTrue(fog.speed.isVisible());assertFalse(fog.fadeSpeed.isVisible());
        fog.tint.setValue("Custom");assertTrue(fog.color.isVisible());assertTrue(fog.secondaryColor.isVisible());assertTrue(fog.fadeSpeed.isVisible());
    }
    @Test public void legacyRainbowAndSingleColorProfilesKeepTheirAppearance() throws Exception {
        try(PathTestFixture fixture=new PathTestFixture()){
            PathTestFixture.set(dev.vibe.Vibe.class,fixture.vibe,"minecraft",fixture.mc);
            PathTestFixture.set(net.minecraft.client.Minecraft.class,fixture.mc,"session",new net.minecraft.util.Session("Alex","11111111111111111111111111111111","","legacy"));
            FogModule fog=new FogModule();fixture.modules.registerDynamic(fog);
            VibeConfig config=new VibeConfig(temporary.newFolder());Files.createDirectories(config.getDirectory().toPath());
            Path profile=config.getDirectory().toPath().resolve("legacy.json");
            for(String mode:new String[]{"Rainbow","Custom"}){
                String json="{\"modules\":[{\"id\":\"fog\",\"settings\":{\"Color Mode\":\""+mode+"\",\"Fog Color\":-65536}}]}";
                Files.write(profile,Collections.singleton(json),StandardCharsets.UTF_8);assertTrue(config.load("legacy",fixture.modules));
                assertEquals(mode.equals("Rainbow")?"Love":"Custom",fog.tint.getValue());assertEquals(0xFFFF0000,fog.color.getArgb());assertEquals(fog.color.getArgb(),fog.secondaryColor.getArgb());
            }
            fog.secondaryColor.setValue(0xFF0000FF);assertTrue(config.save("modern",fixture.modules));fog.secondaryColor.setValue(0xFF00FF00);
            assertTrue(config.load("modern",fixture.modules));assertEquals(0xFF0000FF,fog.secondaryColor.getArgb());
        }
    }
    private static void assertRgb(int expected,double[] actual){assertEquals((expected>>16&255)/255D,actual[0],.0001);assertEquals((expected>>8&255)/255D,actual[1],.0001);assertEquals((expected&255)/255D,actual[2],.0001);}
}
