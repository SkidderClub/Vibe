package dev.vibe.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class BlockOutlineGradientTest {
    @Test public void everyFaceHasOppositeMatchingCornersAndAnimatedEdges() {
        for (int[] face : new int[][]{{0,1,3,2},{4,5,7,6},{0,4,6,2},{1,5,7,3},{0,1,5,4},{2,3,7,6}}) {
            assertEquals(BlockOverlayRenderer.cornerPhase(face[0]),BlockOverlayRenderer.cornerPhase(face[2]),0);
            assertEquals(BlockOverlayRenderer.cornerPhase(face[1]),BlockOverlayRenderer.cornerPhase(face[3]),0);
            assertNotEquals(BlockOverlayRenderer.cornerPhase(face[0]),BlockOverlayRenderer.cornerPhase(face[1]),0);
        }
        int cyan=0xCC40E8D0, pink=0xCCEF50E8;
        assertEquals(cyan,BlockOverlayRenderer.color("Fade",cyan,pink,0,0,1));
        assertEquals(pink,BlockOverlayRenderer.color("Fade",cyan,pink,1,0,1));
        for(String mode:new String[]{"Fade","Rainbow"}) {
            assertNotEquals(BlockOverlayRenderer.color(mode,cyan,pink,0,0,1),BlockOverlayRenderer.color(mode,cyan,pink,0,1200,1));
            assertEquals(0xCC,BlockOverlayRenderer.color(mode,cyan,pink,.5F,300,1)>>>24);
        }
    }
}
