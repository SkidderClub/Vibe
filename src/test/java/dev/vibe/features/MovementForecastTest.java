package dev.vibe.features;

import dev.vibe.combat.ProjectileFixture;
import dev.vibe.path.PathTestFixture;
import dev.vibe.module.impl.combat.ProjectileDodgeModule;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.*;
import java.util.*;
import java.lang.reflect.Proxy;
import org.junit.Test;
import static org.junit.Assert.*;

public class MovementForecastTest {
    static final class Fixture implements AutoCloseable {
        final ProjectileFixture base=new ProjectileFixture();final PredictionWorld world=PathTestFixture.allocate(PredictionWorld.class);
        Fixture()throws Exception{
            world.blocks=base.world.blocks;world.loaded=true;world.chunk=new Chunk(world,0,0){@Override public boolean isLoaded(){return true;}@Override public boolean isEmpty(){return false;}};
            PathTestFixture.set(World.class,world,"isRemote",true);PathTestFixture.set(World.class,world,"provider",new WorldProviderSurface());PathTestFixture.set(World.class,world,"theProfiler",new net.minecraft.profiler.Profiler());PathTestFixture.set(World.class,world,"worldBorder",new WorldBorder());
            IChunkProvider chunks=(IChunkProvider)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IChunkProvider.class},(p,m,a)->{if(m.getName().equals("provideChunk")||m.getName().equals("loadChunk"))return world.chunk;if(m.getReturnType()==boolean.class)return true;if(m.getReturnType()==int.class)return 1;if(m.getReturnType()==String.class)return "Fixture";return null;});PathTestFixture.set(World.class,world,"chunkProvider",chunks);
            PathTestFixture.set(World.class,world,"loadedEntityList",base.world.loadedEntityList);PathTestFixture.set(World.class,world,"playerEntities",base.world.playerEntities);base.base.mc.theWorld=world;base.self.worldObj=world;base.self.capabilities=new PlayerCapabilities();base.self.jumpMovementFactor=.02F;base.self.stepHeight=.6F;base.self.movementInput=new MovementInput();
            PathTestFixture.set(EntityPlayer.class,base.self,"foodStats",new FoodStats());PathTestFixture.set(EntityLivingBase.class,base.self,"activePotionsMap",new HashMap<>());base.self.getAttributeMap().registerAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(.1);
        }
        public void close()throws Exception{base.close();}
    }
    public static class PredictionWorld extends ProjectileFixture.TestWorld {
        Chunk chunk;private PredictionWorld(){super();}
        @Override public Chunk getChunkFromChunkCoords(int x,int z){return chunk;}
        @Override public boolean isFlammableWithin(AxisAlignedBB box){return false;}
        @Override public boolean isAnyLiquid(AxisAlignedBB box){return false;}
        @Override public boolean isMaterialInBB(AxisAlignedBB box,net.minecraft.block.material.Material m){return false;}
        @Override public boolean handleMaterialAcceleration(AxisAlignedBB box,net.minecraft.block.material.Material m,Entity e){return false;}
        @Override public float getRainStrength(float partial){return 0;}
    }
    @Test public void nativePhysicsPredictsTravelWithoutChangingRealPlayer()throws Exception{try(Fixture f=new Fixture()){MovementInput input=new MovementInput();input.moveForward=1;double x=f.base.self.posX,z=f.base.self.posZ;MovementForecast.Path p=MovementForecast.simulate(input,30,0);assertTrue(p.loaded);assertTrue(p.safe);assertTrue(p.points.get(30).zCoord>4);assertEquals(x,f.base.self.posX,0);assertEquals(z,f.base.self.posZ,0);assertEquals(0,f.base.self.motionZ,0);assertEquals(1,input.moveForward,0);}}
    @Test public void futureVoidAndUnloadedTerrainRejectThePath()throws Exception{try(Fixture f=new Fixture()){f.world.blocks.entrySet().removeIf(e->e.getKey().getZ()>=2);MovementInput input=new MovementInput();input.moveForward=1;assertFalse(MovementForecast.simulate(input,30,0).safe);f.world.loaded=false;int before=f.world.reads;MovementForecast.Path path=MovementForecast.simulate(input,30,0);assertFalse(path.loaded);assertEquals(before,f.world.reads);}}
}
