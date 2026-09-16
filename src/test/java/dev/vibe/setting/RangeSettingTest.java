package dev.vibe.setting;

import org.junit.Test;
import static org.junit.Assert.*;

public class RangeSettingTest {
    @Test public void draggingKeepsTheEndpointAndPushesAcrossTheOther(){
        RangeSetting s=new RangeSetting("Range",20,60,0,100,1);
        RangeSetting.Drag lower=s.beginDrag(10);lower.move(50);lower.move(80);
        assertEquals(80,s.getMin(),0);assertEquals(80,s.getMax(),0);
        lower.move(30);assertEquals(30,s.getMin(),0);assertEquals(80,s.getMax(),0);
        RangeSetting.Drag upper=s.beginDrag(95);upper.move(10);
        assertEquals(10,s.getMin(),0);assertEquals(10,s.getMax(),0);
        upper.move(90);assertEquals(10,s.getMin(),0);assertEquals(90,s.getMax(),0);
    }
    @Test public void overlappedHandlesSeparateInEitherDirection(){
        RangeSetting s=new RangeSetting("Range",40,40,0,100,1);
        RangeSetting.Drag right=s.beginDrag(40);right.move(40);right.move(70);
        assertEquals(40,s.getMin(),0);assertEquals(70,s.getMax(),0);
        s.setRange(40,40);RangeSetting.Drag left=s.beginDrag(40);left.move(15);
        assertEquals(15,s.getMin(),0);assertEquals(40,s.getMax(),0);
    }
    @Test public void everyClickSelectsAnEndpointAndSnappingStaysBounded(){
        for(int click=0;click<=100;click++){
            RangeSetting s=new RangeSetting("Range",20,60,0,100,1);s.beginDrag(click).move(click);
            assertTrue(s.getMin()==click||s.getMax()==click);
        }
        RangeSetting s=new RangeSetting("Step",0,9,0,9,2);s.setMin(100);
        assertEquals(9,s.getMin(),0);assertEquals(9,s.getMax(),0);s.setMax(-50);
        assertEquals(0,s.getMin(),0);assertEquals(0,s.getMax(),0);
    }
}
