package dev.vibe.module.impl.world;

import dev.vibe.combat.RotationMath;
import dev.vibe.features.FeatureModule;
import dev.vibe.input.VanillaClicks;
import dev.vibe.inventory.PlayerInventorySession;
import dev.vibe.module.impl.combat.AutoBuffModule;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.meme.RewoundPitModule;
import dev.vibe.module.impl.movement.FreecamModule;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Nearby chest interaction. Both halves of a double chest share the same attempt. */
final class ChestAura {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ChestStealerModule owner;
    private final BooleanSetting enabled, walls, checkLid, pauseCombat;
    private final NumberSetting range, preAimRange, speed;
    private final RangeSetting delay;
    private final Set<BlockPos> attempted = new HashSet<>();
    private final Random random = new Random();
    private WorldClient world;
    private MovingObjectPosition target;
    private long next;

    ChestAura(ChestStealerModule owner) {
        this.owner = owner;
        enabled = owner.auraSetting(new BooleanSetting("Chest Aura", false));
        range = owner.auraSetting(new NumberSetting("Aura Range", 4, 1, 5, .1, enabled::isEnabled));
        preAimRange = owner.auraSetting(new NumberSetting("Aura Pre-Aim Range", 4, 1, 7, .1, enabled::isEnabled));
        speed = owner.auraSetting(new NumberSetting("Aura Rotation Speed", 90, 5, 180, 1, enabled::isEnabled));
        delay = owner.auraSetting(new RangeSetting("Aura Open Delay (ms)", 250, 400, 100, 2000, 10, enabled::isEnabled));
        walls = owner.auraSetting(new BooleanSetting("Aura Through Walls", false, enabled::isEnabled));
        checkLid = owner.auraSetting(new BooleanSetting("Aura Check Lid", true, enabled::isEnabled));
        pauseCombat = owner.auraSetting(new BooleanSetting("Aura Pause In Combat", true, enabled::isEnabled));
    }

    private boolean available() {
        if (!enabled.isEnabled() || mc.thePlayer == null || mc.theWorld == null || mc.playerController == null
                || !mc.thePlayer.isEntityAlive() || mc.currentScreen != null || !mc.inGameHasFocus || mc.isGamePaused()
                || mc.thePlayer.isSpectator() || mc.thePlayer.isSneaking() || mc.thePlayer.isUsingItem()
                || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer || mc.playerController.getIsHittingBlock()
                || PlayerInventorySession.isBusy() || AutoBuffModule.ownsUseInput() || RewoundPitModule.reservesActions()
                || ScaffoldModule.isActive() || FreecamModule.cameraActiveHook()
                || VanillaClicks.physicallyDown(mc.gameSettings.keyBindAttack)
                || VanillaClicks.physicallyDown(mc.gameSettings.keyBindUseItem)) return false;
        KillAuraModule combat = KillAuraModule.activeController();
        return !pauseCombat.isEnabled() || combat == null || !combat.isEnabled() || combat.getTarget() == null;
    }

    void aim() {
        if (world != mc.theWorld) {
            world = mc.theWorld;
            attempted.clear();
            next = 0;
        }
        target = null;
        MoveFixModule fix = FeatureModule.module(MoveFixModule.class);
        if (fix == null || fix.hasOtherRotationOwner(owner.getId())) return;
        if (!available() || now() < next) { clearPending(); return; }
        Vec3 eyes = mc.thePlayer.getPositionEyes(1);
        double search = Math.max(reach(), preAimRange.getDouble());
        double nearest = search * search;
        for (TileEntity tile : world.loadedTileEntityList) {
            if (!(tile instanceof TileEntityChest) || tile.isInvalid()) continue;
            BlockPos pos = tile.getPos();
            if (eyes.squareDistanceTo(new Vec3(pos).addVector(.5, .5, .5)) > (search + 1) * (search + 1)
                    || !eligible(pos)) continue;
            MovingObjectPosition hit = aimPoint(pos, eyes);
            if (hit == null) continue;
            double distance = eyes.squareDistanceTo(hit.hitVec);
            if (distance <= nearest) { nearest = distance; target = hit; }
        }
        if (target == null) { next = now() + 150; clearPending(); return; }
        Vec3 offset = target.hitVec.subtract(eyes);
        float yaw = (float) Math.toDegrees(Math.atan2(offset.zCoord, offset.xCoord)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(offset.yCoord, Math.hypot(offset.xCoord, offset.zCoord)));
        float limit = speed.getFloat();
        fix.setFakeRotation(owner.getId(), fix.getRotationYaw() + RotationMath.clamp(
                RotationMath.difference(yaw, fix.getRotationYaw()), -limit, limit),
                fix.getRotationPitch() + RotationMath.clamp(pitch - fix.getRotationPitch(), -limit, limit));
    }

    private MovingObjectPosition aimPoint(BlockPos pos, Vec3 eyes) {
        Vec3 center = new Vec3(pos).addVector(.5, .44, .5);
        MovingObjectPosition nearest = trace(pos, eyes, center);
        if (nearest != null) return nearest;
        for (EnumFacing side : EnumFacing.values()) {
            if (side == EnumFacing.DOWN) continue;
            MovingObjectPosition hit = trace(pos, eyes, center.addVector(side.getFrontOffsetX() * .35,
                    side.getFrontOffsetY() * .35, side.getFrontOffsetZ() * .35));
            if (hit != null) return hit;
        }
        return null;
    }

    private MovingObjectPosition trace(BlockPos pos, Vec3 eyes, Vec3 end) {
        MovingObjectPosition hit = walls.isEnabled()
                ? world.getBlockState(pos).getBlock().collisionRayTrace(world, pos, eyes, end)
                : world.rayTraceBlocks(eyes, end, false, true, false);
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && pos.equals(hit.getBlockPos()) ? hit : null;
    }

    void open() {
        MoveFixModule fix = FeatureModule.module(MoveFixModule.class);
        if (target == null || fix == null || !fix.ownsRotation(owner.getId()) || world != mc.theWorld || !available()) return;
        BlockPos pos = target.getBlockPos();
        if (!eligible(pos)) { clearPending(); return; }
        fix.withActionRotation(owner.getId(), () -> {
            Vec3 eyes = mc.thePlayer.getPositionEyes(1);
            double yaw = Math.toRadians(fix.getRotationYaw());
            double pitch = Math.toRadians(fix.getRotationPitch());
            Vec3 look = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            double reach = reach();
            MovingObjectPosition hit = trace(pos, eyes, eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach));
            if (hit == null) return false;
            attempted.add(pos);
            Block chest = world.getBlockState(pos).getBlock();
            for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
                BlockPos neighbor = pos.offset(side);
                if (world.isBlockLoaded(neighbor) && world.getBlockState(neighbor).getBlock() == chest) attempted.add(neighbor);
            }
            next = now() + delay.getMinInt() + random.nextInt(delay.getMaxInt() - delay.getMinInt() + 1);
            return mc.playerController.onPlayerRightClick(mc.thePlayer, world, mc.thePlayer.getHeldItem(), pos, hit.sideHit, hit.hitVec);
        });
        if (attempted.contains(pos)) clearPending();
    }

    private boolean eligible(BlockPos pos) {
        if (attempted.contains(pos) || !world.isBlockLoaded(pos)) return false;
        Block chest = world.getBlockState(pos).getBlock();
        if (chest != Blocks.chest && chest != Blocks.trapped_chest) return false;
        if (checkLid.isEnabled() && blocked(pos)) return false;
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.offset(side);
            if (world.isBlockLoaded(neighbor) && world.getBlockState(neighbor).getBlock() == chest
                    && (attempted.contains(neighbor) || checkLid.isEnabled() && blocked(neighbor))) return false;
        }
        return true;
    }

    private boolean blocked(BlockPos pos) {
        if (world.isBlockNormalCube(pos.up(), false)) return true;
        AxisAlignedBB lid = new AxisAlignedBB(pos.up(), pos.add(1, 2, 1));
        for (EntityOcelot cat : world.getEntitiesWithinAABB(EntityOcelot.class, lid)) if (cat.isSitting()) return true;
        return false;
    }

    private double reach() { return Math.min(range.getDouble(), mc.playerController.getBlockReachDistance()); }
    private static long now() { return System.nanoTime() / 1000000L; }

    void clearPending() {
        target = null;
        MoveFixModule fix = FeatureModule.module(MoveFixModule.class);
        if (fix != null) fix.clearFakeRotation(owner.getId());
    }
}
