package dev.vibe.camera;

import org.junit.Test;
import static org.junit.Assert.*;

public class PerspectiveTransitionTest {
    @Test public void enablingDoesNotMoveTheExistingPerspectiveAndEveryEaseEndsExactly(){
        for(String ease:new String[]{"Linear","Sine","Cubic","Quintic"})for(int view=0;view<3;view++){
            PerspectiveTransition t=new PerspectiveTransition();assertFalse(t.update(view,0,4,ease,"Top"));assertEquals(view==0?0:4,t.distance(),0);
            t.update((view+1)%3,0,4,ease,"Top");assertTrue(t.active(1));assertFalse(t.update((view+1)%3,250000000,4,ease,"Top"));assertEquals((view+1)%3==0?0:4,t.distance(),0);
        }
    }
    @Test public void topPassesAboveTheHeadAndSideRoutesUseOppositeSides(){
        double[][] frames=new double[3][];String[] modes={"Top","Left","Right"};
        for(int i=0;i<3;i++){PerspectiveTransition t=new PerspectiveTransition();t.reset(1);t.update(2,0,2,"Linear",modes[i]);t.update(2,250000000,2,"Linear",modes[i]);frames[i]=t.frame(0,0);}
        assertEquals(4,frames[0][1],1e-6);assertEquals(-4,frames[1][0],1e-6);assertEquals(4,frames[2][0],1e-6);
    }
    @Test public void retargetingDuringAnimationKeepsPositionAndOrientationContinuous(){
        PerspectiveTransition t=new PerspectiveTransition();t.reset(1);t.update(2,0,2,"Cubic","Top");t.update(2,200000000,2,"Cubic","Top");double[] before=t.frame(32,-12);
        t.update(0,200000000,2,"Cubic","Top");assertArrayEquals(before,t.frame(32,-12),1e-9);
        t.update(0,700000000,2,"Cubic","Top");assertEquals(0,t.distance(),0);assertEquals(0,t.angle(),0);
    }
    @Test public void cameraBasisStaysFiniteUnitLengthAndOrthogonalAcrossThePole(){
        for(String direction:new String[]{"Top","Left","Right"}){
            PerspectiveTransition t=new PerspectiveTransition();t.reset(1);t.update(2,0,1,"Sine",direction);
            for(int frame=0;frame<=100;frame++){t.update(2,frame*10000000L,1,"Sine",direction);double[] f=t.frame(125,34);double look=0,up=0,dot=0;
                for(int i=0;i<3;i++){assertTrue(Double.isFinite(f[i]));look+=f[3+i]*f[3+i];up+=f[6+i]*f[6+i];dot+=f[3+i]*f[6+i];}
                assertEquals(1,look,1e-8);assertEquals(1,up,1e-8);assertEquals(0,dot,1e-8);
            }
        }
    }
    @Test public void fasterSpeedFinishesEarlierAndRepeatedCyclesReturnToVanillaAngles(){
        PerspectiveTransition slow=new PerspectiveTransition(),fast=new PerspectiveTransition();slow.reset(0);fast.reset(0);slow.update(1,0,1,"Sine","Right");fast.update(1,0,8,"Sine","Right");
        assertTrue(slow.update(1,200000000,1,"Sine","Right"));assertFalse(fast.update(1,200000000,8,"Sine","Right"));
        for(int step=0;step<15;step++){int view=(step+2)%3;fast.update(view,step*1000000000L+1000000000L,8,"Sine","Right");fast.update(view,step*1000000000L+1200000000L,8,"Sine","Right");assertEquals(view==2?Math.PI:0,fast.angle(),1e-9);}
    }
}
