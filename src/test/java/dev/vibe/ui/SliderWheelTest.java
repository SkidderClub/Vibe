package dev.vibe.ui;

import dev.vibe.setting.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SliderWheelTest {
    @Test public void scrollbarThumbDragsDoNotJumpAndCanReachBothEnds() {
        int thumb=ScrollbarDrag.thumb(100,300,12);assertEquals(25,thumb);
        ScrollbarDrag drag=new ScrollbarDrag(10,110,120,300,12,45);
        assertEquals(120,drag.at(45));assertEquals(0,drag.at(-100));assertEquals(300,drag.at(1000));
        ScrollbarDrag trackClick=new ScrollbarDrag(10,110,0,300,12,90);
        assertTrue(trackClick.at(90)>200);
    }
    @Test public void wheelUsesDeclaredStepsAndClampsWithoutCrossingRangeHandles() {
        NumberSetting n=new NumberSetting("Value",.5,0,1,.05);
        assertTrue(SliderWheel.adjust(n,SliderWheel.notches(120),0));assertEquals(.55,n.getDouble(),.00001);
        SliderWheel.adjust(n,30,0);assertEquals(1,n.getDouble(),0);
        SliderWheel.adjust(n,-60,0);assertEquals(0,n.getDouble(),0);
        RangeSetting r=new RangeSetting("Range",4,8,0,10,1);
        assertEquals(0,SliderWheel.nearestHandle(r,.4));assertEquals(1,SliderWheel.nearestHandle(r,.8));
        SliderWheel.adjust(r,20,0);assertEquals(8,r.getMin(),0);assertEquals(8,r.getMax(),0);
        SliderWheel.adjust(r,-20,1);assertEquals(8,r.getMin(),0);assertEquals(8,r.getMax(),0);
        assertFalse(SliderWheel.adjust(n,0,0));assertFalse(SliderWheel.adjust(n,Double.NaN,0));
    }
}
