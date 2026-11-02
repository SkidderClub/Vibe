package dev.vibe.module.impl.visual;

import dev.vibe.module.*;
import dev.vibe.setting.*;
import dev.vibe.ui.render.esp.TntPrediction;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/** Client-observed fuses and collision-aware forecasts, never simulated entities in the real world. */
public final class TntESPModule extends dev.vibe.module.Module {
    public final NumberSetting range=addSetting(new NumberSetting("Range",64,8,160,4));
    public final NumberSetting limit=addSetting(new NumberSetting("Max TNT",16,1,64,1));
    public final BooleanSetting predict=addSetting(new BooleanSetting("Predict Explosion Position",true));
    public final NumberSetting interval=addSetting(new NumberSetting("Prediction Interval",3,1,10,1,()->predict.isEnabled()));
    public final ModeSetting radiusMode=addSetting(new ModeSetting("Radius","Entity Reach","Entity Reach","Custom"));
    public final NumberSetting customRadius=addSetting(new NumberSetting("Custom Radius",8,1,16,.25,()->radiusMode.is("Custom")));
    public final ColorSetting color=addSetting(new ColorSetting("Color",0xFF80CFFF));
    public final ColorSetting urgent=addSetting(new ColorSetting("Urgent Color",0xFFFF6778));
    public final BooleanSetting fuseColors=addSetting(new BooleanSetting("Fuse Color Fade",true));
    public final NumberSetting opacity=addSetting(new NumberSetting("Line Opacity",.65,.05,1,.05));
    public final NumberSetting lineWidth=addSetting(new NumberSetting("Line Width",1.3,.5,3,.1));
    public final NumberSetting gridDensity=addSetting(new NumberSetting("Grid Density",8,4,12,2));
    public final NumberSetting gridOpacity=addSetting(new NumberSetting("Grid Opacity",.45,0,1,.05));
    public final BooleanSetting hidden=addSetting(new BooleanSetting("Occluded Lines",true));
    public final NumberSetting hiddenOpacity=addSetting(new NumberSetting("Occluded Opacity",.10,0,.4,.02,()->hidden.isEnabled()));
    public final BooleanSetting glow=addSetting(new BooleanSetting("Glow",true));
    public final NumberSetting glowStrength=addSetting(new NumberSetting("Glow Strength",.12,.02,.4,.02,()->glow.isEnabled()));
    public final BooleanSetting timer=addSetting(new BooleanSetting("Fuse Label",true));
    public final BooleanSetting path=addSetting(new BooleanSetting("Predicted Path",false));
    public final ModeSetting quality=addSetting(new ModeSetting("Quality","Balanced","Low","Balanced","High"));
    private final List<Forecast> forecasts=new ArrayList<>();
    private World world;private long next;
    public TntESPModule(){super("TNT ESP","Fuse countdown and predicted maximum entity blast reach; terrain reduces exposure",Category.VISUAL,Keyboard.KEY_NONE);}
    public double radius(){return radiusMode.is("Custom")?customRadius.getDouble():8;}
    public List<Forecast> forecasts(){return Collections.unmodifiableList(forecasts);}
    public void clear(){forecasts.clear();world=null;next=0;}
    @Override protected void onDisable(){clear();}
    public void tick(){
        Minecraft mc=Minecraft.getMinecraft();if(!isEnabled()||mc.theWorld==null||mc.thePlayer==null){onDisable();return;}
        if(world!=mc.theWorld){onDisable();world=mc.theWorld;}long now=world.getTotalWorldTime();
        forecasts.removeIf(f->f.entity.isDead||!world.loadedEntityList.contains(f.entity));if(now<next)return;next=now+interval.getInt();
        Entity camera=mc.getRenderViewEntity()==null?mc.thePlayer:mc.getRenderViewEntity();List<EntityTNTPrimed> nearest=new ArrayList<>();
        for(Entity e:world.loadedEntityList)if(e instanceof EntityTNTPrimed&&!e.isDead&&e.getDistanceSqToEntity(camera)<=range.getDouble()*range.getDouble())nearest.add((EntityTNTPrimed)e);
        nearest.sort(Comparator.comparingDouble(e->e.getDistanceSqToEntity(camera)));forecasts.clear();
        for(int i=0;i<Math.min(limit.getInt(),nearest.size());i++){EntityTNTPrimed e=nearest.get(i);forecasts.add(new Forecast(e,predict.isEnabled()?TntPrediction.simulate(world,e):TntPrediction.current(e)));}
    }
    public static final class Forecast {public final EntityTNTPrimed entity;public final TntPrediction.Result prediction;Forecast(EntityTNTPrimed e,TntPrediction.Result p){entity=e;prediction=p;}}
}
