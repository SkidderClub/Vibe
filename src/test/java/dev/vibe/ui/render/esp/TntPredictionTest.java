package dev.vibe.ui.render.esp;

import dev.vibe.path.PathTestFixture;
import java.util.*;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.*;
import net.minecraft.world.World;
import org.junit.Test;
import static org.junit.Assert.*;

public class TntPredictionTest {
    private CollisionWorld world() throws Exception {
        dev.vibe.camera.DroneCollisionsTest.registerBlocks();
        CollisionWorld w=PathTestFixture.allocate(CollisionWorld.class);w.loaded=true;
        PathTestFixture.set(World.class,w,"theProfiler",new Profiler());PathTestFixture.set(World.class,w,"provider",new net.minecraft.world.WorldProviderSurface());return w;
    }
    private EntityTNTPrimed tnt(CollisionWorld w,double x,double y,double vx,double vy,int fuse){
        EntityTNTPrimed e=new EntityTNTPrimed(w);e.setPosition(x,y,.5);e.motionX=vx;e.motionY=vy;e.motionZ=.03;e.fuse=fuse;return e;
    }
    @Test public void forecastMatchesNativeTicksForAirGroundAndWall() throws Exception {
        for(double x:new double[]{.5,3.6})for(double y:new double[]{64,68})for(int ticks:new int[]{0,1,10,40,80}){
            CollisionWorld w=world();PathTestFixture.set(World.class,w,"isRemote",true);EntityTNTPrimed e=tnt(w,x,y,.17,.2,ticks),nativeTnt=tnt(w,x,y,.17,.2,ticks);
            TntPrediction.Result result=TntPrediction.simulate(w,e);
            for(int i=0;i<ticks+2&&!nativeTnt.isDead;i++)nativeTnt.onUpdate();assertTrue(nativeTnt.isDead);
            assertTrue(result.complete);assertEquals(nativeTnt.posX,result.position.xCoord,1e-7);
            assertEquals(nativeTnt.posY+nativeTnt.height/16D,result.position.yCoord,1e-7);assertEquals(nativeTnt.posZ,result.position.zCoord,1e-7);
            assertEquals("Prediction mutated the live entity",x,e.posX,0);assertEquals(ticks,e.fuse);
        }
    }
    @Test public void unknownTerrainAndExcessiveFusesNeverPretendToBeCertain() throws Exception {
        CollisionWorld w=world();EntityTNTPrimed e=tnt(w,.5,68,0,0,80);w.loaded=false;
        assertFalse(TntPrediction.simulate(w,e).complete);assertEquals(0,w.collisionReads);
        w.loaded=true;e.fuse=10000;TntPrediction.Result result=TntPrediction.simulate(w,e);assertFalse(result.complete);assertTrue(result.path.size()<=136);
        e.fuse=0;assertTrue(TntPrediction.simulate(w,e).position.yCoord<e.posY+e.height/16D);
    }
    public static class CollisionWorld extends WorldClient {
        boolean loaded;int collisionReads;
        private CollisionWorld(){super(null,null,0,null,null);}
        @Override public boolean isBlockLoaded(BlockPos p){return loaded;}
        @Override public boolean isAreaLoaded(BlockPos from,BlockPos to){return loaded;}
        @Override public boolean isAreaLoaded(BlockPos from,BlockPos to,boolean empty){return loaded;}
        @Override public boolean isBlockLoaded(BlockPos p,boolean empty){return loaded;}
        @Override protected boolean isChunkLoaded(int x,int z,boolean empty){return loaded;}
        @Override public long getTotalWorldTime(){return 0;}
        @Override public net.minecraft.block.state.IBlockState getBlockState(BlockPos p){return (p.getY()==63||p.getX()==4&&p.getY()<70?Blocks.stone:Blocks.air).getDefaultState();}
        @Override public List<AxisAlignedBB> getCollidingBoundingBoxes(Entity e,AxisAlignedBB box){
            collisionReads++;List<AxisAlignedBB> result=new ArrayList<>();
            AxisAlignedBB floor=new AxisAlignedBB(-100,63,-100,100,64,100),wall=new AxisAlignedBB(4,64,-100,5,70,100);
            if(floor.intersectsWith(box))result.add(floor);if(wall.intersectsWith(box))result.add(wall);return result;
        }
        @Override public void spawnParticle(EnumParticleTypes type,double x,double y,double z,double vx,double vy,double vz,int... args){}
    }
}
