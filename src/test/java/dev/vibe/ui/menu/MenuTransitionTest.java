package dev.vibe.ui.menu;

import org.junit.Test;
import static org.junit.Assert.*;

public class MenuTransitionTest {
    @Test public void resizeIsBoundedMonotonicAndFinishesIn220Milliseconds() {
        assertEquals(1,MenuTransitions.opacity(-1),0);
        assertEquals(1,MenuTransitions.opacity(0),0);
        assertEquals(.5F,MenuTransitions.opacity(110000000),.0001F);
        assertEquals(0,MenuTransitions.opacity(220000000),0);
        assertEquals(0,MenuTransitions.opacity(Long.MAX_VALUE),0);
        float previous=1;
        for(long time=0;time<=250000000;time+=1000000){float alpha=MenuTransitions.opacity(time);assertTrue(alpha>=0&&alpha<=previous);previous=alpha;}
    }
    @Test public void panelInterpolatesItsActualBoundsInBothDirections(){
        MenuTransitions.Rect small=new MenuTransitions.Rect(300,120,360,300),large=new MenuTransitions.Rect(24,24,912,492);
        MenuTransitions.Rect mid=small.mix(large,.5F);assertEquals(162,mid.x,0);assertEquals(72,mid.y,0);assertEquals(636,mid.width,0);assertEquals(396,mid.height,0);
        MenuTransitions.Rect reversed=mid.mix(small,0);assertEquals(mid.width,reversed.width,0);assertEquals(mid.x,reversed.x,0);
        assertEquals(small.width,mid.mix(small,1).width,0);
    }
    @Test public void incomingControlsWaitUntilThePanelHasFinishedGrowing(){
        for(long time=0;time<MenuTransitions.RESIZE_DURATION;time+=1000000){
            assertEquals(0,MenuTransitions.contentProgress(time),0);
            assertTrue(MenuTransitions.resizeProgress(time)<1);
        }
        assertEquals(1,MenuTransitions.resizeProgress(160000000),0);
        assertEquals(0,MenuTransitions.contentProgress(160000000),0);
        assertEquals(.5F,MenuTransitions.contentProgress(190000000),.0001F);
        assertEquals(1,MenuTransitions.contentProgress(220000000),0);
    }
}
