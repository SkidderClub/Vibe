package dev.vibe.game.battlefront;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static dev.vibe.game.battlefront.BattlefrontContent.*;

public class BattlefrontExpansionTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private BattlefrontProgress progress()throws Exception{return BattlefrontProgress.load(temp.newFolder().toPath().resolve("campaign.json"));}
    private BattlefrontGame game(Scenario s)throws Exception{BattlefrontGame g=new BattlefrontGame(progress(),s,0,Mode.CONQUEST,1,0,1);g.soldiers.clear();return g;}
    private void walk(BattlefrontGame g,double seconds){BattlefrontGame.Input in=new BattlefrontGame.Input();in.forward=true;for(int i=0;i<(int)(seconds*30);i++)g.advance(1.0/30,in);}
    @Test public void allBuildingsHaveEnterableDoorsFloorsAndInteriorCaches()throws Exception{
        for(Scenario scenario:Scenario.values()){
            BattlefrontGame g=game(scenario);assertTrue(g.world.buildings.size()>=3);
            for(BattlefrontArchitecture.Building b:g.world.buildings){
                assertTrue(b.name,b.parts.size()>60);
                for(int face:new int[]{-1,1}){
                    g.x=b.x;g.z=b.z+face*(b.d+(b.floor>1?23:3));g.y=g.world.height(g.x,g.z);g.yaw=face==1?0:180;g.jumpVelocity=0;
                    walk(g,b.floor>1?4.35:.85);
                    assertTrue("Could not enter "+b.name+" face "+face+" at "+g.x+","+g.y+","+g.z,b.contains(g.x,g.y,g.z));
                    assertEquals("Floor of "+b.name,b.base+b.floor,g.y,.12);
                }
                assertFalse("Door blocked: "+b.name,g.world.blocked(b.x,b.base+b.floor,b.z+b.d,.35,1.85));
                assertTrue("Solid side wall missing: "+b.name,g.world.blocked(b.x+b.w,b.base+b.floor,b.z,.35,1.85));
                g.x=b.cacheX()-1.35;g.z=b.cacheZ();g.y=b.base+b.floor;long before=g.progress.credits();g.interact();assertEquals(before+85,g.progress.credits());g.interact();assertEquals(before+85,g.progress.credits());
            }
        }
    }
    @Test public void factoryMezzanineHasContinuousStairsAndSolidCeiling()throws Exception{
        BattlefrontGame g=game(Scenario.GEONOSIS);BattlefrontArchitecture.Building b=g.world.buildings.get(0);
        g.x=b.x-b.w+2;g.z=b.z+b.d-.5;g.y=b.base+b.floor;g.yaw=0;walk(g,2.1);
        assertEquals(b.base+b.floor+4.25,g.y,.12);assertTrue(g.z<b.z-b.d+4);
        double ceiling=g.world.ceiling(b.x,b.z,b.base+b.floor);assertEquals(b.base+b.floor+8.5,ceiling,.01);
        assertTrue(g.world.ray(b.x,b.base+b.floor+1,b.z,0,1,0,15)<9);
    }
    @Test public void thirdPersonCameraIsBehindPlayerAndRetractsAtWalls()throws Exception{
        BattlefrontGame g=game(Scenario.ENDOR);g.yaw=0;g.pitch=0;BattlefrontGame.Camera open=g.camera(false);
        assertTrue(open.z>g.z+3);assertTrue(open.x>g.x);assertTrue(open.y>g.eyeY());
        BattlefrontArchitecture.Building b=g.world.buildings.get(0);g.x=b.x+3;g.z=b.z+b.d-1;g.y=b.base+b.floor;
        BattlefrontGame.Camera blocked=g.camera(false);assertTrue("Camera crossed the wall",blocked.z<b.z+b.d);assertTrue(blocked.z-g.z<1);
        for(int pitch=-80;pitch<=80;pitch+=20){g.pitch=pitch;BattlefrontGame.Camera c=g.camera(false);assertTrue(c.y>=g.world.height(c.x,c.z));assertTrue(Double.isFinite(c.x+c.y+c.z));}
    }
    @Test public void thinDoorwayWallsBlockVisibilityEvenWhenTargetHugsTheWall()throws Exception{
        BattlefrontGame g=game(Scenario.ENDOR);BattlefrontArchitecture.Building b=g.world.buildings.get(0);
        assertFalse(g.world.visible(b.x+3,b.base+b.floor+1.5,b.z+b.d-.3,b.x+3,b.base+b.floor+1.5,b.z+b.d+.3));
        assertTrue(g.world.visible(b.x,b.base+b.floor+1.5,b.z+b.d-.3,b.x,b.base+b.floor+1.5,b.z+b.d+.3));
    }
    @Test public void gearOwnershipModificationsAndEquippedStatsPersist()throws Exception{
        Path file=temp.newFolder().toPath().resolve("save.json");BattlefrontProgress p=BattlefrontProgress.load(file);
        assertFalse(p.purchase(Weapon.DLT19X));assertFalse(p.purchase(Weapon.DLT19X,WeaponMod.POWER));p.award(1000000,0);
        for(Weapon w:Weapon.values()){assertTrue(p.purchase(w));long before=p.credits();assertTrue(p.purchase(w));assertEquals(before,p.credits());for(WeaponMod m:WeaponMod.values()){for(int n=0;n<8;n++)assertTrue(p.purchase(w,m));assertFalse(p.purchase(w,m));}}
        for(Armor a:Armor.values()){assertTrue(p.purchase(a));for(ArmorMod m:ArmorMod.values())assertTrue(p.purchase(a,m));}
        BattlefrontProgress loaded=BattlefrontProgress.load(file);assertEquals(Weapon.ION,loaded.weapon());assertEquals(Armor.BESKAR,loaded.armor());assertEquals(8,loaded.level(Weapon.DLT19X,WeaponMod.CELL));assertEquals(p.credits(),loaded.credits());assertTrue(loaded.resistance()>.28);
    }
    @Test public void olderCampaignGetsStarterEquipmentWithoutLosingCurrency()throws Exception{
        Path file=temp.newFolder().toPath().resolve("save.json");BattlefrontProgress p=BattlefrontProgress.load(file);p.award(712,150);p.save();
        JsonObject d=new JsonParser().parse(new String(Files.readAllBytes(file),StandardCharsets.UTF_8)).getAsJsonObject();d.addProperty("version",1);
        for(String field:new String[]{"weapons","armors","weapon","armor","weaponMods","armorMods"})d.remove(field);Files.write(file,d.toString().getBytes(StandardCharsets.UTF_8));
        BattlefrontProgress migrated=BattlefrontProgress.load(file);assertEquals(p.credits(),migrated.credits());assertTrue(migrated.owns(Weapon.DC15A));assertTrue(migrated.owns(Armor.FIELD));assertTrue(migrated.save());
    }
    @Test public void differentWeaponsAndArmorChangeCombatAndJetpackIsLimited()throws Exception{
        BattlefrontProgress p=progress();p.award(20000,0);p.purchase(Weapon.BOWCASTER);p.purchase(Armor.HEAVY);
        BattlefrontGame g=new BattlefrontGame(p,Scenario.GEONOSIS,0,Mode.CONQUEST,1,0,1);g.soldiers.clear();g.fire(true);assertEquals(3,g.bolts.size());assertEquals(11,g.ammo);
        g.protection=0;double hp=g.health;g.hurt(100);assertEquals(hp-78,g.health,.001);
        p.purchase(Armor.BESKAR);g=new BattlefrontGame(p,Scenario.GEONOSIS,0,Mode.CONQUEST,1,0,2);g.soldiers.clear();BattlefrontGame.Input in=new BattlefrontGame.Input();in.jump=true;
        for(int i=0;i<105;i++)g.advance(1.0/30,in);assertTrue(g.y>g.world.height(g.x,g.z)+6);assertTrue(g.jetFuel<1);
        for(int i=0;i<60;i++)g.advance(1.0/30,in);assertFalse(g.jetting);assertEquals(0,g.jetFuel,.001);
    }
}
