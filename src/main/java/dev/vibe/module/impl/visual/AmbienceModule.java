package dev.vibe.module.impl.visual;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.meme.HypixelModule;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.ambience.*;
import java.util.Arrays;
import net.minecraft.client.multiplayer.WorldClient;
import org.lwjgl.input.Keyboard;

/**
 * Local world-time and weather presentation.  It deliberately changes only
 * the client world; it neither sends weather/time packets nor changes world
 * colour data.
 */
public final class AmbienceModule extends Module {

    public static final String TIME="Time",WEATHER="Weather",FOG="Fog",FIREFLIES="Fireflies",MOON="Custom Moon",SUN="Custom Sun",STARS="Shooting Stars",AURORA="Polar Lights";
    public final MultiSelectSetting effects=addSetting(new MultiSelectSetting("Effects",Arrays.asList(TIME,WEATHER,FOG,FIREFLIES,MOON,SUN,STARS,AURORA),Arrays.asList(TIME)));

    private final ModeSetting timeMode = addSetting(new ModeSetting("Time Mode", "Custom", () -> effect(TIME),
            "None", "Normal", "Custom", "Dawn", "Day", "Noon", "Dusk", "Night", "Midnight", "Dynamic"));
    private final NumberSetting customTime = addSetting(new NumberSetting("Time", 6.0D, 0.0D, 24.0D, 1.0D,
            () -> effect(TIME)&&timeMode.is("Custom")));
    private final NumberSetting normalSpeed = addSetting(new NumberSetting("Time Speed", 150.0D, 10.0D, 500.0D, 1.0D,
            () -> effect(TIME)&&timeMode.is("Normal")));
    private final NumberSetting dynamicSpeed = addSetting(new NumberSetting("Dynamic Speed", 20.0D, 1.0D, 50.0D, 1.0D,
            () -> effect(TIME)&&timeMode.is("Dynamic")));
    private final ModeSetting weatherMode = addSetting(new ModeSetting("Weather Mode", "Sun", () -> effect(WEATHER),
            "Sun", "Rain", "Snow", "Thunder"));
    private final NumberSetting weatherStrength = addSetting(new NumberSetting("Weather Strength", 1.0D, 0.0D, 1.0D, 0.01D,
            () -> effect(WEATHER)&&!weatherMode.is("Sun")));

    public final FogSettings fog=new FogSettings(this::addSetting,()->effect(FOG));
    public final AmbienceSettings.Fireflies fireflies=new AmbienceSettings.Fireflies(this::addSetting,()->effect(FIREFLIES));
    public final AmbienceSettings.Celestial moon=new AmbienceSettings.Celestial(this::addSetting,()->effect(MOON),true);
    public final AmbienceSettings.Celestial sun=new AmbienceSettings.Celestial(this::addSetting,()->effect(SUN),false);
    public final AmbienceSettings.ShootingStars stars=new AmbienceSettings.ShootingStars(this::addSetting,()->effect(STARS));
    public final AmbienceSettings.Aurora aurora=new AmbienceSettings.Aurora(this::addSetting,()->effect(AURORA));
    public final AmbienceScene scene=new AmbienceScene();
    private boolean timeApplied,weatherApplied;

    private WorldClient capturedWorld;
    private long capturedTime;
    private float capturedRain;
    private float capturedThunder;
    private long animatedTime;
    private boolean hasAnimatedTime;

    public AmbienceModule() {
        super("Ambience", "Local time, weather, fog and atmospheric world effects", Category.VISUAL, Keyboard.KEY_NONE);
    }

    /** Advances animated time once per game tick. */
    public void tick() {
        if (HypixelModule.visualsSuppressed()) { onDisable(); return; }
        if (!isEnabled()) {
            scene.clear();
            return;
        }
        WorldClient world = Vibe.getInstance().getMinecraft().theWorld;
        if (world == null) {
            scene.clear();capturedWorld=null;
            return;
        }
        if (capturedWorld != world) {
            capturedWorld = world;
            capturedTime = world.getWorldTime();
            capturedRain = world.getRainStrength(0.0F);
            capturedThunder = world.getThunderStrength(0.0F);
            animatedTime = Math.floorMod(capturedTime, 24000L);
            hasAnimatedTime = true;
        }

        advanceTime();
        // Keep the client state correct for code which runs after the game
        // tick, while renderTick() below also applies it immediately before
        // each world frame. Server time packets can otherwise replace the
        // value for one rendered frame, which was the visible day flash.
        applyTime(world);
        applyWeather(world);
        scene.tick(this,world,Vibe.getInstance().getMinecraft().getRenderViewEntity());
    }

    /** Called at RenderTick START so a server update cannot flash between ticks. */
    public void renderTick() {
        if (HypixelModule.visualsSuppressed()) { onDisable(); return; }
        if (!isEnabled()) {
            return;
        }
        WorldClient world = Vibe.getInstance().getMinecraft().theWorld;
        if (world == null) {
            return;
        }
        if (capturedWorld != world) {
            capturedWorld = world;
            capturedTime = world.getWorldTime();
            capturedRain = world.getRainStrength(0.0F);
            capturedThunder = world.getThunderStrength(0.0F);
            animatedTime = Math.floorMod(capturedTime, 24000L);
            hasAnimatedTime = true;
        }
        applyTime(world);
        applyWeather(world);
    }

    private void advanceTime() {
        if (!effect(TIME)||timeMode.is("None")) {
            return;
        }
        if (!hasAnimatedTime) {
            animatedTime = 0L;
            hasAnimatedTime = true;
        }
        if (timeMode.is("Normal")) {
            animatedTime = Math.floorMod(animatedTime + normalSpeed.getInt(), 24000L);
        } else if (timeMode.is("Dynamic")) {
            animatedTime = Math.floorMod(animatedTime + dynamicSpeed.getInt(), 24000L);
        } else if (timeMode.is("Custom")) {
            animatedTime = customTime.getInt() * 1000L;
        } else if (timeMode.is("Dawn")) {
            animatedTime = 23041L;
        } else if (timeMode.is("Day")) {
            animatedTime = 2000L;
        } else if (timeMode.is("Noon")) {
            animatedTime = 6000L;
        } else if (timeMode.is("Dusk")) {
            animatedTime = 13050L;
        } else if (timeMode.is("Night")) {
            animatedTime = 16000L;
        } else if (timeMode.is("Midnight")) {
            animatedTime = 18000L;
        }
    }

    private void applyTime(WorldClient world) {
        if (!effect(TIME)||timeMode.is("None")) {
            if(timeApplied){world.setWorldTime(capturedTime);timeApplied=false;}
            return;
        }
        world.setWorldTime(animatedTime);
        timeApplied=true;
    }

    private void applyWeather(WorldClient world) {
        if(!effect(WEATHER)){
            if(weatherApplied){world.setRainStrength(capturedRain);world.setThunderStrength(capturedThunder);weatherApplied=false;}
            return;
        }
        weatherApplied=true;
        float strength = Math.max(0.0F, Math.min(1.0F, weatherStrength.getFloat()));
        if (weatherMode.is("Sun")) {
            world.setRainStrength(0.0F);
            world.setThunderStrength(0.0F);
        } else if (weatherMode.is("Rain")||weatherMode.is("Snow")) {
            world.setRainStrength(strength);
            world.setThunderStrength(0.0F);
        } else if (weatherMode.is("Thunder")) {
            world.setRainStrength(strength);
            world.setThunderStrength(strength);
        }
    }

    @Override
    protected void onDisable() {
        if (capturedWorld != null && Vibe.getInstance().getMinecraft().theWorld == capturedWorld) {
            if(timeApplied)capturedWorld.setWorldTime(capturedTime);
            if(weatherApplied){capturedWorld.setRainStrength(capturedRain);capturedWorld.setThunderStrength(capturedThunder);}
        }
        capturedWorld = null;
        animatedTime = 0L;
        hasAnimatedTime = false;
        timeApplied=weatherApplied=false;scene.clear();
    }

    public boolean effect(String name){return effects.isSelected(name);}

    /** Shared precipitation hooks affect rendering only, including warm/dry biomes and OptiFine. */
    public static boolean snowActive(){
        Vibe vibe=Vibe.getInstance();if(vibe==null||vibe.getModuleManager()==null)return false;
        AmbienceModule m=vibe.getModuleManager().getModule(AmbienceModule.class);
        return m!=null&&m.isEnabled()&&m.effect(WEATHER)&&m.weatherMode.is("Snow")&&!HypixelModule.visualsSuppressed();
    }

    public ModeSetting getTimeMode() { return timeMode; }
    public NumberSetting getCustomTime() { return customTime; }
    public NumberSetting getNormalSpeed() { return normalSpeed; }
    public NumberSetting getDynamicSpeed() { return dynamicSpeed; }
    public ModeSetting getWeatherMode() { return weatherMode; }
    public NumberSetting getWeatherStrength() { return weatherStrength; }
}
