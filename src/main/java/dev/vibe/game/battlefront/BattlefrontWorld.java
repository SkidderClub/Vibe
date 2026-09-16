package dev.vibe.game.battlefront;

import java.util.*;
import static dev.vibe.game.battlefront.BattlefrontContent.*;

/** Deterministic height field shared by rendering, movement, shadows and ballistics. */
public final class BattlefrontWorld {
    public static final double LIMIT=224, STEP=2;
    public static final int TERRAIN_EDGE=240, GRID_SIZE=48;
    public final Scenario scenario;
    public final List<Prop> props=new ArrayList<Prop>();
    public final List<BattlefrontArchitecture.Building> buildings=new ArrayList<BattlefrontArchitecture.Building>();
    public final double[][] posts,bases;
    public final String[] names;
    private final List<Prop>[][] grid;
    private final List<BattlefrontArchitecture.Part>[][] architecture;
    private double[][] elevations;
    public static final class Prop {
        public final String type; public final double x,y,z,size,radius,height; public final int variant;
        Prop(String t,double x,double y,double z,double s,double r,double h,int v){type=t;this.x=x;this.y=y;this.z=z;size=s;radius=r;height=h;variant=v;}
    }
    @SuppressWarnings("unchecked") public BattlefrontWorld(Scenario scenario){
        this.scenario=scenario;
        posts=scenario==Scenario.GEONOSIS?new double[][]{{-118,112},{112,0},{-66,-139}}:new double[][]{{104,118},{-118,-8},{86,-136}};
        bases=scenario==Scenario.GEONOSIS?new double[][]{{-90,195},{50,-195}}:new double[][]{{60,196},{-25,-196}};
        names=scenario==Scenario.GEONOSIS?new String[]{"Dustfall relay","Petranaki crossroads","Northern foundry approach"}:new String[]{"Ranger landing","Fern valley crossing","Shield relay clearing"};
        grid=(List<Prop>[][])new List[GRID_SIZE][GRID_SIZE];for(int x=0;x<GRID_SIZE;x++)for(int z=0;z<GRID_SIZE;z++)grid[x][z]=new ArrayList<Prop>();
        architecture=(List<BattlefrontArchitecture.Part>[][])new List[GRID_SIZE][GRID_SIZE];for(int x=0;x<GRID_SIZE;x++)for(int z=0;z<GRID_SIZE;z++)architecture[x][z]=new ArrayList<BattlefrontArchitecture.Part>();
        if(scenario==Scenario.GEONOSIS){
            building("Droid assembly works","factory",-35,-77,10,8,.22);
            building("Republic command post","command",55,65,7,6,.22);
            building("Canyon supply station","command",-68,40,6,5,.22);
            building("Stalgasin reactor hall","power",-155,-155,9,8,.22);
            building("Outer foundry annex","factory",135,-160,12,10,.22);
            building("Republic forward armory","armory",145,145,9,7,.22);
            building("Dustfall field hospital","medical",-155,148,8,7,.22);
            building("Petranaki barracks","barracks",165,15,10,7,.22);
            building("Western excavation depot","armory",-174,-35,8,7,.22);
            building("Canyon observatory","command",-100,-170,7,6,.22);
            building("Droid repair bay","medical",65,-176,8,7,.22);
            building("Southern relay station","power",58,173,8,6,.22);
        }else{
            building("Shield generator bunker","bunker",-18,-75,10,7,.22);
            building("Bright Tree lodge","village",-49,9,5,5,9.4);
            building("Hunter's watch","village",-69,-18,5,5,9.4);
            building("Imperial communications","command",65,-73,7,6,.22);
            building("Rebel field hospital","medical",-64,70,7,6,.22);
            building("Moonwood great hall","village",-154,-148,7,7,9.4);
            building("Canopy scout lodge","village",-165,145,5,6,9.4);
            building("Imperial power station","power",145,-156,10,8,.22);
            building("Eastern garrison","barracks",166,23,10,8,.22);
            building("Rebel ranger armory","armory",144,150,8,7,.22);
            building("Fern valley sanctuary","medical",-170,-30,8,6,.22);
            building("Northern listening post","command",-78,-175,7,6,.22);
            building("Shield relay annex","bunker",70,-178,9,7,.22);
            building("Southern supply lodge","village",48,174,6,6,9.4);
        }
        // Cache the exact grid used by both triangles and collision rays. Larger maps must
        // not repeat all building/terrain blending work for every bullet sample.
        int samples=TERRAIN_EDGE*2/(int)STEP+1;
        double[][] heights=new double[samples][samples];
        for(int x=0;x<samples;x++)for(int z=0;z<samples;z++)heights[x][z]=sample(x*STEP-TERRAIN_EDGE,z*STEP-TERRAIN_EDGE);
        elevations=heights;
        Random r=new Random(1707+scenario.ordinal()*31);
        for(int i=0;i<(scenario==Scenario.ENDOR?940:430);i++){
            double x=r.nextDouble()*440-220,z=r.nextDouble()*440-220;
            if(clearLane(x,z,6))continue;
            if(scenario==Scenario.ENDOR)add("tree",x,z,12+r.nextDouble()*20,1.2+r.nextDouble()*.8,30,r.nextInt(4));
            else add("spire",x,z,3+r.nextDouble()*15,2+r.nextDouble()*2,20,r.nextInt(4));
        }
        for(int i=0;i<410;i++){
            double x=r.nextDouble()*438-219,z=r.nextDouble()*438-219;
            if(!clearLane(x,z,3))add("rock",x,z,1+r.nextDouble()*2.5,1.2,2.2,r.nextInt(4));
        }
        for(int i=0;i<1650;i++){
            double x=r.nextDouble()*440-220,z=r.nextDouble()*440-220;
            boolean inside=false;for(BattlefrontArchitecture.Building b:buildings)if(b.apron(x,z)<1){inside=true;break;}
            if(!inside)add("grass",x,z,1,0,0,i);
        }
        for(int p=0;p<posts.length;p++){
            double x=posts[p][0],z=posts[p][1];
            for(int i=0;i<5;i++)add("crate",x-13+i*2.8,z+9,1.6,.95,1.8,i);
            add("barricade",x+12,z-8,3,2.3,1.4,p);
            add("antenna",x-11,z-9,7,.5,8,p);
        }
        for(double[] base:bases)for(int face:new int[]{-1,1}){add("beacon",base[0]+face*12,base[1]-8,1,0,0,0);add("beacon",base[0]+face*12,base[1]+8,1,0,0,0);}
        if(scenario==Scenario.GEONOSIS){
            add("gunship",-43,77,1,6,5,0);
            add("crawler",48,-33,1,6,6,0);add("wreck",41,35,1,5,3,0);
            for(int i=0;i<10;i++)add("spire",-215+i*47,-219,26+i%3*8,5,40,i);
            add("crawler",179,-117,1,6,6,1);add("wreck",-97,85,1,5,4,1);add("gunship",185,173,1,7,5,1);
        }else{
            add("walker",47,-35,1,4,10,0);
            add("shuttle",-42,78,1,7,10,0);
            add("walker",181,-112,1,4,10,1);add("shuttle",189,180,1,7,14,1);
            for(int i=0;i<160;i++){double x=r.nextDouble()*438-219,z=r.nextDouble()*438-219;if(!clearLane(x,z,2))add("fern",x,z,1.7,0,0,i);}
        }
        // Each remote district has its own maintenance yard and readable approach lights.
        for(BattlefrontArchitecture.Building b:buildings){
            add("generator",b.x+b.w+5,b.z-2,1,1.4,2.4,0);
            add("antenna",b.x-b.w-5,b.z-2,1,.5,8,0);
            for(int i=0;i<3;i++)add("crate",b.x+b.w+5+i*2.4,b.z+5,1,.95,1.8,i);
            for(int face:new int[]{-1,1}){double entry=b.d+(b.floor>1?24:4);add("beacon",b.x-3,b.z+face*entry,1,0,0,0);add("beacon",b.x+3,b.z+face*entry,1,0,0,0);}
            if(scenario==Scenario.ENDOR)add("log",b.x-b.w-8,b.z+9,1,1,1.4,0);
            else add("vaporator",b.x-b.w-7,b.z+10,1,1,5,0);
        }
    }
    private boolean clearLane(double x,double z,double radius){
        for(BattlefrontArchitecture.Building b:buildings)if(b.apron(x,z)<radius+3)return true;
        if(onTrail(x,z,radius+4))return true;
        for(double[] base:bases)if(Math.hypot(x-base[0],z-base[1])<24+radius)return true;
        for(double[] p:posts)if(Math.hypot(x-p[0],z-p[1])<12+radius)return true;
        return false;
    }
    private void add(String type,double x,double z,double s,double radius,double height,int variant){
        Prop p=new Prop(type,x,height(x,z),z,s,radius,height,variant);props.add(p);
        if(radius>0)for(int gx=cell(x-radius);gx<=cell(x+radius);gx++)for(int gz=cell(z-radius);gz<=cell(z+radius);gz++)grid[gx][gz].add(p);
    }
    private static int cell(double v){return Math.max(0,Math.min(GRID_SIZE-1,(int)((v+TERRAIN_EDGE)/10)));}
    public boolean onTrail(double x,double z,double width){
        if(Math.abs(x)<width||Math.abs(Math.abs(x)-145)<width||Math.abs(Math.abs(z)-125)<width)return true;
        if(segmentDistance(x,z,bases[0],posts[0])<width||segmentDistance(x,z,bases[1],posts[2])<width)return true;
        for(int i=0;i<posts.length;i++)if(segmentDistance(x,z,posts[i],posts[(i+1)%posts.length])<width)return true;
        for(BattlefrontArchitecture.Building b:buildings){
            double approach=b.z+(b.z<0?1:-1)*(b.d+(b.floor>1?24:6));
            double route=b.z<0?-125:125;
            if(Math.abs(x-b.x)<width&&z>=Math.min(route,approach)-width&&z<=Math.max(route,approach)+width)return true;
        }return false;
    }
    private static double segmentDistance(double x,double z,double[] a,double[] b){double dx=b[0]-a[0],dz=b[1]-a[1],t=Math.max(0,Math.min(1,((x-a[0])*dx+(z-a[1])*dz)/(dx*dx+dz*dz)));return Math.hypot(x-a[0]-t*dx,z-a[1]-t*dz);}
    private void building(String name,String type,double x,double z,double w,double d,double floor){
        BattlefrontArchitecture.Building b=new BattlefrontArchitecture.Building(name,type,x,z,w,d,floor);b.build(rawSample(x,z));buildings.add(b);
        for(BattlefrontArchitecture.Part p:b.parts)if(p.solid)for(int gx=cell(p.x0);gx<=cell(p.x1);gx++)for(int gz=cell(p.z0);gz<=cell(p.z1);gz++)architecture[gx][gz].add(p);
    }
    private double rawSample(double x,double z){
        double rolling=3.5*Math.sin(x*.041)*Math.cos(z*.036)+1.5*Math.sin(z*.072+x*.028);
        double ridge=9*Math.exp(-Math.pow((x+65)/22,2))*Math.pow(Math.sin(z*.028),2);
        double crater=scenario==Scenario.GEONOSIS?-4*Math.exp(-(x*x+(z-24)*(z-24))/750):0;
        double outer=Math.min(1,Math.max(0,(Math.max(Math.abs(x),Math.abs(z))-100)/70));
        double uplands=outer*(8+7*Math.sin(x*.021)*Math.cos(z*.026)+4*Math.sin(z*.043));
        return 6+rolling+ridge+crater+uplands;
    }
    private double sample(double x,double z){double h=rawSample(x,z);
        for(double[] p:posts)h=levelClearing(h,x,z,p,10,22);
        for(double[] p:bases)h=levelClearing(h,x,z,p,18,32);
        for(BattlefrontArchitecture.Building b:buildings){double d=b.apron(x,z);if(d<7){double blend=1-Math.min(1,d/7);blend=blend*blend*(3-2*blend);h=h*(1-blend)+b.base*blend;}}return h;}
    private double levelClearing(double h,double x,double z,double[] p,double inner,double outer){double distance=Math.hypot(x-p[0],z-p[1]);if(distance>=outer)return h;double t=Math.max(0,Math.min(1,(outer-distance)/(outer-inner)));t=t*t*(3-2*t);return h*(1-t)+rawSample(p[0],p[1])*t;}
    private double gridHeight(double x,double z){int gx=(int)((x+TERRAIN_EDGE)/STEP),gz=(int)((z+TERRAIN_EDGE)/STEP);return elevations!=null&&gx>=0&&gz>=0&&gx<elevations.length&&gz<elevations.length?elevations[gx][gz]:sample(x,z);}
    /** Triangular interpolation exactly matches the terrain mesh, including at the rim. */
    public double height(double x,double z){
        double gx=Math.floor(x/STEP)*STEP,gz=Math.floor(z/STEP)*STEP,u=(x-gx)/STEP,v=(z-gz)/STEP;
        double a=gridHeight(gx,gz),b=gridHeight(gx+STEP,gz),c=gridHeight(gx,gz+STEP),d=gridHeight(gx+STEP,gz+STEP);
        return u+v<=1?a+(b-a)*u+(c-a)*v:d+(c-d)*(1-u)+(b-d)*(1-v);
    }
    public boolean blocked(double x,double z,double radius){
        return blocked(x,height(x,z),z,radius,1.85);
    }
    public boolean blocked(double x,double y,double z,double radius,double bodyHeight){
        if(Math.abs(x)>LIMIT-radius||Math.abs(z)>LIMIT-radius)return true;
        for(int gx=cell(x-radius);gx<=cell(x+radius);gx++)for(int gz=cell(z-radius);gz<=cell(z+radius);gz++)
            {for(Prop p:grid[gx][gz])if(p.radius>0&&y<p.y+p.height&&y+bodyHeight>p.y&&Math.hypot(x-p.x,z-p.z)<p.radius+radius)return true;
            for(BattlefrontArchitecture.Part p:architecture[gx][gz])if(p.overlaps(x,y,z,radius,bodyHeight))return true;}
        return false;
    }
    public double floor(double x,double z,double feet){double y=height(x,z);for(int gx=cell(x-.4);gx<=cell(x+.4);gx++)for(int gz=cell(z-.4);gz<=cell(z+.4);gz++)for(BattlefrontArchitecture.Part p:architecture[gx][gz])if(x+.4>p.x0&&x-.4<p.x1&&z+.4>p.z0&&z-.4<p.z1&&p.y1<=feet+.55&&p.y1>y)y=p.y1;return y;}
    public double ceiling(double x,double z,double feet){double y=10000;for(BattlefrontArchitecture.Part p:architecture[cell(x)][cell(z)])if(x>p.x0-.35&&x<p.x1+.35&&z>p.z0-.35&&z<p.z1+.35&&p.y0>=feet+1.5)y=Math.min(y,p.y0);return y;}
    public BattlefrontArchitecture.Building buildingAt(double x,double y,double z){for(BattlefrontArchitecture.Building b:buildings)if(b.contains(x,y,z))return b;return null;}
    /** Finite ray against terrain and cylindrical scenery; no ray can shoot through a hill. */
    public double ray(double x,double y,double z,double dx,double dy,double dz,double max){
        double hit=max;int lastX=-1,lastZ=-1;
        for(double t=0;t<=hit;t+=.35){
            double px=x+dx*t,py=y+dy*t,pz=z+dz*t;
            if(py<height(px,pz)||Math.abs(px)>LIMIT+8||Math.abs(pz)>LIMIT+8)return t;
            for(Prop p:grid[cell(px)][cell(pz)])if(py>p.y&&py<p.y+p.height&&Math.hypot(px-p.x,pz-p.z)<p.radius)return t;
            int cx=cell(px),cz=cell(pz);if(cx!=lastX||cz!=lastZ){for(BattlefrontArchitecture.Part p:architecture[cx][cz])hit=Math.min(hit,p.ray(x,y,z,dx,dy,dz,hit));lastX=cx;lastZ=cz;}
        }
        return hit;
    }
    public boolean visible(double x,double y,double z,double tx,double ty,double tz){
        double dx=tx-x,dy=ty-y,dz=tz-z,d=Math.sqrt(dx*dx+dy*dy+dz*dz);
        return d<.05||ray(x,y,z,dx/d,dy/d,dz/d,d)>=d-.04;
    }
    public double[] spawn(int side,int index){
        double bx=bases[side][0]+(index%7-3)*2.8,bz=bases[side][1]+(side==0?-1:1)*(index/7)*2.8;
        for(int i=0;i<40;i++){double x=bx+Math.sin(i*2.4)*i*.4,z=bz+Math.cos(i*2.4)*i*.4;if(!blocked(x,z,.65))return new double[]{x,z};}
        return bases[side].clone();
    }
}
