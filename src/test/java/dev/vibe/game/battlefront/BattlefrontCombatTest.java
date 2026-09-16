package dev.vibe.game.battlefront;

import dev.vibe.ui.BattlefrontMapTexture;
import java.awt.image.BufferedImage;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static dev.vibe.game.battlefront.BattlefrontContent.*;

public class BattlefrontCombatTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private BattlefrontGame game()throws Exception{
        BattlefrontGame g=new BattlefrontGame(BattlefrontProgress.load(temp.newFolder().toPath().resolve("campaign.json")),Scenario.GEONOSIS,0,Mode.CONQUEST,1,0,42);
        g.soldiers.clear();return g;
    }
    private void ticks(BattlefrontGame g,int ticks){for(int i=0;i<ticks;i++)g.advance(1.0/30,new BattlefrontGame.Input());}
    @Test public void jumpClearsLowCoverAndStillLandsAndRespectsCeilings()throws Exception{
        BattlefrontGame g=game();double floor=g.y,peak=floor;BattlefrontGame.Input input=new BattlefrontGame.Input();input.jump=true;
        for(int i=0;i<70;i++){g.advance(1.0/30,input);peak=Math.max(peak,g.y);}
        assertTrue("Jump should clear 2m cover",peak-floor>2.3);assertTrue(peak-floor<2.6);assertEquals(floor,g.y,.01);
        BattlefrontArchitecture.Building b=g.world.buildings.get(1);g.x=b.x;g.z=b.z+b.d-1;g.y=b.base+b.floor;
        ticks(g,1);double ceiling=g.world.ceiling(g.x,g.z,g.y);assertTrue(Double.isFinite(ceiling));
        for(int i=0;i<50;i++){g.advance(1.0/30,input);assertTrue("Head went through ceiling",g.y+1.85<=ceiling+.001);}
    }
    @Test public void reloadCannotFireOrRefillEarlyAndHandlingChangesWholeAnimation()throws Exception{
        BattlefrontGame g=game();g.ammo=3;g.startReload();double duration=g.reload;int shots=g.shots;
        assertEquals(0,g.reloadProgress(),.001);g.startReload();assertEquals(duration,g.reload,.001);g.fire(true);assertEquals(shots,g.shots);
        ticks(g,(int)(duration*15));assertEquals(3,g.ammo);assertTrue(g.reloadProgress()>.4&&g.reloadProgress()<.6);
        ticks(g,100);assertEquals(g.magazine(),g.ammo);assertEquals(-1,g.reloadProgress(),0);
        g.progress.award(10000,0);assertTrue(g.progress.purchase(g.progress.weapon(),WeaponMod.HANDLING));g.ammo=1;g.startReload();assertTrue(g.reloadDuration<duration);
        g.protection=0;g.hurt(9999);assertEquals(0,g.reload,0);assertEquals(-1,g.reloadProgress(),0);
    }
    @Test public void incomingDirectionsTrackWorldOriginMergeAndExpire()throws Exception{
        BattlefrontGame g=game();g.hurt(5,g.x,g.z-10);assertTrue(g.damageDirections.isEmpty());g.protection=0;
        g.hurt(5,g.x,g.z-10);assertEquals(0,g.damageDirections.get(0).yaw,.001);g.yaw=90;g.hurt(5,g.x+.1,g.z-10);assertEquals(1,g.damageDirections.size());
        g.hurt(5,g.x+10,g.z);assertEquals(90,g.damageDirections.get(1).yaw,.001);g.hurt(5,g.x,g.z+10);g.hurt(5,g.x-10,g.z);assertEquals(4,g.damageDirections.size());
        g.hurt(Double.NaN,g.x,g.z);assertTrue(Double.isFinite(g.health));ticks(g,36);assertTrue(g.damageDirections.isEmpty());
    }
    @Test public void damageNumbersReportActualDamageMergePelletsAndExpire()throws Exception{
        BattlefrontGame g=game();BattlefrontGame.Soldier target=new BattlefrontGame.Soldier(1,Role.ASSAULT,0);target.health=70;
        g.damage(target,20,true);g.damage(target,25,true);assertEquals(1,g.damageNumbers.size());assertEquals(45,g.damageNumbers.get(0).amount,.001);
        g.damage(target,1000,true);assertEquals(70,g.damageNumbers.get(0).amount,.001);assertTrue(g.damageNumbers.get(0).kill);
        ticks(g,34);assertTrue(g.damageNumbers.isEmpty());target.health=100;g.damage(target,10,false);assertTrue(g.damageNumbers.isEmpty());
        for(int i=0;i<8;i++){ticks(g,3);target.x+=1;g.damage(target,1,true);}assertEquals("Sustained fire must not stack overlapping numbers",1,g.damageNumbers.size());assertEquals(8,g.damageNumbers.get(0).amount,0);assertEquals(target.x,g.damageNumbers.get(0).x,0);
        for(int i=0;i<30;i++){BattlefrontGame.Soldier other=new BattlefrontGame.Soldier(1,Role.ASSAULT,i);other.health=100;g.damage(other,1,true);}assertEquals(16,g.damageNumbers.size());
    }
    @Test public void projectedHitsStayOnCameraRayAcrossHeadingsAimAndAspectRatios()throws Exception{
        BattlefrontGame g=game();g.y=70;
        for(boolean aim:new boolean[]{false,true})for(double yaw:new double[]{0,90,170,-75})for(double pitch:new double[]{-40,0,65})for(double aspect:new double[]{4./3,16./9,21./9}){
            g.yaw=yaw;g.pitch=pitch;g.aiming=aim;BattlefrontGame.Camera c=g.camera(aim);
            double[] p=BattlefrontProjection.project(g,c.x+c.dx*20,c.y+c.dy*20,c.z+c.dz*20,aspect);
            assertNotNull(p);assertEquals(0,p[0],.00001);assertEquals(0,p[1],.00001);
            assertNull(BattlefrontProjection.project(g,c.x-c.dx*10,c.y-c.dy*10,c.z-c.dz*10,aspect));
        }
    }
    @Test public void reloadPoseSeatsCellAndReturnsToIdleWithoutDiscontinuities(){
        assertEquals(0,BattlefrontReloadPose.at(0).lift,0);assertEquals(0,BattlefrontReloadPose.at(1).lift,0);
        assertEquals(1,BattlefrontReloadPose.at(.48).cellTravel,.001);assertEquals(0,BattlefrontReloadPose.at(.8).cellTravel,.001);
        double last=0;for(int i=0;i<=1000;i++){BattlefrontReloadPose p=BattlefrontReloadPose.at(i/1000.);assertTrue(p.cellTravel>=0&&p.cellTravel<=1);assertTrue(Math.abs(p.lift-last)<.02);last=p.lift;}
    }
    @Test public void minimapContainsDistinctTerrainAndAccurateBuildingFootprints(){
        for(Scenario s:Scenario.values()){BattlefrontWorld w=new BattlefrontWorld(s);BufferedImage map=BattlefrontMapTexture.rasterize(w);
            assertEquals(512,map.getWidth());assertEquals(0xFF151C20,map.getRGB(0,0));
            BattlefrontArchitecture.Building b=w.buildings.get(0);int x=(int)((b.x+240)/480*512),z=(int)((b.z+240)/480*512);
            assertEquals(0xFF667D75,map.getRGB(x,z));java.util.Set<Integer> colors=new java.util.HashSet<Integer>();for(int px=0;px<512;px+=8)for(int py=0;py<512;py+=8)colors.add(map.getRGB(px,py));assertTrue(colors.size()>50);
        }
    }
}
