package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.util.Arrays;
import java.util.Collections;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Client-side aim smoothing adapted to Vibe's settings and Target filter.
 * It intentionally excludes Vape-specific friend and control-claim features.
 */
public final class AimAssistModule extends Module {

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Simple", "Simple", "Adaptive"));
    private final BooleanSetting requireMouseDown = addSetting(new BooleanSetting("Require Mouse Down", true));
    private final BooleanSetting aimVertically = addSetting(new BooleanSetting("Aim Vertically", false));
    private final BooleanSetting boostAim = addSetting(new BooleanSetting("Boost Aim", false));
    private final NumberSetting boostTowards = addSetting(new NumberSetting("Boost Towards", 1.5D, 1.0D, 3.0D, 0.05D,
            () -> boostAim.isEnabled()));
    private final NumberSetting slowAway = addSetting(new NumberSetting("Slow Away", .45D, 0.0D, 1.0D, 0.05D,
            () -> boostAim.isEnabled()));
    private final BooleanSetting strafeIncrease = addSetting(new BooleanSetting("Strafe Increase", false));
    private final BooleanSetting checkBlockBreak = addSetting(new BooleanSetting("Check Block Break", false));
    private final BooleanSetting breakBlocksWhitelist = addSetting(new BooleanSetting("Break Blocks Whitelist", false,
            () -> checkBlockBreak.isEnabled()));
    private final MultiSelectSetting breakBlockItems = addSetting(new MultiSelectSetting("Break Block Items",
            Arrays.asList("Pickaxes", "Shovels", "Axes"), Arrays.asList("Pickaxes", "Shovels"),
            () -> checkBlockBreak.isEnabled() && breakBlocksWhitelist.isEnabled()));
    private final BooleanSetting limitToItems = addSetting(new BooleanSetting("Limit To Items", false));
    private final MultiSelectSetting allowedItems = addSetting(new MultiSelectSetting("Allowed Items",
            Arrays.asList("Swords", "Axes", "Tools", "Bows"), Collections.singletonList("Swords"),
            () -> limitToItems.isEnabled()));
    private final NumberSetting verticalSpeed = addSetting(new NumberSetting("Vertical Speed", 5.0D, 1.0D, 10.0D, 0.1D,
            () -> aimVertically.isEnabled()));
    private final NumberSetting horizontalSpeed = addSetting(new NumberSetting("Horizontal Speed", 5.0D, 1.0D, 10.0D, 0.1D));
    private final NumberSetting maxAngle = addSetting(new NumberSetting("Max Angle", 180.0D, 1.0D, 360.0D, 1.0D));
    private final NumberSetting yawDeadzone = addSetting(new NumberSetting("Yaw Deadzone", 0.35D, 0.0D, 12.0D, 0.05D));
    private final NumberSetting pitchDeadzone = addSetting(new NumberSetting("Pitch Deadzone", 0.25D, 0.0D, 12.0D, 0.05D,
            () -> aimVertically.isEnabled()));
    private final NumberSetting distance = addSetting(new NumberSetting("Distance", 5.0D, 1.0D, 8.0D, 0.1D));
    private final BooleanSetting throughWalls = addSetting(new BooleanSetting("Through Walls", true));
    private final ModeSetting targetArea = addSetting(new ModeSetting("Target Area", "Center", "Center", "Closest"));
    private final ModeSetting targetMode = addSetting(new ModeSetting("Target Mode", "Yaw", "Yaw", "Distance", "Armor", "Threat", "Health"));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private EntityLivingBase target;
    private float previousYaw;
    private boolean hasPreviousYaw;

    public AimAssistModule() {
        super("AimAssist", "Smoothly aims at the best Vibe target", Category.COMBAT, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.currentScreen != null
                || (requireMouseDown.isEnabled() && !Mouse.isButtonDown(0)) || !canAim()) {
            target = null;
            return;
        }
        target = findBestTarget();
        if (target == null) {
            return;
        }
        float[] rotations = rotationsTo(target);
        float yawDelta = MathHelper.wrapAngleTo180_float(rotations[0] - minecraft.thePlayer.rotationYaw);
        float pitchDelta = rotations[1] - minecraft.thePlayer.rotationPitch;
        if (Math.abs(yawDelta) <= yawDeadzone.getFloat()) yawDelta = 0.0F;
        if (Math.abs(pitchDelta) <= pitchDeadzone.getFloat()) pitchDelta = 0.0F;
        float yawSpeed = speed(horizontalSpeed.getFloat(), Math.abs(yawDelta));
        if (boostAim.isEnabled() && hasPreviousYaw) {
            float mouseDirection = MathHelper.wrapAngleTo180_float(minecraft.thePlayer.rotationYaw - previousYaw);
            if (Math.abs(mouseDirection) > .001F) {
                boolean towards = Math.signum(mouseDirection) == Math.signum(yawDelta);
                yawSpeed *= towards ? boostTowards.getFloat() : slowAway.getFloat();
            }
        }
        if (strafeIncrease.isEnabled() && Math.abs(minecraft.thePlayer.movementInput.moveStrafe) > 0.01F) {
            yawSpeed *= 1.6F;
        }
        minecraft.thePlayer.rotationYaw += clamp(yawDelta, -yawSpeed, yawSpeed);
        if (aimVertically.isEnabled()) {
            float pitchSpeed = speed(verticalSpeed.getFloat(), Math.abs(pitchDelta));
            minecraft.thePlayer.rotationPitch = MathHelper.clamp_float(minecraft.thePlayer.rotationPitch
                    + clamp(pitchDelta, -pitchSpeed, pitchSpeed), -90.0F, 90.0F);
        }
        previousYaw = minecraft.thePlayer.rotationYaw;
        hasPreviousYaw = true;
    }

    @Override
    protected void onDisable() {
        target = null;
        hasPreviousYaw = false;
    }

    private boolean canAim() {
        if (limitToItems.isEnabled() && !matches(minecraft.thePlayer.getHeldItem(), allowedItems)) {
            return false;
        }
        if (!checkBlockBreak.isEnabled()) {
            return true;
        }
        MovingObjectPosition hit = minecraft.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return true;
        }
        return breakBlocksWhitelist.isEnabled() && !matches(minecraft.thePlayer.getHeldItem(), breakBlockItems);
    }

    private EntityLivingBase findBestTarget() {
        TargetsModule targets = Vibe.getInstance().getModuleManager().getModule(TargetsModule.class);
        if (targets == null) {
            return null;
        }
        EntityLivingBase best = null;
        double bestScore = Double.MAX_VALUE;
        for (Object object : minecraft.theWorld.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase candidate = (EntityLivingBase) object;
            if (candidate == minecraft.thePlayer || !targets.canTarget(candidate) || minecraft.thePlayer.getDistanceToEntity(candidate) > distance.getDouble()) {
                continue;
            }
            if (!throughWalls.isEnabled() && !hasLineOfSight(candidate)) {
                continue;
            }
            float yawDifference = Math.abs(MathHelper.wrapAngleTo180_float(rotationsTo(candidate)[0] - minecraft.thePlayer.rotationYaw));
            if (yawDifference > maxAngle.getFloat() * 0.5F) {
                continue;
            }
            double score = score(candidate, yawDifference);
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private double score(EntityLivingBase candidate, float yawDifference) {
        if (candidate instanceof net.minecraft.entity.player.EntityPlayer && Vibe.getInstance().getTargetManager() != null
                && Vibe.getInstance().getTargetManager().isTarget((net.minecraft.entity.player.EntityPlayer) candidate)) {
            return -1000000.0D;
        }
        if (targetMode.is("Distance")) {
            return minecraft.thePlayer.getDistanceToEntity(candidate);
        }
        if (targetMode.is("Armor")) {
            return candidate.getTotalArmorValue() * 100.0D + minecraft.thePlayer.getDistanceToEntity(candidate);
        }
        if (targetMode.is("Threat")) {
            return -candidate.getTotalArmorValue() * 100.0D + minecraft.thePlayer.getDistanceToEntity(candidate);
        }
        if (targetMode.is("Health")) {
            return candidate.getHealth() * 100.0D + minecraft.thePlayer.getDistanceToEntity(candidate);
        }
        return yawDifference;
    }

    private float[] rotationsTo(EntityLivingBase entity) {
        AxisAlignedBB box = entity.getEntityBoundingBox();
        double eyeX = minecraft.thePlayer.posX;
        double eyeY = minecraft.thePlayer.posY + minecraft.thePlayer.getEyeHeight();
        double eyeZ = minecraft.thePlayer.posZ;
        double x;
        double y;
        double z;
        if (targetArea.is("Closest")) {
            x = clamp(eyeX, box.minX, box.maxX);
            y = clamp(eyeY, box.minY + 0.1D, box.maxY - 0.1D);
            z = clamp(eyeZ, box.minZ, box.maxZ);
        } else {
            x = (box.minX + box.maxX) * 0.5D;
            y = box.minY + (box.maxY - box.minY) * 0.60D;
            z = (box.minZ + box.maxZ) * 0.5D;
        }
        double deltaX = x - eyeX;
        double deltaY = y - eyeY;
        double deltaZ = z - eyeZ;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) -(Math.atan2(deltaY, horizontal) * 180.0D / Math.PI);
        return new float[] {yaw, MathHelper.clamp_float(pitch, -90.0F, 90.0F)};
    }

    /** Stop before a target if the intended aim point is hidden by terrain. */
    private boolean hasLineOfSight(EntityLivingBase entity) {
        AxisAlignedBB box = entity.getEntityBoundingBox();
        double eyeX = minecraft.thePlayer.posX;
        double eyeY = minecraft.thePlayer.posY + minecraft.thePlayer.getEyeHeight();
        double eyeZ = minecraft.thePlayer.posZ;
        double targetX = targetArea.is("Closest") ? clamp(eyeX, box.minX, box.maxX) : (box.minX + box.maxX) * 0.5D;
        double targetY = targetArea.is("Closest") ? clamp(eyeY, box.minY + 0.1D, box.maxY - 0.1D) : box.minY + (box.maxY - box.minY) * 0.60D;
        double targetZ = targetArea.is("Closest") ? clamp(eyeZ, box.minZ, box.maxZ) : (box.minZ + box.maxZ) * 0.5D;
        return minecraft.theWorld.rayTraceBlocks(new net.minecraft.util.Vec3(eyeX, eyeY, eyeZ),
                new net.minecraft.util.Vec3(targetX, targetY, targetZ), false, true, false) == null;
    }

    private float speed(float configured, float error) {
        if (mode.is("Simple")) {
            return configured;
        }
        float strength = Math.min(1.0F, error / 35.0F);
        return configured * (0.35F + strength * 0.65F);
    }

    private boolean matches(ItemStack stack, MultiSelectSetting filter) {
        if (stack == null) {
            return false;
        }
        Item item = stack.getItem();
        return (filter.isSelected("Swords") && item instanceof ItemSword)
                || (filter.isSelected("Axes") && item instanceof ItemAxe)
                || (filter.isSelected("Pickaxes") && item instanceof ItemPickaxe)
                || (filter.isSelected("Shovels") && item instanceof ItemSpade)
                || (filter.isSelected("Tools") && (item instanceof ItemPickaxe || item instanceof ItemSpade))
                || (filter.isSelected("Bows") && item instanceof ItemBow);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public EntityLivingBase getTarget() { return target; }
}
