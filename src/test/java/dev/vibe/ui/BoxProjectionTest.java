package dev.vibe.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class BoxProjectionTest {
    private final float[] identity = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
    private final float[] perspective = {1,0,0,0, 0,1,0,0, 0,0,-1.0002F,-1, 0,0,-.20002F,0};

    @Test public void stableSmallBoundsAtDistanceAndFractionalGuiScale() {
        double[] bounds = BoxProjection.bounds(new double[]{-.3, 0, -200.3}, new double[]{.3, 1.8, -199.7}, identity, perspective, 853.333333, 480);
        assertNotNull(bounds);
        assertEquals(426.666666, (bounds[0] + bounds[2]) / 2, .00001);
        assertTrue(bounds[2] - bounds[0] > 1);
        assertTrue(bounds[2] - bounds[0] < 2);
        assertEquals(480 * .5, bounds[3], .00001);
    }
    @Test public void nearPlaneIntersectionIsClippedAndBehindCameraIsRejected() {
        assertNull(BoxProjection.bounds(new double[]{-.3,0,1}, new double[]{.3,1.8,2}, identity, perspective, 800, 600));
        double[] clipped = BoxProjection.bounds(new double[]{-.01,-.01,-1}, new double[]{.01,.01,.1}, identity, perspective, 800, 600);
        assertNotNull(clipped);
        assertTrue(clipped[0] >= 0 && clipped[2] <= 800);
        assertTrue(clipped[1] >= 0 && clipped[3] <= 600);
    }
    @Test public void offscreenBoundsDoNotLeavePhantomBoxes() {
        assertNull(BoxProjection.bounds(new double[]{20,0,-2}, new double[]{21,1.8,-1}, identity, perspective, 800, 600));
    }
}
