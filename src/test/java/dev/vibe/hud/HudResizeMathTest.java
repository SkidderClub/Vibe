package dev.vibe.hud;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class HudResizeMathTest {
    @Test public void resizeUsesDragStartRatherThanAccumulatingEachFrame() {
        float first = HudResizeMath.fromDrag(1.0F, 120, 60, 24, 12);
        float repeated = HudResizeMath.fromDrag(1.0F, 120, 60, 24, 12);
        assertEquals(1.2F, first, .0001F);
        assertEquals(first, repeated, .0001F);
        assertEquals(.8F, HudResizeMath.fromDrag(1.0F, 120, 60, -24, -12), .0001F);
    }

    @Test public void scaleStaysWithinSupportedBounds() {
        assertEquals(2.0F, HudResizeMath.fromDrag(1.0F, 120, 60, 1000, 1000), 0F);
        assertEquals(.5F, HudResizeMath.fromDrag(1.0F, 120, 60, -1000, -1000), 0F);
    }
}
