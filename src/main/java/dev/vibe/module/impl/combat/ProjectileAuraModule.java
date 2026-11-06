package dev.vibe.module.impl.combat;

import dev.vibe.Vibe;
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
    public final MultiSelectSetting modes = addSetting(new MultiSelectSetting("Modes", TYPES, TYPES));
    public final RangeSetting distance = addSetting(new RangeSetting("Distance", 3, 10, 0, 24, .1));
    public final NumberSetting delay = addSetting(new NumberSetting("Delay (ms)", 500, 0, 3000, 10));
    public final NumberSetting rotationSpeed = addSetting(new NumberSetting("Rotation Speed", 45, 1, 180, 1));
    private EntityPlayerSP player;
    private World world;
    private EntityLivingBase target;
    private ProjectileAimSolver.Shot shot;
    private Kind kind;
    private int slot = -1, modeIndex, nextMode;
    private long nextThrow;
    private AutoToolModule.HotbarAction rodAction;
    private int rodSlot, rodAge, rodTimeout;
    private boolean rodSeen, retract;

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
            if (player.inventory.currentItem != rodSlot) { releaseRod(false); return; }
            if (!available(mc) || !modes.isSelected("Rod")) { releaseRod(true); return; }
            rodAge++;
            if (player.fishEntity != null && !player.fishEntity.isDead) rodSeen = true;
            else if (rodSeen) { releaseRod(true); return; }
            retract = player.fishEntity != null && (player.fishEntity.caughtEntity != null
                    || player.fishEntity.isCollided || player.fishEntity.onGround) || rodAge >= rodTimeout;
            return;
        }
        if (!available(mc) || AutoToolModule.hasHotbarAction() || System.currentTimeMillis() < nextThrow) return;
        boolean stocked = false;
        for (int type = 0; type < TYPES.size(); type++) if (chooseSlot(player.inventory.mainInventory, type) >= 0) stocked = true;
        if (!stocked) return;
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
                        target = candidate; shot = solution; return;
                    }
                }
            }
        }
        clearPlan();
    }
    private EntityLivingBase targetFromAura() {
        AIAuraModule ai = module(AIAuraModule.class);
        if (ai != null && ai.isEnabled() && ai.getTarget() != null) return ai.getTarget();
        KillAuraModule aura = module(KillAuraModule.class);
        return aura != null && aura.isEnabled() ? aura.getTarget() : null;
    }
    private boolean valid(EntityLivingBase entity, TargetsModule filters) {
        if (entity == player || entity.worldObj != world || !entity.isEntityAlive() || entity.isDead) return false;
        double squared = player.getDistanceSqToEntity(entity);
        return squared >= distance.getMin() * distance.getMin() && squared <= distance.getMax() * distance.getMax()
                && (filters == null ? entity instanceof net.minecraft.entity.player.EntityPlayer : filters.canTarget(entity));
    }
    private boolean available(Minecraft mc) {
        return isEnabled() && player != null && world != null && mc.thePlayer == player && mc.theWorld == world
                && player.worldObj == world && player.isEntityAlive() && !player.isSpectator() && mc.playerController != null
                && mc.currentScreen == null && mc.inGameHasFocus && !mc.isGamePaused() && !player.isUsingItem()
                && !mc.playerController.getIsHittingBlock() && !AutoBuffModule.ownsUseInput()
                && !dev.vibe.module.impl.meme.HypixelModule.ownsSilentSlot()
                && !dev.vibe.inventory.PlayerInventorySession.isBusy() && !AntiFireballModule.reserving() && !BowAimbotModule.drawing()
                && !dev.vibe.module.impl.world.ScaffoldModule.isActive() && !dev.vibe.module.impl.world.BedAuraModule.ownsMiningInput()
                && !dev.vibe.module.impl.movement.FreecamModule.cameraActiveHook();
    }
    public void apply() {
        MoveFixModule fix = fix();
        if (shot == null || fix == null || !available(Minecraft.getMinecraft()) || fix.hasOtherRotationOwner(getId())) return;
        float speed = rotationSpeed.getFloat(), yaw = fix.getRotationYaw(), pitch = fix.getRotationPitch();
        fix.setFakeRotation(getId(), yaw + RotationMath.clamp(RotationMath.difference(shot.yaw, yaw), -speed, speed),
                pitch + RotationMath.clamp(shot.pitch - pitch, -speed, speed));
    }

    /** C03 rotation has been sent before any native C09 / C08 item-use transaction. */
    public void afterWalking(Object entity, MoveFixModule fix) {
        if (entity != player || !available(Minecraft.getMinecraft())) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (rodAction != null) {
            if (!retract || fix.hasOtherRotationOwner(getId())) return;
            try {
                if (player.inventory.currentItem == rodSlot && player.fishEntity != null) use(mc);
            } finally { releaseRod(player.inventory.currentItem == rodSlot); }
            return;
        }
        if (shot == null || !fix.ownsRotation(getId()) || System.currentTimeMillis() < nextThrow
                || !valid(target, module(TargetsModule.class)) || AutoToolModule.hasHotbarAction()) return;
        int selected = slot;
        if (type(player.inventory.mainInventory[selected]) != modeIndex || !modes.isSelected(TYPES.get(modeIndex))) return;
        // Reforecast from the position AFTER movement, not the tick-start eye position.
        Vec3[] forecast = ArrowAimSolver.forecast(world, target, true, ProjectileAimSolver.HORIZON);
        if (!ProjectileAimSolver.hits(world, player.getPositionEyes(1), target.getEntityBoundingBox(), forecast,
                kind, fix.getRotationYaw(), fix.getRotationPitch(), shot.ticks)) return;
        AutoToolModule.HotbarAction action = AutoToolModule.beginHotbarAction(selected);
        if (action == null) return;
        boolean holdRod = false;
        try {
            // sendUseItem returns false for rods despite performing the cast (the stack stays identical).
            use(mc);
            nextThrow = System.currentTimeMillis() + delay.getInt();
            nextMode = (modeIndex + 1) % TYPES.size();
            if (kind == Kind.ROD) {
                rodAction = action; rodSlot = selected; rodAge = 0;
                rodTimeout = Math.min(ProjectileAimSolver.HORIZON, (int)Math.ceil(shot.ticks) + 6);
                rodSeen = false; retract = false; holdRod = true;
            }
        } finally {
            if (!holdRod) action.close();
            clearPlan();
        }
    }
    private void use(Minecraft mc) {
        mc.playerController.sendUseItem(player, world, player.getHeldItem());
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
        AutoToolModule.HotbarAction action = rodAction;
        rodAction = null; retract = rodSeen = false;
        if (action != null) { if (restore) action.close(); else action.closePreservingSelection(); }
    }
    private void clearPlan() {
        target = null; shot = null; slot = -1;
        MoveFixModule fix = fix(); if (fix != null) fix.clearFakeRotation(getId());
    }
    @Override protected void onDisable() {
        releaseRod(player != null && player.inventory.currentItem == rodSlot);
        clearPlan(); player = null; world = null; nextThrow = 0;
    }
    public static boolean reserving() {
        Vibe vibe = Vibe.getInstance();
        ProjectileAuraModule module = vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(ProjectileAuraModule.class);
        return module != null && module.isEnabled() && (module.shot != null || module.rodAction != null);
    }
}
