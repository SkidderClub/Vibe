package dev.vibe.path;

import dev.vibe.movement.WalkPathfinder;
import dev.vibe.movement.WorldWalkTerrain;
import java.util.*;
import net.minecraft.block.BlockLiquid;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import org.junit.*;
import static org.junit.Assert.*;

/** Real block geometry and the same cached, bounded planner used by AutoWalk. */
public class WaterPathTest {
    private PathTestFixture f;
    @Before public void setup() throws Exception { f=new PathTestFixture(); }
    @After public void cleanup() throws Exception { f.close(); }
    private void pool() {
        for(int x=2;x<=5;x++)for(int z=-3;z<=3;z++) {
            f.block(x,59,z,Blocks.stone.getDefaultState());
            for(int y=60;y<=63;y++)f.block(x,y,z,Blocks.water.getDefaultState());
        }
    }
    private static void finish(PathPlanner planner) {
        for(int tick=0;tick<200&&!planner.isDone();tick++)planner.advance(32,Long.MAX_VALUE);
        assertTrue(planner.isDone());assertTrue(planner.getWork()<=2048);
    }
    @Test public void routesAcrossDeepWaterToTheOtherBankWithoutLandAutoJump() {
        pool();PathTerrain terrain=new PathTerrain(f.world,f.player);
        BlockPos start=new BlockPos(0,64,0),goal=new BlockPos(7,64,0);
        PathPlanner planner=new PathPlanner(terrain,start,goal,12,2048,24,false,3,true,null);
        finish(planner);assertTrue(planner.reachedGoal());assertTrue(planner.path().stream().anyMatch(terrain::isSwimming));
        BlockPos from=start;for(BlockPos to:planner.path()) {
            assertTrue(WalkPathfinder.canStep(terrain,from,to,false,3,true));from=to;
        }
        assertEquals(goal,from);
        double surface=terrain.standingY(new BlockPos(3,64,0));assertTrue(surface>63&&surface<64);
    }
    @Test public void submergedStartsCanAscendAVerticalWaterColumnAndExit() {
        for(int y=54;y<=63;y++) {
            f.block(0,y,0,Blocks.water.getDefaultState());
            for(int[] side:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})f.block(side[0],y,side[1],Blocks.stone.getDefaultState());
        }
        PathTerrain terrain=new PathTerrain(f.world,f.player);BlockPos start=new BlockPos(0,55,0),goal=new BlockPos(2,64,0);
        assertEquals(start,terrain.grounded(start));
        PathPlanner planner=new PathPlanner(terrain,start,goal,12,2048,24,false,0,true,null);
        finish(planner);assertTrue(planner.reachedGoal());
        long vertical=0;BlockPos from=start;
        for(BlockPos to:planner.path()) {
            if(to.getX()==from.getX()&&to.getZ()==from.getZ())vertical++;
            assertTrue(WalkPathfinder.canStep(terrain,from,to,false,0,true));from=to;
        }
        assertTrue("No surfacing edges in the actual route",vertical>=7);assertEquals(goal,from);
    }
    @Test public void waterDoesNotPermitAirPlatformsLavaOrLowCeilingClipping() {
        pool();PathTerrain terrain=new PathTerrain(f.world,f.player);
        assertFalse(terrain.canStand(new BlockPos(3,67,0)));
        assertFalse(WalkPathfinder.canStep(terrain,new BlockPos(0,64,0),new BlockPos(0,65,0),true,3,true));
        f.block(3,65,0,Blocks.stone.getDefaultState());assertFalse(new PathTerrain(f.world,f.player).canStand(new BlockPos(3,64,0)));
        f.block(4,64,0,Blocks.flowing_lava.getDefaultState());assertFalse(new PathTerrain(f.world,f.player).canStand(new BlockPos(4,64,0)));
        f.block(6,64,0,Blocks.stone.getDefaultState());
        assertFalse("A high bank still needs a lower exit",WalkPathfinder.canStep(new PathTerrain(f.world,f.player),
                new BlockPos(5,64,0),new BlockPos(6,65,0),false,3,true));
    }
    @Test public void landOnlyCallersStillRejectWaterAndFlowingWaterUsesItsLevel() {
        pool();BlockPos node=new BlockPos(3,64,0);
        assertFalse(new WorldWalkTerrain(f.world,f.player).canStand(node));
        WorldWalkTerrain swimming=new WorldWalkTerrain(f.world,f.player,true);double source=swimming.standingY(node);
        f.block(3,63,0,Blocks.flowing_water.getDefaultState().withProperty(BlockLiquid.LEVEL,6));
        assertTrue(swimming.standingY(node)<source);assertTrue(swimming.canStand(node));
    }
    @Test public void swimmingReuseAndUnloadedTerrainKeepTheExistingBounds() {
        pool();PathTerrain terrain=new PathTerrain(f.world,f.player);BlockPos start=new BlockPos(0,64,0),goal=new BlockPos(7,64,0);
        PathPlanner original=new PathPlanner(terrain,start,goal,12,2048,24,false,3,true,null);finish(original);assertTrue(original.reachedGoal());
        List<BlockPos> previous=new ArrayList<>();previous.add(start);previous.addAll(original.path());
        PathPlanner reused=new PathPlanner(new PathTerrain(f.world,f.player),start,goal,12,2048,24,false,3,true,previous);finish(reused);
        assertTrue(reused.reachedGoal());assertTrue(reused.getReusedNodes()>0);
        f.world.loaded=false;PathTerrain unloaded=new PathTerrain(f.world,f.player);
        assertNull(unloaded.grounded(new BlockPos(3,64,0)));assertFalse(unloaded.isSwimming(new BlockPos(3,64,0)));
    }
}
