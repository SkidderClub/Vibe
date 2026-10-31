package dev.vibe.aiaura;

import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AuraHeightTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static TrainedAura flatModel(){TrainedAura model=AuraFixtures.policy();AuraFixtures.bias(model,0,0);AuraFixtures.bias(model,1,0);model.minRotationDelay=model.maxRotationDelay=0;for(double[] cap:model.maxCounts)cap[1]=0;return model;}
    @Test public void flatLegacyModelConvergesOnTargetsAboveAndBelow(){
        for(double targetY:new double[]{-4,-2,1,3,5}){
            TrainedAura model=flatModel();AuraBrain brain=new AuraBrain(new Random(1));AuraFeatures.Observation o=AuraFixtures.observation();o.targetX=0;o.targetZ=5;o.targetY=targetY;
            double start=Math.abs(AuraFeatures.angles(0,o.eyeY,0,0,targetY+.9,5)[1]);for(int i=0;i<80;i++){int[] counts=brain.advance(model,o,i*50L,1,1);o.pitch=MouseMath.pitch((float)o.pitch,counts[1],o.sensitivity);}
            double goal=AuraFeatures.angles(0,o.eyeY,0,0,targetY+brain.memory.pointY*1.8,5)[1];assertTrue("Pitch failed at height "+targetY+": "+o.pitch+" vs "+goal,Math.abs(goal-o.pitch)<.5);assertTrue(start>2);
        }
    }
    @Test public void verticalTrackingHandlesTargetAndPlayerJumpMotion(){
        TrainedAura model=flatModel();for(double[] cap:model.maxCounts)cap[0]=40;AuraBrain brain=new AuraBrain(new Random(3));AuraFeatures.Observation o=AuraFixtures.observation();o.targetX=0;o.targetZ=4;double total=0,max=0;
        for(int i=0;i<180;i++){double oldY=o.targetY,oldEye=o.eyeY;o.targetY=1.5+Math.sin(i*.09)*1.1;o.eyeY=1.62+Math.sin(i*.075)*.55;o.targetVy=o.targetY-oldY;o.vy=o.eyeY-oldEye;int[] counts=brain.advance(model,o,i*50L,1,1);o.pitch=MouseMath.pitch((float)o.pitch,counts[1],o.sensitivity);double goal=AuraFeatures.angles(0,o.eyeY,0,0,o.targetY+brain.memory.pointY*1.8,4)[1];if(i>40){double error=Math.abs(goal-o.pitch);total+=error;max=Math.max(max,error);}}
        assertTrue("Mean moving-height error "+total/139,total/139<4);assertTrue("Peak moving-height error "+max,max<8);
    }
    @Test public void pitchTrackingTransfersAcrossNativeSensitivities(){
        for(float sensitivity:new float[]{.1F,.5F,1F}){AuraBrain brain=new AuraBrain();AuraFeatures.Observation o=AuraFixtures.observation();o.targetX=0;o.targetZ=5;o.targetY=3;o.sensitivity=sensitivity;TrainedAura model=flatModel();for(int i=0;i<80;i++){int[] counts=brain.advance(model,o,i*50L,1,1);o.pitch=MouseMath.pitch((float)o.pitch,counts[1],sensitivity);}double goal=AuraFeatures.angles(0,o.eyeY,0,0,o.targetY+brain.memory.pointY*1.8,5)[1];assertTrue(Math.abs(goal-o.pitch)<.8);}
    }
    @Test public void steepViewsUseCameraSpaceInsteadOfIndependentYawAngles(){
        AuraFeatures.Observation o=AuraFixtures.observation();o.targetX=2;o.targetZ=0;o.targetY=6;o.pitch=-80;assertTrue(AuraFeatures.inView(o,70,16/9D));o.targetY=-10;assertFalse(AuraFeatures.inView(o,70,16/9D));o.pitch=0;o.targetY=10;o.targetX=0;o.targetZ=3;assertFalse(AuraFeatures.inView(o,70,16/9D));
    }
    @Test public void pitchPredictionCannotDriveAwayFromTheTarget(){
        TrainedAura model=flatModel();AuraFixtures.bias(model,1,1);AuraBrain brain=new AuraBrain();AuraFeatures.Observation o=AuraFixtures.observation();o.targetX=0;o.targetY=3;o.targetZ=5;
        for(int i=0;i<90;i++){int[] counts=brain.advance(model,o,i*50L,1,1);o.pitch=MouseMath.pitch((float)o.pitch,counts[1],o.sensitivity);}assertTrue(o.pitch< -15);assertTrue(o.pitch> -60);
    }
    @Test public void sparseVerticalActionsRemainInTrainingSubsample(){
        AuraDataset data=AuraFixtures.demonstrations();for(AuraDataset.Frame frame:data.frames){frame.y[1]=0;frame.x[10]=-.324F;}data.frames.get(17).y[1]=.2F;data.frames.get(53).x[10]=.3F;
        int[] sample=AuraNetwork.trainingIndices(data,792,100);boolean pitch=false,height=false;for(int index:sample){pitch|=index==17;height|=index==53;}assertTrue(pitch);assertTrue(height);
        AuraPitchStyle style=AuraPitchStyle.learn(data);assertTrue(style.valid());assertTrue(style.maximumCounts>0);assertEquals(1,style.verticalSamples);
    }
    @Test public void platformsAreConnectedByJumpableRoutes(){
        List<RecorderArena.Pillar> map=ArenaNavigation.trainingMap();List<net.minecraft.util.Vec3> path=ArenaNavigation.route(0,2,8,2,map);assertFalse(path.isEmpty());assertEquals(3,path.get(path.size()-1).yCoord,0);
        boolean jumps=false;for(int i=1;i<path.size();i++){double rise=path.get(i).yCoord-path.get(i-1).yCoord;assertTrue(rise<=1.01);jumps|=rise>.6;}assertTrue(jumps);
        for(RecorderArena.Pillar platform:map)assertFalse("Unreachable platform at "+platform.x+", "+platform.z,ArenaNavigation.route(0,0,platform.x,platform.z,map).isEmpty());
    }
    @Test public void opponentsActuallyClimbToThePlayer()throws Exception{
        AuraLearning learning=new AuraLearning(temp.newFolder().toPath());RecorderArena arena=new RecorderArena(learning,4);arena.begin(false);arena.x=arena.previousX=8;arena.z=arena.previousZ=2;arena.y=arena.previousY=3;
        RecorderArena.Enemy enemy=new RecorderArena.Enemy();enemy.id=1;enemy.x=2;enemy.z=2;enemy.previousX=2;enemy.previousZ=2;enemy.yaw=-90;arena.enemies.add(enemy);double peak=0;
        for(int i=0;i<240;i++){arena.tick(0,1,1);peak=Math.max(peak,enemy.y);}arena.finish();assertTrue("Opponent stayed below the platform: "+peak,peak>=2.9);assertTrue(arena.receivedHits>0);
    }
    @Test public void playerNeverDiesOrTeleportsWhenTakingDamage()throws Exception{
        AuraLearning learning=new AuraLearning(temp.newFolder().toPath());RecorderArena arena=new RecorderArena(learning,4);arena.begin(false);arena.x=arena.previousX=10;arena.z=arena.previousZ=-10;
        RecorderArena.Enemy e=new RecorderArena.Enemy();e.id=1;arena.enemies.add(e);
        for(int i=0;i<600;i++){e.x=arena.x;e.y=arena.y;e.z=arena.z+1;e.vx=e.vy=e.vz=0;e.yaw=180;e.nextAttack=0;arena.vx=arena.vy=arena.vz=0;arena.playerImmuneTicks=0;arena.tick(0,1,1);assertEquals(20,arena.playerHealth,0);assertTrue(arena.x>9);assertTrue(arena.z< -9);}arena.finish();assertTrue(arena.damageTaken>=20);
    }
    @Test public void armUsesSixTickSwingAndPerspectiveDoesNotAlterAim()throws Exception{
        RecorderArena arena=new RecorderArena(new AuraLearning(temp.newFolder().toPath()),2);arena.begin(false);arena.yaw=32;arena.pitch=14;arena.cyclePerspective();arena.cyclePerspective();assertEquals(2,arena.perspective);assertEquals(32,arena.yaw,0);assertEquals(14,arena.pitch,0);arena.cyclePerspective();assertEquals(0,arena.perspective);
        arena.attackDown();arena.releaseAttack();boolean moved=false;for(int i=0;i<8;i++){arena.tick(0,1,1);moved|=arena.swing>0;}assertTrue(moved);assertEquals(0,arena.swing,0);arena.finish();
    }
}
