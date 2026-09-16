package dev.vibe.game.battlefront;

import dev.vibe.ui.BattlefrontIcons;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static dev.vibe.game.battlefront.BattlefrontContent.*;

public class BattlefrontFrontlineTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private BattlefrontGame game(Scenario scenario,int side)throws Exception{Path file=temp.newFolder().toPath().resolve("campaign.json");return new BattlefrontGame(BattlefrontProgress.load(file),scenario,side,Mode.CONQUEST,1,0,42);}
    @Test public void objectivesSpanBothAxesAndEveryBaseCanReachEveryPost()throws Exception{
        for(Scenario scenario:Scenario.values()){
            BattlefrontWorld w=new BattlefrontWorld(scenario);BattlefrontNavigation nav=new BattlefrontNavigation(w);
            assertTrue(Math.hypot(w.bases[0][0]-w.bases[1][0],w.bases[0][1]-w.bases[1][1])>380);
            double minX=999,minZ=999,maxX=-999,maxZ=-999;
            for(int i=0;i<3;i++){double[] p=w.posts[i];minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minZ=Math.min(minZ,p[1]);maxZ=Math.max(maxZ,p[1]);
                for(int j=i+1;j<3;j++)assertTrue(Math.hypot(p[0]-w.posts[j][0],p[1]-w.posts[j][1])>210);
                for(int a=0;a<16;a++){double angle=a*Math.PI/8;assertFalse("Blocked capture ring in "+scenario,w.blocked(p[0]+Math.sin(angle)*7,p[1]+Math.cos(angle)*7,.4));}
            }
            assertTrue(maxX-minX>200);assertTrue(maxZ-minZ>240);
            for(int side=0;side<2;side++){
                for(int index=0;index<36;index++){double[] spawn=w.spawn(side,index);assertFalse(w.blocked(spawn[0],spawn[1],.6));assertTrue(Math.abs(spawn[1])>175);}
                double[] start=w.spawn(side,3);for(double[] p:w.posts)assertFalse("No base route in "+scenario,nav.route(start[0],start[1],p[0],p[1]).isEmpty());
            }
        }
    }
    @Test public void aiUsesBothTheCentralAndOuterAttackRoutes()throws Exception{
        for(Scenario scenario:Scenario.values()){
            BattlefrontGame g=game(scenario,0);g.protection=1000;boolean[] reached=new boolean[3];
            for(int tick=0;tick<150*30&&!g.finished;tick++){
                g.advance(1.0/30,new BattlefrontGame.Input());
                for(BattlefrontGame.Soldier s:g.soldiers)if(s.guardLocation==null&&s.alive())for(int i=0;i<3;i++)if(Math.hypot(s.x-g.posts[i].x,s.z-g.posts[i].z)<12)reached[i]=true;
            }
            for(int i=0;i<3;i++)assertTrue("Unused objective "+i+" in "+scenario,reached[i]);assertTrue("No fighting in "+scenario,g.armyKills>0);
        }
    }
    @Test public void hitFeedbackDistinguishesBodyHeadKillAndMiss()throws Exception{
        BattlefrontGame g=game(Scenario.GEONOSIS,0);g.soldiers.clear();g.x=0;g.z=0;g.y=50;g.yaw=0;g.pitch=0;
        for(int state=0;state<3;state++){
            g.soldiers.clear();g.hitMarker=0;g.fireDelay=0;BattlefrontGame.Camera camera=g.camera(true);
            BattlefrontGame.Soldier target=new BattlefrontGame.Soldier(1,Role.ASSAULT,0);target.x=camera.x;target.z=camera.z-12;target.y=camera.y-(state==1?1.95:1.1);target.health=target.maxHealth=state==2?1:500;g.soldiers.add(target);
            g.fire(true);assertTrue(g.hitMarker>0);assertEquals(state==1,g.lastHitHead);assertEquals(state==2,g.lastHitKill);
            BattlefrontGame.DamageNumber number=g.damageNumbers.get(g.damageNumbers.size()-1);assertEquals(state==1,number.head);assertEquals(state==2,number.kill);
            if(state==1)assertEquals(500-g.progress.weaponDamage()*1.65,target.health,.001);
        }
        // A body hit from the next rapid shot must not inherit the previous kill marker.
        BattlefrontGame.Soldier next=g.soldiers.get(0);next.health=500;g.fireDelay=0;g.fire(true);assertTrue(g.hitMarker>0);assertFalse(g.lastHitKill);assertFalse(g.lastHitHead);
        g.soldiers.clear();for(int i=0;i<15;i++)g.advance(1.0/30,new BattlefrontGame.Input());assertEquals(0,g.hitMarker,0);g.fireDelay=0;g.fire(true);assertEquals(0,g.hitMarker,0);
    }
    @Test public void bundledSvgSymbolsProduceDistinctAntialiasedIcons(){
        String[] names={"operations","army","arsenal","record","manual","planet","forest","shield","tactics","appearance","weapon","helmet","upgrade","deploy","credits","arrow","exit","check","plus","minus","support","scout","pack","settings"};Set<Integer> signatures=new HashSet<Integer>();
        for(String name:names){BufferedImage image=BattlefrontIcons.rasterize(name);int covered=0,soft=0;int[] pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());for(int pixel:pixels){int alpha=pixel>>>24;if(alpha>0)covered++;if(alpha>0&&alpha<255)soft++;}assertTrue(name,covered>200);assertTrue(name,soft>50);assertTrue("Duplicate SVG icon: "+name,signatures.add(Arrays.hashCode(pixels)));}
    }
}
