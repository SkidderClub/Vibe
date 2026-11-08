package dev.vibe.hitmarker;

import dev.vibe.path.PathTestFixture;
import dev.vibe.module.impl.visual.HitmarkerModule;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class HitParticlesTest {
    public static PathTestFixture fixture()throws Exception{
        PathTestFixture f=new PathTestFixture();ParticleWorld world=PathTestFixture.allocate(ParticleWorld.class);
        world.blocks=f.world.blocks;world.entities=f.world.entities;world.loaded=true;
        PathTestFixture.set(PathTestFixture.class,f,"world",world);f.mc.theWorld=world;f.player.worldObj=world;return f;
    }
    public static class ParticleWorld extends PathTestFixture.FixtureWorld{
        private ParticleWorld(){super();}
        @Override public boolean isAreaLoaded(BlockPos from,BlockPos to){return loaded&&from.getX()>-32&&to.getX()<32&&from.getZ()>-32&&to.getZ()<32;}
    }
    private static HitParticleSettings settings(){HitParticleSettings s=new HitParticleSettings(v->v,()->true);s.count.setValue(1D);s.speed.setValue(0D);s.lift.setValue(0D);s.lifetime.setValue(5000D);s.delay.setValue(0D);return s;}
    private static HitParticles emit(PathTestFixture f,HitParticleSettings s,double x,double y,double z){HitParticles p=new HitParticles(new Random(1));p.emit(f.world,s,new Vec3(x,y,z),new Vec3(0,0,1),1000);assertEquals(1,p.particles().size());return p;}
    @Test public void fallsBouncesAndSettlesOnTheFloorThenFallsIfSupportDisappears()throws Exception{
        try(PathTestFixture f=fixture()){
            HitParticleSettings s=settings();HitParticles system=emit(f,s,.5,65,.5);HitParticles.Particle p=system.particles().get(0);boolean bounced=false;
            for(int i=0;i<45;i++){system.tick(f.world,s);assertEquals(1,system.particles().size());assertTrue(p.y-p.radius>=64-1e-6);if(p.vy>0)bounced=true;}
            assertTrue(bounced);assertEquals(64+p.radius,p.y,1e-6);assertEquals(0,p.vy,0);assertTrue(p.grounded);
            f.world.blocks.clear();for(int i=0;i<5;i++)system.tick(f.world,s);assertTrue(p.y<64);assertFalse(p.grounded);
        }
    }
    @Test public void sweptMovementCannotTunnelThroughThinPanesAndCeilings()throws Exception{
        try(PathTestFixture f=fixture()){
            f.block(2,65,0,Blocks.glass_pane.getDefaultState());HitParticleSettings s=settings();s.gravity.setValue(0D);
            HitParticles system=emit(f,s,.5,65.5,.5);HitParticles.Particle p=system.particles().get(0);p.vx=80;
            system.tick(f.world,s);assertTrue(p.x<2.6);assertTrue(p.vx<0);assertTrue(new dev.vibe.ambience.FireflyBlockSpace(f.world,null).collisions(p.bounds()).isEmpty());
            f.block(0,67,0,Blocks.stone.getDefaultState());system=emit(f,s,.5,66,.5);p=system.particles().get(0);p.vy=60;system.tick(f.world,s);
            assertTrue(p.y+p.radius<=67);assertTrue(p.vy<0);
        }
    }
    @Test public void slabsUseTheirActualHalfHeightAndStairsAreNotFullCubes()throws Exception{
        try(PathTestFixture f=fixture()){
            f.block(0,64,0,Blocks.stone_slab.getDefaultState());HitParticleSettings s=settings();s.bounce.setValue(0D);
            HitParticles system=emit(f,s,.5,65.5,.5);HitParticles.Particle p=system.particles().get(0);
            for(int i=0;i<35;i++)system.tick(f.world,s);assertEquals(64.5+p.radius,p.y,1e-6);
            f.block(2,64,0,Blocks.stone_stairs.getDefaultState());
            system=emit(f,s,2.15,65.5,.5);p=system.particles().get(0);for(int i=0;i<35;i++)system.tick(f.world,s);
            assertTrue(p.y>=64.5+p.radius-1e-6&&p.y<=65+p.radius+1e-6);
        }
    }
    @Test public void burstsAreBoundedThrottledAndExpireIndependentlyOfNormalHitmarkers()throws Exception{
        try(PathTestFixture f=fixture()){
            HitParticleSettings s=settings();s.count.setValue(40D);s.limit.setValue(64D);s.delay.setValue(100D);s.gravity.setValue(0D);HitParticles system=new HitParticles(new Random(2));
            Vec3 point=new Vec3(.5,65,.5),look=new Vec3(0,0,1);system.emit(f.world,s,point,look,1000);system.emit(f.world,s,point,look,1001);assertEquals(40,system.particles().size());
            system.emit(f.world,s,point,look,1100);assertEquals(64,system.particles().size());
            assertTrue(system.particles().stream().map(p->p.radius).distinct().count()>1);
            for(int i=0;i<120;i++)system.tick(f.world,s);assertTrue(system.particles().isEmpty());
            HitmarkerModule module=new HitmarkerModule();f.modules.registerDynamic(module);module.getModes().setValue(new LinkedHashSet<>(Arrays.asList("Particle","2D (Crosshair)")));module.setEnabled(true);
            system.emit(f.world,s,point,look,2000);module.particles.emit(f.world,s,point,look,2000);module.setEnabled(false);assertTrue(module.particles.particles().isEmpty());
            system.tick(null,s);assertTrue(system.particles().isEmpty());
        }
    }
    @Test public void unloadedChunksAndBlocksPlacedAroundParticlesDiscardThemWithoutReadingOrLoadingTerrain()throws Exception{
        try(PathTestFixture f=fixture()){
            HitParticleSettings s=settings();HitParticles system=emit(f,s,.5,65.5,.5);f.world.loaded=false;system.tick(f.world,s);assertTrue(system.particles().isEmpty());
            system.emit(f.world,s,new Vec3(.5,65,.5),new Vec3(0,0,1),2000);assertTrue(system.particles().isEmpty());
            f.world.loaded=true;system=emit(f,s,.5,65.5,.5);f.block(0,65,0,Blocks.stone.getDefaultState());system.tick(f.world,s);assertTrue(system.particles().isEmpty());
        }
    }
    @Test public void lifeColorsAndFadeAreStableAndRespectAlpha()throws Exception{
        try(PathTestFixture f=fixture()){
            HitParticleSettings s=settings();HitParticles system=emit(f,s,.5,65,.5);HitParticles.Particle p=system.particles().get(0);
            assertEquals(1,HitParticles.opacity(p,s,0),0);s.colorMode.setValue("Static");s.color.setValue(0x8067DFFF);assertEquals(0x8067DFFF,HitParticles.color(p,s,0));
            s.colorMode.setValue("Fade");s.endColor.setValue(0x40112233);p.age=p.lifetime-10;assertTrue(HitParticles.opacity(p,s,0)<.01F);assertNotEquals(s.color.getArgb(),HitParticles.color(p,s,0));
            s.fade.setEnabled(false);assertEquals(1,HitParticles.opacity(p,s,0),0);p.age=p.lifetime;assertEquals(0,HitParticles.opacity(p,s,0),0);
        }
    }
    @Test public void moduleEmitsAtTheActualSavedContactAndChangingModesClearsOnlyParticles()throws Exception{
        try(dev.vibe.combat.ProjectileFixture f=new dev.vibe.combat.ProjectileFixture()){
            HitmarkerModule module=new HitmarkerModule();f.base.modules.registerDynamic(module);
            module.getModes().setValue(new LinkedHashSet<>(Arrays.asList("Particle","3D (World)")));module.setEnabled(true);
            dev.vibe.combat.ProjectileFixture.Player target=f.player("Enemy",.5,64,3.5);
            module.mark(target);assertEquals(module.particleSettings.count.getInt(),module.particles.particles().size());
            for(HitParticles.Particle p:module.particles.particles()){
                assertEquals(module.getLastX(),p.x,.02);assertEquals(module.getLastY(),p.y,.02);assertEquals(module.getLastZ(),p.z,.02);
            }
            assertTrue(module.getLastZ()<target.posZ);assertEquals(1,module.getMarkers().size());module.tickParticles();
            module.getModes().setValue(Collections.singleton("3D (World)"));module.tickParticles();assertTrue(module.particles.particles().isEmpty());
            assertEquals(1,module.getMarkers().size());module.setEnabled(false);
        }
    }
}
