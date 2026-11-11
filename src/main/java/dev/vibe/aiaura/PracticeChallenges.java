package dev.vibe.aiaura;

import java.util.*;

/** Exercise scores describe observed play; demonstration coverage is a separate measurement. */
public final class PracticeChallenges {
    public enum Mode { FREE("Free practice"),SPACING("Spacing"),HEIGHT("Height changes"),SWITCH("Target switches"),PROJECTILES("Projectile dodges");public final String label;Mode(String s){label=s;}}
    public static final class Arrow {public double x,y,z,px,py,pz,vx,vy,vz;public int age;public boolean hit,near;}
    public Mode mode=Mode.FREE;
    public final List<Arrow> arrows=new ArrayList<>();
    public int samples,goodSpacing,heightHits,switchHits,avoided,collisions;private long lastTarget=-1;private int lastHitTick;private final Set<Integer> demonstrated=new HashSet<>();
    public void reset(){arrows.clear();samples=goodSpacing=heightHits=switchHits=avoided=collisions=0;lastTarget=-1;demonstrated.clear();}
    public void hit(RecorderArena arena,RecorderArena.Enemy enemy){if(Math.abs(enemy.y-arena.y)>.55)heightHits++;if(lastTarget!=-1&&lastTarget!=enemy.id&&arena.ticks-lastHitTick<80)switchHits++;lastTarget=enemy.id;lastHitTick=arena.ticks;}
    public void tick(RecorderArena a){
        RecorderArena.Enemy nearest=null;double distance=Double.MAX_VALUE;for(RecorderArena.Enemy e:a.enemies)if(e.health>0){double d=Math.sqrt((a.x-e.x)*(a.x-e.x)+(a.y-e.y)*(a.y-e.y)+(a.z-e.z)*(a.z-e.z));if(d<distance){nearest=e;distance=d;}}
        if(nearest!=null){samples++;if(distance>=2.3&&distance<=3.25)goodSpacing++;if(!a.preview){int reach=distance<2.3?0:distance<=3.25?1:2,height=a.y-nearest.y<-.55?0:a.y-nearest.y>.55?2:1,moving=(a.keys&15)==0?0:1;demonstrated.add(reach+height*3+moving*9);}}
        if(mode==Mode.PROJECTILES&&a.ticks%60==0&&nearest!=null&&arrows.size()<8){Arrow p=new Arrow();p.x=p.px=nearest.x;p.y=p.py=nearest.y+1.5;p.z=p.pz=nearest.z;double dx=a.x-p.x,dy=a.y+1-p.y,dz=a.z-p.z,d=Math.max(.01,Math.sqrt(dx*dx+dy*dy+dz*dz));p.vx=dx/d*.7;p.vy=dy/d*.7+d*.025;p.vz=dz/d*.7;arrows.add(p);}
        for(Iterator<Arrow> it=arrows.iterator();it.hasNext();){Arrow p=it.next();p.px=p.x;p.py=p.y;p.pz=p.z;p.x+=p.vx;p.y+=p.vy;p.z+=p.vz;p.vx*=.99;p.vy=p.vy*.99-.05;p.vz*=.99;p.age++;
            if(Math.hypot(p.x-a.x,p.z-a.z)<3)p.near=true;
            boolean wall=false;for(RecorderArena.Pillar b:a.pillars)if(RecorderArena.intersect(p.px,p.py,p.pz,p.x,p.y,p.z,b.x-b.width/2,0,b.z-b.width/2,b.x+b.width/2,b.height,b.z+b.width/2)!=null){wall=true;break;}
            boolean hit=!wall&&RecorderArena.intersect(p.px,p.py,p.pz,p.x,p.y,p.z,a.x-.4,a.y-.1,a.z-.4,a.x+.4,a.y+1.9,a.z+.4)!=null;
            if(hit){collisions++;p.hit=true;}
            if(hit||wall||p.y<0||p.age>100){if(!p.hit&&p.near&&!wall)avoided++;it.remove();}
        }
    }
    public int score(){switch(mode){case SPACING:return samples==0?0:Math.min(100,Math.round(100F*goodSpacing/samples));case HEIGHT:return Math.min(100,heightHits*10);case SWITCH:return Math.min(100,switchHits*10);case PROJECTILES:return avoided+collisions==0?0:Math.round(100F*avoided/(avoided+collisions));default:return 0;}}
    public int coverage(){return Math.round(demonstrated.size()*100F/18);}
}
