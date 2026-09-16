package dev.vibe.game.battlefront;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static dev.vibe.game.battlefront.BattlefrontContent.*;

public class BattlefrontGameTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private BattlefrontProgress progress()throws Exception{return BattlefrontProgress.load(temp.newFolder().toPath().resolve("campaign.json"));}
    private BattlefrontGame game()throws Exception{return new BattlefrontGame(progress(),Scenario.GEONOSIS,0,Mode.CONQUEST,1,0,42);}
    private void advance(BattlefrontGame g,double seconds){advance(g,seconds,new BattlefrontGame.Input());}
    private void advance(BattlefrontGame g,double seconds,BattlefrontGame.Input input){for(int i=0;i<(int)(seconds*30);i++)g.advance(1.0/30,input);}
    @Test public void allSidesHaveSafeSpawnsDetailedWorldsAndRealElevation(){
        for(Scenario scenario:Scenario.values()){
            BattlefrontWorld w=new BattlefrontWorld(scenario);assertTrue(w.props.size()>120);
            double low=100,high=-100;for(int x=-100;x<=100;x+=5)for(int z=-100;z<=100;z+=5){double h=w.height(x,z);low=Math.min(low,h);high=Math.max(high,h);}
            assertTrue(high-low>10);
            for(int side=0;side<2;side++)for(int i=0;i<36;i++){double[] s=w.spawn(side,i);assertFalse(w.blocked(s[0],s[1],.48));}
            for(double[] p:w.posts)assertFalse(w.blocked(p[0],p[1],1));
            assertTrue(w.ray(0,w.height(0,0)+5,0,0,-1,0,20)<6);
        }
    }
    @Test public void saveRoundTripAndRecoveryNeverReplacesBrokenOriginals()throws Exception{
        Path file=temp.newFolder().toPath().resolve("save.json");BattlefrontProgress p=BattlefrontProgress.load(file);
        assertTrue(p.purchase(Upgrade.DAMAGE));assertTrue(p.adjust(Role.ASSAULT,-1));assertTrue(p.adjust(Role.HEAVY,1));p.award(200,100);p.save();
        BattlefrontProgress loaded=BattlefrontProgress.load(file);assertEquals(p.credits(),loaded.credits());assertEquals(1,loaded.level(Upgrade.DAMAGE));assertEquals(3,loaded.squad(Role.HEAVY));
        Files.write(file,"broken".getBytes(StandardCharsets.UTF_8));BattlefrontProgress recovered=BattlefrontProgress.load(file);assertEquals(p.credits(),recovered.credits());assertNotNull(recovered.error());assertTrue(recovered.save());
        Files.write(file,"broken".getBytes(StandardCharsets.UTF_8));Files.write(file.resolveSibling("save.json.bak"),"broken too".getBytes(StandardCharsets.UTF_8));
        BattlefrontProgress broken=BattlefrontProgress.load(file);assertFalse(broken.save());assertFalse(broken.purchase(Upgrade.DAMAGE));assertEquals("broken",new String(Files.readAllBytes(file),StandardCharsets.UTF_8));
    }
    @Test public void upgradeLimitsAndArmyCapacityCannotOverspend()throws Exception{
        BattlefrontProgress p=progress();assertFalse(p.adjust(Role.HEAVY,1));assertTrue(p.purchase(Upgrade.CAPACITY));assertEquals(14,p.capacity());assertTrue(p.adjust(Role.HEAVY,1));
        p.award(10000000,0);for(Upgrade u:Upgrade.values()){while(p.purchase(u)){}assertEquals(12,p.level(u));long credits=p.credits();assertFalse(p.purchase(u));assertEquals(credits,p.credits());}
        for(Role role:Role.values())while(p.adjust(role,-1)){}assertEquals(1,p.deployed());
    }
    @Test public void movementUsesFixedStepsJumpAndWorldBoundaries()throws Exception{
        BattlefrontGame a=game(),b=game();a.soldiers.clear();b.soldiers.clear();BattlefrontGame.Input input=new BattlefrontGame.Input();input.forward=true;
        double start=a.z;a.yaw=b.yaw=0;advance(a,1,input);for(int i=0;i<60;i++)b.advance(1.0/60,input);assertEquals(a.z,b.z,1e-8);assertEquals(start-6,a.z,.01);
        input.forward=false;input.jump=true;advance(a,.2,input);assertTrue(a.y>a.world.height(a.x,a.z)+.3);input.jump=false;advance(a,2,input);assertEquals(a.world.height(a.x,a.z),a.y,.001);
        a.x=BattlefrontWorld.LIMIT-2;a.z=0;a.y=a.world.height(a.x,a.z);a.yaw=90;input.forward=true;advance(a,10,input);assertTrue(a.x<BattlefrontWorld.LIMIT);
    }
    @Test public void blasterHitsOnlyInSightAndEliminationPaysOnce()throws Exception{
        BattlefrontGame g=game();g.soldiers.clear();g.x=0;g.z=90;g.y=g.world.height(0,90);g.yaw=0;
        BattlefrontGame.Soldier s=new BattlefrontGame.Soldier(1,Role.ASSAULT,0);s.x=0;s.z=83;s.y=g.world.height(0,83);s.health=s.maxHealth=100;g.soldiers.add(s);
        g.pitch=80;g.fire(true);assertEquals(100,s.health,0);g.pitch=Math.toDegrees(Math.atan2(g.eyeY()-(s.y+1.1),7));g.fireDelay=0;g.fire(true);assertTrue(s.health<100);
        long before=g.progress.credits();g.damage(s,1000,true);long after=g.progress.credits();assertTrue(after>before);g.damage(s,1000,true);assertEquals(after,g.progress.credits());assertEquals(1,g.kills);
        g.ammo=0;g.fireDelay=0;g.fire(true);assertTrue(g.reload>0);g.soldiers.clear();advance(g,2.1);assertEquals(g.magazine(),g.ammo);
    }
    @Test public void coverBlocksBallistics()throws Exception{
        BattlefrontGame g=game();BattlefrontWorld.Prop cover=null;for(BattlefrontWorld.Prop p:g.world.props)if(p.type.equals("crate")){cover=p;break;}
        assertNotNull(cover);double distance=g.world.ray(cover.x,cover.y+.7,cover.z+4,0,0,-1,9);assertTrue(distance<5);
        assertFalse(g.world.visible(cover.x,cover.y+.7,cover.z+4,cover.x,cover.y+.7,cover.z-4));
    }
    @Test public void commandPostsContestCaptureDrainTicketsAndRewardOnce()throws Exception{
        BattlefrontGame g=game();g.soldiers.clear();BattlefrontGame.Post p=g.posts[1];g.x=p.x;g.z=p.z;g.y=g.world.height(g.x,g.z);double before=g.tickets[1];
        advance(g,18);assertEquals(0,p.owner);assertEquals(1,g.captures);assertTrue(g.tickets[1]<before);long credits=g.progress.credits();advance(g,3);assertEquals(credits,g.progress.credits());
        BattlefrontGame enemy=new BattlefrontGame(progress(),Scenario.GEONOSIS,1,Mode.CONQUEST,1,0,42);enemy.soldiers.clear();enemy.x=enemy.posts[1].x;enemy.z=enemy.posts[1].z;advance(enemy,18);assertEquals(1,enemy.posts[1].owner);
    }
    @Test public void deathRespawnsAndAbilitiesRespectCooldowns()throws Exception{
        BattlefrontGame g=game();g.soldiers.clear();g.protection=0;g.hurt(30);g.heal();assertEquals(g.maxHealth(),g.health,0);assertTrue(g.healCooldown>0);g.hurt(20);g.heal();assertEquals(g.maxHealth()-20,g.health,0);
        g.grenade();assertEquals(16,g.grenadeCooldown,0);int particles=g.particles.size();g.grenade();assertEquals(particles,g.particles.size());
        g.hurt(10000);assertFalse(g.alive());advance(g,5.2);assertTrue(g.alive());assertTrue(g.protection>0);assertEquals(1,g.deaths);
    }
    @Test public void completedBattlesPayOnceAndUnlockTiers()throws Exception{
        BattlefrontProgress p=progress();BattlefrontGame g=new BattlefrontGame(p,Scenario.ENDOR,1,Mode.CONQUEST,1,0,1);g.finish(true);long credits=p.credits();g.finish(true);g.retreat();advance(g,10);assertEquals(credits,p.credits());assertEquals(1,p.wins());
        new BattlefrontGame(p,Scenario.ENDOR,1,Mode.SUPREMACY,1,0,2).finish(true);assertEquals(2,p.maxTier());assertEquals(2,p.wins(Faction.REBELS));
    }
    @Test public void instantRetreatCannotFarmCreditsOrExperience()throws Exception{
        BattlefrontProgress p=progress();long credits=p.credits();int xp=p.xp();
        for(int i=0;i<8;i++)new BattlefrontGame(p,Scenario.GEONOSIS,0,Mode.CONQUEST,1,0,i).retreat();
        assertEquals(credits,p.credits());assertEquals(xp,p.xp());assertEquals(8,p.battles());assertEquals(0,p.wins());
    }
    @Test public void aiCanFightAndEveryOperationTerminatesWithinSevenMinutes()throws Exception{
        for(Scenario scenario:Scenario.values())for(int side=0;side<2;side++)for(Mode mode:Mode.values()){
            BattlefrontGame g=new BattlefrontGame(progress(),scenario,side,mode,1,0,87);g.protection=1000;
            advance(g,425);assertTrue(scenario+" "+side+" "+mode,g.finished);assertTrue("AI failed to engage: "+scenario+" "+side+" "+mode,g.armyKills>0);
            assertTrue(g.bolts.size()<=180);assertTrue(g.particles.size()<=400);assertEquals(1,g.progress.battles());
        }
    }
    @Test public void breakthroughRequiresSequentialObjectivesForEitherSide()throws Exception{
        for(int side=0;side<2;side++){
            BattlefrontGame g=new BattlefrontGame(progress(),Scenario.ENDOR,side,Mode.BREAKTHROUGH,1,0,1);g.soldiers.clear();
            for(int i=0;i<3;i++){BattlefrontGame.Post p=g.posts[side==0?i:2-i];g.x=p.x;g.z=p.z;g.y=g.world.height(g.x,g.z);advance(g,29);assertEquals(side,p.owner);}
            assertTrue(g.finished);assertTrue(g.victory);
        }
    }
}
