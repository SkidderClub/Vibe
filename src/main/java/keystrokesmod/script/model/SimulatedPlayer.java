package keystrokesmod.script.model;

import java.util.Collections;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;

/**
 * Lightweight public facade for Raven's prediction object. Vibe routes its
 * core position/motion behaviour through Simulation and exposes the same
 * utility method signatures expected by scripts without touching live state.
 */
public class SimulatedPlayer {
    private final Simulation simulation = Simulation.create();
    private boolean sprintRequested;
    public static SimulatedPlayer fromClientPlayer(net.minecraft.util.MovementInput input) {
        SimulatedPlayer value = new SimulatedPlayer();
        if (Minecraft.getMinecraft().thePlayer != null) {
            net.minecraft.client.entity.EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            value.resetSimulationState(player.posX, player.posY, player.posZ, player.onGround);
            value.simulation.setYaw(player.rotationYaw); value.simulation.setPitch(player.rotationPitch);
            value.simulation.setForward(input.moveForward); value.simulation.setStrafe(input.moveStrafe);
            value.simulation.setJump(input.jump); value.simulation.setSneak(input.sneak);
        }
        return value;
    }
    public Vec3 getPos(){return simulation.getPosition();} public void tick(){simulation.setSprinting(sprintRequested);simulation.tick();}
    public void setSprintRequested(boolean value){sprintRequested=value;} public void resetSimulationState(double x,double y,double z,boolean ground){simulation.resetState(x,y,z,ground);}
    public void moveEntity(double x,double y,double z){Vec3 p=simulation.getPosition();simulation.resetState(p.x+x,p.y+y,p.z+z,simulation.onGround());}
    public AxisAlignedBB getEntityBoundingBox(){Vec3 p=getPos();return new AxisAlignedBB(p.x-0.3,p.y,p.z-0.3,p.x+0.3,p.y+1.8,p.z+0.3);} public void setEntityBoundingBox(AxisAlignedBB box){resetSimulationState((box.minX+box.maxX)*0.5,box.minY,(box.minZ+box.maxZ)*0.5,simulation.onGround());}
    public void setOnFireFromLava(){} public void setFire(int seconds){} public boolean isWet(){return false;} public void doBlockCollisions(){} public void updateFallState(double motionY,boolean ground){} public boolean handleWaterMovement(){return false;} public boolean handleMaterialAcceleration(AxisAlignedBB box,Material material){return false;} public boolean isAreaLoaded(int minX,int minY,int minZ,int maxX,int maxY,int maxZ,boolean value){return true;} public void onEntityCollidedWithBlock(Block block){} public boolean canTriggerWalking(){return true;} public boolean isOnLadder(){return false;}
    public void moveFlying(float strafe,float forward,float friction){simulation.setStrafe(strafe);simulation.setForward(forward);} public void jump(){simulation.setJump(true);} public boolean isSprinting(){return sprintRequested;} public boolean isPotionActive(Potion potion){return false;} public PotionEffect getActivePotionEffect(Potion potion){return null;} public float getJumpUpwardsMotion(){return 0.42F;} public boolean isInWater(){return false;} public void updateLivingEntityInput(){} public boolean isServerWorld(){return false;} public boolean isMovementBlocked(){return false;} public boolean isInLava(){return false;} public void updateAITick(){} public boolean isOffsetPositionInLiquid(double x,double y,double z){return false;} public boolean isLiquidPresentInAABB(AxisAlignedBB box){return false;} public List<AxisAlignedBB> getCollidingBoundingBoxes(AxisAlignedBB box){return Collections.emptyList();} public IBlockState getBlockState(BlockPos pos){return Minecraft.getMinecraft().theWorld.getBlockState(pos);} public boolean isSneaking(){return false;} public float getEyeHeight(){return 1.62F;}
}
