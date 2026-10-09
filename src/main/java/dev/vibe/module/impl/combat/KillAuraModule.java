package dev.vibe.module.impl.combat;

import dev.vibe.Vibe;
import dev.vibe.combat.AuraClickScheduler;
import dev.vibe.combat.AuraRotation;
import dev.vibe.combat.RotationMath;
import dev.vibe.input.VanillaClicks;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.module.impl.world.ScaffoldModule;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Selects rotations before movement; supplies clicks in vanilla's input pass. */
public final class KillAuraModule extends Module {
    private final RangeSetting reach = addSetting(new RangeSetting("Reach", 3.0D, 3.0D, 3.0D, 6.5D, 0.05D));
    private final ModeSetting autoblock = addSetting(new ModeSetting("Autoblock Mode", "None", "None", "Vanilla", "Fake", "Legit", "AAC330", "Hypixel"));
    private final BooleanSetting onlyRightClick = addSetting(new BooleanSetting("Only When Rightclicking", false,
            () -> realBlockMode()));
    private final NumberSetting blockingRange = addSetting(new NumberSetting("Blocking Range", 3.0D, 1.0D, 7.0D, 0.1D,
            () -> !autoblock.is("None") && !autoblock.is("Hypixel")));
    // RavenBS Auto Block, exposed here only when its Hypixel controller is
    // selected.  These defaults intentionally mirror the supplied preset.
    final NumberSetting hypixelRange = addSetting(new NumberSetting("Range", 3.5D, 2.0D, 6.0D, 0.1D,
            () -> autoblock.is("Hypixel")));
    final NumberSetting hypixelMaximumHurtTime = addSetting(new NumberSetting("Maximum hurt time", 200.0D, 50.0D, 500.0D, 50.0D,
            () -> autoblock.is("Hypixel")));
    final NumberSetting hypixelMaximumHoldDuration = addSetting(new NumberSetting("Maximum hold duration", 150.0D, 50.0D, 500.0D, 50.0D,
            () -> autoblock.is("Hypixel")));
    final NumberSetting hypixelCooldown = addSetting(new NumberSetting("Cooldown", 150.0D, 0.0D, 500.0D, 50.0D,
            () -> autoblock.is("Hypixel")));
    final ModeSetting hypixelUnblockOutOfRange = addSetting(new ModeSetting("Unblock out of range", "Once",
            () -> autoblock.is("Hypixel"), "Once", "Always", "Fake"));
    final BooleanSetting hypixelForceAttack = addSetting(new BooleanSetting("Force attack", true,
            () -> autoblock.is("Hypixel")));
    final BooleanSetting hypixelForceBlockAnimation = addSetting(new BooleanSetting("Force block animation", false,
            () -> autoblock.is("Hypixel")));
    final BooleanSetting hypixelRequireLeftMouse = addSetting(new BooleanSetting("Require left mouse", true,
            () -> autoblock.is("Hypixel")));
    final BooleanSetting hypixelRequireRightMouse = addSetting(new BooleanSetting("Require right mouse", false,
            () -> autoblock.is("Hypixel")));
    final BooleanSetting hypixelDamaged = addSetting(new BooleanSetting("Damaged", false,
            () -> autoblock.is("Hypixel")));
    final BooleanSetting hypixelIgnoreTeammates = addSetting(new BooleanSetting("Ignore teammates", true,
            () -> autoblock.is("Hypixel")));
    private final NumberSetting hypixelTargetCps = addSetting(new NumberSetting("Target CPS", 10.0D, 1.0D, 20.0D, 0.5D,
            () -> autoblock.is("Hypixel")));
    private final BooleanSetting hypixelSimulateExhaust = addSetting(new BooleanSetting("Simulate exhaust", true,
            () -> autoblock.is("Hypixel")));
    private final ModeSetting yawAimpoint = addSetting(new ModeSetting("Yaw Aimpoint", "Center", "Center", "Closest"));
    private final ModeSetting pitchAimpoint = addSetting(new ModeSetting("Pitch Aimpoint", "Center", "Center", "Closest"));
    private final ModeSetting rotationMode = addSetting(new ModeSetting("Rotation Mode", "Normal", "Normal", "Acceleration"));
    private final RangeSetting rotationSpeed = addSetting(new RangeSetting("Rotation Speed", 25.0D, 40.0D, 1.0D, 180.0D, 0.5D,
            () -> rotationMode.is("Normal")));
    private final RangeSetting rotationAcceleration = addSetting(new RangeSetting("Rotation Acceleration", 2.0D, 5.0D, 0.25D, 40.0D, 0.25D,
            () -> rotationMode.is("Acceleration")));
    private final NumberSetting clickingRange = addSetting(new NumberSetting("Clicking Range", 3.0D, 2.5D, 7.0D, 0.1D));
    private final ModeSetting clickingMode = addSetting(new ModeSetting("Clicking Mode", "Normal", () -> !autoblock.is("Hypixel"), "Normal", "Drag Clicking", "Butterfly"));
    private final RangeSetting normalCps = addSetting(new RangeSetting("Normal CPS", 8.0D, 12.0D, 1.0D, 20.0D, 0.1D,
            () -> !autoblock.is("Hypixel") && clickingMode.is("Normal")));
    private final RangeSetting dragCps = addSetting(new RangeSetting("Drag CPS", 25.0D, 40.0D, 5.0D, 60.0D, 0.5D,
            () -> !autoblock.is("Hypixel") && clickingMode.is("Drag Clicking")));
    private final RangeSetting dragDuration = addSetting(new RangeSetting("Drag Duration (ms)", 120.0D, 350.0D, 50.0D, 1000.0D, 10.0D,
            () -> !autoblock.is("Hypixel") && clickingMode.is("Drag Clicking")));
    private final RangeSetting dragPause = addSetting(new RangeSetting("Drag Pause (ms)", 150.0D, 400.0D, 50.0D, 1500.0D, 10.0D,
            () -> !autoblock.is("Hypixel") && clickingMode.is("Drag Clicking")));
    private final RangeSetting butterflyCps = addSetting(new RangeSetting("Butterfly CPS", 12.0D, 18.0D, 1.0D, 40.0D, 0.1D,
            () -> !autoblock.is("Hypixel") && clickingMode.is("Butterfly")));
    private final BooleanSetting notBlockBreaking = addSetting(new BooleanSetting("Not When BlockBreaking", true));
    private final BooleanSetting aimThroughWalls = addSetting(new BooleanSetting("Aim Through Walls", false));
    private final BooleanSetting piercing = addSetting(new BooleanSetting("Piercing", false));
    private final BooleanSetting notInInv = addSetting(new BooleanSetting("Not In Inv", true));
    private final ModeSetting targetMode = addSetting(new ModeSetting("Target Mode", "Health", "Health", "Armor", "Yaw", "Distance"));
    private final NumberSetting maxAngle = addSetting(new NumberSetting("Max Angle", 180.0D, 0.0D, 180.0D, 1.0D));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private final AuraRotation rotation = new AuraRotation();
    private final AuraClickScheduler clicks = new AuraClickScheduler(random);
    private EntityLivingBase target;
    private EntityPlayer sessionPlayer;
    private boolean rotationInitialized;
    private boolean inputPrepared;
    private boolean attackOwned;
    private boolean useOwned;
    private boolean blockOwned;
    private boolean aacReleaseResubmit;
    private boolean visualBlocking;
    private boolean hitOverridden;
    private MovingObjectPosition savedMouseOver;
    private Entity savedPointedEntity;
    private long legitBlockUntil;
    private long nextLegitBlock;
    private final HypixelAutoblock hypixelBlock = new HypixelAutoblock(this);
    private long hypixelNextAttack;
    private String previousBlockMode = "None";
    private String previousClickMode = "Normal";

    public KillAuraModule() {
        super("Killaura", "Automatically aims, clicks and sword-blocks using vanilla input", Category.COMBAT, Keyboard.KEY_NONE);
    }

    public void tickStart() {
        restoreInput();
        if (previousBlockMode.equals("Hypixel") && !autoblock.is("Hypixel")) {
            resetHypixelBlock(true);
            previousBlockMode = autoblock.getValue();
        }
        inputPrepared = false;
        if (sessionPlayer != minecraft.thePlayer) {
            clear();
            blockOwned = false;
            sessionPlayer = minecraft.thePlayer;
        }
        // Scaffold owns the server rotation and the held slot while enabled.
        if (!canOperate() || isBreakingBlock() || ScaffoldModule.isActive()) { clear(); return; }
        EntityLivingBase selected = chooseTarget();
        if (selected == null) {
            // RavenBS permits the physical right mouse button to maintain its
            // manual block branch after a target leaves range.  Rotation and
            // aura attacks still stop immediately; the autoclicker can continue
            // while the physical attack button is held.
            if (autoblock.is("Hypixel")) {
                MoveFixModule fix = moveFix();
                if (fix != null) fix.clearFakeRotation(getId());
                target = null;
                rotationInitialized = false;
                clicks.reset();
                return;
            }
            clear();
            return;
        }
        if (target != selected) {
            clicks.reset();
            legitBlockUntil = nextLegitBlock = 0L;
        }
        target = selected;
        MoveFixModule fix = moveFix();
        if (fix == null) { clear(); return; }
        if (!rotationInitialized) {
            rotation.reset(fix.getRotationYaw(), fix.getRotationPitch());
            rotationInitialized = true;
        }
        float[] desired = rotationsTo(target);
        boolean acceleration = rotationMode.is("Acceleration");
        rotation.advance(desired[0], desired[1], (float) sample(acceleration ? rotationAcceleration : rotationSpeed),
                acceleration, minecraft.gameSettings.mouseSensitivity);
        fix.setFakeRotation(getId(), rotation.getYaw(), rotation.getPitch());
    }

    /** Injected after physical input/hotbar handling, before vanilla isUsingItem. */
    private void prepareInput() {
        if (inputPrepared) return;
        inputPrepared = true;
        if (!canOperate() || isBreakingBlock() || ScaffoldModule.isActive()) {
            clear();
            releaseOwnedBlock();
            return;
        }
        long now = System.nanoTime() / 1000000L;
        if (autoblock.is("Hypixel")) {
            prepareHypixelInput(now);
            return;
        }
        if (target == null || !validTarget(target)) {
            clear();
            releaseOwnedBlock();
            return;
        }
        if (!previousClickMode.equals(clickingMode.getValue())) {
            clicks.reset();
            previousClickMode = clickingMode.getValue();
        }
        if (!previousBlockMode.equals(autoblock.getValue())) {
            legitBlockUntil = nextLegitBlock = 0L;
            releaseOwnedBlock();
            previousBlockMode = autoblock.getValue();
        }
        boolean inClickRange = distanceTo(target) <= clickingRange.getDouble();
        if (!inClickRange) clicks.reset();
        boolean clickDue = inClickRange && clicks.isDue(now);
        boolean inBlockRange = swordHeld() && distanceTo(target) <= blockingRange.getDouble();
        visualBlocking = !autoblock.is("None") && inBlockRange
                && (autoblock.is("Fake") || !onlyRightClick.isEnabled() || rightMouseDown());
        boolean wantsBlock = realBlockMode() && visualBlocking;
        if (autoblock.is("Legit")) {
            if (!wantsBlock) legitBlockUntil = nextLegitBlock = 0L;
            if (wantsBlock && now >= nextLegitBlock) {
                legitBlockUntil = random.nextDouble() < 0.4D ? now + 100L + random.nextInt(151) : 0L;
                nextLegitBlock = now + 350L + random.nextInt(401);
            }
            wantsBlock &= now < legitBlockUntil;
            visualBlocking = wantsBlock;
        }
        KeyBinding attack = minecraft.gameSettings.keyBindAttack;
        attackOwned = true;
        VanillaClicks.discardPresses(attack);
        KeyBinding.setKeyBindState(attack.getKeyCode(), false);
        // Let vanilla abort an existing dig before combat takes over, even
        // when the user permits aura to interrupt block breaking.
        if (minecraft.playerController.getIsHittingBlock()) return;
        // Vanilla releases use and discards attacks in this branch. Attack on
        // the following tick, never by injecting release+attack packets here.
        if (minecraft.thePlayer.isUsingItem()) {
            if (minecraft.thePlayer.isBlocking() && (blockOwned || wantsBlock)) {
                setUseKey(wantsBlock && (!clickDue || autoblock.is("Legit")));
            } else blockOwned = false;
            return;
        }
        blockOwned = false;
        overrideHit(attackHit(sample(reach)));
        if (clickDue) {
            RangeSetting cps = clickingMode.is("Drag Clicking") ? dragCps
                    : clickingMode.is("Butterfly") ? butterflyCps : normalCps;
            int count = clicks.poll(now, clickingMode.getValue(), cps.getMin(), cps.getMax(),
                    dragDuration.getMin(), dragDuration.getMax(), dragPause.getMin(), dragPause.getMax());
            for (int i = 0; i < count; i++) VanillaClicks.pulseAttack(attack);
        }
        if (wantsBlock) {
            // A physical-style use edge; vanilla emits interact-at/interact/use.
            setUseKey(true);
            KeyBinding.onTick(minecraft.gameSettings.keyBindUseItem.getKeyCode());
            blockOwned = true;
        }
    }

    /**
     * RavenBS's Auto Block state machine, adapted to Vibe's vanilla-input
     * path.  It deliberately counts client ticks for max-hold/hurt windows
     * while using wall time only for the configured cooldown, exactly like
     * the source controller.
     */
    private void prepareHypixelInput(long now) {
        if (!previousBlockMode.equals(autoblock.getValue())) {
            hypixelBlock.reset(true);
            blockOwned = false;
            previousBlockMode = autoblock.getValue();
        }
        if (!previousClickMode.equals(clickingMode.getValue())) {
            clicks.reset();
            previousClickMode = clickingMode.getValue();
        }
        // Raven order: Auto Block, click producer, then LOWEST force-attack.
        hypixelBlock.onPrePlayerInteract();
        KeyBinding attack = minecraft.gameSettings.keyBindAttack;
        attackOwned = true;
        VanillaClicks.discardPresses(attack);
        KeyBinding.setKeyBindState(attack.getKeyCode(), false);
        boolean inClickRange = target != null && validTarget(target) && distanceTo(target) <= clickingRange.getDouble();
        // Raven's autoclicker also runs on physical LMB while aura has no target.
        boolean clickActive = inClickRange || target == null && VanillaClicks.physicallyDown(attack);
        if (!clickActive || !minecraft.inGameHasFocus || minecraft.currentScreen != null) hypixelNextAttack = 0L;
        else {
            long clickNow = System.currentTimeMillis();
            int count = 0;
            if (hypixelNextAttack == 0L) hypixelNextAttack = clickNow + hypixelAttackDelay();
            else while (hypixelNextAttack <= clickNow) {
                count++;
                hypixelNextAttack += hypixelAttackDelay();
            }
            for (int i = 0; i < count; i++) VanillaClicks.pulseAttack(attack);
        }
        if (target != null && validTarget(target)) overrideHit(attackHit(sample(reach)));
        hypixelBlock.onForceAttack();
    }

    private void resetHypixelBlock(boolean release) { hypixelBlock.reset(release); hypixelNextAttack = 0L; }

    /** AutoClicker.nextDelay from the uploaded RavenBS source. */
    private long hypixelAttackDelay() {
        int target = Math.max(1, (int) hypixelTargetCps.getDouble());
        int baseDelay = 1000 / target;
        int finalDelay;
        if (hypixelSimulateExhaust.isEnabled()) {
            int variation = random.nextInt(baseDelay + 1) - baseDelay / 2;
            finalDelay = baseDelay + variation;
            if (random.nextInt(100) < 15) {
                if (random.nextBoolean()) {
                    finalDelay = 25 + random.nextInt(16);
                } else {
                    finalDelay = baseDelay + 50 + random.nextInt(41);
                }
            }
            if (random.nextInt(100) < 8) {
                int spikeMult = 50 + random.nextInt(151);
                finalDelay = finalDelay * spikeMult / 100;
            }
            if (random.nextInt(100) < 10) {
                finalDelay += 10 + random.nextInt(26);
            }
        } else {
            finalDelay = baseDelay + (random.nextInt(21) - 10);
        }
        return Math.max(33, Math.min(180, finalDelay));
    }

    public void tickEnd() { restoreInput(); }

    private boolean canOperate() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.playerController == null
                || !minecraft.thePlayer.isEntityAlive() || minecraft.thePlayer.isSpectator() || minecraft.isGamePaused()) return false;
        return minecraft.currentScreen == null || (!notInInv.isEnabled() && minecraft.currentScreen instanceof GuiContainer);
    }

    private boolean isBreakingBlock() {
        if (!notBlockBreaking.isEnabled() || minecraft.playerController == null) return false;
        return minecraft.playerController.getIsHittingBlock() || (minecraft.objectMouseOver != null
                && minecraft.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && VanillaClicks.physicallyDown(minecraft.gameSettings.keyBindAttack));
    }

    private EntityLivingBase chooseTarget() {
        EntityLivingBase best = null;
        for (Object value : minecraft.theWorld.loadedEntityList) {
            if (!(value instanceof EntityLivingBase)) continue;
            EntityLivingBase candidate = (EntityLivingBase) value;
            if (validTarget(candidate) && (best == null || compare(candidate, best) < 0)) best = candidate;
        }
        return best;
    }

    private int compare(EntityLivingBase first, EntityLivingBase second) {
        if (priority(first) != priority(second)) return priority(first) ? -1 : 1;
        int result = Double.compare(score(first), score(second));
        if (result == 0) result = Double.compare(distanceTo(first), distanceTo(second));
        if (result == 0) result = Integer.compare(first.getEntityId(), second.getEntityId());
        return result;
    }

    private boolean priority(EntityLivingBase entity) {
        return entity instanceof EntityPlayer && Vibe.getInstance().getTargetManager() != null
                && Vibe.getInstance().getTargetManager().isTarget((EntityPlayer) entity);
    }

    private double score(EntityLivingBase entity) {
        if (targetMode.is("Health")) return entity.getHealth() + entity.getAbsorptionAmount();
        if (targetMode.is("Armor")) return entity.getTotalArmorValue();
        if (targetMode.is("Yaw")) return Math.abs(RotationMath.difference(rotationsTo(entity)[0], minecraft.thePlayer.rotationYaw));
        return distanceTo(entity);
    }

    private boolean validTarget(EntityLivingBase entity) {
        if (entity == null || entity == minecraft.thePlayer || entity.worldObj != minecraft.theWorld
                || !entity.isEntityAlive() || entity.isDead || !entity.canBeCollidedWith()
                || (entity == minecraft.thePlayer.ridingEntity && !minecraft.thePlayer.canRiderInteract())
                || (entity instanceof EntityPlayer && ((EntityPlayer) entity).isSpectator())) return false;
        TargetsModule targets = Vibe.getInstance().getModuleManager().getModule(TargetsModule.class);
        if (targets == null || !targets.canTarget(entity)) return false;
        double range = Math.max(reach.getMax(), clickingRange.getDouble());
        if (!autoblock.is("None") && !autoblock.is("Hypixel")) range = Math.max(range, blockingRange.getDouble());
        if (distanceTo(entity) > range) return false;
        float[] desired = rotationsTo(entity);
        if (!RotationMath.withinAngle(desired[0], desired[1], minecraft.thePlayer.rotationYaw,
                minecraft.thePlayer.rotationPitch, maxAngle.getFloat())) return false;
        return aimThroughWalls.isEnabled() || piercing.isEnabled()
                || minecraft.theWorld.rayTraceBlocks(eyes(), aimPoint(entity), false, true, false) == null;
    }

    /** AimAssist's horizontal and vertical aimpoint choices are independent. */
    private Vec3 aimPoint(EntityLivingBase entity) {
        AxisAlignedBB box = entity.getEntityBoundingBox();
        Vec3 eye = eyes();
        double inset = Math.min(0.1D, (box.maxY - box.minY) * 0.25D);
        return new Vec3(yawAimpoint.is("Closest") ? clamp(eye.xCoord, box.minX, box.maxX) : (box.minX + box.maxX) * 0.5D,
                pitchAimpoint.is("Closest") ? clamp(eye.yCoord, box.minY + inset, box.maxY - inset)
                        : box.minY + (box.maxY - box.minY) * 0.60D,
                yawAimpoint.is("Closest") ? clamp(eye.zCoord, box.minZ, box.maxZ) : (box.minZ + box.maxZ) * 0.5D);
    }

    private float[] rotationsTo(EntityLivingBase entity) {
        Vec3 point = aimPoint(entity).subtract(eyes());
        double horizontal = Math.sqrt(point.xCoord * point.xCoord + point.zCoord * point.zCoord);
        float yaw = horizontal < 1.0E-7D ? (rotationInitialized ? rotation.getYaw() : minecraft.thePlayer.rotationYaw)
                : (float) (Math.toDegrees(Math.atan2(point.zCoord, point.xCoord)) - 90.0D);
        return new float[] {yaw, (float) -Math.toDegrees(Math.atan2(point.yCoord, horizontal))};
    }

    /** The rotated ray must intersect the target within reach; otherwise swing air. */
    private MovingObjectPosition attackHit(double attackReach) {
        Vec3 eye = eyes();
        double yaw = Math.toRadians(rotation.getYaw());
        double pitch = Math.toRadians(rotation.getPitch());
        Vec3 end = eye.addVector(-Math.sin(yaw) * Math.cos(pitch) * attackReach,
                -Math.sin(pitch) * attackReach, Math.cos(yaw) * Math.cos(pitch) * attackReach);
        MovingObjectPosition miss = new MovingObjectPosition(MovingObjectPosition.MovingObjectType.MISS, end, null, new BlockPos(end));
        AxisAlignedBB box = hitbox(target);
        MovingObjectPosition intercept = box.calculateIntercept(eye, end);
        Vec3 point = box.isVecInside(eye) ? eye : intercept == null ? null : intercept.hitVec;
        if (point == null) return miss;
        if (!piercing.isEnabled() && minecraft.theWorld.rayTraceBlocks(eye, point, false, true, false) != null) return miss;
        // Friends and other entities obstruct this ray even with Piercing on.
        for (Object value : minecraft.theWorld.getEntitiesWithinAABBExcludingEntity(minecraft.thePlayer,
                minecraft.thePlayer.getEntityBoundingBox().addCoord(end.xCoord - eye.xCoord,
                        end.yCoord - eye.yCoord, end.zCoord - eye.zCoord).expand(1.0D, 1.0D, 1.0D))) {
            Entity other = (Entity) value;
            if (other == target || !other.canBeCollidedWith() || other.isDead
                    || (other == minecraft.thePlayer.ridingEntity && !minecraft.thePlayer.canRiderInteract())) continue;
            AxisAlignedBB otherBox = hitbox(other);
            MovingObjectPosition obstruction = otherBox.calculateIntercept(eye, end);
            if (otherBox.isVecInside(eye) || (obstruction != null
                    && eye.squareDistanceTo(obstruction.hitVec) < eye.squareDistanceTo(point))) return miss;
        }
        return new MovingObjectPosition(target, point);
    }

    private double distanceTo(Entity entity) {
        AxisAlignedBB box = hitbox(entity);
        Vec3 eye = eyes();
        return eye.distanceTo(new Vec3(clamp(eye.xCoord, box.minX, box.maxX), clamp(eye.yCoord, box.minY, box.maxY),
                clamp(eye.zCoord, box.minZ, box.maxZ)));
    }

    private AxisAlignedBB hitbox(Entity entity) {
        float border = entity.getCollisionBorderSize();
        return entity.getEntityBoundingBox().expand(border, border, border);
    }

    private Vec3 eyes() { return minecraft.thePlayer.getPositionEyes(1.0F); }
    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private double sample(RangeSetting setting) { return setting.getMin() + random.nextDouble() * (setting.getMax() - setting.getMin()); }
    private boolean realBlockMode() { return autoblock.is("Vanilla") || autoblock.is("Legit") || autoblock.is("AAC330"); }
    private boolean rightMouseDown() { return Mouse.isCreated() && Mouse.isButtonDown(1); }
    private boolean swordHeld() {
        ItemStack held = minecraft.thePlayer == null ? null : minecraft.thePlayer.getHeldItem();
        return held != null && held.getItem() instanceof ItemSword;
    }

    private void setUseKey(boolean down) {
        useOwned = true;
        KeyBinding binding = minecraft.gameSettings.keyBindUseItem;
        VanillaClicks.discardPresses(binding);
        KeyBinding.setKeyBindState(binding.getKeyCode(), down);
    }

    private void releaseOwnedBlock() {
        if (!blockOwned || minecraft.thePlayer == null) return;
        if (minecraft.thePlayer.isBlocking()) setUseKey(false);
        else blockOwned = false;
    }

    /**
     * LiquidSense's AAC autoblock alternates release packets: the first
     * RELEASE_USE_ITEM is cancelled and pushed through the normal queue once
     * more; the replay is allowed. PacketDelayService calls this at that
     * boundary, so packet order remains Minecraft's original order.
     */
    public boolean deferAacRelease(Packet<?> packet) {
        if (!isEnabled() || !autoblock.is("AAC330") || !swordHeld()
                || !(packet instanceof C07PacketPlayerDigging)
                || ((C07PacketPlayerDigging) packet).getStatus() != C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) return false;
        if (aacReleaseResubmit) { aacReleaseResubmit = false; return false; }
        aacReleaseResubmit = true;
        final Packet<?> replay = packet;
        minecraft.addScheduledTask(new Runnable() {
            @Override public void run() {
                if (aacReleaseResubmit && minecraft.getNetHandler() != null) minecraft.getNetHandler().addToSendQueue(replay);
            }
        });
        return true;
    }

    private void overrideHit(MovingObjectPosition hit) {
        savedMouseOver = minecraft.objectMouseOver;
        savedPointedEntity = minecraft.pointedEntity;
        hitOverridden = true;
        minecraft.objectMouseOver = hit;
        minecraft.pointedEntity = hit.entityHit;
    }

    private void restoreInput() {
        if (hitOverridden) {
            minecraft.objectMouseOver = savedMouseOver;
            minecraft.pointedEntity = savedPointedEntity;
            savedMouseOver = null;
            savedPointedEntity = null;
            hitOverridden = false;
        }
        if (minecraft.gameSettings != null) {
            if (attackOwned) {
                VanillaClicks.discardPresses(minecraft.gameSettings.keyBindAttack);
                VanillaClicks.restore(minecraft.gameSettings.keyBindAttack);
            }
            if (useOwned) {
                VanillaClicks.discardPresses(minecraft.gameSettings.keyBindUseItem);
                VanillaClicks.restore(minecraft.gameSettings.keyBindUseItem);
            }
        }
        attackOwned = useOwned = false;
    }

    private void clear() {
        restoreInput();
        MoveFixModule fix = moveFix();
        if (fix != null) fix.clearFakeRotation(getId());
        target = null;
        rotationInitialized = false;
        visualBlocking = false;
        legitBlockUntil = nextLegitBlock = 0L;
        resetHypixelBlock(false);
        clicks.reset();
    }

    @Override protected void onDisable() { aacReleaseResubmit = false; resetHypixelBlock(true); releaseOwnedBlock(); clear(); }

    private MoveFixModule moveFix() {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(MoveFixModule.class);
    }

    public EntityLivingBase getTarget() { return target; }
    public double getReachRadius() { return reach.getMax(); }
    boolean isHypixelBlock() { return autoblock.is("Hypixel"); }
    void setHypixelVisual(boolean value) { visualBlocking = value; }
    public void hypixelFrame() { if (isEnabled() && autoblock.is("Hypixel")) hypixelBlock.frame(); }
    public void onHypixelMouse(net.minecraftforge.client.event.MouseEvent event) {
        if (isEnabled() && autoblock.is("Hypixel")) hypixelBlock.onMouse(event);
    }
    public static boolean hypixelUseHook() {
        KillAuraModule aura = module();
        return aura != null && aura.isEnabled() && aura.autoblock.is("Hypixel") && aura.hypixelBlock.blocksUse();
    }
    public static boolean hypixelRightClickHook() {
        KillAuraModule aura = module();
        return aura != null && aura.isEnabled() && aura.autoblock.is("Hypixel") && aura.hypixelBlock.blocksRightClick();
    }

    public boolean isVisualBlocking() { return isEnabled() && visualBlocking && swordHeld(); }

    public static void prepareInputHook() {
        ScaffoldModule.prepareInputHook();
        AutoClickerModule.prepareInputHook();
        KillAuraModule aura = module();
        if (aura != null) aura.prepareInput();
    }

    public static boolean allowInputHook(boolean vanilla) {
        KillAuraModule aura = module();
        return vanilla || (aura != null && aura.minecraft.currentScreen instanceof GuiContainer
                && ((aura.canOperate() && aura.target != null) || aura.blockOwned));
    }

    public static int guiClickCounterHook(int vanilla) {
        KillAuraModule aura = module();
        // GuiContainer normally installs a 10,000-tick attack lock. Only the
        // explicit Not In Inv = off option removes that GUI lock; ordinary
        // vanilla miss cooldowns are left alone.
        return vanilla == 10000 && aura != null && aura.canOperate() && aura.target != null
                && aura.minecraft.currentScreen instanceof GuiContainer ? 0 : vanilla;
    }

    private static KillAuraModule module() {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(KillAuraModule.class);
    }
}
