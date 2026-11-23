package dev.vibe.module.impl.visual;

import com.mojang.authlib.GameProfile;
import dev.vibe.features.FeatureModule;
import dev.vibe.module.Category;
import dev.vibe.setting.BooleanSetting;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** A stationary rendering dummy whose inventory and pose can mirror the local player. */
public final class FakePlayerModule extends FeatureModule {
    private final BooleanSetting mirrorGear = addSetting(new BooleanSetting("Mirror Equipment", true));
    private final BooleanSetting mirrorPose = addSetting(new BooleanSetting("Mirror Pose", true));
    private WorldClient world;
    private EntityPlayerSP owner;
    private Dummy dummy;

    public FakePlayerModule() {
        super("Fake Player", "Spawn a local dummy for previewing ESP, cosmetics and animations", Category.VISUAL);
    }

    @Override public void tick() {
        if (world != mc.theWorld || owner != mc.thePlayer || mc.thePlayer == null || mc.thePlayer.isDead) clear();
        if (!playing()) return;
        if (dummy == null) spawn();
        for (int slot = 0; slot <= 4; slot++) {
            ItemStack source = mirrorGear.isEnabled() ? owner.getEquipmentInSlot(slot) : null;
            if (!ItemStack.areItemStacksEqual(source, dummy.getEquipmentInSlot(slot))) {
                dummy.setCurrentItemOrArmor(slot, source == null ? null : source.copy());
            }
        }
        dummy.setSneaking(mirrorPose.isEnabled() && owner.isSneaking());
        dummy.setSprinting(mirrorPose.isEnabled() && owner.isSprinting());
        if (mirrorPose.isEnabled() && owner.isSwingInProgress && owner.swingProgressInt == 1) dummy.swingItem();
        dummy.mirroredUse = mirrorPose.isEnabled() && owner.isUsingItem() && dummy.getHeldItem() != null;
    }

    private void spawn() {
        world = mc.theWorld;
        owner = mc.thePlayer;
        dummy = new Dummy(world, owner);
        double yaw = Math.toRadians(owner.rotationYaw);
        for (double distance : new double[]{2.5, 1.5, .7, 0}) {
            dummy.setLocationAndAngles(owner.posX - Math.sin(yaw) * distance, owner.posY,
                    owner.posZ + Math.cos(yaw) * distance, owner.rotationYaw + 180F, 0);
            if (world.getCollidingBoundingBoxes(dummy, dummy.getEntityBoundingBox()).isEmpty()) break;
        }
        dummy.rotationYawHead = dummy.prevRotationYawHead = dummy.rotationYaw;
        dummy.renderYawOffset = dummy.prevRenderYawOffset = dummy.rotationYaw;
        int id = Integer.MIN_VALUE + 550;
        while (world.getEntityByID(id) != null) id++;
        dummy.setEntityId(id);
        world.addEntityToWorld(id, dummy);
    }

    private void clear() {
        if (dummy != null && world.getEntityByID(dummy.getEntityId()) == dummy) world.removeEntityFromWorld(dummy.getEntityId());
        dummy = null;
        owner = null;
        world = null;
    }

    @Override protected void onDisable() { clear(); }

    public static boolean isDummy(Entity entity) { return entity instanceof Dummy; }

    private static final class Dummy extends EntityOtherPlayerMP {
        private final EntityPlayerSP owner;
        private boolean mirroredUse;

        Dummy(WorldClient world, EntityPlayerSP owner) {
            super(world, new GameProfile(java.util.UUID.randomUUID(), "Fake Player"));
            this.owner = owner;
        }

        @Override public boolean canBeCollidedWith() { return false; }
        @Override public boolean canBePushed() { return false; }
        @Override public boolean isUsingItem() { return mirroredUse; }
        @Override public int getItemInUseCount() { return mirroredUse ? owner.getItemInUseCount() : 0; }
        @Override public ResourceLocation getLocationSkin() { return owner.getLocationSkin(); }
        @Override public ResourceLocation getLocationCape() { return owner.getLocationCape(); }
        @Override public String getSkinType() { return owner.getSkinType(); }
        @Override public boolean isWearing(net.minecraft.entity.player.EnumPlayerModelParts part) { return owner.isWearing(part); }
    }
}
