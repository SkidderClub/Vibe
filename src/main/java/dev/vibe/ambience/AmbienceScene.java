package dev.vibe.ambience;

import dev.vibe.module.impl.visual.AmbienceModule;
import dev.vibe.module.impl.movement.FreecamModule;
import java.util.*;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.*;

/** Bounded decoration state owned by one world; no entities, packets or chunk loads. */
public final class AmbienceScene {
    public final FireflySwarm swarm=new FireflySwarm();
    private final List<Meteor> meteors=new ArrayList<>();
    private final Random random=new Random(0x57A25);
    private WorldClient world;
    private int ticks;
    private double nextStar;
    public double seconds(float partial){return (ticks+partial)/20D;}
    public List<Meteor> meteors(){return Collections.unmodifiableList(meteors);}
    public void clear(){world=null;ticks=0;nextStar=0;swarm.clear();meteors.clear();}
    public void tick(AmbienceModule module,WorldClient world,Entity view){
        if(world!=this.world){clear();this.world=world;}
        if(view==null||!world.provider.isSurfaceWorld()){swarm.clear();meteors.clear();return;}
        ticks++;double time=seconds(0);
        float night=night(world,0),rain=1-world.getRainStrength(0);
        if(module.effect(AmbienceModule.FIREFLIES)&&(!module.fireflies.nightOnly.isEnabled()||night>.015F)){
            double x=FreecamModule.terrainXHook(view,view.posX),y=FreecamModule.terrainYHook(view,view.posY),z=FreecamModule.terrainZHook(view,view.posZ);
            swarm.tick(x,y+view.getEyeHeight()*.5,z,module.fireflies,new FireflyBlockSpace(world,view));
        }else swarm.clear();
        for(Iterator<Meteor> iterator=meteors.iterator();iterator.hasNext();){Meteor star=iterator.next();if(time-star.born>star.duration)iterator.remove();}
        if(!module.effect(AmbienceModule.STARS)||rain<.05F||(module.stars.nightOnly.isEnabled()&&night<.015F)){meteors.clear();nextStar=time+1;return;}
        if(time>=nextStar){
            nextStar=time+module.stars.interval.getDouble()*(.65+random.nextDouble()*.7);
            if(meteors.size()<4)meteors.add(Meteor.create(time,random,module.stars.speed.getDouble()));
        }
    }
    public static float night(WorldClient world,float partial){return Math.max(0,Math.min(1,world.getStarBrightness(partial)*2));}
    public static final class Meteor {
        public final double born,duration;
        public final Vec3 start,tangent;
        private Meteor(double born,double duration,Vec3 start,Vec3 tangent){this.born=born;this.duration=duration;this.start=start;this.tangent=tangent;}
        public static Meteor create(double now,Random random,double speed){
            double yaw=random.nextDouble()*Math.PI*2,elevation=Math.toRadians(30+random.nextDouble()*40);
            Vec3 start=new Vec3(Math.sin(yaw)*Math.cos(elevation),Math.sin(elevation),Math.cos(yaw)*Math.cos(elevation));
            Vec3 side=new Vec3(Math.cos(yaw),0,-Math.sin(yaw));
            Vec3 down=new Vec3(Math.sin(yaw)*Math.sin(elevation),-Math.cos(elevation),Math.cos(yaw)*Math.sin(elevation));
            double sign=random.nextBoolean()?1:-1;
            Vec3 tangent=new Vec3(side.xCoord*sign*.9+down.xCoord*.42,down.yCoord*.42,side.zCoord*sign*.9+down.zCoord*.42).normalize();
            return new Meteor(now,(.9+random.nextDouble()*.5)/speed,start,tangent);
        }
        public Vec3 position(double time){double angle=(time-born)/duration*.52;return new Vec3(start.xCoord*Math.cos(angle)+tangent.xCoord*Math.sin(angle),start.yCoord*Math.cos(angle)+tangent.yCoord*Math.sin(angle),start.zCoord*Math.cos(angle)+tangent.zCoord*Math.sin(angle));}
        public float visibility(double time){double t=(time-born)/duration;return (float)Math.max(0,Math.min(1,Math.min(t/.08,(1-t)/.22)));}
    }
}
