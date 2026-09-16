package dev.vibe.game.battlefront;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static dev.vibe.game.battlefront.BattlefrontContent.*;

public class BattlefrontBattalionTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private BattlefrontProgress progress()throws Exception{return BattlefrontProgress.load(temp.newFolder().toPath().resolve("campaign.json"));}
    @Test public void enlargedTheatersHaveReachableOuterDistrictsAndSolidOuterWalls(){
        assertEquals(448,2*BattlefrontWorld.LIMIT,0);
        for(Scenario scenario:Scenario.values()){
            BattlefrontWorld w=new BattlefrontWorld(scenario);assertEquals(scenario==Scenario.GEONOSIS?12:14,w.buildings.size());assertTrue(w.props.size()>1800);
            BattlefrontNavigation nav=new BattlefrontNavigation(w);int outer=0;double lo=1000,hi=-1000;
            for(int x=-220;x<=220;x+=8)for(int z=-220;z<=220;z+=8){lo=Math.min(lo,w.height(x,z));hi=Math.max(hi,w.height(x,z));}assertTrue(hi-lo>24);
            for(BattlefrontArchitecture.Building b:w.buildings){
                if(Math.max(Math.abs(b.x),Math.abs(b.z))>112)outer++;
                for(int face:new int[]{-1,1}){
                    double tx=b.x,tz=b.z+face*(b.d+(b.floor>1?27:5));List<double[]> path=nav.route(0,94,tx,tz);
                    assertFalse("Unreachable approach: "+b.name+" "+face,path.isEmpty());
                    double ax=0,az=94;for(double[] step:path){int samples=(int)Math.ceil(Math.hypot(step[0]-ax,step[1]-az)*2);for(int i=0;i<=samples;i++){double t=samples==0?0:(double)i/samples;assertFalse("Route crosses cover at "+b.name,w.blocked(ax+(step[0]-ax)*t,az+(step[1]-az)*t,.38));}ax=step[0];az=step[1];}
                }
                assertFalse(w.visible(b.x+3,b.base+b.floor+1.5,b.z+b.d-1,b.x+3,b.base+b.floor+1.5,b.z+b.d+1));
            }assertEquals(9,outer);
            assertTrue(w.blocked(225,0,.38));assertTrue(w.blocked(0,-225,.38));
        }
    }
    @Test public void roleEquipmentTrainingAndDoctrinesAffectDeployedSoldiers()throws Exception{
        BattlefrontProgress p=progress();assertFalse(p.equipArmy(Role.HEAVY,Armor.HEAVY));assertFalse(p.equipArmy(Role.ASSAULT,Weapon.DLT19X));
        p.award(100000,0);p.purchase(Armor.HEAVY);p.purchase(Weapon.DLT19X);p.equipArmy(Role.ASSAULT,Weapon.DLT19X);p.equipArmy(Role.ASSAULT,Armor.HEAVY);
        double health=p.armyHealth(Role.ASSAULT),damage=p.armyDamage(Role.ASSAULT);assertTrue(p.train(Role.ASSAULT));assertTrue(p.armyHealth(Role.ASSAULT)>health);assertTrue(p.armyDamage(Role.ASSAULT)>damage);
        p.doctrine(Doctrine.BULWARK);assertTrue(p.armyHealth(Role.ASSAULT)>health*1.15);assertTrue(p.armySpeed(Role.ASSAULT)<Role.ASSAULT.speed);
        BattlefrontGame g=new BattlefrontGame(p,Scenario.ENDOR,0,Mode.SUPREMACY,1,0,2);BattlefrontGame.Soldier s=g.soldiers.get(0);assertEquals(p.armyHealth(Role.ASSAULT),s.maxHealth,0);
        g.damage(s,20,false);assertEquals(s.maxHealth-20*(1-Armor.HEAVY.resistance*.7),s.health,.0001);
        assertTrue(p.armyInterval(Role.ASSAULT)>Role.ASSAULT.interval);
        for(Role r:Role.values()){while(p.train(r)){}assertEquals(5,p.veteran(r));long credits=p.credits();assertFalse(p.train(r));assertEquals(credits,p.credits());}
    }
    @Test public void customizationPersistsSeparatelyAndCopyDoesNotAliasRows()throws Exception{
        Path file=temp.newFolder().toPath().resolve("save.json");BattlefrontProgress p=BattlefrontProgress.load(file);p.award(10000,0);p.train(Role.SCOUT);p.purchase(Weapon.ION);p.equipArmy(Role.SCOUT,Weapon.ION);p.doctrine(Doctrine.RECON);p.formation(Formation.COLUMN);
        long before=p.credits();for(Cosmetic c:Cosmetic.values())assertTrue(p.customize(null,c,c.choices.length-1));assertTrue(p.copyAppearanceToArmy(null));assertTrue(p.customize(Role.SCOUT,Cosmetic.PAINT,2));assertEquals(before,p.credits());
        BattlefrontProgress loaded=BattlefrontProgress.load(file);assertNull(loaded.error());assertEquals(Doctrine.RECON,loaded.doctrine());assertEquals(Formation.COLUMN,loaded.formation());assertEquals(1,loaded.veteran(Role.SCOUT));assertEquals(Weapon.ION,loaded.armyWeapon(Role.SCOUT));assertEquals(2,loaded.appearance(Role.SCOUT).paint);assertEquals(7,loaded.appearance(null).paint);assertEquals(7,loaded.appearance(Role.ASSAULT).paint);
        assertFalse(loaded.customize(null,Cosmetic.HELMET,99));assertFalse(loaded.customize(null,Cosmetic.HELMET,-1));
    }
    @Test public void versionTwoMigratesAndInvalidVersionThreeIsPreserved()throws Exception{
        Path file=temp.newFolder().toPath().resolve("save.json");BattlefrontProgress p=BattlefrontProgress.load(file);p.award(4000,0);p.purchase(Weapon.A280);p.purchase(Weapon.A280,WeaponMod.POWER);p.save();
        JsonObject data=new JsonParser().parse(new String(Files.readAllBytes(file),StandardCharsets.UTF_8)).getAsJsonObject();data.addProperty("version",2);
        for(String field:new String[]{"formation","doctrine","veterans","armyWeapons","armyArmor","appearances"})data.remove(field);Files.write(file,data.toString().getBytes(StandardCharsets.UTF_8));
        BattlefrontProgress migrated=BattlefrontProgress.load(file);assertNull(migrated.error());assertEquals(p.credits(),migrated.credits());assertEquals(Weapon.A280,migrated.weapon());assertEquals(1,migrated.level(Weapon.A280,WeaponMod.POWER));assertEquals(Doctrine.BALANCED,migrated.doctrine());assertNull(migrated.armyWeapon(Role.SCOUT));migrated.save();
        data=new JsonParser().parse(new String(Files.readAllBytes(file),StandardCharsets.UTF_8)).getAsJsonObject();data.addProperty("formation",200);byte[] corrupt=data.toString().getBytes(StandardCharsets.UTF_8);Files.write(file,corrupt);Files.write(file.resolveSibling("save.json.bak"),corrupt);
        BattlefrontProgress broken=BattlefrontProgress.load(file);assertNotNull(broken.error());assertFalse(broken.train(Role.SCOUT));assertFalse(broken.customize(null,Cosmetic.PAINT,1));assertFalse(broken.doctrine(Doctrine.RECON));assertFalse(broken.save());assertArrayEquals(corrupt,Files.readAllBytes(file));
    }
    @Test public void rallyOrdersUseFormationsAndReconRewardPaysOnlyOnce()throws Exception{
        BattlefrontGame g=new BattlefrontGame(progress(),Scenario.ENDOR,0,Mode.SUPREMACY,1,0,2);g.soldiers.clear();
        assertFalse(g.markWaypoint(Double.NaN,0));assertFalse(g.markWaypoint(500,0));assertTrue(g.markWaypoint(0,125));g.rallyWaypoint();assertEquals(Order.HOLD,g.order);assertEquals(g.waypointX,g.orderX,0);assertEquals(g.waypointZ,g.orderZ,0);
        Set<String> slots=new HashSet<String>();for(Formation f:Formation.values()){slots.clear();for(int i=0;i<36;i++){double[] offset=f.offset(i);assertTrue(slots.add(Arrays.toString(offset)));}}
        for(int i=0;i<5;i++)g.searchedCaches.add(g.world.buildings.get(i).name);long before=g.progress.credits();g.advance(.1,new BattlefrontGame.Input());assertTrue(g.reconContract);assertEquals(before+300,g.progress.credits());g.advance(.1,new BattlefrontGame.Input());assertEquals(before+300,g.progress.credits());
    }
    @Test public void alliedSquadTravelsToAnOuterRallyPoint()throws Exception{
        for(Scenario scenario:Scenario.values()){
            BattlefrontProgress p=progress();p.formation(Formation.COLUMN);BattlefrontGame g=new BattlefrontGame(p,scenario,0,Mode.SUPREMACY,1,0,7);g.soldiers.removeIf(s->s.side!=g.side);g.protection=1000;assertTrue(g.markWaypoint(145,125));g.rallyWaypoint();
            for(int i=0;i<110*30;i++)g.advance(1.0/30,new BattlefrontGame.Input());int arrived=0;for(BattlefrontGame.Soldier s:g.soldiers)if(Math.hypot(s.x-g.orderX,s.z-g.orderZ)<25)arrived++;
            assertTrue("Rally stalled in "+scenario+": "+arrived,arrived>=9);
        }
    }
    @Test public void outerPatrolsGuardTheirDistrictsAndStayClearedAfterCacheRecovery()throws Exception{
        for(Scenario scenario:Scenario.values()){
            BattlefrontGame g=new BattlefrontGame(progress(),scenario,0,Mode.SUPREMACY,1,0,3);List<BattlefrontGame.Soldier> guards=new ArrayList<BattlefrontGame.Soldier>();
            for(BattlefrontGame.Soldier s:g.soldiers)if(s.guardLocation!=null){guards.add(s);assertFalse(g.world.blocked(s.x,s.z,.48));assertTrue(Math.max(Math.abs(s.x),Math.abs(s.z))>112);}
            assertEquals(18,guards.size());g.soldiers.clear();g.soldiers.addAll(guards);g.protection=1000;
            for(int i=0;i<20*30;i++)g.advance(1.0/30,new BattlefrontGame.Input());for(BattlefrontGame.Soldier s:guards)assertTrue(Math.max(Math.abs(s.x),Math.abs(s.z))>105);
            BattlefrontGame.Soldier defeated=guards.get(0);g.damage(defeated,10000,true);g.searchedCaches.add(defeated.guardLocation);
            for(int i=0;i<50*30;i++)g.advance(1.0/30,new BattlefrontGame.Input());assertFalse(defeated.alive());
        }
    }
}
