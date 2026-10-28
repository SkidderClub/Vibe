package dev.vibe.easter;

import dev.vibe.path.PathTestFixture;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFireworkRocket;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClientFireworksTest {
    @Test public void nativeRocketNbtFlightLifetimeAndStatusExplosionAreUsed()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            Recorder world=PathTestFixture.allocate(Recorder.class);world.entities=new HashMap<>();world.loaded=true;
            PathTestFixture.set(World.class,world,"provider",new WorldProviderSurface());PathTestFixture.set(World.class,world,"isRemote",true);
            ClientFireworks fireworks=new ClientFireworks();fireworks.spawn(world,0,64,0);assertEquals(1,fireworks.count());
            EntityFireworkRocket rocket=(EntityFireworkRocket)world.spawned;assertEquals(EntityFireworkRocket.class,rocket.getClass());assertTrue(rocket.getEntityId()<0);assertTrue(rocket.motionY>0);
            NBTTagCompound state=new NBTTagCompound();rocket.writeEntityToNBT(state);int lifetime=state.getInteger("LifeTime");assertTrue(lifetime>=20&&lifetime<=31);
            for(int tick=0;tick<lifetime;tick++)fireworks.tick(world);assertEquals(0,world.explosions);
            fireworks.tick(world);assertEquals(1,world.explosions);assertTrue(rocket.isDead);assertEquals(0,fireworks.count());
            assertTrue(world.effects.getTagList("Explosions",10).tagCount()>=1);
        }
    }
    @Test public void activeRocketsAreBoundedAndWorldChangesRemoveOnlyClientOwnedEntities()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            Recorder world=PathTestFixture.allocate(Recorder.class);world.entities=new HashMap<>();world.loaded=true;PathTestFixture.set(World.class,world,"provider",new WorldProviderSurface());PathTestFixture.set(World.class,world,"isRemote",true);
            ClientFireworks fireworks=new ClientFireworks();for(int i=0;i<40;i++)fireworks.spawn(world,i,64,0);assertEquals(12,fireworks.count());
            fireworks.tick(f.world);assertEquals(0,fireworks.count());assertEquals(0,world.explosions);
        }
    }
    public static class Recorder extends PathTestFixture.FixtureWorld {
        Entity spawned;int explosions;NBTTagCompound effects;
        private Recorder(){super();}
        @Override public boolean spawnEntityInWorld(Entity entity){spawned=entity;entities.put(entity.getEntityId(),entity);return true;}
        @Override public void makeFireworks(double x,double y,double z,double vx,double vy,double vz,NBTTagCompound nbt){explosions++;effects=nbt;}
    }
}
