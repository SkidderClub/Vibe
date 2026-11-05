package dev.vibe.ambience;

import dev.vibe.module.impl.visual.AmbienceModule;
import java.util.*;
import net.minecraft.util.AxisAlignedBB;
import org.junit.Test;
import static org.junit.Assert.*;

public class AmbienceTest {
    private static final FireflySwarm.Space AIR=new FireflySwarm.Space(){
        public boolean available(double x,double y,double z){return true;}
        public List<AxisAlignedBB> collisions(AxisAlignedBB box){return Collections.emptyList();}
    };
    @Test public void effectSelectionRevealsOnlyItsControls(){
        AmbienceModule m=new AmbienceModule();
        m.effects.setValue(Collections.singleton(AmbienceModule.FOG));
        assertTrue(m.fog.start.isVisible());assertFalse(m.getTimeMode().isVisible());assertFalse(m.getWeatherMode().isVisible());assertFalse(m.fireflies.count.isVisible());
        m.effects.setValue(Collections.singleton(AmbienceModule.FIREFLIES));
        assertFalse(m.fog.start.isVisible());assertTrue(m.fireflies.count.isVisible());assertFalse(m.sun.color.isVisible());
        m.effects.setValue(new LinkedHashSet<>(m.effects.getOptions()));
        assertTrue(m.sun.color.isVisible());assertTrue(m.moon.phase.isVisible());assertTrue(m.aurora.lower.isVisible());assertTrue(m.stars.interval.isVisible());
    }
    @Test public void sweptCollisionStopsFastFliesAtWallsAndFloors(){
        final AxisAlignedBB wall=new AxisAlignedBB(0,-10,-10,1,10,10),floor=new AxisAlignedBB(-10,-1,-10,10,0,10);
        FireflySwarm.Space space=new FireflySwarm.Space(){
            public boolean available(double x,double y,double z){return true;}
            public List<AxisAlignedBB> collisions(AxisAlignedBB box){List<AxisAlignedBB> out=new ArrayList<>();for(AxisAlignedBB b:Arrays.asList(wall,floor))if(b.intersectsWith(box))out.add(b);return out;}
        };
        FireflySwarm.Fly fly=new FireflySwarm.Fly(-.5,.5,0,0,800);fly.vx=2;fly.vy=-2;fly.move(space);
        assertEquals(-.07,fly.x,.00001);assertEquals(.07,fly.y,.00001);
        assertTrue(space.collisions(fly.box()).isEmpty());assertTrue(fly.vx<0);assertTrue(fly.vy>0);
    }
    @Test public void swarmAndHistoryStayBoundedAndUnloadedSpaceNeverSpawns(){
        AmbienceModule module=new AmbienceModule();module.fireflies.count.setValue(16D);
        FireflySwarm swarm=new FireflySwarm();
        for(int i=0;i<2000;i++){
            swarm.tick(0,3,0,module.fireflies,AIR);assertTrue(swarm.flies().size()<=16);
            for(FireflySwarm.Fly fly:swarm.flies())assertTrue(fly.samples<=FireflySwarm.HISTORY&&Double.isFinite(fly.x)&&Double.isFinite(fly.y)&&Double.isFinite(fly.z));
        }
        assertFalse(swarm.flies().isEmpty());swarm.clear();assertTrue(swarm.flies().isEmpty());
        swarm.tick(0,0,0,module.fireflies,new FireflySwarm.Space(){
            public boolean available(double x,double y,double z){return false;}
            public List<AxisAlignedBB> collisions(AxisAlignedBB box){throw new AssertionError("Unloaded space queried collisions");}
        });assertTrue(swarm.flies().isEmpty());
    }
    @Test public void shootingStarsFollowContinuousSkyArcsAndFadeCompletely(){
        for(int i=0;i<20;i++){
            AmbienceScene.Meteor star=AmbienceScene.Meteor.create(5,new Random(i),.3+i*.1);
            assertEquals(0,star.visibility(5),0);assertEquals(0,star.visibility(5+star.duration+.01),0);
            assertTrue(star.visibility(5+star.duration*.5)>.99);
            for(int t=0;t<20;t++)assertEquals(1,star.position(5+star.duration*t/20).lengthVector(),.00001);
        }
    }
}
