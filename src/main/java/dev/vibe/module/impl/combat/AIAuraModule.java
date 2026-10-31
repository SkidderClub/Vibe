package dev.vibe.module.impl.combat;

import dev.vibe.Vibe;
import dev.vibe.aiaura.*;
import dev.vibe.input.VanillaClicks;
import dev.vibe.module.impl.movement.FreecamModule;
import dev.vibe.module.impl.world.BedAuraModule;
import dev.vibe.setting.*;
import dev.vibe.ui.screen.AIAuraRecorderGui;
import java.io.File;
import net.minecraft.client.gui.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.*;

/** Learned policy on KillAura's existing MoveFix, ray validation and native input transport. */
public final class AIAuraModule extends KillAuraModule {
    public final BooleanSetting openRecorder;
    public final NumberSetting reach,boosterClicking,boosterAiming;
    public final BooleanSetting reset;
    public final BooleanSetting lockFov;
    private AuraLearning learning;
    private final AuraBrain brain=new AuraBrain();
    private int requested;
    private GuiScreen parent;
    private boolean returning,cameraFollowing;
    private float cameraYaw,cameraPitch,previousCameraYaw,previousCameraPitch,framePartial=1;
    private net.minecraft.entity.Entity cameraPlayer;
    public AIAuraModule(){
        super("AIAura","Personal neural combat policy trained from your own recorder sessions");
        openRecorder=addSetting(new BooleanSetting("Open Recorder",false).onEnable(()->request(1)));
        reach=addSetting(new NumberSetting("Reach",3,1,6.5,.05));
        boosterClicking=addSetting(new NumberSetting("Booster Clicking (x)",1,.8,4,.05));
        boosterAiming=addSetting(new NumberSetting("Booster Aiming (x)",1,.8,4,.05));
        lockFov=addSetting(new BooleanSetting("LockFOV",false).onEnable(()->cameraFollowing=false));
        reset=addSetting(new BooleanSetting("Reset",false).onEnable(()->request(2)));
        replaceSettings(openRecorder,reach,boosterClicking,boosterAiming,lockFov,reset);
    }
    public AuraLearning learning(){if(learning==null)learning=new AuraLearning(new File(minecraft.mcDataDir,"vibe/aiaura").toPath().toAbsolutePath().normalize());return learning;}
    private void request(int type){if(type==1)openRecorder.setEnabled(false);else reset.setEnabled(false);if(isConfigLoading())return;parent=minecraft.currentScreen instanceof GuiChat?null:minecraft.currentScreen;requested=type;}
    public void tickMenus(){
        if(requested==0)return;int action=requested;requested=0;
        if(action==1)minecraft.displayGuiScreen(new AIAuraRecorderGui(this,parent));
        else{GuiScreen previous=parent;minecraft.displayGuiScreen(new dev.vibe.ui.screen.AIAuraResetGui((confirmed,id)->{if(confirmed){setEnabled(false);try{learning().reset();}catch(java.io.IOException e){learning().status="Reset failed: "+e.getMessage();}}minecraft.displayGuiScreen(previous);}));}
        parent=null;
    }
    @Override protected void onEnable(){
        KillAuraModule ordinary=Vibe.getInstance()==null?null:Vibe.getInstance().getModuleManager().getModule(KillAuraModule.class);if(ordinary!=null&&ordinary!=this&&ordinary.isEnabled())ordinary.setEnabled(false);
        if(!isConfigLoading()&&minecraft.thePlayer!=null&&learning().model()==null)dev.vibe.chat.ClientChat.send(new net.minecraft.util.ChatComponentText("AIAura: open Recorder, record manual play, then train your model."));
    }
    @Override protected boolean canOperate(){return super.canOperate()&&minecraft.inGameHasFocus&&!FreecamModule.cameraActiveHook()&&learning().model()!=null;}
    @Override public void tickStart(){
        boolean wasTracking=target!=null||returning;float lastYaw=rotation.getYaw(),lastPitch=rotation.getPitch();super.tickStart();
        if(target!=null){returning=false;cameraFrame(rotation.getYaw(),rotation.getPitch());if(lockFov.isEnabled()){minecraft.thePlayer.rotationYaw=rotation.getYaw();minecraft.thePlayer.rotationPitch=rotation.getPitch();}return;}
        if(wasTracking&&canOperate()&&!dev.vibe.module.impl.world.ScaffoldModule.isActive()&&!BedAuraModule.ownsMiningInput()){
            double step=MouseMath.step(minecraft.gameSettings.mouseSensitivity),dy=MouseMath.wrap(minecraft.thePlayer.rotationYaw-lastYaw),dp=minecraft.thePlayer.rotationPitch-lastPitch;
            dev.vibe.module.impl.movement.MoveFixModule fix=Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.movement.MoveFixModule.class);
            if(fix!=null&&(Math.abs(dy)>step||Math.abs(dp)>step)){
                int x=(int)Math.round(dy*.28/step),y=(int)Math.round(dp*.28/step);if(x==0&&Math.abs(dy)>step)x=(int)Math.signum(dy);if(y==0&&Math.abs(dp)>step)y=(int)Math.signum(dp);
                rotation.reset(MouseMath.angle(lastYaw,x,minecraft.gameSettings.mouseSensitivity),MouseMath.pitch(lastPitch,y,minecraft.gameSettings.mouseSensitivity));fix.setFakeRotation(getId(),rotation.getYaw(),rotation.getPitch());returning=true;cameraFrame(rotation.getYaw(),rotation.getPitch());return;
            }
            if(fix!=null)fix.discardOwnedState(getId());
        }
        returning=cameraFollowing=false;cameraPlayer=null;
    }
    private void cameraFrame(float yaw,float pitch){
        if(!cameraFollowing||cameraPlayer!=minecraft.thePlayer){previousCameraYaw=minecraft.thePlayer.rotationYaw;previousCameraPitch=minecraft.thePlayer.rotationPitch;}else{previousCameraYaw=cameraYaw;previousCameraPitch=cameraPitch;}
        cameraYaw=previousCameraYaw+(float)MouseMath.wrap(yaw-previousCameraYaw);cameraPitch=pitch;cameraPlayer=minecraft.thePlayer;cameraFollowing=true;
    }
    @Override protected void onDisable(){super.onDisable();returning=cameraFollowing=false;cameraPlayer=null;}
    public void frameTick(float partial){
        framePartial=Math.max(0,Math.min(1,partial));
        if(minecraft.thePlayer!=cameraPlayer||minecraft.theWorld==null)cameraFollowing=false;
        // Vanilla updates entity interpolation fields after the module's tick.
        // Keep the first-person hand, camera and renderer on the same endpoints.
        if(camera(minecraft.thePlayer)==this)applyViewAngles();
    }
    private void applyViewAngles(){
        cameraPlayer.prevRotationYaw=previousCameraYaw;cameraPlayer.rotationYaw=cameraYaw;
        cameraPlayer.prevRotationPitch=previousCameraPitch;cameraPlayer.rotationPitch=cameraPitch;
    }
    /** Mouse deltas are still observed by input tracking, but cannot replace an active locked view. */
    public static boolean lockMouseLookHook(Object entity){
        AIAuraModule module=camera(entity);
        if(module==null||module.target==null)return false;
        module.applyViewAngles();return true;
    }
    /** Forge's final camera event also covers render paths that do not read the entity fields directly. */
    public static void cameraSetupHook(net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup event){
        AIAuraModule module=camera(event.entity);if(module==null)return;
        module.frameTick((float)event.renderPartialTicks);
        // orientCamera adds 180 degrees to entity yaw before posting CameraSetup.
        event.yaw=module.previousCameraYaw+(module.cameraYaw-module.previousCameraYaw)*module.framePartial+180F;
        event.pitch=module.previousCameraPitch+(module.cameraPitch-module.previousCameraPitch)*module.framePartial;
    }
    private static AIAuraModule camera(Object entity){Vibe v=Vibe.getInstance();if(v==null||v.getModuleManager()==null)return null;AIAuraModule m=v.getModuleManager().getModule(AIAuraModule.class);return m!=null&&m.isEnabled()&&m.lockFov.isEnabled()&&m.cameraFollowing&&entity==m.cameraPlayer&&entity==m.minecraft.getRenderViewEntity()&&m.minecraft.currentScreen==null&&!FreecamModule.cameraActiveHook()?m:null;}
    public static float cameraYawHook(Object entity,float vanilla){AIAuraModule m=camera(entity);return m==null?vanilla:m.previousCameraYaw+(m.cameraYaw-m.previousCameraYaw)*m.framePartial;}
    public static float cameraPitchHook(Object entity,float vanilla){AIAuraModule m=camera(entity);return m==null?vanilla:m.previousCameraPitch+(m.cameraPitch-m.previousCameraPitch)*m.framePartial;}
    @Override protected double selectionRange(){return Math.max(12,reach.getDouble()+2);}
    @Override protected double clickRange(){return selectionRange();}
    @Override protected double attackReach(){return reach.getDouble();}
    @Override public double getReachRadius(){return reach.getDouble();}
    @Override protected double score(EntityLivingBase entity){double[] a=AuraFeatures.angles(eyes().xCoord,eyes().yCoord,eyes().zCoord,entity.posX,entity.posY+entity.height*.6,entity.posZ);return (Math.hypot(MouseMath.wrap(a[0]-rotation.getYaw()),a[1]-rotation.getPitch())+distanceTo(entity)*2)*(entity==target?.8:1);}
    @Override protected boolean validTarget(EntityLivingBase entity){float yaw=lockFov.isEnabled()&&cameraFollowing?rotation.getYaw():minecraft.thePlayer.rotationYaw,pitch=lockFov.isEnabled()&&cameraFollowing?rotation.getPitch():minecraft.thePlayer.rotationPitch;return super.validTarget(entity)&&AuraFeatures.inView(observation(entity,yaw,pitch),minecraft.gameSettings.fovSetting,minecraft.displayWidth/(double)Math.max(1,minecraft.displayHeight));}
    @Override protected Vec3 aimPoint(EntityLivingBase entity){AxisAlignedBB box=entity.getEntityBoundingBox();return new Vec3(box.minX+(box.maxX-box.minX)*brain.memory.pointX,box.minY+(box.maxY-box.minY)*brain.memory.pointY,box.minZ+(box.maxZ-box.minZ)*brain.memory.pointZ);}
    @Override protected void advanceRotation(EntityLivingBase entity){
        int[] counts=brain.advance(learning().model(),observation(entity,rotation.getYaw(),rotation.getPitch()),System.nanoTime()/1000000L,boosterAiming.getDouble(),boosterClicking.getDouble());
        rotation.reset(MouseMath.angle(rotation.getYaw(),counts[0],minecraft.gameSettings.mouseSensitivity),MouseMath.pitch(rotation.getPitch(),counts[1],minecraft.gameSettings.mouseSensitivity));
    }
    @Override protected boolean clickDue(long now){return brain.due(now);}
    @Override protected void targetChanged(EntityLivingBase previous,EntityLivingBase next){if(previous!=null&&previous.getEntityId()==next.getEntityId())brain.memory.target=Long.MIN_VALUE;}
    @Override protected int pollClicks(long now){return brain.poll(now);}
    @Override protected void resetPolicy(){brain.reset();}
    private AuraFeatures.Observation observation(EntityLivingBase target,float yaw,float pitch){
        AuraFeatures.Observation o=new AuraFeatures.Observation();Vec3 eye=eyes();AxisAlignedBB box=target.getEntityBoundingBox();o.eyeX=eye.xCoord;o.eyeY=eye.yCoord;o.eyeZ=eye.zCoord;o.yaw=yaw;o.pitch=pitch;
        o.targetX=(box.minX+box.maxX)*.5;o.targetY=box.minY;o.targetZ=(box.minZ+box.maxZ)*.5;o.targetWidth=box.maxX-box.minX;o.targetHeight=box.maxY-box.minY;o.border=target.getCollisionBorderSize();o.targetVx=target.posX-target.lastTickPosX;o.targetVy=target.posY-target.lastTickPosY;o.targetVz=target.posZ-target.lastTickPosZ;
        o.targetId=target.getEntityId();o.visible=true;o.vx=minecraft.thePlayer.motionX;o.vy=minecraft.thePlayer.motionY;o.vz=minecraft.thePlayer.motionZ;o.grounded=minecraft.thePlayer.onGround;o.sprint=minecraft.thePlayer.isSprinting();o.leftHeld=VanillaClicks.physicallyDown(minecraft.gameSettings.keyBindAttack);o.jump=minecraft.gameSettings.keyBindJump.isKeyDown();o.sensitivity=minecraft.gameSettings.mouseSensitivity;o.reach=reach.getDouble();o.targetHurt=target.hurtTime;o.selfHurt=minecraft.thePlayer.hurtTime;o.targetHealth=target.getHealth()/Math.max(1,target.getMaxHealth());o.selfHealth=minecraft.thePlayer.getHealth()/Math.max(1,minecraft.thePlayer.getMaxHealth());o.targetGrounded=target.onGround;o.targetYaw=target.rotationYaw;return o;
    }
}
