package dev.vibe.hitmarker;

import dev.vibe.ambience.FireflyBlockSpace;
import dev.vibe.ui.RenderUtils;
import java.awt.Color;
import java.util.*;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** Fixed-tick, swept-AABB debris. No entities, packets or chunk loading. */
public final class HitParticles {
    private final List<Particle> particles=new ArrayList<>();
    private final List<Particle> view=Collections.unmodifiableList(particles);
    private final Random random;
    private World world;
    private FireflyBlockSpace space;
    private long lastEmission=Long.MIN_VALUE;
    public HitParticles(){this(new Random());}
    public HitParticles(Random random){this.random=random;}
    private void bind(World next){if(next!=world){clear();world=next;space=next==null?null:new FireflyBlockSpace(next,null);}}
    public void clear(){particles.clear();world=null;space=null;lastEmission=Long.MIN_VALUE;}
    public List<Particle> particles(){return view;}
    public boolean isWorld(World current){return world==current;}
    public void emit(World world,HitParticleSettings s,Vec3 point,Vec3 look,long now){
        bind(world);if(space==null||lastEmission!=Long.MIN_VALUE&&now>=lastEmission&&now-lastEmission<s.delay.getInt())return;
        lastEmission=now;int count=Math.min(s.count.getInt(),s.limit.getInt());
        int excess=particles.size()+count-s.limit.getInt();if(excess>0)particles.subList(0,Math.min(excess,particles.size())).clear();
        for(int i=0;i<count;i++){
            double radius=s.size.getDouble()*(.75+random.nextDouble()*.5);
            double x=point.xCoord+(random.nextDouble()-.5)*radius*.3,y=point.yCoord+(random.nextDouble()-.5)*radius*.3,z=point.zCoord+(random.nextDouble()-.5)*radius*.3;
            Particle p=new Particle(x,y,z,radius,(int)(s.lifetime.getInt()*(.8+random.nextDouble()*.35)),random.nextFloat());
            if(!space.loaded(p.bounds())||!space.collisions(p.bounds()).isEmpty())continue;
            double angle=random.nextDouble()*Math.PI*2,speed=s.speed.getDouble()*(.3+random.nextDouble()*.7);
            p.vx=Math.cos(angle)*speed-look.xCoord*s.speed.getDouble()*.25;p.vz=Math.sin(angle)*speed-look.zCoord*s.speed.getDouble()*.25;
            p.vy=s.lift.getDouble()*(.4+random.nextDouble()*.6);particles.add(p);
        }
    }
    public void tick(World world,HitParticleSettings settings){
        bind(world);if(space==null)return;
        while(particles.size()>settings.limit.getInt())particles.remove(0);
        for(Iterator<Particle> it=particles.iterator();it.hasNext();){Particle p=it.next();
            p.age+=50;if(p.age>=p.lifetime||p.y<0||p.y>256){it.remove();continue;}
            p.previousX=p.x;p.previousY=p.y;p.previousZ=p.z;
            p.vy=Math.max(-12,p.vy-settings.gravity.getDouble()*.05);
            if(!move(p,settings)){it.remove();continue;}
            double drag=Math.exp(-settings.drag.getDouble()*.05);p.vx*=drag;p.vy*=drag;p.vz*=drag;
        }
    }
    private boolean move(Particle p,HitParticleSettings settings){
        double dx=p.vx*.05,dy=p.vy*.05,dz=p.vz*.05;AxisAlignedBB box=p.bounds(),swept=box.addCoord(dx,dy,dz);
        if(!space.loaded(swept)||!space.collisions(box).isEmpty())return false;
        List<AxisAlignedBB> collisions=space.collisions(swept);
        double x=dx,y=dy,z=dz;
        for(AxisAlignedBB block:collisions)y=block.calculateYOffset(box,y);box=box.offset(0,y,0);
        for(AxisAlignedBB block:collisions)x=block.calculateXOffset(box,x);box=box.offset(x,0,0);
        for(AxisAlignedBB block:collisions)z=block.calculateZOffset(box,z);
        p.x+=x;p.y+=y;p.z+=z;
        if(x!=dx){p.x+=dx>0?-1e-7:1e-7;p.vx=-p.vx*settings.bounce.getDouble();}
        if(z!=dz){p.z+=dz>0?-1e-7:1e-7;p.vz=-p.vz*settings.bounce.getDouble();}
        p.grounded=y!=dy&&dy<0;
        if(y!=dy){p.y+=dy>0?-1e-7:1e-7;double strength=p.grounded?Math.min(.92,settings.bounce.getDouble()*1.25):settings.bounce.getDouble();
            double rebound=-p.vy*strength;p.vy=Math.abs(p.vy)<.7?0:rebound;
            if(p.grounded){double friction=1-settings.friction.getDouble();p.vx*=friction;p.vz*=friction;}}
        return true;
    }
    public static float opacity(Particle p,HitParticleSettings settings,float partial){
        float age=Math.max(0,Math.min(1,(p.age+partial*50)/p.lifetime));
        if(!settings.fade.isEnabled())return age>=1?0:1;
        float t=Math.max(0,Math.min(1,(1-age)/.4F));
        return settings.easing.is("Smooth")?t*t*(3-2*t):settings.easing.is("Ease Out")?t*t:t;
    }
    public static int color(Particle p,HitParticleSettings settings,float partial){
        float age=Math.max(0,Math.min(1,(p.age+partial*50)/p.lifetime));
        if(settings.colorMode.is("Rainbow"))return settings.color.getArgb()&0xFF000000|Color.HSBtoRGB((p.seed+age*.3F)%1,.65F,1)&0xFFFFFF;
        return settings.colorMode.is("Fade")?RenderUtils.blend(settings.color.getArgb(),settings.endColor.getArgb(),age):settings.color.getArgb();
    }
    public static final class Particle {
        public double x,y,z,previousX,previousY,previousZ,vx,vy,vz;
        public final double radius;
        public final int lifetime;
        public final float seed;
        public int age;
        public boolean grounded;
        private Particle(double x,double y,double z,double radius,int lifetime,float seed){this.x=previousX=x;this.y=previousY=y;this.z=previousZ=z;this.radius=radius;this.lifetime=lifetime;this.seed=seed;}
        public AxisAlignedBB bounds(){return new AxisAlignedBB(x-radius,y-radius,z-radius,x+radius,y+radius,z+radius);}
    }
}
