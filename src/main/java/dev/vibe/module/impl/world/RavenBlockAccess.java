package dev.vibe.module.impl.world;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.enchantment.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import dev.vibe.input.VanillaClicks;

/** Source-preserved RavenBS utility operations used by the port. */
public final class RavenBlockAccess {
   private static Minecraft mc() { return Minecraft.getMinecraft(); }
   private RavenBlockAccess() { }
   public static boolean nullCheck() { return mc().thePlayer != null && mc().theWorld != null; }
   public static boolean isBindDown(KeyBinding binding) {
      // Native input in-game; a binding-state fallback permits headless verification.
      return org.lwjgl.input.Keyboard.isCreated() || org.lwjgl.input.Mouse.isCreated()
         ? VanillaClicks.physicallyDown(binding) : binding.isKeyDown();
   }
   public static boolean canBePlaced(ItemBlock itemBlock) {
      Block block = itemBlock.getBlock();
      return block == null
         ? false
         : !RavenBlockAccess.isInteractable(block)
            && !(block instanceof BlockSnow)
            && !(block instanceof BlockWeb)
            && !(block instanceof BlockSapling)
            && !(block instanceof BlockDaylightDetector)
            && !(block instanceof BlockBeacon)
            && !(block instanceof BlockBanner)
            && !(block instanceof BlockEndPortalFrame)
            && !(block instanceof BlockEndPortal)
            && !(block instanceof BlockLever)
            && !(block instanceof BlockButton)
            && !(block instanceof BlockSkull)
            && !(block instanceof BlockLiquid)
            && !(block instanceof BlockCactus)
            && !(block instanceof BlockDoublePlant)
            && !(block instanceof BlockLilyPad)
            && !(block instanceof BlockCarpet)
            && !(block instanceof BlockTripWire)
            && !(block instanceof BlockTripWireHook)
            && !(block instanceof BlockTallGrass)
            && !(block instanceof BlockFlower)
            && !(block instanceof BlockFlowerPot)
            && !(block instanceof BlockSign)
            && !(block instanceof BlockLadder)
            && !(block instanceof BlockTorch)
            && !(block instanceof BlockRedstoneTorch)
            && !(block instanceof BlockStairs)
            && !(block instanceof BlockSlab)
            && !(block instanceof BlockFence)
            && !(block instanceof BlockPane)
            && !(block instanceof BlockStainedGlassPane)
            && !(block instanceof BlockGravel)
            && !(block instanceof BlockClay)
            && !(block instanceof BlockSand)
            && !(block instanceof BlockSoulSand)
            && !(block instanceof BlockRailBase);
   }
   public static double getHorizontalSpeed() {
      return getHorizontalSpeed(mc().thePlayer);
   }
   public static double getHorizontalSpeed(Entity entity) {
      return Math.sqrt(entity.motionX * entity.motionX + entity.motionZ * entity.motionZ);
   }
   public static Vec3 getLookVec(float yaw, float pitch) {
      float f = MathHelper.cos(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
      float f1 = MathHelper.sin(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
      float f2 = -MathHelper.cos(-pitch * (float) (Math.PI / 180.0));
      float f3 = MathHelper.sin(-pitch * (float) (Math.PI / 180.0));
      return new Vec3(f1 * f2, f3, f * f2);
   }
   public static Block getBlock(BlockPos blockPos) {
      return getBlockState(blockPos).getBlock();
   }
   public static Block getBlock(double x, double y, double z) {
      return getBlockState(new BlockPos(x, y, z)).getBlock();
   }
   public static Block getBlock(Vec3 position) {
      return getBlockState(new BlockPos(position.xCoord, position.yCoord, position.zCoord)).getBlock();
   }
   public static IBlockState getBlockState(BlockPos blockPos) {
      return mc().theWorld != null && blockPos != null ? mc().theWorld.getBlockState(blockPos) : Blocks.air.getDefaultState();
   }
   public static boolean notFull(Block block) {
      return block instanceof BlockFenceGate
         || block instanceof BlockLadder
         || block instanceof BlockFlowerPot
         || block instanceof BlockBasePressurePlate
         || isFluid(block)
         || block instanceof BlockFence
         || block instanceof BlockAnvil
         || block instanceof BlockEnchantmentTable
         || block instanceof BlockChest;
   }
   public static boolean isFluid(Block block) {
      return block.getMaterial() == Material.lava || block.getMaterial() == Material.water;
   }
   public static boolean isInteractable(Block block) {
      return block instanceof BlockTrapDoor
         || block instanceof BlockDoor
         || block instanceof BlockContainer
         || block instanceof BlockJukebox
         || block instanceof BlockFenceGate
         || block instanceof BlockChest
         || block instanceof BlockEnderChest
         || block instanceof BlockEnchantmentTable
         || block instanceof BlockBrewingStand
         || block instanceof BlockBed
         || block instanceof BlockDropper
         || block instanceof BlockDispenser
         || block instanceof BlockHopper
         || block instanceof BlockAnvil
         || block instanceof BlockNote
         || block instanceof BlockWorkbench;
   }
   public static boolean isInteractable(MovingObjectPosition mv) {
      if (mv == null || mv.typeOfHit != MovingObjectType.BLOCK || mv.getBlockPos() == null) {
         return false;
      } else {
         return mc().thePlayer.isSneaking() && mc().thePlayer.getHeldItem() != null ? false : isInteractable(getBlock(mv.getBlockPos()));
      }
   }
   public static BlockPos offsetPos(MovingObjectPosition mop) {
      return mop.getBlockPos().offset(mop.sideHit);
   }
   public static boolean canPlaceBlockOnSide(ItemStack stack, BlockPos pos, EnumFacing side) {
      return stack != null && stack.getItem() instanceof ItemBlock
         ? ((ItemBlock)stack.getItem()).canPlaceBlockOnSide(mc().theWorld, pos, side, mc().thePlayer, stack)
         : false;
   }
   public static boolean check(BlockPos blockPos, Block block) {
      return getBlock(blockPos) == block;
   }
   public static float getBlockHardness(Block block, ItemStack itemStack, boolean ignoreSlow, boolean ignoreGround) {
      float getBlockHardness = block.getBlockHardness(mc().theWorld, null);
      if (getBlockHardness < 0.0F) {
         return 0.0F;
      } else {
         return !block.getMaterial().isToolNotRequired() && (itemStack == null || !itemStack.canHarvestBlock(block))
            ? getToolDigEfficiency(itemStack, block, ignoreSlow, ignoreGround) / getBlockHardness / 100.0F
            : getToolDigEfficiency(itemStack, block, ignoreSlow, ignoreGround) / getBlockHardness / 30.0F;
      }
   }
   public static float getToolDigEfficiency(ItemStack itemStack, Block block, boolean ignoreSlow, boolean ignoreGround) {
      float n = itemStack == null ? 1.0F : itemStack.getItem().getStrVsBlock(itemStack, block);
      if (n > 1.0F) {
         int getEnchantmentLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, itemStack);
         if (getEnchantmentLevel > 0 && itemStack != null) {
            n += getEnchantmentLevel * getEnchantmentLevel + 1;
         }
      }

      if (mc().thePlayer.isPotionActive(Potion.digSpeed)) {
         n *= 1.0F + (mc().thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1) * 0.2F;
      }

      if (!ignoreSlow) {
         if (mc().thePlayer.isPotionActive(Potion.digSlowdown)) {
            float n2;
            switch (mc().thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) {
               case 0:
                  n2 = 0.3F;
                  break;
               case 1:
                  n2 = 0.09F;
                  break;
               case 2:
                  n2 = 0.0027F;
                  break;
               default:
                  n2 = 8.1E-4F;
            }

            n *= n2;
         }

         if (mc().thePlayer.isInsideOfMaterial(Material.water) && !EnchantmentHelper.getAquaAffinityModifier(mc().thePlayer)) {
            n /= 5.0F;
         }

         if (!mc().thePlayer.onGround && !ignoreGround) {
            n /= 5.0F;
         }
      }

      return n;
   }
   public static MovingObjectPosition rayCastBlock(double distance, float yaw, float pitch) {
      Vec3 eyeVec = mc().thePlayer.getPositionEyes(1.0F);
      Vec3 lookVec = getLookVec(yaw, pitch);
      Vec3 sumVec = eyeVec.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
      MovingObjectPosition mop = mc().theWorld.rayTraceBlocks(eyeVec, sumVec, false, false, false);
      return mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK ? mop : null;
   }
}
