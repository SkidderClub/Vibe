package dev.vibe.movement;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorldWalkTerrainTest {
    @Test public void usesRealCollisionBoxesForSlabsCeilingsAndHazards() throws Exception {
        net.minecraft.init.Bootstrap.register();
        FixtureWorld world=allocate(FixtureWorld.class);world.blocks=new HashMap<>();
        EntityPlayerSP player=allocate(EntityPlayerSP.class);player.width=.6F;player.height=1.8F;
        WorldWalkTerrain terrain=new WorldWalkTerrain(world,player);
        world.blocks.put(new BlockPos(0,0,0),Blocks.stone.getDefaultState());
        world.blocks.put(new BlockPos(1,0,0),Blocks.stone_slab.getDefaultState());
        BlockPos start=new BlockPos(0,1,0),slab=new BlockPos(1,0,0);
        assertTrue(terrain.canStand(start));assertTrue(terrain.canStand(slab));
        assertEquals(.5,terrain.standingY(slab),0);
        assertTrue(terrain.canMove(start,slab));assertTrue(terrain.canMove(slab,start));
        assertFalse(terrain.canStand(new BlockPos(2,1,0)));
        world.blocks.put(new BlockPos(1,2,0),Blocks.stone.getDefaultState());
        assertFalse(terrain.canStand(slab));
        world.blocks.put(new BlockPos(0,1,0),Blocks.flowing_lava.getDefaultState());
        assertFalse(terrain.canStand(start));
        assertFalse(terrain.canStand(new BlockPos(20,1,0)));
    }
    private static <T>T allocate(Class<T> type)throws Exception {
        Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field f=unsafe.getDeclaredField("theUnsafe");f.setAccessible(true);
        return type.cast(unsafe.getMethod("allocateInstance",Class.class).invoke(f.get(null),type));
    }
    public static final class FixtureWorld extends WorldClient {
        Map<BlockPos,IBlockState> blocks;
        private FixtureWorld(){super(null,null,0,null,null);}
        @Override public IBlockState getBlockState(BlockPos pos){return blocks.getOrDefault(pos,Blocks.air.getDefaultState());}
        @Override public boolean isBlockLoaded(BlockPos pos){return Math.abs(pos.getX())<16;}
    }
}
