package dev.vibe.aiaura;

import dev.vibe.path.PathTestFixture;
import java.util.*;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.ServersideAttributeMap;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class ArenaPhysicsTest {
    @Test public void accelerationDragAndJumpMatchNativeMinecraft()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            NativePlayer nativePlayer=PathTestFixture.allocate(NativePlayer.class);nativePlayer.worldObj=f.world;nativePlayer.capabilities=new net.minecraft.entity.player.PlayerCapabilities();nativePlayer.body=new ArenaPhysics.Body();nativePlayer.body.vy=-.0784000015258789;
            PathTestFixture.set(EntityLivingBase.class,nativePlayer,"activePotionsMap",new HashMap<Integer,PotionEffect>());
            ArenaPhysics.Body simulated=new ArenaPhysics.Body();simulated.vy=nativePlayer.body.vy;simulated.yaw=nativePlayer.rotationYaw=37;
            int jumpTimer=0;
            for(int tick=0;tick<160;tick++){
                boolean sprint=tick>=40&&tick<100,jump=tick>=65&&tick<120;float strafe=tick<100?.4F:0,forward=tick<130?1:0;
                simulated.sprint=nativePlayer.sprinting=sprint;nativePlayer.posX=nativePlayer.body.x;nativePlayer.posY=nativePlayer.body.y+64;nativePlayer.posZ=nativePlayer.body.z;nativePlayer.onGround=nativePlayer.body.grounded;nativePlayer.motionX=nativePlayer.body.vx;nativePlayer.motionY=nativePlayer.body.vy;nativePlayer.motionZ=nativePlayer.body.vz;nativePlayer.setEntityBoundingBox(nativePlayer.body.box().offset(0,64,0));nativePlayer.jumpMovementFactor=sprint?.026F:.02F;
                if(jumpTimer>0)jumpTimer--;if(!jump)jumpTimer=0;if(Math.abs(nativePlayer.motionX)<.005)nativePlayer.motionX=0;if(Math.abs(nativePlayer.motionY)<.005)nativePlayer.motionY=0;if(Math.abs(nativePlayer.motionZ)<.005)nativePlayer.motionZ=0;
                if(jump&&nativePlayer.onGround&&jumpTimer==0){nativePlayer.nativeJump();jumpTimer=10;}
                nativePlayer.moveEntityWithHeading(strafe*.98F,forward*.98F);nativePlayer.body.vx=nativePlayer.motionX;nativePlayer.body.vy=nativePlayer.motionY;nativePlayer.body.vz=nativePlayer.motionZ;
                ArenaPhysics.tick(simulated,strafe,forward,jump,false,Collections.emptyList());
                assertEquals("x tick "+tick,nativePlayer.body.x,simulated.x,2e-5);assertEquals("y tick "+tick,nativePlayer.body.y,simulated.y,2e-5);assertEquals("z tick "+tick,nativePlayer.body.z,simulated.z,2e-5);assertEquals(nativePlayer.motionY,simulated.vy,1e-7);
            }
        }
    }
    @Test public void collisionsStepAndSustainedJumpHaveVanillaShape(){
        List<RecorderArena.Pillar> step=Collections.singletonList(new RecorderArena.Pillar(0,1.4,1,.5));ArenaPhysics.Body b=new ArenaPhysics.Body();b.vy=-.0784;
        double peak=0;for(int i=0;i<15;i++){ArenaPhysics.tick(b,0,1,false,false,step);peak=Math.max(peak,b.y);}assertTrue("Half-block step was not climbed",peak>=.5);assertTrue(b.z>1);
        List<RecorderArena.Pillar> wall=Collections.singletonList(new RecorderArena.Pillar(0,1.4,1,3));b=new ArenaPhysics.Body();b.vy=-.0784;for(int i=0;i<30;i++)ArenaPhysics.tick(b,0,1,false,false,wall);assertTrue(b.z<=.600001);assertTrue(b.horizontalCollision);
        b=new ArenaPhysics.Body();b.vy=-.0784;peak=0;int jumps=0;boolean wasAir=false;for(int i=0;i<70;i++){ArenaPhysics.tick(b,0,0,true,false,Collections.emptyList());peak=Math.max(peak,b.y);if(!b.grounded&&!wasAir)jumps++;wasAir=!b.grounded;}assertEquals(1.2522,peak,.01);assertTrue(jumps>=4&&jumps<=6);
    }
    @Test public void knockbackMatchesNativeLivingEntity()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            NativePlayer p=PathTestFixture.allocate(NativePlayer.class);p.worldObj=f.world;p.motionX=.2;p.motionY=.1;p.motionZ=-.3;PathTestFixture.set(Entity.class,p,"rand",new Random(1));ServersideAttributeMap map=new ServersideAttributeMap();map.registerAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(0);PathTestFixture.set(EntityLivingBase.class,p,"attributeMap",map);
            ArenaPhysics.Body b=new ArenaPhysics.Body();b.vx=.2;b.vy=.1;b.vz=-.3;ArenaPhysics.knockback(b,-2,-1);p.knockBack(null,1,-2,-1);assertEquals(p.motionX,b.vx,0);assertEquals(p.motionY,b.vy,0);assertEquals(p.motionZ,b.vz,0);
        }
    }
    public static class NativePlayer extends EntityPlayerSP {
        ArenaPhysics.Body body;boolean sprinting;
        private NativePlayer(){super(null,null,null,null);}
        @Override public boolean isInWater(){return false;}@Override public boolean isInLava(){return false;}@Override public boolean isOnLadder(){return false;}@Override public boolean isSprinting(){return sprinting;}@Override public boolean isSneaking(){return false;}@Override public boolean isServerWorld(){return true;}@Override public float getAIMoveSpeed(){return sprinting?.13F:.1F;}
        void nativeJump(){super.jump();}
        @Override public void addStat(net.minecraft.stats.StatBase stat,int amount){}@Override public void addExhaustion(float amount){}
        @Override public void moveEntity(double dx,double dy,double dz){body.vx=motionX;body.vy=motionY;body.vz=motionZ;ArenaPhysics.move(body,dx,dy,dz,false,Collections.emptyList());motionX=body.vx;motionY=body.vy;motionZ=body.vz;posX=body.x;posY=body.y+64;posZ=body.z;onGround=body.grounded;setEntityBoundingBox(body.box().offset(0,64,0));}
    }
}
