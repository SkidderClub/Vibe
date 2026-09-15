package dev.vibe.game.gta;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class Gta7GameTest {
    private static Gta7World world;
    @BeforeClass public static void city(){world=new Gta7World();}
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private Gta7Game game() throws Exception {
        Gta7Game game=new Gta7Game(world,new Gta7Progress(temporary.newFolder().toPath().resolve("progress.json")));
        game.npcs.clear();game.cars.clear();game.x=72;game.z=72;game.yaw=0;game.time=1;
        return game;
    }
    private void advance(Gta7Game game,double seconds,Gta7Game.Input input){for(int i=0;i<Math.round(seconds*60);i++)game.advance(1.0/60,input);}

    @Test public void everyBuildingHasAPhysicalDoorAndAnInteriorRoute(){
        assertTrue(world.buildings.size()>=80);
        for(Gta7World.Building b:world.buildings){
            double door=b.doorX;
            assertFalse(b.name,world.blocked(door,.14,b.doorZ,.28,1.78));
            if(b.width==9)assertTrue(b.name,world.blocked(b.x+1,.14,b.z,.28,1.78));
            List<double[]> path=world.path(door,b.doorZ-2,door,b.doorZ+6);
            assertFalse(b.name,path.isEmpty());
            for(double[] p:path)assertFalse(world.blocked(p[0],.16,p[1],.27,1.7));
        }
    }

    @Test public void raySlabsHandleParallelRaysAndInsideOrigins(){
        Gta7Bounds box=new Gta7Bounds(1,1,1,2,2,2);
        assertEquals(1,box.ray(1.5,1.5,0,0,0,1,10),1e-8);
        assertEquals(10,box.ray(3,1.5,0,0,0,1,10),1e-8);
        assertEquals(0,box.ray(1.5,1.5,1.5,0,0,1,10),1e-8);
        assertEquals(10,box.ray(1.5,1.5,0,0,0,-1,10),1e-8);
    }

    @Test public void normalizedMovementIsFrameIndependentAndJumpReturnsToGround() throws Exception {
        Gta7Game a=game(),b=game();Gta7Game.Input forward=new Gta7Game.Input();forward.forward=true;
        advance(a,1,forward);for(int i=0;i<30;i++)b.advance(1.0/30,forward);
        assertEquals(a.z,b.z,1e-7);assertEquals(4.3,72-a.z,.001);
        Gta7Game diagonal=game();forward.right=true;advance(diagonal,1,forward);
        assertEquals(4.3,Math.hypot(diagonal.x-72,diagonal.z-72),.001);
        Gta7Game.Input jump=new Gta7Game.Input();jump.jump=true;a.advance(1.0/60,jump);assertTrue(a.y>.14);
        advance(a,1,new Gta7Game.Input());assertEquals(.14,a.y,.001);assertTrue(a.grounded);
    }

    @Test public void gunsRequireAnAimedUnoccludedHitAndKnifeHasShortReach() throws Exception {
        Gta7Game g=game();Gta7Game.Npc target=new Gta7Game.Npc(72,68,false,0);g.npcs.add(target);
        g.attack();assertEquals(100,target.health,0);assertEquals(0,g.wanted);
        g.attackCooldown=0;g.equip(1);g.pitch=85;g.attack();assertEquals(100,target.health,0);assertEquals(0,g.wanted);
        g.attackCooldown=0;g.pitch=0;g.attack();assertTrue(target.health<100);assertTrue(g.wanted>0);
        Gta7Game w=game();w.x=8.4;w.z=6;w.yaw=180;w.weapon=1;
        Gta7Game.Npc behind=new Gta7Game.Npc(8.4,10,false,0);w.npcs.add(behind);w.attack();
        assertEquals(100,behind.health,0);assertEquals(0,w.wanted);
    }

    @Test public void killsDropXpOnlyOnceAndPickupImmediatelyPersists() throws Exception {
        Gta7Game g=game();Gta7Game.Npc target=new Gta7Game.Npc(72,71,false,0);g.npcs.add(target);
        g.damageNpc(target,BigDecimal.valueOf(1000));assertEquals(1,g.kills);assertEquals(1,g.drops.size());
        assertEquals(BigInteger.ZERO,g.progress.xp());
        g.damageNpc(target,BigDecimal.TEN);assertEquals(1,g.drops.size());
        g.advance(1.0/60,new Gta7Game.Input());assertEquals(BigInteger.valueOf(18),g.progress.xp());assertTrue(g.drops.isEmpty());
    }

    @Test public void policeReturnFireAndWallsProvideCover() throws Exception {
        Gta7Game g=game();Gta7Game.Npc cop=new Gta7Game.Npc(72,64,true,0);g.npcs.add(cop);g.wanted=1;
        advance(g,5,new Gta7Game.Input());assertTrue(g.health.compareTo(BigDecimal.valueOf(100))<0);
        Gta7Game cover=game();cover.x=8.4;cover.z=10;cover.wanted=1;
        Gta7Game.Npc blocked=new Gta7Game.Npc(8.4,6,true,0);blocked.repath=100;cover.npcs.add(blocked);
        cover.advance(.1,new Gta7Game.Input());assertFalse(blocked.seesPlayer);assertEquals(0,cover.health.compareTo(BigDecimal.valueOf(100)));
    }

    @Test public void deathBanksLooseXpAndRespawnKeepsPurchasedStats() throws Exception {
        Gta7Game g=game();g.drops.add(new Gta7Game.Drop(20,20,BigInteger.valueOf(150)));
        g.hurt(1000);assertTrue(g.dead);assertEquals(BigInteger.valueOf(150),g.progress.xp());
        assertTrue(g.progress.purchase(Gta7Progress.Upgrade.HEALTH));
        assertTrue(g.progress.purchase(Gta7Progress.Upgrade.AK47));
        g.respawn();assertFalse(g.dead);assertEquals("125",g.health.toPlainString());assertEquals("36",g.progress.damage(1).toPlainString());
        assertEquals(BigInteger.valueOf(65),g.progress.xp());assertEquals(0,g.wanted);assertEquals(BigInteger.valueOf(30),g.ammo);
    }

    @Test public void sustainedFireReloadsAndTrafficActuallyMoves() throws Exception {
        Gta7Game g=game();g.weapon=1;Gta7Game.Input attack=new Gta7Game.Input();attack.attack=true;
        advance(g,3.2,attack);assertEquals(BigInteger.ZERO,g.ammo);assertTrue(g.reload>0);
        advance(g,2.2,new Gta7Game.Input());assertEquals(BigInteger.valueOf(30),g.ammo);assertEquals(0,g.reload,0);
        g.respawn();double[] positions=new double[g.cars.size()];for(int i=0;i<positions.length;i++)positions[i]=g.cars.get(i).route;
        advance(g,2,new Gta7Game.Input());int moving=0;
        for(int i=0;i<positions.length;i++)if(Math.abs(positions[i]-g.cars.get(i).route)>.1)moving++;
        assertTrue("Traffic should not all deadlock",moving>g.cars.size()/2);
    }

    @Test public void trafficKeepsCirculatingAfterAMinuteWithPedestrians() throws Exception {
        Gta7Game g=game();g.respawn();
        advance(g,55,new Gta7Game.Input());
        double[] before=new double[g.cars.size()];for(int i=0;i<before.length;i++)before[i]=g.cars.get(i).route;
        advance(g,5,new Gta7Game.Input());int moving=0;
        for(int i=0;i<before.length;i++)if(Math.abs(before[i]-g.cars.get(i).route)>1)moving++;
        assertTrue("Traffic stopped circulating: "+moving+" moving cars",moving>=3);
    }

    @Test public void stairsReachUpperFloorsAndElevatorsReachBothEnds() throws Exception {
        Gta7Game g=game();Gta7World.Building b=world.buildings.get(0);
        g.x=b.x+7.5;g.z=b.z+8.4;g.y=.14;g.yaw=0;
        assertFalse(world.blocked(g.x,g.y,g.z,.28,1.78));
        Gta7Game.Input forward=new Gta7Game.Input();forward.forward=true;advance(g,1.5,forward);
        assertTrue("Stairs did not reach first floor: "+g.y,g.y>3.3);assertTrue(g.grounded);
        assertFalse(world.blocked(g.x,g.y+.001,g.z,.28,1.78));
        Gta7World.Elevator lift=world.elevators.get(0);g.x=lift.x;g.z=lift.z;g.y=.14;
        g.useElevator(false,true);assertEquals(.14+(lift.floors-1)*3.2,g.y,.00001);
        advance(g,.3,new Gta7Game.Input());assertEquals(.14+(lift.floors-1)*3.2,g.y,.00001);
        g.useElevator(true,true);assertEquals(.14,g.y,.00001);
    }

    @Test public void glassIsTransparentToRaysAndBreaksBeforeTheTarget() throws Exception {
        Gta7Game g=game();g.x=9.6;g.z=6;g.yaw=180;g.weapon=1;
        Gta7Game.Npc target=new Gta7Game.Npc(9.6,9,false,0);g.npcs.add(target);
        Gta7World.Window pane=null;
        for(Gta7World.Window w:world.windows)if(w.bounds.x0==8.8&&w.bounds.z0<8.2){pane=w;break;}
        assertNotNull(pane);assertFalse(pane.broken);
        g.attack();assertTrue(pane.broken);assertTrue(target.health<100);
        g.respawn();assertFalse(pane.broken);
    }

    @Test public void jetpackConsumesFuelRechargesAndMagazineReloadUsesUpgrade() throws Exception {
        Gta7Game g=game();g.progress.award(BigInteger.valueOf(10000));
        g.progress.purchase(Gta7Progress.Upgrade.JET_PACK);g.progress.purchase(Gta7Progress.Upgrade.MAX_AMMO);
        g.respawn();g.npcs.clear();g.cars.clear();g.x=72;g.z=72;g.weapon=1;
        assertEquals(BigInteger.valueOf(35),g.ammo);
        Gta7Game.Input jump=new Gta7Game.Input();jump.jump=true;advance(g,.6,jump);
        assertTrue(g.jetting);assertTrue(g.y>2);assertTrue(g.jetFuel.doubleValue()<.5);
        advance(g,1,jump);assertEquals(0,g.jetFuel.signum());
        advance(g,3,new Gta7Game.Input());assertTrue(g.grounded);assertEquals(1,g.jetFuel.doubleValue(),.001);
        g.ammo=BigInteger.TEN;g.startReload();advance(g,2.2,new Gta7Game.Input());assertEquals(BigInteger.valueOf(35),g.ammo);
    }

    @Test public void penetrationUpgradeHitsThroughWallsButBaseWeaponCannot() throws Exception {
        Gta7Game g=game();g.x=8.4;g.z=6;g.yaw=180;g.weapon=1;
        Gta7Game.Npc target=new Gta7Game.Npc(8.4,10,false,0);g.npcs.add(target);
        g.attack();assertEquals(100,target.health,0);
        g.progress.award(BigInteger.valueOf(1000000));for(int i=0;i<10;i++)g.progress.purchase(Gta7Progress.Upgrade.PENETRATION);
        for(int i=0;i<20&&target.health==100;i++){g.attackCooldown=0;g.attack();}
        assertTrue(target.health<100);
    }

    @Test public void regionalFactionsKeepIdentityAndRespondToTheirRules() throws Exception {
        Gta7Game g=game();
        for(Gta7Game.Kind kind:Gta7Game.Kind.values()) {
            Gta7Game.Npc npc=new Gta7Game.Npc(72,66,kind,0);
            assertEquals(kind.aggressive,g.hostile(npc));
            g.damageNpc(npc,BigDecimal.ONE);assertSame(kind,npc.kind);
            g.damageNpc(npc,BigDecimal.valueOf(1000));assertSame(kind,npc.kind);assertEquals(kind.law,npc.cop);
            g.wanted=0;
        }
        Gta7Game.Npc ct=new Gta7Game.Npc(72,66,Gta7Game.Kind.COUNTER_TERRORIST,0);
        assertFalse(g.hostile(ct));g.damageNpc(ct,BigDecimal.ONE);assertTrue(g.hostile(ct));
        Gta7Game.Npc penguin=new Gta7Game.Npc(72,66,Gta7Game.Kind.PENGUIN,0);
        assertFalse(g.hostile(penguin));
        g.respawn();
        java.util.EnumSet<Gta7Game.Kind> present=java.util.EnumSet.noneOf(Gta7Game.Kind.class);
        for(Gta7Game.Npc npc:g.npcs){present.add(npc.kind);assertFalse(npc.kind.toString(),world.blocked(npc.x,npc.y,npc.z,.27,npc.kind.height));}
        assertEquals(java.util.EnumSet.allOf(Gta7Game.Kind.class),present);
    }

    @Test public void hintsRespectActivityAndClockCyclesAcrossRespawn() throws Exception {
        Gta7Game g=game();g.time=0;assertTrue(g.hintsVisible());g.time=8;assertFalse(g.hintsVisible());
        g.time=26;assertTrue(g.hintsVisible());g.activity();assertFalse(g.hintsVisible());
        g.dayTime=0;assertEquals(1,g.daylight(),1e-8);assertEquals("12:00",g.clock());
        g.dayTime=300;assertEquals(0,g.daylight(),1e-8);assertEquals("00:00",g.clock());
        g.respawn();assertEquals(300,g.dayTime,0);assertTrue(g.hintsVisible());
    }

    @Test public void wantedStarsIncreaseAndDecayWhenNobodySeesPlayer() throws Exception {
        Gta7Game g=game();
        for(int i=0;i<20;i++)g.damageNpc(new Gta7Game.Npc(72,70,false,0),BigDecimal.valueOf(200));
        assertEquals(5,g.wanted);g.npcs.clear();g.drops.clear();
        for(String name:new String[]{"dispatch","population"}){java.lang.reflect.Field field=Gta7Game.class.getDeclaredField(name);field.setAccessible(true);field.setDouble(g,10000);}
        advance(g,90,new Gta7Game.Input());assertEquals(0,g.wanted);
    }

    @Test public void allEightRegionsAreConnectedToNorthside() {
        for(double[] site:new double[][]{{310,-140},{233,-102},{296,-44}})assertFalse("Mirage site route",world.path(260,-22,site[0],site[1]).isEmpty());
        for(Gta7Regions.Region region:Gta7Regions.Region.values()) {
            double x=region.x(),z=region.z();
            assertEquals(region,Gta7Regions.at(x,z));
            if(region==Gta7Regions.Region.MIRAGE){x=260;z=-22;}
            else if(region==Gta7Regions.Region.EMOJI)z-=25;
            else z-=8;
            assertFalse(region.title,world.path(90,77,x,z).isEmpty());
        }
    }

    @Test public void holdingJumpClimbsSuccessiveLadderSectionsAndCrouchDescends() throws Exception {
        Gta7Game g=game();Gta7World.Building building=null;
        for(Gta7World.Building b:world.buildings)if(b.height>9&&b.width==9){building=b;break;}
        assertNotNull(building);g.x=building.x+5.95;g.z=building.z+8.2;g.y=.14;
        Gta7Game.Input input=new Gta7Game.Input();input.jump=true;advance(g,2.4,input);
        assertTrue("Ladder stopped at a floor boundary: "+g.y,g.y>6.4);
        input.jump=false;input.sneak=true;advance(g,2.5,input);assertEquals(.14,g.y,.001);
    }

    @Test public void maxFastShootingFiresTwiceAsManyRoundsOverTime() throws Exception {
        Gta7Game base=game(),fast=game();fast.progress.award(BigInteger.TEN.pow(8));
        for(int i=0;i<10;i++)fast.progress.purchase(Gta7Progress.Upgrade.FAST_SHOOTING);
        base.ammo=fast.ammo=BigInteger.valueOf(10000);base.weapon=fast.weapon=1;
        Gta7Game.Input firing=new Gta7Game.Input();firing.attack=true;advance(base,10,firing);advance(fast,10,firing);
        int slowShots=10000-base.ammo.intValue(),fastShots=10000-fast.ammo.intValue();
        assertEquals(slowShots*2,fastShots,2);
    }
}
