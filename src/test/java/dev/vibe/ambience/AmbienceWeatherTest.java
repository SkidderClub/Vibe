package dev.vibe.ambience;

import dev.vibe.Vibe;
import dev.vibe.easter.EasterEggs;
import dev.vibe.module.impl.visual.AmbienceModule;
import dev.vibe.path.PathTestFixture;
import java.util.Collections;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;
import net.minecraft.world.biome.BiomeGenBase;
import org.junit.Test;
import static org.junit.Assert.*;

public class AmbienceWeatherTest {
    @Test public void snowUsesConfiguredStrengthInWarmBiomesAndRestoresTheWorld()throws Exception{
        try(PathTestFixture fixture=new PathTestFixture()){
            PathTestFixture.set(Vibe.class,fixture.vibe,"minecraft",fixture.mc);
            WeatherWorld world=PathTestFixture.allocate(WeatherWorld.class);world.time=6000;world.rain=.35F;world.thunder=.2F;fixture.mc.theWorld=world;
            AmbienceModule ambience=new AmbienceModule();fixture.modules.registerDynamic(ambience);
            assertFalse(ambience.getWeatherMode().getModes().contains("None"));assertTrue(ambience.getWeatherMode().getModes().contains("Snow"));
            ambience.effects.setValue(Collections.singleton(AmbienceModule.WEATHER));ambience.getWeatherMode().setValue("Snow");ambience.getWeatherStrength().setValue(.6D);ambience.setEnabled(true);
            EasterEggs.INSTANCE.command("off");ambience.renderTick();
            assertEquals(.6F,world.rain,.0001F);assertEquals(0,world.thunder,0);assertTrue(AmbienceModule.snowActive());
            assertTrue(EasterEggs.canRain(BiomeGenBase.desert));assertTrue(EasterEggs.snowBiome(BiomeGenBase.desert));
            assertEquals(-.5F,EasterEggs.temperature(BiomeGenBase.desert,new BlockPos(0,64,0)),0);assertFalse(EasterEggs.rainOff(true));
            assertFalse("Snow must not change biome data",BiomeGenBase.desert.getEnableSnow());
            ambience.effects.setValue(Collections.emptySet());ambience.renderTick();
            assertEquals(.35F,world.rain,.0001F);assertEquals(.2F,world.thunder,.0001F);assertFalse(AmbienceModule.snowActive());
            assertFalse(EasterEggs.canRain(BiomeGenBase.desert));assertTrue(EasterEggs.rainOff(true));
            assertEquals(6000,world.time);
        }
    }
    public static final class WeatherWorld extends WorldClient {
        long time;float rain,thunder;
        private WeatherWorld(){super(null,null,0,null,null);}
        @Override public long getWorldTime(){return time;}
        @Override public void setWorldTime(long value){time=value;}
        @Override public float getRainStrength(float partial){return rain;}
        @Override public void setRainStrength(float value){rain=value;}
        @Override public float getThunderStrength(float partial){return thunder;}
        @Override public void setThunderStrength(float value){thunder=value;}
    }
}
