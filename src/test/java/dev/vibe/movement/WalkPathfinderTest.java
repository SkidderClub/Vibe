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

    private static WalkPathfinder.Terrain terrain(Set<BlockPos> ground) {
        return new WalkPathfinder.Terrain(){
            public boolean canStand(BlockPos p){return ground.contains(p);}
            public boolean canMove(BlockPos a,BlockPos b){return true;}
        };
    }
}
