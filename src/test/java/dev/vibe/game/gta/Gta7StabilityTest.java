package dev.vibe.game.gta;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class Gta7StabilityTest {
    private static Gta7World world;
    @BeforeClass public static void world(){world=new Gta7World();}
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private Gta7Game game() throws Exception {
        Gta7Game game=new Gta7Game(world,new Gta7Progress(temporary.newFolder().toPath().resolve("progress.json")));
        game.npcs.clear();game.cars.clear();game.x=72;game.z=72;game.time=1;
        return game;
    }
    @Test public void everyPropIsDrawnOnceAndChunkBoundsIncludeEveryFloor(){
        Set<Gta7World.Prop> seen=Collections.newSetFromMap(new IdentityHashMap<Gta7World.Prop,Boolean>());
        for(Gta7World.Chunk c:world.chunks){
            for(Gta7World.Prop p:c.props){assertTrue("Repeated "+p.type,seen.add(p));assertEquals(0,c.distanceSquared(p.x,p.size,p.z),0);}
            for(Gta7World.Box box:c.boxes){Gta7Bounds b=box.bounds;assertEquals(0,c.distanceSquared(b.x0,b.y0,b.z0),0);assertEquals(0,c.distanceSquared(b.x1,b.y1,b.z1),0);}
        }
        assertEquals(world.props.size(),seen.size());assertTrue(seen.size()>4000);
    }
    @Test public void reusedPathSearchesRemainIndependentAndRespectTheirWorkBudget(){
        List<double[]> expected=world.path(72,72,108,108);assertFalse(expected.isEmpty());
        for(int i=0;i<30;i++){
            world.path(90,77,260,-22,32);assertTrue(world.lastPathExpanded<=32);
            List<double[]> actual=world.path(72,72,108,108);assertEquals(expected.size(),actual.size());
            for(int j=0;j<expected.size();j++)assertArrayEquals(expected.get(j),actual.get(j),0);
        }
    }
    @Test public void crowdedSceneCannotStartMoreThanTwoRoutesPerAdvance() throws Exception {
        Gta7Game g=game();g.wanted=3;g.health=BigDecimal.valueOf(100000);
        for(int i=0;i<120;i++){Gta7Game.Npc cop=new Gta7Game.Npc(72,53,true,i%8);cop.shoot=1000;g.npcs.add(cop);}
        int routed=0;
        for(int i=0;i<20;i++){g.advance(.1,new Gta7Game.Input());assertTrue(g.lastPathRequests<=2);routed+=g.lastPathRequests;}
        assertTrue("Deferred actors never received navigation work",routed>10);
    }
    @Test public void fastUpwardMotionCannotTunnelThroughAnUpperFloor() throws Exception {
        Gta7Game g=game();Gta7World.Building b=world.buildings.get(0);
        g.x=b.x+4.5;g.z=b.z+6;g.y=.14;g.grounded=false;g.velocityY=800;
        g.advance(1.0/120,new Gta7Game.Input());
        assertTrue(g.y>.14);assertTrue("Player crossed ceiling",g.y+g.bodyHeight()<=3.16+.00001);
        assertFalse(world.blocked(g.x,g.y,g.z,.28,g.bodyHeight()));assertEquals(0,g.velocityY,0);
    }
    @Test public void peripheralReliefHasMatchingWalkingAndBulletSurfaces(){
        assertFalse(world.reliefs.isEmpty());
        Gta7World.Relief r=world.reliefs.get(0);double top=r.height(r.x,r.z,0);
        assertTrue(top>.2);assertTrue(world.blocked(r.x,top-.1,r.z,.1,1.78));
        assertTrue(world.support(r.x,r.z,top+.001,0)>=top-.00001);
        assertEquals(5,r.ray(r.x,top+5,r.z,0,-1,0,20),.00001);
    }
    @Test public void elevatedPedestriansDoNotStopTrafficAndCornerRollbackRestoresHeading() throws Exception {
        Gta7Game g=game();Gta7Game.Car car=new Gta7Game.Car(1,1,false,10,0);car.speed=6;g.cars.add(car);
        Gta7Game.Npc above=new Gta7Game.Npc(car.x,car.z,false,0);above.y=6;above.think=1000;g.npcs.add(above);
        g.advance(1.0/120,new Gta7Game.Input());assertTrue(car.route>10);assertTrue(car.speed>0);
        g.npcs.clear();car.route=car.side-.01;car.locate();car.speed=6;double route=car.route,yaw=car.yaw;
        g.x=car.x;g.z=car.z;g.y=.14;g.advance(1.0/120,new Gta7Game.Input());
        assertEquals(route,car.route,0);assertEquals(yaw,car.yaw,0);assertEquals(0,car.speed,0);
    }
    @Test public void glassAndImpactsHaveBoundedParticlesThatExpire() throws Exception {
        Gta7Game g=game();g.x=9.6;g.z=6;g.yaw=180;g.weapon=1;g.ammo=BigInteger.valueOf(1000);
        g.attack();assertFalse(g.particles.isEmpty());
        for(int i=0;i<100;i++){g.attackCooldown=0;g.attack();assertTrue(g.particles.size()<=256);}
        for(int i=0;i<150;i++)g.advance(1.0/60,new Gta7Game.Input());assertTrue(g.particles.isEmpty());
    }
    @Test public void nonFiniteTimingAndDamageDoNotPoisonTheSimulation() throws Exception {
        Gta7Game g=game();g.hurt(Double.NaN);g.hurt(Double.POSITIVE_INFINITY);
        assertEquals(BigDecimal.valueOf(100),g.health);
        g.advance(Double.NaN,new Gta7Game.Input());g.advance(Double.POSITIVE_INFINITY,new Gta7Game.Input());
        g.advance(1.0/60,new Gta7Game.Input());assertTrue(Double.isFinite(g.y));assertTrue(g.time>1);
    }
    @Test public void longSessionsBankOverflowDropsWithoutLosingExperience() throws Exception {
        Gta7Game g=game();
        for(int i=0;i<300;i++){g.damageNpc(new Gta7Game.Npc(72,64,false,0),BigDecimal.valueOf(200));assertTrue(g.drops.size()<=256);}
        assertEquals(BigInteger.valueOf(44*18),g.progress.xp());g.hurt(1000);
        assertEquals(BigInteger.valueOf(300*18),g.progress.xp());assertEquals(300*18,g.runXp);
    }
    @Test public void residentRespawnsKeepStableIdentityDespiteDisplacedSpawnPoints() throws Exception {
        Gta7Game g=game();g.respawn();g.health=BigDecimal.valueOf(100000);
        for(Gta7World.Spawn resident:world.residents){
            int count=0;for(Gta7Game.Npc npc:g.npcs)if(npc.resident==resident)count++;
            assertEquals(resident.kind.toString(),1,count);
        }
        Gta7World.Spawn resident=world.residents.get(0);g.x=resident.x;g.z=resident.z;
        for(int i=0;i<240;i++)g.advance(1.0/60,new Gta7Game.Input());
        for(Gta7World.Spawn original:world.residents){int count=0;for(Gta7Game.Npc npc:g.npcs)if(npc.resident==original&&npc.health>0)count++;assertEquals(1,count);}
    }
}
