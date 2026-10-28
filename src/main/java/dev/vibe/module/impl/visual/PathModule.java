package dev.vibe.module.impl.visual;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.AimAssistModule;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.combat.TargetsModule;
import dev.vibe.module.impl.meme.HypixelModule;
import dev.vibe.path.PathPlanner;
import dev.vibe.path.PathRoute;
import dev.vibe.path.PathTerrain;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.MultiSelectSetting;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/** Read-only walking route to an entity. Never controls movement, attacks, rotations or packets. */
public final class PathModule extends Module {
    private final ModeSetting targetMode = addSetting(new ModeSetting("Target", "Nearest Player",
            "Nearest Player", "Nearest Target", "Combat Target", "Priority Target"));
    public static final String TO_MURDERER = "To murderer", TO_BOW_HOLDERS = "To bow holders as murderer";
    private final MultiSelectSetting murderLines = addSetting(new MultiSelectSetting("Murder Mystery lines",
            Arrays.asList(TO_MURDERER, TO_BOW_HOLDERS), Arrays.asList(TO_MURDERER, TO_BOW_HOLDERS)));
    private final NumberSetting range = addSetting(new NumberSetting("Max Range", 64, 8, 128, 4));
    private final ColorSetting color = addSetting(new ColorSetting("Color", 0xFF44E88C));
    private final BooleanSetting gradient = addSetting(new BooleanSetting("Gradient", false));
    private final ColorSetting endColor = addSetting(new ColorSetting("End Color", 0xFF42DDF5, gradient::isEnabled));
    private final NumberSetting lineWidth = addSetting(new NumberSetting("Line Width", 1.5, 1, 6, .1));
    private final ModeSetting lineStyle = addSetting(new ModeSetting("Line Style", "Solid", "Solid", "Dashed"));
    private final NumberSetting dashLength = addSetting(new NumberSetting("Dash Length", .35, .1, 1, .05,
            () -> lineStyle.is("Dashed")));
    private final NumberSetting heightOffset = addSetting(new NumberSetting("Height Offset", .08, .03, 1, .01));
    private final BooleanSetting throughWalls = addSetting(new BooleanSetting("Through Walls", true));
    private final BooleanSetting endpoint = addSetting(new BooleanSetting("Endpoint", true));
    private final ModeSetting endpointStyle = addSetting(new ModeSetting("Endpoint Style", "Square", endpoint::isEnabled,
            "Square", "Box"));
    private final NumberSetting endpointSize = addSetting(new NumberSetting("Endpoint Size", .25, .1, .8, .05, endpoint::isEnabled));
    private final BooleanSetting partial = addSetting(new BooleanSetting("Show Partial Path", true));
    private final ColorSetting partialColor = addSetting(new ColorSetting("Partial Color", 0xFFFFC65C, partial::isEnabled));
    private final BooleanSetting jumps = addSetting(new BooleanSetting("Allow Jumps", true));
    private final BooleanSetting diagonals = addSetting(new BooleanSetting("Diagonal", true));
    private final BooleanSetting reuse = addSetting(new BooleanSetting("Reuse Path", true));
    private final NumberSetting stability = addSetting(new NumberSetting("Path Stability", 8, 0, 25, 1, reuse::isEnabled));
    private final NumberSetting optimizeInterval = addSetting(new NumberSetting("Optimize Interval", 750, 250, 3000, 250, reuse::isEnabled));
    private final NumberSetting drop = addSetting(new NumberSetting("Max Drop", 3, 0, 3, 1));
    private final NumberSetting updateInterval = addSetting(new NumberSetting("Update Interval", 250, 100, 1000, 50));
    private final NumberSetting searchBudget = addSetting(new NumberSetting("Search Budget", 2048, 256, 8192, 256));
    private final NumberSetting nodesPerTick = addSetting(new NumberSetting("Nodes per Tick", 64, 16, 256, 16));
    private final NumberSetting timeBudget = addSetting(new NumberSetting("Time Budget ms", 1.5, .25, 4, .25));
    private World world;
    private EntityLivingBase target;
    private EntityItem goldTarget;
    private final RouteState mainPath = new RouteState(), goldPath = new RouteState();
    private long nextSelection;
    private int searchOptions;
    private boolean walkingRequested,walkingJumps=true,walkingLine;

    public PathModule() {
        super("Path", "Draw a walking route to a nearby player or selected target", Category.VISUAL, Keyboard.KEY_NONE);
        color.enableEntityOptions(setting -> addSetting(setting));
        endColor.enableEntityOptions(setting -> addSetting(setting));
    }

    @Override protected void onDisable() { clear(); }

    public void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if ((!isEnabled() && !walkingRequested) || mc == null || mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.isDead) {
            clear(); return;
        }
        if (world != mc.theWorld) { clear(); world = mc.theWorld; }
        if (mc.isGamePaused()) return;
        long now = System.nanoTime();
        int options = options();
        if (options != searchOptions) {
            mainPath.clear(); goldPath.clear(); nextSelection = 0; searchOptions = options;
        }
        if (now >= nextSelection || targetMode.is("Combat Target") || target != null && !eligible(target, mc)
                || goldTarget != null && !eligibleGold(goldTarget, mc)) {
            target = selectTarget(mc);
            goldTarget = selectGoldTarget(mc);
            nextSelection = now + 200_000_000L;
        }
        int routes = target != null && goldTarget != null ? 2 : 1;
        int nodes = Math.max(1, nodesPerTick.getInt() / routes);
        long nanos = (long) (timeBudget.getDouble() * 1_000_000 / routes);
        mainPath.tick(mc, target, now, nodes, nanos);
        goldPath.tick(mc, goldTarget, now, nodes, nanos);
    }

    /** Both routes share the same collision checks, replan rules and total tick budget. */
    private final class RouteState {
        private Entity destination;
        private PathRoute route = PathRoute.EMPTY;
        private PathPlanner search;
        private PathTerrain terrain;
        private BlockPos searchStart, searchGoal;
        private long nextSearch, nextOptimize, searchSince, routeSince;

        void tick(Minecraft mc, Entity chosen, long now, int nodes, long nanos) {
            if (destination != chosen) { clear(); destination = chosen; }
            if (destination == null) return;
            BlockPos feet = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + .05, mc.thePlayer.posZ);
            BlockPos goal = new BlockPos(destination.posX, destination.posY + .05, destination.posZ);
            if (search != null && (searchStart.distanceSq(feet) > 16 || searchGoal.distanceSq(goal) > 9)) {
                cancelSearch(); nextSearch = 0;
            }
            if (routeSince != 0 && now - routeSince > 1_500_000_000L) route = PathRoute.EMPTY;
            if (search == null && now >= nextSearch) {
                terrain = new PathTerrain(mc.theWorld, mc.thePlayer);
                searchStart = terrain.grounded(feet); searchGoal = terrain.grounded(goal);
                nextSearch = now + updateInterval.getInt() * 1_000_000L;
                if (searchStart == null || searchGoal == null) {
                    // Neither void nor unloaded terrain is connected by a fictitious straight tracer.
                    route = PathRoute.EMPTY; cancelSearch(); return;
                }
                boolean optimize = now >= nextOptimize;
                if (optimize && !route.nodes.isEmpty()) nextOptimize = now + optimizeInterval.getInt()*1_000_000L;
                search = new PathPlanner(terrain, searchStart, searchGoal, range.getInt(), searchBudget.getInt(),
                        24, allowJumps(), drop.getInt(), diagonals.isEnabled(), reuse.isEnabled() ? route.nodes : null,
                        optimize, stability.getDouble()/100);
                searchSince = now;
            }
            if (search == null) return;
            long deadline = System.nanoTime() + nanos;
            search.advance(nodes, deadline);
            if (search.isDone() || now - searchSince >= 1_000_000_000L) {
                List<BlockPos> steps = search.path();
                route = !steps.isEmpty() || search.reachedGoal()
                        ? PathRoute.build(searchStart, steps, terrain, search.reachedGoal()) : PathRoute.EMPTY;
                routeSince = now;
                cancelSearch(); nextSearch = now + updateInterval.getInt() * 1_000_000L;
            }
        }

        private void cancelSearch() { search = null; terrain = null; searchStart = searchGoal = null; searchSince = 0; }
        private void clear() {
            cancelSearch(); destination = null; route = PathRoute.EMPTY;
            nextSearch = nextOptimize = routeSince = 0;
        }
    }

    /** Stable nearest selection, sharing Friends/Teams/Invisibles/type filtering with combat. */
    public EntityLivingBase selectTarget(Minecraft mc) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || Vibe.getInstance() == null) return null;
        boolean murderTargets = murderTargets();
        if (!murderTargets && targetMode.is("Combat Target")) {
            KillAuraModule aura = Vibe.getInstance().getModuleManager().getModule(KillAuraModule.class);
            EntityLivingBase candidate = aura != null && aura.isEnabled() ? aura.getTarget() : null;
            if (eligible(candidate, mc)) return candidate;
            AimAssistModule aim = Vibe.getInstance().getModuleManager().getModule(AimAssistModule.class);
            candidate = aim != null && aim.isEnabled() ? aim.getTarget() : null;
            return eligible(candidate, mc) ? candidate : null;
        }
        EntityLivingBase nearest = eligible(target, mc) ? target : null;
        double best = nearest == null ? range.getDouble() * range.getDouble() : mc.thePlayer.getDistanceSqToEntity(nearest);
        Iterable<?> candidates = !murderTargets && targetMode.is("Nearest Target") ? mc.theWorld.loadedEntityList : mc.theWorld.playerEntities;
        for (Object object : candidates) {
            if (!(object instanceof EntityLivingBase)) continue;
            EntityLivingBase entity = (EntityLivingBase) object;
            if (!eligible(entity, mc)) continue;
            double distance = mc.thePlayer.getDistanceSqToEntity(entity);
            // Small hysteresis prevents similarly distant players flickering the route every update.
            if (nearest == null || distance < best * (nearest == target ? .85 : 1)) {
                nearest = entity; best = distance;
            }
        }
        return nearest;
    }

    private boolean eligible(EntityLivingBase entity, Minecraft mc) {
        if (entity == null || entity == mc.thePlayer || entity.worldObj != mc.theWorld || entity.isDead
                || !entity.isEntityAlive() || mc.theWorld.getEntityByID(entity.getEntityId()) != entity
                || mc.thePlayer.getDistanceSqToEntity(entity) > range.getDouble() * range.getDouble()) return false;
        if (!targetMode.is("Nearest Target") && !targetMode.is("Combat Target") && !(entity instanceof EntityPlayer)) return false;
        boolean murderTargets = murderTargets();
        if (murderTargets) {
            HypixelModule hypixel = Vibe.getInstance().getModuleManager().getModule(HypixelModule.class);
            if (hypixel == null || !hypixel.isMurderMysteryPathTarget(entity, murderLines.isSelected(TO_MURDERER),
                    murderLines.isSelected(TO_BOW_HOLDERS))) return false;
        }
        TargetsModule targets = Vibe.getInstance().getModuleManager().getModule(TargetsModule.class);
        if (targets != null ? !targets.canTarget(entity) : entity.isInvisible()) return false;
        return murderTargets || !targetMode.is("Priority Target") || entity instanceof EntityPlayer && Vibe.getInstance().getTargetManager() != null
                && Vibe.getInstance().getTargetManager().isTarget((EntityPlayer) entity);
    }

    /** Gold is a separate optional route; it does not consume the murderer/bow-holder route. */
    private EntityItem selectGoldTarget(Minecraft mc) {
        HypixelModule hypixel = hypixel();
        if (hypixel == null || !hypixel.isMurderMysteryActive() || !hypixel.getGoldPath().isEnabled()) return null;
        EntityItem nearest = null;
        double best = range.getDouble() * range.getDouble();
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityItem) || !eligibleGold((EntityItem) object, mc)) continue;
            EntityItem item = (EntityItem) object;
            double distance = mc.thePlayer.getDistanceSqToEntity(item);
            if (nearest == null || distance < best) { nearest = item; best = distance; }
        }
        return nearest;
    }

    private boolean eligibleGold(EntityItem item, Minecraft mc) {
        HypixelModule hypixel = hypixel();
        return hypixel != null && hypixel.isGoldPathTarget(item) && item.worldObj == mc.theWorld
                && mc.theWorld.getEntityByID(item.getEntityId()) == item
                && mc.thePlayer.getDistanceSqToEntity(item) <= range.getDouble() * range.getDouble();
    }

    private int options() {
        int hash = targetMode.getValue().hashCode();
        hash = hash * 31 + murderLines.getValue().hashCode();
        hash = hash * 31 + (murderTargets() ? 1 : 0);
        HypixelModule hypixel = hypixel();
        hash = hash * 31 + (hypixel != null && hypixel.isMurderMysteryActive() && hypixel.getGoldPath().isEnabled() ? 1 : 0);
        hash = hash * 31 + range.getInt(); hash = hash * 31 + (allowJumps() ? 1 : 0);
        hash = hash * 31 + drop.getInt(); hash = hash * 31 + searchBudget.getInt();
        hash = hash * 31 + (diagonals.isEnabled() ? 1 : 0); hash = hash * 31 + (reuse.isEnabled() ? 1 : 0);
        hash = hash * 31 + stability.getInt();
        return hash;
    }

    private boolean murderTargets() {
        HypixelModule hypixel = hypixel();
        return hypixel != null && hypixel.usesMurderMysteryPathTargets(murderLines.isSelected(TO_MURDERER),
                murderLines.isSelected(TO_BOW_HOLDERS));
    }

    private HypixelModule hypixel() {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(HypixelModule.class);
    }

    private void clear() {
        mainPath.clear(); goldPath.clear(); world = null; target = null; goldTarget = null;
        nextSelection = 0;
    }

    public PathRoute getRoute() { return mainPath.route; }
    /** Request a refresh without restarting an in-progress, bounded search. */
    public void refreshWalkingRoute() { if(walkingRequested && mainPath.search==null)mainPath.nextSearch=0; }
    public void requestWalking(boolean active,boolean jumpAllowed,boolean line) { walkingRequested=active;walkingJumps=jumpAllowed;walkingLine=line; }
    public boolean shouldRender() { return isEnabled() || walkingRequested && walkingLine; }
    private boolean allowJumps() { return walkingRequested ? walkingJumps : jumps.isEnabled(); }
    public PathRoute getGoldRoute() { return goldPath.route; }
    public EntityItem getGoldTarget() { return goldTarget; }
    public EntityLivingBase getTarget() { return target; }
    public ModeSetting getTargetMode() { return targetMode; }
    public MultiSelectSetting getMurderLines() { return murderLines; }
    public NumberSetting getRange() { return range; }
    public NumberSetting getLineWidth() { return lineWidth; }
    public NumberSetting getHeightOffset() { return heightOffset; }
    public NumberSetting getDashLength() { return dashLength; }
    public ModeSetting getLineStyle() { return lineStyle; }
    public BooleanSetting getThroughWalls() { return throughWalls; }
    public BooleanSetting getEndpoint() { return endpoint; }
    public ModeSetting getEndpointStyle() { return endpointStyle; }
    public NumberSetting getEndpointSize() { return endpointSize; }
    public BooleanSetting getPartial() { return partial; }
    public ColorSetting getColor() { return color; }
    public ColorSetting getEndColor() { return endColor; }
    public BooleanSetting getGradient() { return gradient; }
    public BooleanSetting getDiagonals() { return diagonals; }
    public BooleanSetting getReuse() { return reuse; }
    public NumberSetting getStability() { return stability; }
    public NumberSetting getOptimizeInterval() { return optimizeInterval; }
    public ColorSetting getPartialColor() { return partialColor; }
}
