package dev.vibe.ui;

import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class DebugOverlayTest {
    @After public void reset() { DebugOverlay.clear(); }

    @Test public void onlyWidgetsTouchingEitherTextColumnAreHidden() {
        DebugOverlay.begin(800, 9, Arrays.asList("left text"), Arrays.asList("right text"), text -> text.length() * 6);
        assertTrue(DebugOverlay.overlaps(9, 9, 180, 35));
        assertTrue(DebugOverlay.overlaps(750, 9, 790, 35));
        assertFalse(DebugOverlay.overlaps(300, 3, 500, 30));
        assertFalse(DebugOverlay.overlaps(9, 100, 220, 180));
    }

    @Test public void shortRowsDoNotReserveTheWidthOfLongerRows() {
        DebugOverlay.begin(800, 9, Arrays.asList("long", "short"), Collections.emptyList(), text -> text.equals("long") ? 240 : 30);
        assertTrue(DebugOverlay.overlaps(120, 1, 160, 10));
        assertFalse(DebugOverlay.overlaps(120, 10, 160, 19));
        assertFalse(DebugOverlay.overlaps(243, 1, 260, 10));
        assertTrue(DebugOverlay.overlaps(242.5f, 1, 260, 10));
    }

    @Test public void emptyLinesLeaveGapsAndNullLinesDoNotAdvance() {
        DebugOverlay.begin(800, 9, Arrays.asList("A", "", null, "B"), Collections.emptyList(), text -> text.length() * 6);
        assertFalse(DebugOverlay.overlaps(2, 10, 8, 19));
        assertTrue(DebugOverlay.overlaps(2, 19, 8, 28));
    }

    @Test public void textAndGuiScaleChangesReplaceThePreviousGeometry() {
        DebugOverlay.begin(800, 9, Collections.emptyList(), Arrays.asList("GPU"), text -> 100);
        assertTrue(DebugOverlay.overlaps(700, 1, 720, 10));
        DebugOverlay.begin(400, 12, Collections.emptyList(), Arrays.asList("GPU", "driver"), text -> 50);
        assertFalse(DebugOverlay.overlaps(700, 1, 720, 10));
        assertTrue(DebugOverlay.overlaps(350, 14, 375, 24));
        assertFalse(DebugOverlay.overlaps(300, 14, 345, 24));
    }

    @Test public void closingF3OrFinishingTheHudPassRestoresVisibility() {
        DebugOverlay.begin(800, 9, Arrays.asList("debug"), Collections.emptyList(), text -> 100);
        assertTrue(DebugOverlay.isActive());
        assertTrue(DebugOverlay.overlaps(9, 9, 180, 35));
        DebugOverlay.clear();
        assertFalse(DebugOverlay.isActive());
        assertFalse(DebugOverlay.overlaps(9, 9, 180, 35));
        DebugOverlay.begin(800, 9, Collections.emptyList(), Collections.emptyList(), text -> 100);
        assertFalse(DebugOverlay.isActive());
    }
}
