package dev.vibe.module.impl.movement;

import dev.vibe.module.*;
import dev.vibe.module.Module;
import dev.vibe.setting.*;
import dev.vibe.teleport.TeleportDestination;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.*;
import org.lwjgl.input.Keyboard;

/** A repeatable key action. Vanilla reports the changed position in its normal movement update. */
public final class TeleportModule extends Module {
    public final ModeSetting mode=addSetting(new ModeSetting("Mode","Vanilla","Vanilla"));
    public final NumberSetting range=addSetting(new NumberSetting("Range",64,4,96,1));
    public final NumberSetting cooldown=addSetting(new NumberSetting("Cooldown (ms)",400,100,2000,50));
    public final BooleanSetting effects=addSetting(new BooleanSetting("Portal Animation",true));
    public final NumberSetting duration=addSetting(new NumberSetting("Portal Duration (ms)",1000,400,2500,50,()->effects.isEnabled()));
    public final NumberSetting size=addSetting(new NumberSetting("Portal Size",1,.5,2,.05,()->effects.isEnabled()));
    public final NumberSetting glow=addSetting(new NumberSetting("Portal Glow",.6,0,1.5,.05,()->effects.isEnabled()));
    private final List<Portal> portals=new ArrayList<>();private final List<Portal> view=Collections.unmodifiableList(portals);
    private Object world;private long last=-1;
    public TeleportModule(){super("Teleport","Press the bound key to teleport onto the block in view",Category.MOVEMENT,Keyboard.KEY_G);}
    @Override public void toggle(){if(Keyboard.isCreated()&&Keyboard.isRepeatEvent())return;super.toggle();}
    @Override protected void onEnable(){try{if(!isConfigLoading())activate();}finally{setEnabled(false);}}
    public boolean activate(){
        Minecraft mc=Minecraft.getMinecraft();EntityPlayerSP p=mc.thePlayer;
        if(p==null||mc.theWorld==null||mc.currentScreen!=null)return false;
        if(p.isDead||p.getHealth()<=0)return false;
        if(FreecamModule.cameraActiveHook()){say("Leave Freecam before teleporting.");return false;}
        if(p.isRiding()){say("Dismount before teleporting.");return false;}
        bind(mc);long now=System.nanoTime()/1000000;if(last>=0&&now-last<cooldown.getInt())return false;
        float yaw=dev.vibe.module.impl.visual.FreeLookModule.yaw(p,p.rotationYaw),pitch=dev.vibe.module.impl.visual.FreeLookModule.pitch(p,p.rotationPitch);
        double a=Math.toRadians(yaw),b=Math.toRadians(pitch);Vec3 look=new Vec3(-Math.sin(a)*Math.cos(b),-Math.sin(b),Math.cos(a)*Math.cos(b));
        MovingObjectPosition hit=TeleportDestination.look(mc.theWorld,p.getPositionEyes(1),look,range.getDouble());
        if(hit==null||hit.typeOfHit!=MovingObjectPosition.MovingObjectType.BLOCK){say("No loaded block in view within "+range.getInt()+" blocks.");return false;}
        Vec3 destination=TeleportDestination.above(mc.theWorld,p,hit.getBlockPos());
        if(destination==null){say("There is no clear landing space on that block.");return false;}
        if(p.getDistanceSq(destination.xCoord,destination.yCoord,destination.zCoord)<.01){say("You are already on that block.");return false;}
        double fromX=p.posX,fromY=p.posY,fromZ=p.posZ;
        // Position-only updates subdivide long vanilla moves; don't rely on the next walking tick.
        if(p.sendQueue!=null){
            double distance=p.getDistance(destination.xCoord,destination.yCoord,destination.zCoord);
            int steps=Math.max(1,(int)Math.ceil(distance/4));
            for(int i=1;i<=steps;i++){double t=i/(double)steps;p.sendQueue.addToSendQueue(new net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition(
                    fromX+(destination.xCoord-fromX)*t,fromY+(destination.yCoord-fromY)*t,fromZ+(destination.zCoord-fromZ)*t,i==steps));}
        }
        p.setPositionAndUpdate(destination.xCoord,destination.yCoord,destination.zCoord);
        p.prevPosX=p.lastTickPosX=p.posX;p.prevPosY=p.lastTickPosY=p.posY;p.prevPosZ=p.lastTickPosZ=p.posZ;
        p.motionX=p.motionY=p.motionZ=0;p.fallDistance=0;p.onGround=true;last=now;
        if(effects.isEnabled()){
            portals.add(Portal.onFloor(mc.theWorld,fromX,fromY,fromZ,yaw,0xFF359DFF,now,duration.getInt(),size.getDouble()));
            portals.add(Portal.onFloor(mc.theWorld,p.posX,p.posY,p.posZ,yaw,0xFFFF912D,now,duration.getInt(),size.getDouble()));
            while(portals.size()>8)portals.remove(0);
        }
        say("Teleported to "+hit.getBlockPos().getX()+", "+String.format(java.util.Locale.ROOT,"%.2f",p.posY)+", "+hit.getBlockPos().getZ()+".");return true;
    }
    public void tick(){Minecraft mc=Minecraft.getMinecraft();bind(mc);long now=System.nanoTime()/1000000;portals.removeIf(p->now-p.born>=p.duration);}
    private void bind(Minecraft mc){if(world!=mc.theWorld||mc.thePlayer==null){portals.clear();last=-1;world=mc.theWorld;}}
    public List<Portal> portals(){return view;}
    public void clear(){portals.clear();world=null;last=-1;}
    private static void say(String text){dev.vibe.chat.ClientChat.send(text);}
    public static final class Portal{
        public final double x,y,z;public final float yaw;public final long born;public final int duration;
        public final boolean floor;public final int color;public final double span;
        public Portal(double x,double y,double z,float yaw,long born,int duration){this(x,y,z,yaw,born,duration,false,0xFF359DFF,2.3);}
        private Portal(double x,double y,double z,float yaw,long born,int duration,boolean floor,int color,double span){this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.born=born;this.duration=duration;this.floor=floor;this.color=color;this.span=span;}
        public static Portal onFloor(net.minecraft.world.World world,double x,double y,double z,float yaw,int color,long born,int duration,double size){
            MovingObjectPosition hit=world.rayTraceBlocks(new Vec3(x,y+.15,z),new Vec3(x,y-2,z),false,true,false);
            if(hit==null||hit.sideHit!=net.minecraft.util.EnumFacing.UP)return new Portal(x,y,z,yaw,born,duration,false,color,2.3*size);
            double top=hit.hitVec.yCoord,allowed=.2;
            for(double r=.2;r<=Math.min(1.05,size*1.15);r+=.1){boolean supported=true;for(int i=0;i<16;i++){double angle=i*Math.PI/8,px=x+Math.sin(angle)*r,pz=z+Math.cos(angle)*r;if(!world.isBlockLoaded(new BlockPos(px,top,pz))){supported=false;break;}MovingObjectPosition support=world.rayTraceBlocks(new Vec3(px,top+.05,pz),new Vec3(px,top-.12,pz),false,true,false);if(support==null||support.sideHit!=net.minecraft.util.EnumFacing.UP||Math.abs(support.hitVec.yCoord-top)>.025){supported=false;break;}}if(!supported)break;allowed=r;}
            return new Portal(x,top+.018,z,yaw,born,duration,true,color,Math.max(.3,allowed*2-.04));
        }
        public float progress(long now){return Math.max(0,Math.min(1,(now-born)/(float)duration));}
    }
}
