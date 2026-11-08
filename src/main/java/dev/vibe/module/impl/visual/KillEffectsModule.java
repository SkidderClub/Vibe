package dev.vibe.module.impl.visual;

import dev.vibe.module.*;
import dev.vibe.module.Module;
import dev.vibe.setting.*;
import dev.vibe.ui.render.HologramPose;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/** Death-confirmed visuals; despawning or merely hitting an entity never counts as a kill. */
public final class KillEffectsModule extends Module {
    public final ModeSetting mode=addSetting(new ModeSetting("Mode","Shockwave","Shattered Glass","Dissolving Silhouette","Shockwave","Holy","Helicopter"));
    public final BooleanSetting ownKills=addSetting(new BooleanSetting("Only Your Kills",true));
    public final NumberSetting creditWindow=addSetting(new NumberSetting("Hit Window (seconds)",8,1,20,1,()->ownKills.isEnabled()));
    public final NumberSetting range=addSetting(new NumberSetting("Range",48,8,96,1));
    public final NumberSetting duration=addSetting(new NumberSetting("Duration (ms)",3200,800,9000,100));
    public final NumberSetting limit=addSetting(new NumberSetting("Max Effects",6,1,12,1));
    public final ColorSetting color=addSetting(new ColorSetting("Color",0xFF8ADFFF));
    public final NumberSetting opacity=addSetting(new NumberSetting("Opacity",.8,.1,1,.05));
    public final BooleanSetting glow=addSetting(new BooleanSetting("Glow",true));
    public final NumberSetting glowStrength=addSetting(new NumberSetting("Glow Strength",.6,.1,1.5,.05,()->glow.isEnabled()));
    public final BooleanSetting throughWalls=addSetting(new BooleanSetting("Through Walls",false));
    public final NumberSetting shards=addSetting(new NumberSetting("Glass Shards",42,12,100,1,()->mode.is("Shattered Glass")));
    public final NumberSetting shardSize=addSetting(new NumberSetting("Shard Size",.16,.05,.4,.01,()->mode.is("Shattered Glass")));
    public final NumberSetting burstSpeed=addSetting(new NumberSetting("Burst Speed",2.8,.5,7,.1,()->mode.is("Shattered Glass")));
    public final NumberSetting gravity=addSetting(new NumberSetting("Shard Gravity",2.5,0,8,.1,()->mode.is("Shattered Glass")));
    public final NumberSetting dissolveScale=addSetting(new NumberSetting("Dissolve Detail",22,6,60,1,()->mode.is("Dissolving Silhouette")));
    public final NumberSetting dissolveRise=addSetting(new NumberSetting("Dissolve Rise",.7,0,3,.1,()->mode.is("Dissolving Silhouette")));
    public final BooleanSetting skin=addSetting(new BooleanSetting("Original Skin",true,()->!mode.is("Shockwave")&&!mode.is("Shattered Glass")));
    public final NumberSetting radius=addSetting(new NumberSetting("Wave Radius",3,.5,8,.1,()->mode.is("Shockwave")));
    public final NumberSetting waveWidth=addSetting(new NumberSetting("Wave Width",.16,.04,.6,.02,()->mode.is("Shockwave")));
    public final NumberSetting waves=addSetting(new NumberSetting("Waves",3,1,5,1,()->mode.is("Shockwave")));
    public final NumberSetting rise=addSetting(new NumberSetting("Holy Rise",4,1,10,.25,()->mode.is("Holy")));
    public final ColorSetting holyColor=addSetting(new ColorSetting("Holy Color",0xFFFFE4A6,()->mode.is("Holy")));
    public final BooleanSetting wings=addSetting(new BooleanSetting("Wings",true,()->mode.is("Holy")));
    public final BooleanSetting halo=addSetting(new BooleanSetting("Halo",true,()->mode.is("Holy")));
    public final BooleanSetting beam=addSetting(new BooleanSetting("Light Beam",true,()->mode.is("Holy")));
    public final NumberSetting lift=addSetting(new NumberSetting("Helicopter Lift",9,3,20,.5,()->mode.is("Helicopter")));
    public final NumberSetting helicopterSize=addSetting(new NumberSetting("Helicopter Size",1,.5,2,.1,()->mode.is("Helicopter")));
    public final NumberSetting rope=addSetting(new NumberSetting("Rope Length",3,1.5,6,.25,()->mode.is("Helicopter")));
    public final NumberSetting rotorSpeed=addSetting(new NumberSetting("Rotor Speed",16,4,30,1,()->mode.is("Helicopter")));
    public final ColorSetting helicopterColor=addSetting(new ColorSetting("Helicopter Color",0xFF384C43,()->mode.is("Helicopter")));
    private final IdentityHashMap<EntityPlayer,Sample> samples=new IdentityHashMap<>();
    private final Map<UUID,Long> attacks=new HashMap<>();
    private final List<Death> deaths=new ArrayList<>();
    private final List<Death> view=Collections.unmodifiableList(deaths);
    private World world;
    public KillEffectsModule(){super("KillEffects","Glass, dissolving ghosts, shockwaves and cinematic death effects",Category.VISUAL,Keyboard.KEY_NONE);}
    public void attack(EntityLivingBase target){if(isEnabled()&&target instanceof EntityPlayer){Minecraft mc=Minecraft.getMinecraft();if(world!=mc.theWorld){clear();world=mc.theWorld;}long now=System.currentTimeMillis();attacks.put(target.getUniqueID(),now);observe((EntityPlayer)target,now);}}
    public void capture(EntityPlayer player,HologramPose pose){if(!isEnabled()||player.getHealth()<=0||player.deathTime>0)return;Sample sample=samples.get(player);if(sample!=null)sample.pose=pose;}
    public void tick(){Minecraft mc=Minecraft.getMinecraft();if(!isEnabled()||mc.theWorld==null||mc.thePlayer==null){clear();return;}if(world!=mc.theWorld){clear();world=mc.theWorld;}
        long now=System.currentTimeMillis();deaths.removeIf(d->now-d.born>=d.duration);attacks.values().removeIf(at->now-at>creditWindow.getDouble()*1000);
        for(Object o:mc.theWorld.playerEntities){EntityPlayer p=(EntityPlayer)o;if(p!=mc.thePlayer&&p.getDistanceSqToEntity(mc.thePlayer)<=range.getDouble()*range.getDouble())observe(p,now);}
        samples.entrySet().removeIf(e->now-e.getValue().seen>2000);while(deaths.size()>limit.getInt())deaths.remove(0);
    }
    public void observe(EntityPlayer player,long now){
        Sample sample=samples.get(player);boolean dead=player.getHealth()<=0||player.deathTime>0;
        if(sample==null){samples.put(player,new Sample(now,!dead));return;}sample.seen=now;
        if(!dead){sample.alive=true;sample.played=false;return;}
        if(!sample.alive||sample.played)return;sample.played=true;
        Long hit=attacks.get(player.getUniqueID());if(ownKills.isEnabled()&&(hit==null||now-hit>creditWindow.getDouble()*1000))return;
        while(deaths.size()>=limit.getInt())deaths.remove(0);
        HologramPose pose=sample.pose==null||now-sample.pose.created>500?HologramPose.playerPose(player,now):sample.pose;
        deaths.add(new Death(player.getUniqueID(),player.posX,player.posY,player.posZ,player.renderYawOffset,now,duration.getInt(),mode.getValue(),pose,player.getUniqueID().getLeastSignificantBits()^now));
    }
    public List<Death> deaths(){return view;}
    public boolean hideCorpse(EntityPlayer p){if(!isEnabled()||p.deathTime<=0&&p.getHealth()>0)return false;for(Death d:deaths)if(d.id.equals(p.getUniqueID())&&!d.mode.equals("Shockwave")&&d.pose!=null)return true;return false;}
    @Override protected void onDisable(){clear();}
    public void clear(){world=null;samples.clear();attacks.clear();deaths.clear();}
    private static final class Sample{long seen;boolean alive,played;HologramPose pose;Sample(long now,boolean alive){seen=now;this.alive=alive;}}
    public static final class Death{
        public final UUID id;public final double x,y,z;public final float yaw;public final long born,seed;public final int duration;public final String mode;public final HologramPose pose;
        public Death(UUID id,double x,double y,double z,float yaw,long born,int duration,String mode,HologramPose pose,long seed){this.id=id;this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.born=born;this.duration=duration;this.mode=mode;this.pose=pose;this.seed=seed;}
        public float progress(long now){return Math.max(0,Math.min(1,(now-born)/(float)duration));}
    }
}
