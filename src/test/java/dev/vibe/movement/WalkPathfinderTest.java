package dev.vibe.movement;

import java.util.*;
import net.minecraft.util.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class WalkPathfinderTest {
    private static final BlockPos START = new BlockPos(0,1,0);

    @Test public void walksAroundWallInsteadOfPressingIntoIt() {
        Set<BlockPos> ground = new HashSet<>();
        for(int x=-3;x<=6;x++)for(int z=-4;z<=4;z++)
            if(x!=2||Math.abs(z)>2)ground.add(new BlockPos(x,1,z));
        BlockPos goal=new BlockPos(5,1,0);
        List<BlockPos> path=WalkPathfinder.find(terrain(ground),START,goal,12,4096);
        assertEquals(goal,path.get(path.size()-1));
        assertTrue(path.stream().anyMatch(p->Math.abs(p.getZ())==3));
        assertTrue(ground.containsAll(path));
    }

    @Test public void jumpsOneBlockButNeverWalksAcrossAVoid() {
        Set<BlockPos> ground=new HashSet<>(Arrays.asList(START,new BlockPos(1,2,0),new BlockPos(2,2,0),new BlockPos(4,2,0)));
        List<BlockPos> path=WalkPathfinder.find(terrain(ground),START,new BlockPos(4,2,0),12,100);
        assertEquals(Arrays.asList(new BlockPos(1,2,0),new BlockPos(2,2,0)),path);
    }

    @Test public void refusesTwoBlockJumpsAndFourBlockDrops() {
        for(int y:new int[]{3,-3}) {
            Set<BlockPos> ground=new HashSet<>(Arrays.asList(START,new BlockPos(1,y,0)));
            assertTrue(WalkPathfinder.find(terrain(ground),START,new BlockPos(1,y,0),8,100).isEmpty());
        }
    }

    @Test public void searchIsBoundedAndRejectsBlockedSweeps() {
        final int[] calls={0};
        WalkPathfinder.Terrain terrain=new WalkPathfinder.Terrain(){
            public boolean canStand(BlockPos p){calls[0]++;return p.getY()==1;}
            public boolean canMove(BlockPos a,BlockPos b){return b.getX()!=1;}
        };
        List<BlockPos> path=WalkPathfinder.find(terrain,START,new BlockPos(100,1,0),32,40);
        assertTrue(calls[0]<=40*20);
        assertTrue(path.stream().noneMatch(p->p.getX()==1));
    }

    @Test public void incrementalSearchResumesAtExactNodeCapsAndMatchesTheFullSearch() {
        Set<BlockPos> ground = new HashSet<>();
        for (int x = -2; x <= 12; x++) for (int z = -4; z <= 4; z++)
            if (x != 5 || Math.abs(z) > 2) ground.add(new BlockPos(x, 1, z));
        BlockPos goal = new BlockPos(10, 1, 0);
        WalkPathfinder.Terrain terrain = terrain(ground);
        WalkPathfinder.Search search = new WalkPathfinder.Search(terrain, START, goal, 16, 2048, 8, true, 3);
        search.advance(16, 0);
        assertEquals("An expired time slice does not read terrain", 0, search.getVisited());
        while (!search.isDone()) {
            int before = search.getVisited(); search.advance(3, Long.MAX_VALUE);
            assertTrue(search.getVisited() - before <= 3);
        }
        assertTrue(search.reachedGoal());
        assertEquals(WalkPathfinder.find(terrain, START, goal, 16, 2048), search.path());
    }

    @Test public void incrementalSearchHonorsJumpDropAndTotalBudgets() {
        Set<BlockPos> ground = new HashSet<>(Arrays.asList(START, new BlockPos(1,2,0), new BlockPos(2,0,0)));
        WalkPathfinder.Search noJump = new WalkPathfinder.Search(terrain(ground), START, new BlockPos(1,2,0), 8, 50, 8, false, 3);
        noJump.advance(50, Long.MAX_VALUE); assertTrue(noJump.path().isEmpty()); assertFalse(noJump.reachedGoal());
        WalkPathfinder.Search noDrop = new WalkPathfinder.Search(terrain(ground), START, new BlockPos(2,0,0), 8, 50, 8, true, 1);
        noDrop.advance(50, Long.MAX_VALUE); assertFalse(noDrop.reachedGoal());
        WalkPathfinder.Search budget = new WalkPathfinder.Search(terrain(ground), START, new BlockPos(20,1,0), 32, 2, 8, true, 3);
        budget.advance(1000, Long.MAX_VALUE); assertTrue(budget.isDone()); assertEquals(2, budget.getVisited());
        assertFalse(budget.reachedGoal());
    }

    @Test public void optInDiagonalsUseOctileRoutesWithoutChangingLegacyCardinalWalking() {
        Set<BlockPos> ground = new HashSet<>();
        for (int x=-2;x<=10;x++) for (int z=-2;z<=10;z++) ground.add(new BlockPos(x,1,z));
        BlockPos goal = new BlockPos(6,1,3);
        WalkPathfinder.Search search = new WalkPathfinder.Search(terrain(ground),START,goal,16,256,8,true,3,true);
        search.advance(256,Long.MAX_VALUE); assertTrue(search.reachedGoal());
        assertEquals(6,search.path().size());
        BlockPos previous = START; int diagonals = 0;
        for (BlockPos p : search.path()) {
            if (previous.getX()!=p.getX() && previous.getZ()!=p.getZ()) diagonals++;
            previous = p;
        }
        assertEquals(3,diagonals);
        assertEquals(9,WalkPathfinder.find(terrain(ground),START,goal,16,256).size());
    }

    @Test public void diagonalsCannotCutEvenOneBlockedCornerOrCrossUnsupportedSideCells() {
        BlockPos goal = new BlockPos(1,1,1), side = new BlockPos(0,1,1);
        Set<BlockPos> ground = new HashSet<>(Arrays.asList(START,goal,side));
        assertFalse(WalkPathfinder.canStep(terrain(ground),START,goal,true,3,true));
        WalkPathfinder.Search search = new WalkPathfinder.Search(terrain(ground),START,goal,8,64,8,true,3,true);
        search.advance(64,Long.MAX_VALUE); assertTrue(search.reachedGoal());
        assertEquals(Arrays.asList(side,goal),search.path());
        ground.remove(side);
        search = new WalkPathfinder.Search(terrain(ground),START,goal,8,64,8,true,3,true);
        search.advance(64,Long.MAX_VALUE); assertFalse(search.reachedGoal()); assertTrue(search.path().isEmpty());
    }

    @Test public void diagonalStepsStillRespectJumpsDropsAndBodySweeps() {
        BlockPos high = new BlockPos(1,2,1);
        Set<BlockPos> ground = new HashSet<>(Arrays.asList(START,high,new BlockPos(1,2,0),new BlockPos(0,2,1)));
        assertTrue(WalkPathfinder.canStep(terrain(ground),START,high,true,3,true));
        assertFalse(WalkPathfinder.canStep(terrain(ground),START,high,false,3,true));
        assertFalse(WalkPathfinder.canStep(terrain(ground),START,high,true,3,false));
        assertFalse(WalkPathfinder.canStep(terrain(ground),high,START,true,0,true));
        WalkPathfinder.Terrain blockedSweep = new WalkPathfinder.Terrain() {
            public boolean canStand(BlockPos p) { return ground.contains(p); }
            public boolean canMove(BlockPos a, BlockPos b) { return !a.equals(START) || !b.equals(high); }
        };
        assertFalse(WalkPathfinder.canStep(blockedSweep,START,high,true,3,true));
        ground.remove(new BlockPos(1,2,0)); ground.remove(new BlockPos(0,2,1));
        ground.add(new BlockPos(1,1,0)); ground.add(new BlockPos(0,1,1));
        assertTrue("A diagonal jump may have side floors at the departure level",
                WalkPathfinder.canStep(terrain(ground),START,high,true,3,true));
    }

    @Test public void strictCostBoundRejectsUncompetitiveRoutesWithoutTerrainReads() {
        final int[] reads = {0};
        WalkPathfinder.Terrain flat = new WalkPathfinder.Terrain() {
            public boolean canStand(BlockPos p) { reads[0]++; return p.getY()==1; }
            public boolean canMove(BlockPos a, BlockPos b) { return true; }
        };
        BlockPos goal = new BlockPos(6,1,3);
        double optimal = WalkPathfinder.estimate(START,goal,true);
        WalkPathfinder.Search search = new WalkPathfinder.Search(flat,START,goal,12,256,8,true,3,true,optimal);
        search.advance(256,Long.MAX_VALUE); assertTrue(search.isDone()); assertFalse(search.reachedGoal());
        assertEquals(0,search.getVisited()); assertEquals(0,reads[0]);
        search = new WalkPathfinder.Search(flat,START,goal,12,256,8,true,3,true,optimal+.1);
        search.advance(256,Long.MAX_VALUE); assertTrue(search.reachedGoal());
        List<BlockPos> nodes = new ArrayList<>(); nodes.add(START); nodes.addAll(search.path());
        assertEquals(optimal,dev.vibe.path.PathPlanner.routeCost(nodes),1e-9);
    }

    @Test public void longDiagonalRoutesPreferProgressOnEqualCostFrontiersAndAreReproducible() {
        WalkPathfinder.Terrain flat = new WalkPathfinder.Terrain() {
            public boolean canStand(BlockPos p) { return p.getY()==1; }
            public boolean canMove(BlockPos a, BlockPos b) { return true; }
        };
        BlockPos goal = new BlockPos(48,1,32); List<BlockPos> first = null;
        for (int i=0;i<3;i++) {
            WalkPathfinder.Search search = new WalkPathfinder.Search(flat,START,goal,64,2048,8,true,3,true);
            search.advance(2048,Long.MAX_VALUE); assertTrue(search.reachedGoal());
            assertEquals(48,search.path().size()); assertTrue("Avoid fanning across every equivalent diagonal permutation",
                    search.getVisited()<256);
            if (first==null) first=search.path(); else assertEquals(first,search.path());
        }
    }

    private static WalkPathfinder.Terrain terrain(Set<BlockPos> ground) {
        return new WalkPathfinder.Terrain(){
            public boolean canStand(BlockPos p){return ground.contains(p);}
            public boolean canMove(BlockPos a,BlockPos b){return true;}
        };
    }
}
