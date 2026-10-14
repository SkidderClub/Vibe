package dev.vibe.path;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.visual.PathModule;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.Setting;
import dev.vibe.target.TargetManager;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PathModuleTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private PathTestFixture fixture;
    @Before public void setup() throws Exception { fixture = new PathTestFixture(); }
    @After public void cleanup() throws Exception { fixture.close(); }

    @Test public void isAnOptInVisualWithEditableAppearanceAndPerformanceLimits() {
        PathModule path = fixture.module;
        assertEquals("Path", path.getRawName()); assertEquals(Category.VISUAL, path.getCategory()); assertFalse(path.isEnabled());
        assertEquals("Nearest Player", path.getTargetMode().getValue());
        assertEquals(0xFF44E88C, path.getColor().getArgb()); assertTrue(path.getThroughWalls().isEnabled());
        assertTrue(path.getEndpoint().isEnabled()); assertFalse(path.getGradient().isEnabled());
        assertTrue(path.getDiagonals().isEnabled()); assertTrue(path.getReuse().isEnabled());
        assertEquals(8,path.getStability().getDouble(),0); assertEquals(750,path.getOptimizeInterval().getInt());
        assertTrue(path.getStability().isVisible()); assertTrue(path.getOptimizeInterval().isVisible());
        path.getReuse().setEnabled(false);
        assertFalse(path.getStability().isVisible()); assertFalse(path.getOptimizeInterval().isVisible());
        path.getReuse().setEnabled(true);
        assertFalse(path.getEndColor().isVisible()); path.getGradient().setEnabled(true); assertTrue(path.getEndColor().isVisible());
        assertFalse(path.getDashLength().isVisible()); path.getLineStyle().setValue("Dashed"); assertTrue(path.getDashLength().isVisible());
        number("Nodes per Tick").setValue(10000D); assertEquals(256, number("Nodes per Tick").getInt());
        number("Time Budget ms").setValue(10000D); assertEquals(4, number("Time Budget ms").getDouble(), 0);
    }

    @Test public void nearestSelectionExcludesSelfDeadInvisibleRemovedAndOutOfRangePlayers() throws Exception {
        PathTestFixture.Player nearest = fixture.addPlayer(2, "Nearest", 4.5, 64, .5);
        PathTestFixture.Player dead = fixture.addPlayer(3, "Dead", 1.5, 64, .5); dead.alive = false;
        PathTestFixture.Player invisible = fixture.addPlayer(4, "Hidden", 2.5, 64, .5); invisible.hidden = true;
        PathTestFixture.Player removed = fixture.addPlayer(5, "Removed", 1.5, 64, .5); fixture.world.entities.remove(5);
        fixture.addPlayer(6, "Far", 100.5, 64, .5);
        assertSame(nearest, fixture.module.selectTarget(fixture.mc));
        fixture.filters.getInvisibles().setEnabled(true);
        assertSame(invisible, fixture.module.selectTarget(fixture.mc));
    }

    @Test public void priorityAndCombatModesDoNotInventOrEnableCombatTargets() throws Exception {
        fixture.addPlayer(2, "Nearest", 2.5, 64, .5);
        PathTestFixture.Player priority = fixture.addPlayer(3, "Priority", 5.5, 64, .5);
        TargetManager roster = new TargetManager(temporary.getRoot()); roster.add("priority");
        PathTestFixture.set(Vibe.class, fixture.vibe, "targetManager", roster);
        fixture.module.getTargetMode().setValue("Priority Target");
        assertSame(priority, fixture.module.selectTarget(fixture.mc));
        fixture.module.getTargetMode().setValue("Combat Target");
        assertNull(fixture.module.selectTarget(fixture.mc));
        KillAuraModule aura = new KillAuraModule(); fixture.modules.registerDynamic(aura);
        PathTestFixture.set(KillAuraModule.class, aura, "target", priority);
        // Set only the enabled bit: don't run the combat module's enable-time hooks in this read-only fixture.
        PathTestFixture.set(dev.vibe.module.Module.class, aura, "enabled", true);
        assertSame(priority, fixture.module.selectTarget(fixture.mc));
        PathTestFixture.set(dev.vibe.module.Module.class, aura, "enabled", false);
        assertNull(fixture.module.selectTarget(fixture.mc));
    }

    @Test public void routeDetoursAroundATwoBlockWallWithoutChangingAnyPlayerState() throws Exception {
        PathTestFixture.Player target = fixture.addPlayer(2, "Target", 6.5, 64, .5);
        for (int z = -2; z <= 2; z++) for (int y = 64; y <= 66; y++) fixture.block(3, y, z, Blocks.stone.getDefaultState());
        double[] before = state(); fixture.module.setEnabled(true); fixture.finishRoute();
        PathRoute route = fixture.module.getRoute(); assertTrue(route.complete); assertSame(target, fixture.module.getTarget());
        assertTrue(route.points.stream().anyMatch(p -> Math.abs(p.zCoord - .5) >= 3));
        assertArrayEquals(before, state(), 0);
        assertSame(fixture.player, fixture.mc.getRenderViewEntity());
        int reads = fixture.world.reads;
        for (int i = 0; i < 100; i++) fixture.module.getRoute();
        assertEquals("Reading the cached route never pathfinds", reads, fixture.world.reads);
    }

    @Test public void noPathAndUnloadedChunksNeverBecomeAStraightLine() throws Exception {
        fixture.addPlayer(2, "Target", 6.5, 64, .5);
        fixture.world.blocks.clear(); fixture.module.setEnabled(true); fixture.module.tick();
        assertTrue(fixture.module.getRoute().points.isEmpty());
        fixture.floor(-12, 12, -12, 12, 63); fixture.world.loaded = false;
        fixture.forceReplan(); fixture.module.tick();
        assertTrue(fixture.module.getRoute().points.isEmpty());
    }

    @Test public void disablingDisconnectingAndTargetChangesDiscardTheOldRoute() throws Exception {
        PathTestFixture.Player target = fixture.addPlayer(2, "Target", 6.5, 64, .5);
        fixture.module.setEnabled(true); fixture.finishRoute(); assertFalse(fixture.module.getRoute().points.isEmpty());
        target.isDead = true; fixture.module.tick();
        assertNull(fixture.module.getTarget()); assertTrue(fixture.module.getRoute().points.isEmpty());
        target.isDead = false; fixture.forceReplan(); fixture.finishRoute();
        fixture.module.setEnabled(false); assertTrue(fixture.module.getRoute().points.isEmpty());
        fixture.module.setEnabled(true); fixture.finishRoute(); fixture.mc.theWorld = null; fixture.module.tick();
        assertNull(fixture.module.getTarget()); assertTrue(fixture.module.getRoute().points.isEmpty());
    }

    @Test public void routeRetainsStepCornersAndActualSlabHeightsWhileSimplifyingStraightRuns() {
        fixture.block(1, 64, 0, Blocks.stone_slab.getDefaultState());
        PathTerrain terrain = new PathTerrain(fixture.world, fixture.player);
        PathRoute route = PathRoute.build(new BlockPos(0,64,0), java.util.Arrays.asList(new BlockPos(1,64,0), new BlockPos(2,64,0)), terrain, true);
        assertEquals(4, route.points.size());
        assertEquals(64.5, route.points.get(1).yCoord, 0);
        assertEquals(.5, route.points.get(1).xCoord, 0);
        assertEquals(2.5, route.points.get(2).xCoord, 0);
        assertEquals(64.5, route.points.get(2).yCoord, 0);
        assertEquals(64, route.points.get(3).yCoord, 0);
        PathRoute straight = PathRoute.build(new BlockPos(3,64,0), java.util.Arrays.asList(new BlockPos(4,64,0), new BlockPos(5,64,0)), terrain, true);
        assertEquals(2, straight.points.size());
        try { straight.points.add(new Vec3(0,0,0)); fail("Route must be immutable"); } catch (UnsupportedOperationException expected) { }
        assertEquals(java.util.Arrays.asList(new BlockPos(3,64,0),new BlockPos(4,64,0),new BlockPos(5,64,0)),straight.nodes);
        try { straight.nodes.clear(); fail("Retained nodes must be immutable"); } catch (UnsupportedOperationException expected) { }
    }

    @Test public void diagonalRoutesUseActualCollisionShapesAndRespectTheToggle() throws Exception {
        fixture.addPlayer(2,"Target",4.5,64,4.5); fixture.module.setEnabled(true); fixture.finishRoute();
        assertTrue(fixture.module.getRoute().complete); assertEquals(5,fixture.module.getRoute().nodes.size());
        assertEquals(2,fixture.module.getRoute().points.size());
        fixture.module.getDiagonals().setEnabled(false); fixture.module.tick(); fixture.finishRoute();
        assertTrue(fixture.module.getRoute().complete); assertEquals(9,fixture.module.getRoute().nodes.size());
        fixture.module.getDiagonals().setEnabled(true);
        // One adjacent pillar is enough to stop the first diagonal, even though the destination is clear.
        for (int y=64;y<=66;y++) fixture.block(1,y,0,Blocks.stone.getDefaultState());
        fixture.module.tick(); fixture.finishRoute();
        PathRoute route = fixture.module.getRoute(); assertTrue(route.complete);
        assertNotEquals(new BlockPos(1,64,1),route.nodes.get(1));
        assertSafe(route);
    }

    @Test public void reusedRoutesTrimWalkedNodesExtendToMovingTargetsAndRepairNewBlocks() throws Exception {
        PathTestFixture.Player target = fixture.addPlayer(2,"Target",8.5,64,.5);
        fixture.module.setEnabled(true); fixture.finishRoute();
        PathRoute old = fixture.module.getRoute(); assertTrue(old.complete);
        fixture.player.posX = 2.5; target.posX = 9.5;
        PathRoute moved = replan(old); assertTrue(moved.complete);
        assertEquals(new BlockPos(2,64,0),moved.nodes.get(0)); assertEquals(new BlockPos(9,64,0),moved.end);
        assertFalse(moved.nodes.contains(new BlockPos(1,64,0))); assertSafe(moved);
        for (int y=64;y<=66;y++) fixture.block(5,y,0,Blocks.stone.getDefaultState());
        PathRoute repaired = replan(moved); assertTrue(repaired.complete);
        assertFalse(repaired.nodes.contains(new BlockPos(5,64,0)));
        assertTrue(repaired.nodes.contains(new BlockPos(3,64,0))); assertTrue(repaired.nodes.contains(new BlockPos(8,64,0)));
        assertSafe(repaired);
        double[] state = state(); fixture.module.getReuse().setEnabled(false); fixture.module.tick(); fixture.finishRoute();
        assertTrue(fixture.module.getRoute().complete); assertArrayEquals(state,state(),0);
    }

    @Test public void diagonalJumpsCanUseLowerSideFloorsButNeverSkipBodyCollisionChecks() {
        fixture.block(1,64,1,Blocks.stone.getDefaultState());
        BlockPos start = new BlockPos(0,64,0), high = new BlockPos(1,65,1);
        assertTrue(dev.vibe.movement.WalkPathfinder.canStep(new PathTerrain(fixture.world,fixture.player),start,high,true,3,true));
        fixture.block(1,66,0,Blocks.stone.getDefaultState());
        assertFalse(dev.vibe.movement.WalkPathfinder.canStep(new PathTerrain(fixture.world,fixture.player),start,high,true,3,true));
    }

    @Test public void unchangedRouteReuseReducesFreshBlockQueriesComparedWithFullReplanning() throws Exception {
        fixture.addPlayer(2,"Target",8.5,64,.5); fixture.module.setEnabled(true); fixture.finishRoute();
        int before = fixture.world.reads;
        PathRoute reused = replan(fixture.module.getRoute()); assertTrue(reused.complete);
        int reuseReads = fixture.world.reads-before;
        fixture.module.getReuse().setEnabled(false); before = fixture.world.reads;
        fixture.module.tick(); fixture.finishRoute(); assertTrue(fixture.module.getRoute().complete);
        int freshReads = fixture.world.reads-before;
        assertTrue("Old-route validation should read fewer blocks than a new search: "+reuseReads+" vs "+freshReads,
                reuseReads < freshReads*.7);
    }

    @Test public void openedShortcutsAreAdoptedPeriodicallyInsteadOfKeepingAnOldDetourForever() throws Exception {
        fixture.addPlayer(2,"Target",6.5,64,.5);
        for (int z=-2;z<=2;z++) for (int y=64;y<=66;y++) fixture.block(3,y,z,Blocks.stone.getDefaultState());
        fixture.module.setEnabled(true); fixture.finishRoute();
        PathRoute old = fixture.module.getRoute(); assertTrue(old.complete);
        for (int y=64;y<=66;y++) fixture.world.blocks.remove(new BlockPos(3,y,0));
        // Simulate a refresh inside the optimization cooldown without any sleeps.
        PathTestFixture.set(PathModule.class,fixture.module,"nextOptimize",Long.MAX_VALUE);
        PathRoute stable = replan(old); assertTrue(stable.complete); assertEquals(old.nodes,stable.nodes);
        PathTestFixture.set(PathModule.class,fixture.module,"nextOptimize",0L);
        double[] state = state(); PathRoute improved = replan(stable);
        assertTrue(improved.complete); assertEquals(6,PathPlanner.routeCost(improved.nodes),1e-9);
        assertTrue(PathPlanner.routeCost(improved.nodes)<PathPlanner.routeCost(old.nodes)-1);
        assertSafe(improved); assertArrayEquals(state,state(),0);
        for (int i=0;i<5;i++) {
            PathTestFixture.set(PathModule.class,fixture.module,"nextOptimize",0L);
            PathRoute next = replan(improved); assertEquals(improved.nodes,next.nodes); improved=next;
        }
    }

    private PathRoute replan(PathRoute previous) throws Exception {
        fixture.forceReplan();
        for (int i=0;i<200 && fixture.module.getRoute()==previous;i++) fixture.module.tick();
        assertNotSame("A refreshed route must be published",previous,fixture.module.getRoute());
        return fixture.module.getRoute();
    }

    private void assertSafe(PathRoute route) {
        PathTerrain fresh = new PathTerrain(fixture.world,fixture.player);
        for (int i=1;i<route.nodes.size();i++)
            assertTrue(dev.vibe.movement.WalkPathfinder.canStep(fresh,route.nodes.get(i-1),route.nodes.get(i),true,3,true));
    }

    @Test public void geometryCacheAlsoCoversRoutePublishingAndNeverQueriesNeighbouringUnloadedChunks() {
        PathTerrain terrain = new PathTerrain(fixture.world, fixture.player);
        BlockPos start = new BlockPos(0,64,0), goal = new BlockPos(6,64,0);
        dev.vibe.movement.WalkPathfinder.Search search = new dev.vibe.movement.WalkPathfinder.Search(terrain,start,goal,16,256,8,true,3);
        search.advance(256,Long.MAX_VALUE); assertTrue(search.reachedGoal());
        int reads = fixture.world.reads;
        PathRoute.build(start,search.path(),terrain,true);
        for (int i=0;i<100;i++) { assertTrue(terrain.canStand(goal)); assertEquals(64,terrain.standingY(goal),0); }
        assertEquals("No repeated block queries when publishing the cached heights",reads,fixture.world.reads);
        fixture.block(31,63,0,Blocks.glass_pane.getDefaultState());
        assertFalse("Neighbour-dependent shapes at unloaded borders are closed",terrain.canStand(new BlockPos(31,64,0)));
    }

    @Test public void unreachableTargetsPublishOnlyPartialProgressAndReplanWhenTheWallChanges() throws Exception {
        fixture.addPlayer(2,"Target",6.5,64,.5);
        for (int z=-12;z<=12;z++) for (int y=64;y<=66;y++) fixture.block(3,y,z,Blocks.stone.getDefaultState());
        number("Search Budget").setValue(256D); fixture.module.setEnabled(true); fixture.finishRoute();
        PathRoute partial = fixture.module.getRoute();
        assertFalse(partial.points.isEmpty()); assertFalse(partial.complete);
        assertTrue(partial.points.stream().allMatch(p -> p.xCoord < 3));
        // Opening a door-sized gap must use fresh geometry on the next search, not the old cache.
        for (int y=64;y<=66;y++) fixture.world.blocks.remove(new BlockPos(3,y,0));
        fixture.forceReplan();
        for (int i=0;i<200 && !fixture.module.getRoute().complete;i++) fixture.module.tick();
        assertTrue(fixture.module.getRoute().complete);
    }

    @Test public void sharedFriendsPolicyIsRespectedByNearestPlayerMode() throws Exception {
        PathTestFixture.Player friend = fixture.addPlayer(2,"Friend",2.5,64,.5);
        PathTestFixture.Player other = fixture.addPlayer(3,"Other",4.5,64,.5);
        dev.vibe.friend.FriendManager friends = new dev.vibe.friend.FriendManager(temporary.getRoot());
        friends.add(friend.getName(),friend.getName()); PathTestFixture.set(Vibe.class,fixture.vibe,"friendManager",friends);
        fixture.modules.registerDynamic(new dev.vibe.module.impl.client.FriendsModule());
        assertSame(other,fixture.module.selectTarget(fixture.mc));
    }

    private NumberSetting number(String name) {
        for (Setting<?> setting : fixture.module.getSettings()) if (setting.getRawName().equals(name)) return (NumberSetting) setting;
        throw new AssertionError(name);
    }
    private double[] state() {
        Entity player = fixture.player;
        return new double[] {player.posX,player.posY,player.posZ,player.motionX,player.motionY,player.motionZ,
                player.rotationYaw,player.rotationPitch,player.onGround?1:0,player.noClip?1:0,fixture.mc.gameSettings.thirdPersonView};
    }
}
