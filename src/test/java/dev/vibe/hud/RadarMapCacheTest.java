package dev.vibe.hud;

import dev.vibe.combat.ProjectileFixture;
import dev.vibe.path.PathTestFixture;
import java.util.HashMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class RadarMapCacheTest {
    @Test public void solidTerrainIsOpaqueAndVoidAirAndUnloadedColumnsAreTransparent()throws Exception{
        dev.vibe.camera.DroneCollisionsTest.registerBlocks();MapWorld world=PathTestFixture.allocate(MapWorld.class);world.blocks=new HashMap<>();world.loaded=true;
        BlockPos column=new BlockPos(0,0,0);assertEquals(0,RadarMapCache.sample(world,column));
        world.surface=65;assertEquals(0,RadarMapCache.sample(world,column));
        world.blocks.put(new BlockPos(0,64,0),Blocks.stone.getDefaultState());int color=RadarMapCache.sample(world,column);
        assertEquals(255,color>>>24);assertNotEquals(0,color&0xFFFFFF);
        world.loaded=false;int reads=world.reads;assertEquals(0,RadarMapCache.sample(world,column));assertEquals(reads,world.reads);
    }
    public static class MapWorld extends ProjectileFixture.TestWorld {int surface;protected MapWorld(){super();}@Override public BlockPos getHeight(BlockPos pos){return new BlockPos(pos.getX(),surface,pos.getZ());}}
}
