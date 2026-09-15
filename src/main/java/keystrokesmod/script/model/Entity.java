package keystrokesmod.script.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemBlock;
import net.minecraft.potion.PotionEffect;

/** Raven BS entity wrapper backed by Minecraft's 1.8.9 entity object. */
public class Entity {
    public net.minecraft.entity.Entity entity;
    public String type;
    public int entityId;
    public boolean isLiving, isPlayer, isUser;
    private static final HashMap<Integer, Entity> CACHE = new HashMap<Integer, Entity>();
    public Entity(net.minecraft.entity.Entity value) {
        entity=value; if(value==null)return; type=value.getClass().getSimpleName(); entityId=value.getEntityId();
        isLiving=value instanceof EntityLivingBase; isPlayer=value instanceof EntityPlayer;
        isUser=isPlayer && Minecraft.getMinecraft().thePlayer != null && value.getUniqueID().equals(Minecraft.getMinecraft().thePlayer.getUniqueID());
    }
    public static Entity convert(net.minecraft.entity.Entity value) { if(value==null)return null; int key=value.getEntityId()+System.identityHashCode(value); Entity cached=CACHE.get(key); if(cached==null){cached=new Entity(value);CACHE.put(key,cached);} return cached; }
    public static void clearCache(){CACHE.clear();}
    public boolean allowEditing(){return entity instanceof EntityPlayer && ((EntityPlayer)entity).capabilities.allowEdit;}
    public double distanceTo(Vec3 pos){return entity.getDistance(pos.x,pos.y,pos.z);} public double distanceToSq(Vec3 pos){return entity.getDistanceSq(pos.x,pos.y,pos.z);}
    public double distanceToGround(){ if(entity==null||Minecraft.getMinecraft().theWorld==null)return 0; double y=entity.posY; while(y>0&&!Minecraft.getMinecraft().theWorld.isAirBlock(new net.minecraft.util.BlockPos(entity.posX,y-1,entity.posZ))) y--; return entity.posY-y; }
    public boolean isHoldingBlock(){return isLiving&&((EntityLivingBase)entity).getHeldItem()!=null&&((EntityLivingBase)entity).getHeldItem().getItem() instanceof ItemBlock;}
    public boolean isHoldingWeapon(){return isLiving&&((EntityLivingBase)entity).getHeldItem()!=null&&((EntityLivingBase)entity).getHeldItem().getItem() instanceof net.minecraft.item.ItemSword;}
    public float getAbsorption(){return entity instanceof EntityLivingBase?((EntityLivingBase)entity).getAbsorptionAmount():-1;}
    public Vec3 getBlockPosition(){return new Vec3(entity.getPosition());} public String getDisplayName(){return entity instanceof EntityItem?((EntityItem)entity).getEntityItem().getDisplayName():entity.getDisplayName().getUnformattedText();}
    public Entity getRidingEntity(){return convert(entity.ridingEntity);} public Entity getRiddenByEntity(){return convert(entity.riddenByEntity);} public Vec3 getServerPosition(){return new Vec3(entity.serverPosX,entity.serverPosY,entity.serverPosZ);}
    public int getExperienceLevel(){return entity instanceof EntityPlayer?((EntityPlayer)entity).experienceLevel:0;} public float getExperience(){return entity instanceof EntityPlayer?((EntityPlayer)entity).experience:0;}
    public float getFallDistance(){return entity.fallDistance;} public String getUUID(){return entity.getUniqueID().toString();} public String getCustomNameTag(){return entity.getCustomNameTag();}
    public double getBPS(){double x=entity.posX-entity.prevPosX,z=entity.posZ-entity.prevPosZ;return Math.sqrt(x*x+z*z)*20D;} public String getFacing(){return entity.getHorizontalFacing().name();}
    public float getHealth(){return entity instanceof EntityLivingBase?((EntityLivingBase)entity).getHealth():-1;} public boolean isSleeping(){return entity instanceof EntityPlayer&&((EntityPlayer)entity).isPlayerSleeping();}
    public float getEyeHeight(){return entity.getEyeHeight();} public float getHeight(){return entity.height;} public float getWidth(){return entity.width;} public boolean isBurning(){return entity.isBurning();}
    public ItemStack getHeldItem(){if(entity instanceof EntityItem)return ItemStack.convert(((EntityItem)entity).getEntityItem());return entity instanceof EntityLivingBase?ItemStack.convert(((EntityLivingBase)entity).getHeldItem()):null;}
    public int getHurtTime(){return entity instanceof EntityLivingBase?((EntityLivingBase)entity).hurtTime:-1;} public boolean isConsuming(){return entity instanceof EntityPlayer&&((EntityPlayer)entity).isUsingItem();}
    public Vec3 getLastPosition(){return new Vec3(entity.lastTickPosX,entity.lastTickPosY,entity.lastTickPosZ);} public float getMaxHealth(){return entity instanceof EntityLivingBase?((EntityLivingBase)entity).getMaxHealth():-1;} public int getMaxHurtTime(){return entity instanceof EntityLivingBase?((EntityLivingBase)entity).maxHurtTime:-1;}
    public String getName(){return entity instanceof EntityItem?ItemStack.convert(((EntityItem)entity).getEntityItem()).name:entity.getName();} public NetworkPlayer getNetworkPlayer(){return Minecraft.getMinecraft().getNetHandler()==null?null:NetworkPlayer.convert(Minecraft.getMinecraft().getNetHandler().getPlayerInfo(entity.getUniqueID()));}
    public float getPitch(){return entity.rotationPitch;} public Vec3 getPosition(){return new Vec3(entity.posX,entity.posY,entity.posZ);} public double getSpeed(){return Math.sqrt(entity.motionX*entity.motionX+entity.motionZ*entity.motionZ);}
    public List<Object[]> getPotionEffects(){List<Object[]> result=new ArrayList<Object[]>();if(!(entity instanceof EntityLivingBase))return result;for(PotionEffect effect:(List<PotionEffect>)((EntityLivingBase)entity).getActivePotionEffects())result.add(new Object[]{effect.getPotionID(),effect.getEffectName(),effect.getAmplifier(),effect.getDuration()});return result;}
    public ItemStack getArmorInSlot(int slot){return entity instanceof EntityPlayer&&slot>=0&&slot<4?ItemStack.convert(((EntityPlayer)entity).inventory.armorInventory[slot]):null;}
    public int getSwingProgress(){return entity instanceof EntityLivingBase?((EntityLivingBase)entity).swingProgressInt:-1;} public float getPrevSwingProgress(){return entity instanceof EntityLivingBase?((EntityLivingBase)entity).prevSwingProgress:-1F;} public int getTicksExisted(){return entity.ticksExisted;}
    public float getYaw(){return entity.rotationYaw;} public int getFireResistance(){return entity.fireResistance;} public float getPrevYaw(){return entity.prevRotationYaw;} public float getPrevPitch(){return entity.prevRotationPitch;}
    public boolean isCreative(){return entity instanceof EntityPlayer&&((EntityPlayer)entity).capabilities.isCreativeMode;} public boolean isCollided(){return entity.isCollided;} public boolean isCollidedHorizontally(){return entity.isCollidedHorizontally;} public boolean isCollidedVertically(){return entity.isCollidedVertically;}
    public boolean isDead(){return entity.isDead||(entity instanceof EntityLivingBase&&((EntityLivingBase)entity).deathTime>0);} public int getHunger(){return entity instanceof EntityPlayer?((EntityPlayer)entity).getFoodStats().getFoodLevel():0;} public float getSaturation(){return entity instanceof EntityPlayer?((EntityPlayer)entity).getFoodStats().getSaturationLevel():0;}
    public float getAir(){return entity.getAir();} public boolean isInvisible(){return entity.isInvisible();} public boolean isInWater(){return entity.isInWater();} public boolean isInLava(){return entity.isInLava();} public Entity getFisher(){return entity instanceof EntityFishHook?convert(((EntityFishHook)entity).angler):null;}
    public boolean isInLiquid(){return entity.isInWater()||entity.isInLava();} public boolean isOnLadder(){return entity instanceof EntityLivingBase&&((EntityLivingBase)entity).isOnLadder();} public boolean isOnEdge(){return !entity.onGround||!Minecraft.getMinecraft().theWorld.getCollidingBoundingBoxes(entity,entity.getEntityBoundingBox().offset(0,-0.5D,0)).isEmpty();}
    public boolean isSprinting(){return entity.isSprinting();} public boolean isSneaking(){return entity.isSneaking();} public boolean isUsingItem(){return entity instanceof EntityPlayer&&((EntityPlayer)entity).isUsingItem();} public boolean onGround(){return entity.onGround;}
    public void setMotion(double x,double y,double z){entity.motionX=x;entity.motionY=y;entity.motionZ=z;} public Vec3 getMotion(){return new Vec3(entity.motionX,entity.motionY,entity.motionZ);} public void setPitch(float value){entity.rotationPitch=value;} public void setYaw(float value){entity.rotationYaw=value;} public void setPosition(Vec3 value){setPosition(value.x,value.y,value.z);} public void setPosition(double x,double y,double z){entity.setPosition(x,y,z);}
}
