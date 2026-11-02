package dev.vibe.aiaura;

import dev.vibe.path.PathTestFixture;
import org.junit.*;
import static org.junit.Assert.*;

/** Compare practice controls with the native client input path. */
public class RecorderControlsTest {
    @Rule public org.junit.rules.TemporaryFolder temporary=new org.junit.rules.TemporaryFolder();
    @Test public void mouseCountsMatchNativeSetAnglesIncludingPitchClamp()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            for(float sensitivity:new float[]{0,.1F,.5F,.9F,1}){
                RecorderArena arena=new RecorderArena(new AuraLearning(temporary.newFolder().toPath()),1);arena.begin(false);arena.sensitivity=sensitivity;
                f.player.rotationYaw=f.player.prevRotationYaw=f.player.rotationPitch=f.player.prevRotationPitch=0;
                for(int[] counts:new int[][]{{4,7},{-15,-3},{26,11},{0,900},{3,-1000}}){
                    arena.look(counts[0],counts[1]);f.player.setAngles(counts[0]*MouseMath.gain(sensitivity),-counts[1]*MouseMath.gain(sensitivity));
                    assertEquals(f.player.rotationYaw,arena.yaw,0);assertEquals(f.player.rotationPitch,arena.pitch,0);
                    assertEquals(arena.yaw,arena.cameraYaw(.1F),0);assertEquals(arena.pitch,arena.cameraPitch(.1F),0);
                }
                arena.finish();
            }
        }
    }
    @Test public void attacksUseTheDisplayedEyeAndEnemyPositionAtThreeBlockReach()throws Exception{
        RecorderArena arena=new RecorderArena(new AuraLearning(temporary.newFolder().toPath()),2);arena.begin(false);
        arena.x=2;arena.previousX=0;arena.renderPartial=0;RecorderArena.Enemy enemy=enemy(2);arena.enemies.add(enemy);
        arena.attackDown();arena.releaseAttack();assertEquals(1,arena.hits);assertEquals(19,enemy.health,0);arena.finish();
        arena=new RecorderArena(new AuraLearning(temporary.newFolder().toPath()),3);arena.begin(false);enemy=enemy(3.5);arena.enemies.add(enemy);
        arena.attackDown();arena.releaseAttack();assertEquals(0,arena.hits);assertEquals(1,arena.misses);assertEquals(20,enemy.health,0);
        enemy.z=enemy.previousZ=2;arena.attackDown();assertEquals("Native air-miss delay must still apply",0,arena.hits);arena.finish();
    }
    private static RecorderArena.Enemy enemy(double z){RecorderArena.Enemy e=new RecorderArena.Enemy();e.id=1;e.z=e.previousZ=z;return e;}
}
