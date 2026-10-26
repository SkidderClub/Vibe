package dev.vibe.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScrollingTextTest {
    @Test public void longLabelsPauseAtEachEndAndExposeTheirWholeWidth() {
        assertEquals(0, ScrollingText.offset(148, 100, 0), 0);
        assertEquals(0, ScrollingText.offset(148, 100, 900), 0);
        assertEquals(24, ScrollingText.offset(148, 100, 2000), 0);
        assertEquals(48, ScrollingText.offset(148, 100, 3500), 0);
        assertEquals(24, ScrollingText.offset(148, 100, 5000), 0);
        assertEquals(0, ScrollingText.offset(148, 100, 6000), 0);
        assertEquals(0, ScrollingText.offset(70, 100, 2000), 0);
    }
}
