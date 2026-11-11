package dev.vibe.camera;
import com.google.gson.*;
import dev.vibe.chat.ClientChat;
import dev.vibe.features.MovementForecast;
import dev.vibe.setting.StringSetting;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.*;

/** Small editable Catmull-Rom route with an arc-length table for fixed-speed filming. */
public final class CameraRig {
    public static final class Pose {public double x,y,z,yaw,pitch,roll,seconds=2;public String world="";public Pose(){}public Pose(double x,double y,double z,double yaw,double pitch,double roll,String world){this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;this.roll=roll;this.world=world;}public Vec3 position(){return new Vec3(x,y,z);}}
    public static final class Frame{public final Vec3 position;public final double yaw,pitch,roll,distance,time;Frame(Vec3 p,double yaw,double pitch,double roll,double distance,double time){position=p;this.yaw=yaw;this.pitch=pitch;this.roll=roll;this.distance=distance;this.time=time;}}
    private final StringSetting data;private String loaded;private final List<Pose> poses=new ArrayList<>();private final List<Frame> frames=new ArrayList<>();private boolean playing;private double elapsed;
    public CameraRig(StringSetting data){this.data=data;}
    public List<Pose> poses(){load();return Collections.unmodifiableList(poses);}
    public List<Frame> frames(){load();return Collections.unmodifiableList(frames);}
    public void capture(FpvFlight flight){load();if(poses.size()>=32){ClientChat.send("Camera Rig supports up to 32 poses.");return;}poses.add(new Pose(flight.x,flight.y,flight.z,flight.yaw(),flight.pitch(),flight.roll(),worldKey()));save();}
    public void update(int index,Pose p){load();if(index<0||index>=poses.size()||!valid(p))throw new IllegalArgumentException("Enter finite coordinates, angles and a duration between 0.1 and 120 seconds.");p.world=poses.get(index).world;poses.set(index,p);save();}
    public void remove(int index){load();if(index>=0&&index<poses.size()){poses.remove(index);save();}}
    public void move(int index,int direction){load();int next=index+direction;if(index>=0&&next>=0&&index<poses.size()&&next<poses.size()){Collections.swap(poses,index,next);save();}}
    public boolean play(){load();if(frames.size()<2){ClientChat.send("Capture at least two camera poses first.");return false;}String world=worldKey();for(Pose p:poses)if(!p.world.equals(world)){ClientChat.send("This camera path belongs to a different world or dimension.");return false;}playing=true;elapsed=0;return true;}
    public void stop(){playing=false;}
    public boolean playing(){return playing;}
    public double elapsed(){return elapsed;}
    public double duration(boolean fixed,double speed){load();if(frames.isEmpty())return 0;Frame last=frames.get(frames.size()-1);return fixed?last.distance/Math.max(.05,speed):last.time;}
    public void step(double dt,FpvFlight flight,boolean fixed,double speed,boolean collision){if(!playing)return;elapsed+=Math.max(0,Math.min(.1,dt));seek(elapsed,flight,fixed,speed,collision);if(elapsed>=duration(fixed,speed))playing=false;}
    public boolean seek(double seconds,FpvFlight flight,boolean fixed,double speed,boolean collision){load();Frame f=sample(Math.max(0,seconds)*(fixed?speed:1),fixed);if(f==null)return false;Vec3 old=new Vec3(flight.x,flight.y,flight.z);if(collision&&!clear(old,f.position)){playing=false;ClientChat.send("Camera path stopped at an obstruction or unloaded terrain.");return false;}flight.pose(f.position.xCoord,f.position.yCoord,f.position.zCoord,f.yaw,f.pitch,f.roll);elapsed=Math.max(0,Math.min(duration(fixed,speed),seconds));return true;}
    public Frame sample(double value,boolean byDistance){load();if(frames.isEmpty())return null;int lo=0,hi=frames.size()-1;while(lo<hi){int mid=(lo+hi)/2;Frame f=frames.get(mid);if((byDistance?f.distance:f.time)<value)lo=mid+1;else hi=mid;}Frame b=frames.get(lo),a=frames.get(Math.max(0,lo-1));double span=(byDistance?b.distance-a.distance:b.time-a.time),t=span<=1e-8?0:Math.max(0,Math.min(1,(value-(byDistance?a.distance:a.time))/span));return new Frame(a.position.addVector((b.position.xCoord-a.position.xCoord)*t,(b.position.yCoord-a.position.yCoord)*t,(b.position.zCoord-a.position.zCoord)*t),angle(a.yaw,b.yaw,t),angle(a.pitch,b.pitch,t),angle(a.roll,b.roll,t),value,value);}
    public static boolean clear(Vec3 from,Vec3 to){Minecraft mc=Minecraft.getMinecraft();if(mc.theWorld==null)return false;AxisAlignedBB body=new AxisAlignedBB(from.xCoord-.2,from.yCoord-.1,from.zCoord-.2,from.xCoord+.2,from.yCoord+.1,from.zCoord+.2);Vec3 d=to.subtract(from);AxisAlignedBB sweep=body.addCoord(d.xCoord,d.yCoord,d.zCoord);if(!MovementForecast.loaded(sweep))return false;
        List<AxisAlignedBB> boxes=mc.theWorld.getCollisionBoxes(sweep);for(AxisAlignedBB block:boxes)if(block.intersectsWith(body))return false;double y=d.yCoord,x=d.xCoord,z=d.zCoord;for(AxisAlignedBB b:boxes)y=b.calculateYOffset(body,y);body=body.offset(0,y,0);for(AxisAlignedBB b:boxes)x=b.calculateXOffset(body,x);body=body.offset(x,0,0);for(AxisAlignedBB b:boxes)z=b.calculateZOffset(body,z);return Math.abs(x-d.xCoord)<1e-5&&Math.abs(y-d.yCoord)<1e-5&&Math.abs(z-d.zCoord)<1e-5;
    }
    public static String worldKey(){Minecraft mc=Minecraft.getMinecraft();if(mc.theWorld==null)return "";String server=mc.getCurrentServerData()==null?mc.theWorld.getWorldInfo().getWorldName():mc.getCurrentServerData().serverIP;return server+":"+mc.theWorld.provider.getDimensionId();}
    private void load(){String raw=data.getValue();if(Objects.equals(raw,loaded))return;loaded=raw;playing=false;poses.clear();frames.clear();try{JsonArray array=new JsonParser().parse(raw.isEmpty()?"[]":raw).getAsJsonArray();if(array.size()>32)return;Gson gson=new Gson();for(JsonElement e:array){Pose p=gson.fromJson(e,Pose.class);if(!valid(p)){poses.clear();break;}poses.add(p);}}catch(RuntimeException bad){poses.clear();}rebuild();}
    private void save(){playing=false;loaded=new Gson().toJson(poses);data.setValue(loaded);rebuild();}
    private static boolean valid(Pose p){return p!=null&&p.world!=null&&p.world.length()<512&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&Math.abs(p.x)<=30000000&&Math.abs(p.z)<=30000000&&p.y>=-256&&p.y<=512&&Double.isFinite(p.yaw)&&Math.abs(p.yaw)<=360000&&Double.isFinite(p.pitch)&&Math.abs(p.pitch)<=90&&Double.isFinite(p.roll)&&Math.abs(p.roll)<=180&&Double.isFinite(p.seconds)&&p.seconds>=.1&&p.seconds<=120;}
    private void rebuild(){frames.clear();if(poses.isEmpty())return;double distance=0,time=0;Pose first=poses.get(0);frames.add(new Frame(first.position(),first.yaw,first.pitch,first.roll,0,0));for(int i=0;i<poses.size()-1;i++){Pose p0=poses.get(Math.max(0,i-1)),p1=poses.get(i),p2=poses.get(i+1),p3=poses.get(Math.min(poses.size()-1,i+2));for(int n=1;n<=32;n++){double t=n/32.0;Vec3 point=new Vec3(curve(p0.x,p1.x,p2.x,p3.x,t),curve(p0.y,p1.y,p2.y,p3.y,t),curve(p0.z,p1.z,p2.z,p3.z,t));distance+=point.distanceTo(frames.get(frames.size()-1).position);frames.add(new Frame(point,angle(p1.yaw,p2.yaw,t),angle(p1.pitch,p2.pitch,t),angle(p1.roll,p2.roll,t),distance,time+p1.seconds*t));}time+=p1.seconds;}}
    private static double curve(double a,double b,double c,double d,double t){return .5*((2*b)+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t);}
    private static double angle(double a,double b,double t){double d=(b-a)%360;if(d>180)d-=360;if(d< -180)d+=360;return a+d*t;}
}
