package dev.vibe.movement;
import dev.vibe.combat.CombatTimerAccess;
import dev.vibe.features.MovementForecast;
import dev.vibe.network.PacketDelayService;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.util.*;
/** Adjust NcpStepMode: the original collision measurement, offsets, cooldown and timers. */
public final class AdjustStep {
    private static final double[][] OFFSETS={{.42,.753},{.42,.74998,1.0008,1.16,1.23,1.2},{.42,.78,.63,.51,.9,1.21,1.45,1.43}};
    private static final float[] TIMERS={.7F,.35F,.25F};
    private static final double ADJUSTMENT=new java.util.Random().nextDouble();
    private EntityPlayerSP owner;
    private long lastStep;
    private int timerTicks;
    private float prepared = .6F, timerSpeed, previousTimer;

    public void reset(EntityPlayerSP player) {
        if (timerTicks > 0) releaseTimer();
        owner = player;
        timerTicks = 0;
        prepared = .6F;
        lastStep = System.nanoTime() / 1000000;
    }

    public void preUpdate() {
        prepared = .6F;
        if (timerTicks > 0 && --timerTicks == 0) releaseTimer();
    }

    private void releaseTimer() {
        if (Float.compare(CombatTimerAccess.getSpeed(), timerSpeed) == 0) CombatTimerAccess.setSpeed(previousTimer);
    }
    public float height(){return prepared;}
    public float prepare(float configured){
        prepared=.6F;
        if(owner==null||!owner.onGround||owner.motionY>=0||owner.isInWater()||owner.isInLava()||System.nanoTime()/1000000-lastStep<100)return prepared;
        AxisAlignedBB box=owner.getEntityBoundingBox();
        for(int x=MathHelper.floor_double(box.minX);x<=MathHelper.floor_double(box.maxX);x++)for(int z=MathHelper.floor_double(box.minZ);z<=MathHelper.floor_double(box.maxZ);z++)
            if(owner.worldObj.getBlockState(new BlockPos(x,box.minY-.1,z)).getBlock() instanceof net.minecraft.block.BlockLiquid)return prepared;
        float max=Math.min(configured,2F);AxisAlignedBB initial=new AxisAlignedBB(owner.posX-owner.width/2,box.minY,owner.posZ-owner.width/2,owner.posX+owner.width/2,box.maxY,owner.posZ+owner.width/2);
        if(!MovementForecast.loaded(initial.addCoord(owner.motionX,max,owner.motionZ)))return prepared;
        double actual=measureStepOffset(initial,owner.motionX,owner.motionZ,box.minX-initial.minX,box.minZ-initial.minZ,max);
        if (actual > .6001 && actual <= 2.0001) prepared = max;
        return prepared;
    }

    /** Called after vanilla has chosen between its stepped and unstepped collision paths. */
    public void confirm() {
        float attemptedHeight = prepared;
        prepared = .6F;
        if (attemptedHeight <= .6F || owner == null) return;
        double actual = owner.getEntityBoundingBox().minY - owner.posY;
        if (actual <= .6001 || actual > attemptedHeight + .0001) return;
        int index=actual<=1.0001?0:actual<=1.5001?1:2;
        for(int i=0;i<OFFSETS[index].length;i++){
            float offset=(float)(OFFSETS[index][i]+(i==0?0:.0003000000142492354D*ADJUSTMENT));
            PacketDelayService.sendUnmodified(owner.sendQueue,new C04PacketPlayerPosition(owner.posX,owner.posY+offset,owner.posZ,false));
        }
        float current = CombatTimerAccess.getSpeed();
        if (timerTicks == 0 || Float.compare(current, timerSpeed) != 0) previousTimer = current;
        timerTicks = NovolineSupport.hypixel() ? 1 : 3;
        timerSpeed = TIMERS[index];
        CombatTimerAccess.setSpeed(timerSpeed);
        lastStep = System.nanoTime() / 1000000;
    }
    public static double measureStepOffset(AxisAlignedBB initialBox, double origX, double origZ,
                                           double clippedX, double clippedZ, float maxStep) {
        Minecraft mc=Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        List<AxisAlignedBB> boxes = mc.theWorld.getCollidingBoundingBoxes(
                player, initialBox.addCoord(origX, maxStep, origZ));
        AxisAlignedBB box1 = initialBox;
        AxisAlignedBB swept1 = box1.addCoord(origX, 0.0D, origZ);
        double y1 = maxStep;
        for (AxisAlignedBB box : boxes) {
            y1 = box.calculateYOffset(swept1, y1);
        }
        box1 = box1.offset(0.0D, y1, 0.0D);
        double x1 = origX;
        for (AxisAlignedBB box : boxes) {
            x1 = box.calculateXOffset(box1, x1);
        }
        box1 = box1.offset(x1, 0.0D, 0.0D);
        double z1 = origZ;
        for (AxisAlignedBB box : boxes) {
            z1 = box.calculateZOffset(box1, z1);
        }
        box1 = box1.offset(0.0D, 0.0D, z1);

        AxisAlignedBB box2 = initialBox;
        double y2 = maxStep;
        for (AxisAlignedBB box : boxes) {
            y2 = box.calculateYOffset(box2, y2);
        }
        box2 = box2.offset(0.0D, y2, 0.0D);
        double x2 = origX;
        for (AxisAlignedBB box : boxes) {
            x2 = box.calculateXOffset(box2, x2);
        }
        box2 = box2.offset(x2, 0.0D, 0.0D);
        double z2 = origZ;
        for (AxisAlignedBB box : boxes) {
            z2 = box.calculateZOffset(box2, z2);
        }
        box2 = box2.offset(0.0D, 0.0D, z2);

        double dist1 = x1 * x1 + z1 * z1;
        double dist2 = x2 * x2 + z2 * z2;
        double bestX;
        double bestY;
        double bestZ;
        AxisAlignedBB bestBox;
        if (dist1 >= dist2) {
            bestX = x1;
            bestZ = z1;
            bestY = -y1;
            bestBox = box1;
        } else {
            bestX = x2;
            bestZ = z2;
            bestY = -y2;
            bestBox = box2;
        }
        for (AxisAlignedBB box : boxes) {
            bestY = box.calculateYOffset(bestBox, bestY);
        }
        if (clippedX * clippedX + clippedZ * clippedZ >= bestX * bestX + bestZ * bestZ) {
            return 0.0D;
        }
        AxisAlignedBB landed = bestBox.offset(0.0D, bestY, 0.0D);
        return landed.minY - initialBox.minY;
    }

}
