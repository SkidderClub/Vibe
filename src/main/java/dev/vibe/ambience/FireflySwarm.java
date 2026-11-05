package dev.vibe.ambience;

import java.util.*;
import net.minecraft.util.AxisAlignedBB;

/** Fixed-tick wandering with swept block collisions; rendering never queries the world. */
public final class FireflySwarm {
    public interface Space {
        boolean available(double x,double y,double z);
        List<AxisAlignedBB> collisions(AxisAlignedBB bounds);
        default boolean loaded(AxisAlignedBB bounds){return true;}
    }
    public static final int HISTORY=40;
    private final List<Fly> flies=new ArrayList<>();
    private final Random random=new Random(0x71F1E5);
    public List<Fly> flies(){return Collections.unmodifiableList(flies);}
    public void clear(){flies.clear();}
    public void tick(double x,double y,double z,AmbienceSettings.Fireflies settings,Space space){
        int count=settings.count.getInt();double range=settings.radius.getDouble(),speed=.065*settings.speed.getDouble();
        while(flies.size()>count)flies.remove(flies.size()-1);
        Iterator<Fly> it=flies.iterator();
        while(it.hasNext()){
            Fly fly=it.next();double dx=x-fly.x,dy=y+.8-fly.y,dz=z-fly.z;
            if(dx*dx+dy*dy+dz*dz>range*range*3.2||fly.age>fly.lifetime||!space.available(fly.x,fly.y,fly.z)||!space.collisions(fly.box()).isEmpty()){it.remove();continue;}
            fly.previousX=fly.x;fly.previousY=fly.y;fly.previousZ=fly.z;
            double t=fly.age*.028+fly.seed;
            double home=Math.max(0,Math.sqrt(dx*dx+dz*dz)/range-.45);
            fly.vx=fly.vx*.91+(Math.sin(t*.91)+Math.cos(t*1.57)*.45+dx*home*.12)*speed*.09;
            fly.vy=fly.vy*.91+(Math.sin(t*1.13)*.35+dy*.09)*speed*.09;
            fly.vz=fly.vz*.91+(Math.cos(t*.83)+Math.sin(t*1.41)*.45+dz*home*.12)*speed*.09;
            fly.move(space);
            fly.record();fly.age++;
        }
        // Bound spawning attempts, including indoors and in unloaded chunks.
        for(int attempt=0;attempt<8&&flies.size()<count;attempt++){
            double angle=random.nextDouble()*Math.PI*2,r=Math.sqrt(random.nextDouble())*range;
            Fly fly=new Fly(x+Math.cos(angle)*r,y-2+random.nextDouble()*5,z+Math.sin(angle)*r,random.nextDouble()*100,400+random.nextInt(400));
            if(space.available(fly.x,fly.y,fly.z)&&space.collisions(fly.box()).isEmpty())flies.add(fly);
        }
    }
    public static final class Fly {
        private static final double RADIUS=.07;
        public double x,y,z,previousX,previousY,previousZ,vx,vy,vz;
        public final double seed;
        public final int lifetime;
        public int age,head,samples;
        public final double[] trailX=new double[HISTORY],trailY=new double[HISTORY],trailZ=new double[HISTORY];
        public Fly(double x,double y,double z,double seed,int lifetime){this.x=previousX=x;this.y=previousY=y;this.z=previousZ=z;this.seed=seed;this.lifetime=lifetime;record();}
        public AxisAlignedBB box(){return new AxisAlignedBB(x-RADIUS,y-RADIUS,z-RADIUS,x+RADIUS,y+RADIUS,z+RADIUS);}
        public void move(Space space){
            AxisAlignedBB box=box();double dx=vx,dy=vy,dz=vz;
            AxisAlignedBB swept=box.addCoord(dx,dy,dz);
            if(!space.loaded(swept)){vx=-vx*.65;vy=-vy*.65;vz=-vz*.65;return;}
            List<AxisAlignedBB> solid=space.collisions(swept);
            for(AxisAlignedBB wall:solid)dy=wall.calculateYOffset(box,dy);box=box.offset(0,dy,0);
            for(AxisAlignedBB wall:solid)dx=wall.calculateXOffset(box,dx);box=box.offset(dx,0,0);
            for(AxisAlignedBB wall:solid)dz=wall.calculateZOffset(box,dz);
            if(dx!=vx)vx=-vx*.65;if(dy!=vy)vy=-vy*.65;if(dz!=vz)vz=-vz*.65;
            x+=dx;y+=dy;z+=dz;
        }
        private void record(){head=(head+1)%HISTORY;trailX[head]=x;trailY[head]=y;trailZ[head]=z;samples=Math.min(HISTORY,samples+1);}
        public int sample(int back){return Math.floorMod(head-back,HISTORY);}
        public float visibility(float partial){return Math.max(0,Math.min(1,Math.min((age+partial)/16F,(lifetime-age-partial)/30F)));}
    }
}
