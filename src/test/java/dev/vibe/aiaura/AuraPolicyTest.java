package dev.vibe.aiaura;

import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AuraPolicyTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void noModelDoesNotProduceAnyInput(){AuraBrain brain=new AuraBrain();assertArrayEquals(new int[]{0,0},brain.advance(null,AuraFixtures.observation(),100,1,1));assertFalse(brain.due(Long.MAX_VALUE));assertEquals(0,brain.poll(100));}
    @Test public void learnedReactionGatesAimAndClickSeparately(){
        AuraBrain brain=new AuraBrain(new Random(1));TrainedAura model=AuraFixtures.policy();assertArrayEquals(new int[]{0,0},brain.advance(model,AuraFixtures.observation(),0,1,1));assertFalse(brain.due(199));int[] motion=brain.advance(model,AuraFixtures.observation(),150,1,1);assertEquals(5,motion[0]);assertTrue(motion[1]>0&&motion[1]<=16);assertTrue(brain.due(200));assertEquals(1,brain.poll(200));assertFalse(brain.due(299));assertEquals(1,brain.poll(300));
    }
    @Test public void boostersScaleCountsAndClickRateWithoutFractionalMouseCounts(){
        for(double boost:new double[]{.8,1,4}){AuraBrain brain=new AuraBrain(new Random(1));TrainedAura model=AuraFixtures.policy();brain.advance(model,AuraFixtures.observation(),0,boost,boost);int[] counts=brain.advance(model,AuraFixtures.observation(),150,boost,boost);assertEquals(Math.round(8.96*boost*(1-Math.exp(-50/65D))),counts[0]);brain.poll(200);assertFalse(brain.due(200+Math.round(100/boost)-1));assertTrue(brain.due(200+Math.round(100/boost)));}
    }
    @Test public void sensitivityTransferUsesExactCurrentGameCounts(){
        AuraBrain brain=new AuraBrain(new Random(3));TrainedAura model=AuraFixtures.policy();AuraFeatures.Observation o=AuraFixtures.observation();o.sensitivity=.8F;brain.advance(model,o,0,1,1);int[] counts=brain.advance(model,o,150,1,1);assertEquals(Math.round(8.96*model.minStep/MouseMath.step(.8F)*(1-Math.exp(-50/65D))),counts[0]);assertEquals(MouseMath.angle(0,counts[0],.8F),MouseMath.angle(0,counts[0],o.sensitivity),0);
    }
    @Test public void targetSwitchRestartsReactionAndStallsDoNotReplayOldBacklog(){
        AuraBrain brain=new AuraBrain(new Random(1));TrainedAura model=AuraFixtures.policy();AuraFeatures.Observation o=AuraFixtures.observation();brain.advance(model,o,0,1,4);brain.advance(model,o,200,1,4);assertEquals(1,brain.poll(10000));o.targetId++;assertArrayEquals(new int[]{0,0},brain.advance(model,o,10050,1,4));assertFalse(brain.due(10249));o.visible=false;brain.advance(model,o,10300,1,4);assertFalse(brain.due(999999));
    }
    @Test public void arenaUsesManualDataAndPreviewNeverAppends()throws Exception{
        AuraLearning learning=new AuraLearning(temp.newFolder().toPath());RecorderArena arena=new RecorderArena(learning,3);arena.begin(false);for(int i=0;i<30;i++){arena.look(1,0);arena.tick(RecorderArena.FORWARD|RecorderArena.JUMP,1,1);}arena.finish();assertEquals(31,learning.frames());assertTrue(arena.wave>=1);assertTrue(arena.z>0);assertTrue(arena.y>=0);assertFalse(arena.enemies.isEmpty());
        int before=learning.frames();arena.begin(true);for(int i=0;i<100;i++)arena.tick(RecorderArena.FORWARD,1,1);arena.finish();assertEquals(before,learning.frames());
    }
    @Test public void arenaRecordsBeingHitAndItsMovementContext()throws Exception{
        AuraLearning learning=new AuraLearning(temp.newFolder().toPath());RecorderArena a=new RecorderArena(learning,3);a.begin(false);RecorderArena.Enemy e=new RecorderArena.Enemy();e.id=1;e.z=1;e.yaw=180;a.enemies.add(e);a.tick(0,1,1);assertEquals(20,a.playerHealth,0);assertEquals(10,a.playerHurtTicks);assertEquals(10,a.observation(e).selfHurt,0);assertTrue(a.vz<0);assertTrue(a.vy>0);a.tick(0,1,1);a.finish();assertTrue(learning.snapshot().frames.get(1).x[33]>0);
    }
    @Test public void longRecordedAttentionDelaysRemainRepresentable(){
        TrainedAura model=AuraFixtures.policy();model.minRotationDelay=model.maxRotationDelay=12000;model.minClickDelay=model.maxClickDelay=15000;assertTrue(model.valid());AuraBrain brain=new AuraBrain();brain.advance(model,AuraFixtures.observation(),0,1,1);assertArrayEquals(new int[]{0,0},brain.advance(model,AuraFixtures.observation(),11000,1,1));assertFalse(brain.due(14999));assertTrue(brain.due(15000));
    }
    @Test public void noTargetOrHiddenTargetStopsWithoutResidualMovement(){
        TrainedAura m=AuraFixtures.policy();for(double[] sigma:m.sigma)Arrays.fill(sigma,1000);AuraBrain b=new AuraBrain(new Random(3));AuraFeatures.Observation o=AuraFixtures.observation();b.advance(m,o,0,1,1);b.advance(m,o,200,1,1);o.targetId=-1;for(int i=0;i<100;i++){assertArrayEquals(new int[2],b.advance(m,o,250+i*50,1,1));assertFalse(b.due(999999));}o.targetId=5;o.visible=false;assertArrayEquals(new int[2],b.advance(m,o,10000,1,1));
    }
    @Test public void angularNoiseIsNotInjectedAndVelocityChangesAreContinuous(){
        TrainedAura m=AuraFixtures.policy();for(double[] sigma:m.sigma)Arrays.fill(sigma,1000);AuraBrain a=new AuraBrain(new Random(1)),b=new AuraBrain(new Random(82));AuraFeatures.Observation o=AuraFixtures.observation();a.advance(m,o,0,1,1);b.advance(m,AuraFixtures.observation(),0,1,1);for(int i=150;i<1000;i+=50)assertArrayEquals(a.advance(m,o,i,1,1),b.advance(m,AuraFixtures.observation(),i,1,1));
        AuraAimFilter filter=new AuraAimFilter();double prior=0;for(int i=0;i<30;i++){double current=filter.advance(i%2==0?30:-30,0,50,30,30)[0];assertTrue(Math.abs(current-prior)<=13.50001);prior=current;}
    }
    @Test public void statisticsUseOnlyPersistedManualRows(){
        AuraDataset d=AuraFixtures.demonstrations();AuraStatistics s=AuraStatistics.from(d);assertEquals(1000,s.frames);assertEquals(2,s.sessions);assertEquals(50,s.seconds,0);assertEquals(10,s.cps,1e-5);assertEquals(150,s.aimReactionMs,1e-3);assertEquals(200,s.clickReactionMs,1e-3);assertEquals(1000,s.aimpoints);
    }
    @Test public void attacksRespectTheTenTickDamageWindow()throws Exception{
        AuraLearning learning=new AuraLearning(temp.newFolder().toPath());RecorderArena a=new RecorderArena(learning,7);a.begin(false);RecorderArena.Enemy e=new RecorderArena.Enemy();e.id=1;e.z=2;a.enemies.add(e);
        a.attackDown();a.releaseAttack();a.attackDown();a.releaseAttack();assertEquals(19,e.health,0);assertEquals(2,a.hits);assertEquals(1,a.damageHits);assertEquals(20,e.immuneTicks);assertEquals(.4,e.vy,1e-6);assertTrue(e.vz>0);
        e.immuneTicks=10;e.x=0;e.z=2;a.attackDown();a.releaseAttack();assertEquals(18,e.health,0);assertEquals(2,a.damageHits);a.finish();
    }
    @Test public void stationaryAlignedAimDoesNotWiggleEvenWithBadResidualStatistics(){
        TrainedAura m=AuraFixtures.policy();for(double[] sigma:m.sigma)Arrays.fill(sigma,1000);AuraBrain b=new AuraBrain();AuraFeatures.Observation o=AuraFixtures.observation();o.targetX=0;o.eyeY=1.08;AuraFixtures.bias(m,7,(float)Math.log(.6/.4));b.advance(m,o,0,1,1);for(int i=150;i<1000;i+=50)assertArrayEquals(new int[2],b.advance(m,o,i,1,1));
    }
    @Test public void arenaHonorsNativeBorderAndDoesNotHitThroughPillars()throws Exception{
        AuraLearning learning=new AuraLearning(temp.newFolder().toPath());RecorderArena a=new RecorderArena(learning,3);a.begin(false);RecorderArena.Enemy e=new RecorderArena.Enemy();e.id=1;e.z=3.39;a.enemies.add(e);a.attackDown();a.releaseAttack();assertEquals(1,a.hits);assertEquals(19,e.health,0);a.finish();
        a.begin(false);e=new RecorderArena.Enemy();e.id=2;e.x=-12;e.z=10;a.enemies.add(e);a.yaw=(float)Math.toDegrees(Math.atan2(12,10));a.reach=20;a.attackDown();a.releaseAttack();assertEquals(0,a.hits);assertEquals(1,a.misses);a.finish();
        assertNull(RecorderArena.intersect(0,1,0,0,1,3,1,0,1,2,2,2));assertNotNull(RecorderArena.intersect(0,1,0,0,1,3,-1,0,1,1,2,2));
    }
}
