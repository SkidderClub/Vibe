package dev.vibe.module.impl.combat;

import dev.vibe.Vibe;
import dev.vibe.inventory.*;
import dev.vibe.combat.ArrowAimSolver;
import dev.vibe.combat.ProjectileAimSolver;
import dev.vibe.combat.ProjectileAimSolver.Kind;
import dev.vibe.combat.RotationMath;
import dev.vibe.module.Category;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.module.impl.world.AutoToolModule;
import dev.vibe.setting.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/** Native item use after the walking packet; all aiming uses MoveFix's shared rotation. */
public final class ProjectileAuraModule extends dev.vibe.module.Module {
    private static final List<String> TYPES = Arrays.asList("Snowball", "Eggs", "Rod");
    private static final double[] AIM_HEIGHTS = {.55, .85, .25};
    public final ModeSetting activation = addSetting(new ModeSetting("Activation Mode", "Always", "Always", "Only While Killaura", "Only While AIAura"));
    public final MultiSelectSetting modes = addSetting(new MultiSelectSetting("Modes", TYPES, TYPES));
    public final ItemSwitching switching = new ItemSwitching(this::addSetting, "Basic");
    public final RangeSetting distance = addSetting(new RangeSetting("Distance", 3, 10, 0, 24, .1));
    public final NumberSetting fov = addSetting(new NumberSetting("FOV", 180, 0, 180, 1));
    public final NumberSetting delay = addSetting(new NumberSetting("Delay (ms)", 500, 0, 3000, 10));
    public final NumberSetting rotationSpeed = addSetting(new NumberSetting("Rotation Speed", 45, 1, 180, 1, () -> activation.is("Always")));
    private EntityPlayerSP player;
    private World world;
    private EntityLivingBase target;
    private ProjectileAimSolver.Shot shot;
    private Kind kind;
    private int slot = -1, modeIndex, nextMode;
    private long nextThrow;
    private HotbarSwitches.Lease rodAction;
    private int rodSlot, rodAge, rodTimeout;
    private boolean rodSeen, retract;
    private boolean stocked;
    private KillAuraModule rotationAura, rodAura;
    private String rodMode;

    public ProjectileAuraModule() {
        super("Projectile Aura", "Predict and throw snowballs, eggs and fishing hooks using MoveFix", Category.COMBAT, Keyboard.KEY_NONE);
    }
    private <T extends dev.vibe.module.Module> T module(Class<T> type) {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(type);
    }
    private MoveFixModule fix() { return module(MoveFixModule.class); }

    /** Reserve before melee producers so only one action controls a launch tick. */
    public void prepare() {
        Minecraft mc = Minecraft.getMinecraft();
        clearPlan();
        if (player != mc.thePlayer || world != mc.theWorld) {
            releaseRod(false); nextThrow = 0; nextMode = 0;
            player = mc.thePlayer; world = mc.theWorld;
        }
        if (rodAction != null) {
            if (!rodAction.valid()) { releaseRod(false); return; }
            if (!available(mc) || !modes.isSelected("Rod") || !activation.getValue().equals(rodMode)
                    || rodAura != null && !linkedTo(rodAura)) { releaseRod(true); return; }
            rodAge++;
            if (player.fishEntity != null && !player.fishEntity.isDead) rodSeen = true;
            else if (rodSeen) { releaseRod(true); return; }
            retract = player.fishEntity != null && (player.fishEntity.caughtEntity != null
                    || player.fishEntity.isCollided || player.fishEntity.onGround) || rodAge >= rodTimeout;
            return;
        }
        stocked = false;
        if (!available(mc) || AutoToolModule.hasHotbarAction()) return;
        for (int type = 0; type < TYPES.size(); type++) if (chooseSlot(player.inventory.mainInventory, type) >= 0) stocked = true;
        if (!stocked || !activation.is("Always") || System.currentTimeMillis() < nextThrow) return;
        TargetsModule filters = module(TargetsModule.class);
        EntityLivingBase preferred = targetFromAura();
        List<EntityLivingBase> candidates = new ArrayList<>(3);
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase) || !valid((EntityLivingBase)entity, filters)) continue;
            EntityLivingBase living = (EntityLivingBase)entity;
            int index = 0;
            double score = living == preferred ? -1 : player.getDistanceSqToEntity(living);
            while (index < candidates.size() && (candidates.get(index) == preferred ? -1 : player.getDistanceSqToEntity(candidates.get(index))) <= score) index++;
            if (index < 3) { candidates.add(index, living); if (candidates.size() > 3) candidates.remove(3); }
        }
        Vec3 eyes = player.getPositionEyes(1);
        for (EntityLivingBase candidate : candidates) {
            if (plan(candidate, eyes)) return;
        }
        clearPlan();
    }
    private boolean plan(EntityLivingBase candidate, Vec3 eyes) {
        Vec3[] forecast = ArrowAimSolver.forecast(world, candidate, true, ProjectileAimSolver.HORIZON);
        AxisAlignedBB box = candidate.getEntityBoundingBox();
        for (int offset = 0; offset < TYPES.size(); offset++) {
            modeIndex = (nextMode + offset) % TYPES.size();
            slot = chooseSlot(player.inventory.mainInventory, modeIndex);
            if (slot < 0) continue;
            kind = modeIndex == 2 ? Kind.ROD : Kind.THROWABLE;
            // A few useful exposed body points, with the torso first for spread tolerance.
            for (double height : AIM_HEIGHTS) {
                Vec3 point = new Vec3((box.minX + box.maxX) * .5, box.minY + (box.maxY - box.minY) * height, (box.minZ + box.maxZ) * .5);
                ProjectileAimSolver.Shot solution = ProjectileAimSolver.solve(eyes, point, forecast, kind);
                if (solution != null && ProjectileAimSolver.hits(world, eyes, box, forecast, kind, solution.yaw, solution.pitch, solution.ticks)) {
                    target = candidate; shot = solution; return true;
                }
            }
        }
        return false;
    }
    private EntityLivingBase targetFromAura() {
        AIAuraModule ai = module(AIAuraModule.class);
        if (ai != null && ai.isEnabled() && ai.getTarget() != null) return ai.getTarget();
        KillAuraModule aura = module(KillAuraModule.class);
        return aura != null && aura.isEnabled() ? aura.getTarget() : null;
    }
    private boolean valid(EntityLivingBase entity, TargetsModule filters) {
        if (entity == null || entity == player || entity.worldObj != world || !entity.isEntityAlive() || entity.isDead) return false;
        double squared = player.getDistanceSqToEntity(entity);
        float yaw = (float)Math.toDegrees(Math.atan2(entity.posZ - player.posZ, entity.posX - player.posX)) - 90;
        return squared >= distance.getMin() * distance.getMin() && squared <= distance.getMax() * distance.getMax()
                && Math.abs(RotationMath.difference(yaw, player.rotationYaw)) <= fov.getFloat() + .0001F
                && (filters == null ? entity instanceof net.minecraft.entity.player.EntityPlayer : filters.canTarget(entity));
    }
    private boolean linkedTo(KillAuraModule aura) {
        return aura != null && aura.isEnabled() && (activation.is("Only While AIAura") ? aura == module(AIAuraModule.class)
                : activation.is("Only While Killaura") && !(aura instanceof AIAuraModule) && aura == module(KillAuraModule.class));
    }
    private boolean activationAvailable() {
        return activation.is("Always") || linkedTo(activation.is("Only While AIAura") ? module(AIAuraModule.class) : module(KillAuraModule.class));
    }
    private boolean available(Minecraft mc) {
        return isEnabled() && activationAvailable() && player != null && world != null && mc.thePlayer == player && mc.theWorld == world
                && player.worldObj == world && player.isEntityAlive() && !player.isSpectator() && mc.playerController != null
                && mc.currentScreen == null && mc.inGameHasFocus && !mc.isGamePaused() && !player.isUsingItem()
                && !mc.playerController.getIsHittingBlock() && !AutoBuffModule.ownsUseInput()&&!dev.vibe.module.impl.world.AutoWaterModule.reserving()
                && !dev.vibe.module.impl.meme.HypixelModule.ownsSilentSlot()
                && !dev.vibe.inventory.PlayerInventorySession.isBusy() && !AntiFireballModule.reserving() && !PearlCatcherModule.reserving() && !BowAimbotModule.drawing()
                && !dev.vibe.module.impl.world.ScaffoldModule.isActive() && !dev.vibe.module.impl.world.BedAuraModule.ownsMiningInput()
                && !dev.vibe.module.impl.movement.FreecamModule.cameraActiveHook();
    }
    public void apply() {
        MoveFixModule fix = fix();
        if (!activation.is("Always") || shot == null || fix == null || !available(Minecraft.getMinecraft()) || fix.hasOtherRotationOwner(getId())) return;
        float speed = rotationSpeed.getFloat(), yaw = fix.getRotationYaw(), pitch = fix.getRotationPitch();
        fix.setFakeRotation(getId(), yaw + RotationMath.clamp(RotationMath.difference(shot.yaw, yaw), -speed, speed),
                pitch + RotationMath.clamp(shot.pitch - pitch, -speed, speed));
    }

    /** C03 rotation has been sent before any native C09 / C08 item-use transaction. */
    public void afterWalking(Object entity, MoveFixModule fix) {
        if (entity != player || !available(Minecraft.getMinecraft())) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (rodAction != null) {
            String owner = rodAura == null ? getId() : rodAura.getId();
            if (!retract || fix.hasOtherRotationOwner(owner)) return;
            try {
                if (rodAction.active() && player.fishEntity != null) use(mc);
            } finally { releaseRod(rodAction.valid()); }
            return;
        }
        String owner = rotationAura == null ? getId() : rotationAura.getId();
        if (shot == null || !fix.ownsRotation(owner) || System.currentTimeMillis() < nextThrow
                || (rotationAura == null ? !activation.is("Always") : !linkedTo(rotationAura) || rotationAura.getTarget() != target)
                || !valid(target, module(TargetsModule.class)) || AutoToolModule.hasHotbarAction()) return;
        int selected = slot;
        if (type(player.inventory.mainInventory[selected]) != modeIndex || !modes.isSelected(TYPES.get(modeIndex))) return;
        // Reforecast from the position AFTER movement, not the tick-start eye position.
        Vec3[] forecast = ArrowAimSolver.forecast(world, target, true, ProjectileAimSolver.HORIZON);
        if (!ProjectileAimSolver.hits(world, player.getPositionEyes(1), target.getEntityBoundingBox(), forecast,
                kind, fix.getRotationYaw(), fix.getRotationPitch(), shot.ticks)) return;
        HotbarSwitches.Lease action = HotbarSwitches.acquire(this,switching,selected,HotbarSwitches.PROJECTILE);
        if (action == null) return;
        if (!action.active()) { action.close(false,true); return; }
        boolean holdRod = false;
        try {
            // sendUseItem returns false for rods despite performing the cast (the stack stays identical).
            use(mc);
            nextThrow = System.currentTimeMillis() + delay.getInt();
            nextMode = (modeIndex + 1) % TYPES.size();
            if (kind == Kind.ROD) {
                rodAction = action; rodSlot = selected; rodAge = 0;
                rodAura = rotationAura; rodMode = activation.getValue();
                rodTimeout = Math.min(ProjectileAimSolver.HORIZON, (int)Math.ceil(shot.ticks) + 6);
                rodSeen = false; retract = false; holdRod = true;
            }
        } finally {
            if (!holdRod) action.close();
            clearPlan();
        }
    }
    private void use(Minecraft mc) {
        mc.playerController.sendUseItem(player, world, HotbarSwitches.stack());
    }
    private int chooseSlot(ItemStack[] hotbar, int type) {
        if (!modes.isSelected(TYPES.get(type)) || type == 2 && player != null && player.fishEntity != null) return -1;
        for (int i = 0; i < Math.min(9, hotbar.length); i++) if (type(hotbar[i]) == type) return i;
        return -1;
    }
    private static int type(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return -1;
        return stack.getItem() == Items.snowball ? 0 : stack.getItem() == Items.egg ? 1
                : stack.getItem() == Items.fishing_rod && stack.getItemDamage() < stack.getMaxDamage() ? 2 : -1;
    }
    private void releaseRod(boolean restore) {
        HotbarSwitches.Lease action = rodAction;
        rodAction = null; rodAura = null; rodMode = null; retract = rodSeen = false;
        if (action != null) { if (restore) action.close(); else action.closePreservingSelection(); }
    }
    private void clearPlan() {
        target = null; shot = null; slot = -1; rotationAura = null;
        MoveFixModule fix = fix(); if (fix != null) fix.clearFakeRotation(getId());
    }
    @Override protected void onDisable() {
        releaseRod(rodAction != null && rodAction.valid());
        clearPlan(); player = null; world = null; nextThrow = 0;
    }
    public static boolean reserving() {
        ProjectileAuraModule module = active();
        return module != null && module.isEnabled() && (module.shot != null || module.rodAction != null);
    }
    private static ProjectileAuraModule active() {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(ProjectileAuraModule.class);
    }
    /** Only the matched aura supplies rotations in linked modes; other producers still yield. */
    public static boolean blocksRotations(KillAuraModule aura) {
        ProjectileAuraModule module = active();
        return reserving() && (module.rodAction != null ? module.rodAura : module.rotationAura) != aura;
    }
    /** Allow the aura to acquire ranged projectile opponents without changing its melee reach. */
    public static boolean extendsTargetRange(KillAuraModule aura, EntityLivingBase entity) {
        ProjectileAuraModule module = active();
        return module != null && module.stocked && module.linkedTo(aura) && module.available(Minecraft.getMinecraft())
                && module.valid(entity, module.module(TargetsModule.class));
    }
    /** Called after the aura selects its own target, before its normal rotation policy advances. */
    public static void prepareForAura(KillAuraModule aura, EntityLivingBase entity) {
        ProjectileAuraModule module = active();
        if (module == null || !module.linkedTo(aura) || !module.available(Minecraft.getMinecraft())
                || module.rodAction != null || AutoToolModule.hasHotbarAction() || System.currentTimeMillis() < module.nextThrow
                || !module.valid(entity, module.module(TargetsModule.class))) return;
        if (module.plan(entity, module.player.getPositionEyes(1))) module.rotationAura = aura;
    }
    /** A point along the solved launch direction, suitable for both aura rotation policies. */
    public static Vec3 rotationAimPoint(KillAuraModule aura, EntityLivingBase entity) {
        ProjectileAuraModule module = active();
        if (module == null || module.shot == null || module.rotationAura != aura || module.target != entity || !module.linkedTo(aura)) return null;
        Vec3 direction = ProjectileAimSolver.velocity(module.shot.yaw, module.shot.pitch);
        double scale = Math.max(1, module.player.getDistanceToEntity(entity)) / 1.5;
        return module.player.getPositionEyes(1).addVector(direction.xCoord * scale, direction.yCoord * scale, direction.zCoord * scale);
    }
}
