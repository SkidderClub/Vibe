package dev.vibe.ambience;

import dev.vibe.camera.DroneCollisionsTest;
import dev.vibe.path.PathTestFixture;
import java.util.HashMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FireflyBlockSpaceTest {
    @Test public void tallFenceShapesAreSolidAndUnloadedNeighboursAreNeverRead()throws Exception{
        try(PathTestFixture fixture=new PathTestFixture()){
            DroneCollisionsTest.registerBlocks();
            BlockWorld world=PathTestFixture.allocate(BlockWorld.class);world.loaded=true;world.blocks=new HashMap<>();
            world.blocks.put(new BlockPos(0,0,0),Blocks.oak_fence.getDefaultState());
            FireflyBlockSpace space=new FireflyBlockSpace(world,fixture.player);
            assertFalse(space.collisions(new AxisAlignedBB(.43,1.22,.43,.57,1.38,.57)).isEmpty());
            assertTrue(space.collisions(new AxisAlignedBB(.75,1.22,.75,.9,1.38,.9)).isEmpty());
            world.loaded=false;int reads=world.reads;
            assertFalse(space.available(0,1,0));assertFalse(space.loaded(new AxisAlignedBB(0,1,0,1,2,1)));
            space.collisions(new AxisAlignedBB(0,1,0,1,2,1));assertEquals(reads,world.reads);
        }
    }
    public static final class BlockWorld extends PathTestFixture.FixtureWorld {
        private BlockWorld(){super();}
        @Override public boolean isAreaLoaded(BlockPos from,BlockPos to){return loaded;}
    }
}
