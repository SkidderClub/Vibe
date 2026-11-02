package dev.vibe.hud;
import org.junit.Test;
import static org.junit.Assert.*;
public class OverlayMotionTest {
    @Test public void openingClosingAndReversalRemainContinuous(){
        OverlayMotion m=new OverlayMotion();long t=1000000000L;
        assertEquals(0,m.update(true,t),0);assertEquals(.5,m.update(true,t+90000000),.0001);
        assertEquals(.5,m.update(false,t+90000000),.0001);assertEquals(.25,m.update(false,t+180000000),.0001);
        assertEquals(0,m.update(false,t+270000000),0);assertEquals(0,m.update(false,t+400000000),0);
    }
}
