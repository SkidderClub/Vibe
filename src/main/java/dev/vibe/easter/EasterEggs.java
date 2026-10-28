package dev.vibe.easter;

import dev.vibe.easter.HolidayCalendar.Holiday;
import dev.vibe.module.impl.movement.FreecamModule;
import java.time.*;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/** Cosmetic, client-owned seasonal effects. Nothing here launches a native program or sends a packet. */
public final class EasterEggs {
    public static final EasterEggs INSTANCE=new EasterEggs();
    private final HolidayTimeline timeline=new HolidayTimeline(new HolidayCalendar(),new Random());
    private final ClientFireworks fireworks=new ClientFireworks();
    private final HolidayRenderer renderer=new HolidayRenderer();
    private final Random random=new Random();
    private WorldClient world;
    private boolean pumpkinDrawn;
    private EasterEggs() { }
    private static long mono(){return System.nanoTime()/1000000L;}
    public void beginFrame(){pumpkinDrawn=false;timeline.refresh(LocalDate.now());}
    public boolean halloween(){return timeline.active()==Holiday.HALLOWEEN;}
    public boolean shouldRenderPumpkin(){
        Minecraft mc=Minecraft.getMinecraft();
        // The seasonal mask belongs to the first-person view, independently of armor.
        return halloween()&&mc.theWorld!=null&&mc.thePlayer!=null&&mc.gameSettings.thirdPersonView==0;
    }
    public boolean christmas(){return timeline.active()==Holiday.CHRISTMAS;}
    public void tick(){
        Minecraft mc=Minecraft.getMinecraft();long now=mono();
        if(world!=mc.theWorld){fireworks.clear();timeline.clear();world=mc.theWorld;}
        timeline.tick(LocalDateTime.now(),now,world!=null&&mc.thePlayer!=null,mc.isGamePaused(),mc.isFullScreen());
        if(world==null||mc.thePlayer==null||mc.isGamePaused())return;
        fireworks.tick(world);drain(mc);
    }
    private void drain(Minecraft mc){
        if(mc.theWorld==null||mc.thePlayer==null)return;
        HolidayTimeline.Cue cue;while((cue=timeline.poll())!=null){
            if(cue==HolidayTimeline.Cue.CREEPER){
                double yaw=Math.toRadians(mc.thePlayer.rotationYaw);
                mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation("creeper.primed"),1F,1F,
                        (float)(mc.thePlayer.posX+Math.sin(yaw)*3),(float)(mc.thePlayer.posY+mc.thePlayer.getEyeHeight()),(float)(mc.thePlayer.posZ-Math.cos(yaw)*3)));
            }else{
                double yaw=Math.toRadians(FreecamModule.yawHook(mc.thePlayer,mc.thePlayer.rotationYaw)+(random.nextDouble()-.5)*100);
                double distance=10+random.nextDouble()*16;
                double x=FreecamModule.terrainXHook(mc.thePlayer,mc.thePlayer.posX)-Math.sin(yaw)*distance;
                double z=FreecamModule.terrainZHook(mc.thePlayer,mc.thePlayer.posZ)+Math.cos(yaw)*distance;
                double y=FreecamModule.terrainYHook(mc.thePlayer,mc.thePlayer.posY);
                if(mc.theWorld.isBlockLoaded(new BlockPos(x,y,z)))fireworks.spawn(mc.theWorld,x,y+.2,z);
            }
        }
    }
    public void command(String name){Minecraft mc=Minecraft.getMinecraft();timeline.command(name,LocalDate.now(),mono(),mc.isFullScreen());drain(mc);}
    public void renderPumpkin(ScaledResolution resolution){if(shouldRenderPumpkin()){renderer.pumpkin(resolution.getScaledWidth(),resolution.getScaledHeight());pumpkinDrawn=true;}}
    public void renderEnd(){
        Minecraft mc=Minecraft.getMinecraft();if(mc.theWorld==null||mc.thePlayer==null)return;
        long now=mono();boolean crash=timeline.crash(now,mc.isFullScreen()),console=timeline.console(now),pumpkin=shouldRenderPumpkin()&&!pumpkinDrawn;
        if(!crash&&!console&&!pumpkin)return;
        ScaledResolution resolution=new ScaledResolution(mc);mc.entityRenderer.setupOverlayRendering();
        if(pumpkin)renderPumpkin(resolution);
        if(crash)renderer.crash(resolution.getScaledWidth(),resolution.getScaledHeight(),timeline.crashProgress(now));
        else if(console)renderer.console(resolution.getScaledWidth(),resolution.getScaledHeight(),timeline.consoleProgress(now));
    }
    public void clear(){fireworks.clear();timeline.clear();world=null;renderer.close();}

    public static float rainStrength(Object world,float partial){return INSTANCE.christmas()?1F:((World)world).getRainStrength(partial);}
    public static boolean canRain(Object biome){return INSTANCE.christmas()||((BiomeGenBase)biome).canRain();}
    public static boolean snowBiome(Object biome){return INSTANCE.christmas()||((BiomeGenBase)biome).getEnableSnow();}
    public static float temperature(Object biome,Object pos){return INSTANCE.christmas()?-.5F:((BiomeGenBase)biome).getFloatTemperature((BlockPos)pos);}
    public static boolean rainOff(boolean original){return !INSTANCE.christmas()&&original;}
}
