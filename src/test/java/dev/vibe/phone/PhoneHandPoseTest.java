package dev.vibe.phone;

import org.junit.Test;
import static org.junit.Assert.*;

public class PhoneHandPoseTest {
    @Test public void thumbSweepsInwardAndReturnsToTheGripAfterInteraction() {
        PhoneHandPose pose = new PhoneHandPose();
        for (int i = 0; i < 20; i++) pose.advance(182, 205, .3, false, .05);
        assertTrue(pose.angle > 60 && pose.angle < 80);
        assertTrue(pose.gripY > 220 && pose.gripY < 260);
        for (int i = 0; i < 40; i++) pose.advance(182, 205, 2, false, .05);
        assertEquals(344, pose.gripY, .01); assertEquals(40, pose.angle, .01);
    }

    @Test public void tapFlexesTheJointWithoutStretchingOrSnappingTheHand() {
        PhoneHandPose pose = new PhoneHandPose();
        pose.advance(1000, 1000, .12, true, .016);
        assertEquals(.48, pose.curl, .001);
        assertTrue(pose.angle < 50); assertTrue(pose.gripY > 344 && pose.gripY < 370);
        pose.advance(-100, -100, 1, true, .016); assertEquals(0, pose.curl, 0);
        pose.reset(); assertEquals(344, pose.gripY, 0);
    }
}
