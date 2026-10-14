package dev.vibe.path;

import dev.vibe.movement.WalkPathfinder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class PathPlannerTest {
    private final Ground terrain = new Ground();

    @Test public void unchangedRouteAndWalkedPrefixAreReusedWithoutAnyAStarExpansions() {
        List<BlockPos> old = straight(0,8);
        PathPlanner planner = planner(p(2,0),p(6,0),old);
        planner.advance(10,0); assertEquals(0,planner.getWork()); assertEquals(0,terrain.reads);
        finish(planner,2);
        assertTrue(planner.reachedGoal()); assertEquals(straight(3,6),planner.path());
        assertEquals(0,planner.getExpanded()); assertEquals(4,planner.getReusedNodes());
        assertEquals(5,planner.getWork()); assertEquals(straight(0,8),old);
    }

    @Test public void movingBothEndsSearchesOnlyConnectorsAndRetainsTheMiddle() {
        PathPlanner planner = planner(p(0,-1),p(9,0),straight(0,8));
        finish(planner,3);
        assertTrue(planner.reachedGoal()); assertTrue(planner.path().containsAll(straight(0,8)));
        assertEquals(p(9,0),last(planner)); assertEquals(8,planner.getReusedNodes());
        assertTrue("Only the short connectors need queue expansions",planner.getExpanded()<=4);
        assertSafe(planner,p(0,-1));
    }

    @Test public void changedBlocksRepairTheMiddleAndKeepBothValidEnds() {
        terrain.blocked.add(p(4,0));
        PathPlanner planner = planner(p(0,0),p(8,0),straight(0,8));
        finish(planner,3);
        assertTrue(planner.reachedGoal()); assertFalse(planner.path().contains(p(4,0)));
        assertTrue(planner.path().containsAll(Arrays.asList(p(1,0),p(2,0),p(3,0),p(5,0),p(6,0),p(7,0),p(8,0))));
        assertEquals(6,planner.getReusedNodes()); assertSafe(planner,p(0,0));
    }

    @Test public void unchangedNodesWithANewlyBlockedSweepAreNotBlindlyReused() {
        terrain.closedFrom = p(3,0); terrain.closedTo = p(4,0);
        PathPlanner planner = planner(p(0,0),p(8,0),straight(0,8));
        finish(planner,4); assertTrue(planner.reachedGoal());
        assertTrue(planner.path().stream().anyMatch(node -> node.getZ()!=0));
        assertTrue(planner.getReusedNodes()>0); assertSafe(planner,p(0,0));
    }

    @Test public void invalidStartAnchorIsDroppedButTheValidSuffixCanStillBeReused() {
        terrain.blocked.add(p(0,0));
        PathPlanner planner = planner(p(0,1),p(8,0),straight(0,8));
        finish(planner,5); assertTrue(planner.reachedGoal()); assertFalse(planner.path().contains(p(0,0)));
        assertEquals(7,planner.getReusedNodes()); assertSafe(planner,p(0,1));
    }

    @Test public void unreachableOldAnchorFallsBackToANewRouteWithinTheSharedBudget() {
        for (int x=-12;x<=12;x++) terrain.blocked.add(p(x,-1));
        for (int budget : new int[]{256,2048}) {
            PathPlanner planner = new PathPlanner(terrain,p(0,-2),p(8,-2),12,budget,8,true,3,true,straight(0,8));
            finish(planner,16); assertTrue(planner.reachedGoal()); assertEquals(0,planner.getReusedNodes());
            assertEquals(straightAt(1,8,-2),planner.path()); assertTrue(planner.getWork()<=budget);
            assertSafe(planner,p(0,-2));
        }
    }

    @Test public void retainedRouteCanBeTraversedBackwardsButIsRevalidatedInThatDirection() {
        PathPlanner planner = planner(p(7,0),p(2,0),straight(0,8));
        finish(planner,3); assertTrue(planner.reachedGoal()); assertEquals(0,planner.getExpanded());
        assertEquals(Arrays.asList(p(6,0),p(5,0),p(4,0),p(3,0),p(2,0)),planner.path());
        terrain.closedFrom = p(4,0); terrain.closedTo = p(3,0);
        planner = planner(p(7,0),p(2,0),straight(0,8));
        finish(planner,3); assertTrue(planner.reachedGoal());
        assertTrue(planner.path().stream().anyMatch(node -> node.getZ()!=0)); assertSafe(planner,p(7,0));
    }

    @Test public void changedDiagonalSettingRejectsOldDiagonalEdges() {
        List<BlockPos> old = Arrays.asList(p(0,0),p(1,1),p(2,2),p(3,3));
        PathPlanner planner = new PathPlanner(terrain,p(0,0),p(3,3),12,256,8,true,3,false,old);
        finish(planner,2); assertTrue(planner.reachedGoal()); assertEquals(6,planner.path().size());
        assertEquals(0,planner.getReusedNodes());
        BlockPos previous = p(0,0);
        for (BlockPos node : planner.path()) {
            assertTrue(previous.getX()==node.getX() || previous.getZ()==node.getZ()); previous=node;
        }
    }

    @Test public void allWorkIncludingValidationHonorsTotalAndTickBudgets() {
        PathPlanner planner = new PathPlanner(terrain,p(0,0),p(100,0),128,16,8,true,3,true,straight(0,100));
        finish(planner,3); assertEquals(16,planner.getWork()); assertFalse(planner.reachedGoal());
        assertEquals(0,planner.getReusedNodes()); assertFalse(planner.path().isEmpty());
        int reads = terrain.reads; planner.advance(100,Long.MAX_VALUE); assertEquals(reads,terrain.reads);
    }

    @Test public void emptyAndSingleNodeRoutesAreSafeIncludingSameCellGoals() {
        PathPlanner planner = planner(p(0,0),p(0,0),Arrays.asList(p(0,0)));
        finish(planner,1); assertTrue(planner.reachedGoal()); assertTrue(planner.path().isEmpty());
        assertEquals(0,planner.getExpanded());
        planner = planner(p(0,0),p(3,3),null); finish(planner,2);
        assertTrue(planner.reachedGoal()); assertEquals(3,planner.path().size()); assertEquals(0,planner.getReusedNodes());
    }

    @Test public void worthwhileShortcutReplacesAValidButWastefulOldDetour() {
        List<BlockPos> old = detour(4,8);
        PathPlanner planner = balanced(p(0,0),p(8,0),old,true,.08);
        finish(planner,3); assertTrue(planner.reachedGoal());
        assertEquals(straight(1,8),planner.path()); assertEquals(0,planner.getReusedNodes());
        assertTrue(planner.getExpanded()>0); assertSafe(planner,p(0,0));
    }

    @Test public void marginalSavingsKeepExactlyTheSameRouteAcrossRepeatedOptimizationChecks() {
        List<BlockPos> old = gentleDetour(12);
        for (int i=0;i<12;i++) {
            PathPlanner planner = balanced(p(0,0),p(12,0),old,true,.08);
            finish(planner,2); assertTrue(planner.reachedGoal());
            assertEquals(old.subList(1,old.size()),planner.path());
            assertEquals("Lower-bound test avoids even searching for insignificant savings",0,planner.getExpanded());
        }
    }

    @Test public void zeroStabilityOptimizesSmallSavingsAndHigherStabilityRetainsThem() {
        PathPlanner planner = balanced(p(0,0),p(8,0),gentleDetour(),true,0);
        finish(planner,3); assertEquals(straight(1,8),planner.path());
        List<BlockPos> old = detour(1,8);
        planner = balanced(p(0,0),p(8,0),old,true,.25);
        finish(planner,3); assertEquals(old.subList(1,old.size()),planner.path());
        planner = balanced(p(0,0),p(8,0),old,true,.08);
        finish(planner,3); assertEquals(straight(1,8),planner.path());
    }

    @Test public void equallyGoodWallDetoursDoNotKeepSwitchingSides() {
        for (int z=-2;z<=2;z++) terrain.blocked.add(p(4,z));
        WalkPathfinder.Search fresh = new WalkPathfinder.Search(terrain,p(0,0),p(8,0),12,2048,8,true,3,true);
        fresh.advance(2048,Long.MAX_VALUE); assertTrue(fresh.reachedGoal());
        List<BlockPos> old = new ArrayList<>(); old.add(p(0,0)); old.addAll(fresh.path());
        for (int i=0;i<6;i++) {
            PathPlanner planner = balanced(p(0,0),p(8,0),old,true,.08);
            finish(planner,3); assertTrue(planner.reachedGoal());
            assertEquals(fresh.path(),planner.path()); assertTrue(planner.getReusedNodes()>0);
            assertSafe(planner,p(0,0));
        }
    }

    @Test public void optimizationNeverExposesAPartialCandidateOrPerformsRenderTimeWorldReads() {
        List<BlockPos> old = detour(4,8);
        PathPlanner planner = balanced(p(0,0),p(8,0),old,true,.08);
        planner.advance(old.size()+1,Long.MAX_VALUE);
        assertFalse(planner.isDone()); assertTrue("The validated incumbent is still complete",planner.reachedGoal());
        int reads = terrain.reads;
        for (int i=0;i<100;i++) assertEquals(old.subList(1,old.size()),planner.path());
        assertEquals(reads,terrain.reads);
        int work = planner.getWork(); planner.advance(50,0); assertEquals(work,planner.getWork());
        finish(planner,2); assertEquals(straight(1,8),planner.path());
    }

    @Test public void optimizationExhaustionKeepsACompleteValidatedRouteWithinTheSharedBudget() {
        for (int z=-11;z<=11;z++) terrain.blocked.add(p(4,z));
        List<BlockPos> old = detour(12,8);
        PathPlanner planner = new PathPlanner(terrain,p(0,0),p(8,0),12,100,8,true,3,true,old,true,.08);
        finish(planner,3); assertEquals(100,planner.getWork()); assertTrue(planner.reachedGoal());
        assertEquals(old.subList(1,old.size()),planner.path()); assertSafe(planner,p(0,0));
    }

    @Test public void periodicOptimizationFindsShortcutsWithoutForcingSearchEveryRefresh() {
        List<BlockPos> old = detour(4,8);
        PathPlanner planner = balanced(p(0,0),p(8,0),old,false,.08);
        finish(planner,3); assertEquals(0,planner.getExpanded()); assertEquals(old.subList(1,old.size()),planner.path());
        planner = balanced(p(0,0),p(8,0),old,true,.08);
        finish(planner,3); assertEquals(straight(1,8),planner.path());
    }

    @Test public void repairingDoesNotForceTheFinalRouteThroughSuboptimalOldAnchors() {
        terrain.blocked.add(p(4,0));
        PathPlanner planner = balanced(p(0,0),p(8,0),straight(0,8),true,.08);
        finish(planner,3); assertTrue(planner.reachedGoal()); assertSafe(planner,p(0,0));
        List<BlockPos> nodes = new ArrayList<>(); nodes.add(p(0,0)); nodes.addAll(planner.path());
        assertEquals(6+2*Math.sqrt(2),PathPlanner.routeCost(nodes),1e-9);
    }

    @Test public void substantialRelativeDetoursAreAlsoRemovedOnVeryShortRoutes() {
        PathPlanner planner = balanced(p(0,0),p(2,0),Arrays.asList(p(0,0),p(1,1),p(2,0)),true,.08);
        finish(planner,2); assertTrue(planner.reachedGoal()); assertEquals(straight(1,2),planner.path());
    }

    @Test public void movingEndpointsRemainNearOptimalWhileUsuallyKeepingSomeOldSections() {
        List<BlockPos> old = detour(4,8); int reused = 0;
        for (int i=0;i<40;i++) {
            BlockPos start = p((i/3)%3,(i/7)%2), goal = p(8+i%4,(i/4)%5-2);
            PathPlanner planner = balanced(start,goal,old,true,.08);
            finish(planner,3); assertTrue(planner.reachedGoal()); assertSafe(planner,start);
            old = new ArrayList<>(); old.add(start); old.addAll(planner.path());
            double cost = PathPlanner.routeCost(old), optimal = WalkPathfinder.estimate(start,goal,true);
            assertTrue("Do not accumulate detours as endpoints wander: "+cost+" vs "+optimal,
                    cost-optimal<=Math.max(.5,cost*.08)+1e-9);
            if (planner.getReusedNodes()>0) reused++;
        }
        assertTrue("A competitive old route should usually keep its sections",reused>20);
    }

    private PathPlanner planner(BlockPos start, BlockPos goal, List<BlockPos> old) {
        // Repair-only phase: optimization behavior is exercised separately above.
        return balanced(start,goal,old,false,.08);
    }
    private PathPlanner balanced(BlockPos start, BlockPos goal, List<BlockPos> old, boolean optimize, double stability) {
        return new PathPlanner(terrain,start,goal,12,2048,8,true,3,true,old,optimize,stability);
    }
    private static void finish(PathPlanner planner, int slice) {
        for (int i=0;i<3000 && !planner.isDone();i++) {
            int before = planner.getWork(); planner.advance(slice,Long.MAX_VALUE);
            assertTrue(planner.getWork()-before<=slice);
        }
        assertTrue("Planner must finish",planner.isDone());
    }
    private void assertSafe(PathPlanner planner, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>(); seen.add(start);
        for (BlockPos node : planner.path()) {
            assertTrue("Every new and retained edge is safe",WalkPathfinder.canStep(terrain,start,node,true,3,true));
            assertTrue("Connectors must not introduce loops",seen.add(node)); start=node;
        }
    }
    private static BlockPos last(PathPlanner planner) { return planner.path().get(planner.path().size()-1); }
    private static BlockPos p(int x, int z) { return new BlockPos(x,1,z); }
    private static List<BlockPos> straight(int from, int to) { return straightAt(from,to,0); }
    private static List<BlockPos> straightAt(int from, int to, int z) {
        List<BlockPos> nodes = new ArrayList<>(); for (int x=from;x<=to;x++) nodes.add(p(x,z)); return nodes;
    }
    private static List<BlockPos> detour(int height, int end) {
        List<BlockPos> nodes = new ArrayList<>(); nodes.add(p(0,0));
        for (int z=1;z<=height;z++) nodes.add(p(0,z));
        for (int x=1;x<=end;x++) nodes.add(p(x,height));
        for (int z=height-1;z>=0;z--) nodes.add(p(end,z));
        return nodes;
    }
    private static List<BlockPos> gentleDetour() { return gentleDetour(8); }
    private static List<BlockPos> gentleDetour(int end) {
        List<BlockPos> nodes = new ArrayList<>(); nodes.add(p(0,0));
        for (int x=1;x<end;x++) nodes.add(p(x,1));
        nodes.add(p(end,0)); return nodes;
    }
    private static final class Ground implements WalkPathfinder.Terrain {
        final Set<BlockPos> blocked = new HashSet<>();
        BlockPos closedFrom, closedTo;
        int reads;
        public boolean canStand(BlockPos p) {
            reads++; return p.getY()==1 && Math.abs(p.getX())<=12 && Math.abs(p.getZ())<=12 && !blocked.contains(p);
        }
        public boolean canMove(BlockPos a, BlockPos b) { return !a.equals(closedFrom) || !b.equals(closedTo); }
    }
}
