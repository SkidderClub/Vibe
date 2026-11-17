package dev.vibe.movement;

import dev.vibe.combat.CombatTimerAccess;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.network.PacketDelayService;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.potion.Potion;

/** Supplied LongJump.Watchdog: 49 damage pairs, one .70F jump, then base-speed strafe. */
public final class DiabloLongJump {
    private EntityPlayerSP owner;private Object world;private boolean shouldJump;
    public boolean owns(EntityPlayerSP p,Object world){return owner==p&&this.world==world;}
    public void begin(EntityPlayerSP p,Object world){
        owner=p;this.world=world;shouldJump=true;
        if(!p.onGround){dev.vibe.chat.ClientChat.send("You must be on the ground.");return;}
        for(int i=0;i<49;i++){
            PacketDelayService.sendUnmodified(p.sendQueue,new C04PacketPlayerPosition(p.posX,p.posY+.0625,p.posZ,false));
            PacketDelayService.sendUnmodified(p.sendQueue,new C04PacketPlayerPosition(p.posX,p.posY,p.posZ,false));
        }
        p.sendQueue.addToSendQueue(new C04PacketPlayerPosition(p.posX,p.posY,p.posZ,true));
    }
    /** True is the source's disable-on-landing branch. */
    public boolean pre(){
        if(owner==null||owner.moveForward==0&&owner.moveStrafing==0||!owner.onGround)return false;
        if(shouldJump){owner.motionY=(double).70F;shouldJump=false;return false;}
        CombatTimerAccess.setSpeed(1);return true;
    }
    public void post(){
        if(owner==null)return;
        double speed=owner.capabilities.getWalkSpeed()*2.925;
        if(owner.isPotionActive(Potion.moveSlowdown))speed/=1+.15*(owner.getActivePotionEffect(Potion.moveSlowdown).getAmplifier()+1);
        if(owner.isPotionActive(Potion.moveSpeed))speed*=1+.2*(owner.getActivePotionEffect(Potion.moveSpeed).getAmplifier()+1);
        motion(owner,speed*1.14F);
    }
    public static void motion(EntityPlayerSP p,double speed){
        float yaw=MoveFixModule.movementYawHook(p,p.rotationYaw);double forward=p.moveForward,strafe=p.moveStrafing;
        if(forward==0&&strafe==0){p.motionX=p.motionZ=0;return;}
        if(forward!=0){if(strafe>0)yaw+=forward>0?-45:45;else if(strafe<0)yaw+=forward>0?45:-45;strafe=0;forward=Math.signum(forward);}
        double cos=Math.cos(Math.toRadians(yaw+90)),sin=Math.sin(Math.toRadians(yaw+90));
        p.motionX=forward*speed*cos+strafe*speed*sin;p.motionZ=forward*speed*sin-strafe*speed*cos;
    }
    public void reset(){if(owner!=null)CombatTimerAccess.setSpeed(1);owner=null;world=null;shouldJump=false;}
}
