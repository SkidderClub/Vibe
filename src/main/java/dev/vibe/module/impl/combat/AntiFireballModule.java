package dev.vibe.module.impl.combat;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.input.VanillaClicks;
import dev.vibe.combat.RotationMath;
import dev.vibe.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.util.*;
import org.lwjgl.input.Keyboard;

/** Reserves one native attack input against an incoming, deflectable fireball. */
public final class AntiFireballModule extends dev.vibe.module.Module {
    public final NumberSetting range=addSetting(new NumberSetting("Range",3,1,3,.1));
    public final NumberSetting rotationSpeed=addSetting(new NumberSetting("Rotation Speed",90,5,180,1));
    public final NumberSetting cooldown=addSetting(new NumberSetting("Attack Delay (ms)",200,100,1000,10));
    private EntityLargeFireball threat;private Entity oldPointed;private MovingObjectPosition oldHit;private boolean input,prepared;private long next;private float frameYaw,framePitch;
    public AntiFireballModule(){super("Anti Fireball","Deflect approaching fireballs with native attacks and MoveFix rotations",Category.COMBAT,Keyboard.KEY_NONE);}
    private MoveFixModule fix(){return Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);}
    public void prepare(){
        restore();prepared=false;threat=null;Minecraft mc=Minecraft.getMinecraft();
        if(!isEnabled()||mc.thePlayer==null||mc.theWorld==null||mc.playerController==null||mc.currentScreen!=null||!mc.inGameHasFocus||mc.thePlayer.isUsingItem()
                ||dev.vibe.module.impl.world.ScaffoldModule.isActive()||AutoBuffModule.ownsUseInput()||dev.vibe.module.impl.world.BedAuraModule.ownsMiningInput()
                ||dev.vibe.module.impl.movement.FreecamModule.cameraActiveHook()||mc.playerController.getIsHittingBlock()){releaseRotation();return;}
        Vec3 eye=mc.thePlayer.getPositionEyes(1);double nearest=range.getDouble()*range.getDouble();
        for(Entity e:mc.theWorld.loadedEntityList)if(e instanceof EntityLargeFireball&&!e.isDead){
            Vec3 r=new Vec3(e.posX-eye.xCoord,e.posY-eye.yCoord,e.posZ-eye.zCoord),v=new Vec3(e.motionX-mc.thePlayer.motionX,e.motionY-mc.thePlayer.motionY,e.motionZ-mc.thePlayer.motionZ);
            double distance=r.dotProduct(r),vv=v.dotProduct(v),t=vv<1e-6?-1:-r.dotProduct(v)/vv;
            if(distance>nearest||t<0||t>20||r.addVector(v.xCoord*t,v.yCoord*t,v.zCoord*t).lengthVector()>1.7)continue;
            MovingObjectPosition block=mc.theWorld.rayTraceBlocks(eye,new Vec3(e.posX,e.posY,e.posZ),false,true,false);if(block!=null&&block.typeOfHit==MovingObjectPosition.MovingObjectType.BLOCK)continue;
            nearest=distance;threat=(EntityLargeFireball)e;
        }
        if(threat==null)releaseRotation();
    }
    public void apply(){
        if(threat==null)return;Minecraft mc=Minecraft.getMinecraft();MoveFixModule fix=fix();if(fix==null)return;
        Vec3 eye=mc.thePlayer.getPositionEyes(1),aim=new Vec3(threat.posX,threat.posY+threat.height*.5,threat.posZ);double dx=aim.xCoord-eye.xCoord,dy=aim.yCoord-eye.yCoord,dz=aim.zCoord-eye.zCoord;
        float wantedYaw=(float)Math.toDegrees(Math.atan2(dz,dx))-90,wantedPitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))),speed=rotationSpeed.getFloat();
        float yaw=fix.getRotationYaw()+RotationMath.clamp(RotationMath.difference(wantedYaw,fix.getRotationYaw()),-speed,speed),pitch=fix.getRotationPitch()+RotationMath.clamp(wantedPitch-fix.getRotationPitch(),-speed,speed);
        fix.setFakeRotation(getId(),yaw,pitch);
        frameYaw=yaw;framePitch=pitch;
    }
    private void prepareInput(){
        if(prepared||threat==null||threat.isDead)return;prepared=true;Minecraft mc=Minecraft.getMinecraft();if(mc.currentScreen!=null||mc.thePlayer==null||mc.thePlayer.isUsingItem()||AutoBuffModule.ownsUseInput())return;
        Vec3 eye=mc.thePlayer.getPositionEyes(1);double ry=Math.toRadians(frameYaw),rp=Math.toRadians(framePitch);Vec3 end=eye.addVector(-Math.sin(ry)*Math.cos(rp)*range.getDouble(),-Math.sin(rp)*range.getDouble(),Math.cos(ry)*Math.cos(rp)*range.getDouble());
        MovingObjectPosition hit=threat.getEntityBoundingBox().expand(.1,.1,.1).calculateIntercept(eye,end);if(hit==null)return;
        oldHit=mc.objectMouseOver;oldPointed=mc.pointedEntity;mc.objectMouseOver=new MovingObjectPosition(threat,hit.hitVec);mc.pointedEntity=threat;input=true;
        VanillaClicks.discardPresses(mc.gameSettings.keyBindAttack);
        if(System.currentTimeMillis()>=next){VanillaClicks.pulseAttack(mc.gameSettings.keyBindAttack);next=System.currentTimeMillis()+cooldown.getInt();}
    }
    public void restore(){if(!input)return;Minecraft mc=Minecraft.getMinecraft();mc.objectMouseOver=oldHit;mc.pointedEntity=oldPointed;VanillaClicks.discardPresses(mc.gameSettings.keyBindAttack);VanillaClicks.restore(mc.gameSettings.keyBindAttack);oldHit=null;oldPointed=null;input=false;}
    private void releaseRotation(){MoveFixModule f=fix();if(f!=null)f.clearFakeRotation(getId());}
    @Override protected void onDisable(){restore();threat=null;next=0;releaseRotation();}
    public static boolean reserving(){Vibe v=Vibe.getInstance();if(v==null||v.getModuleManager()==null)return false;AntiFireballModule m=v.getModuleManager().getModule(AntiFireballModule.class);return m!=null&&m.isEnabled()&&m.threat!=null;}
    public static void prepareInputHook(){Vibe v=Vibe.getInstance();if(v==null||v.getModuleManager()==null)return;AntiFireballModule m=v.getModuleManager().getModule(AntiFireballModule.class);if(m!=null&&m.isEnabled())m.prepareInput();}
}
