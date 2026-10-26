package dev.vibe.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScrollbarDragTest {
    @Test public void scrollbarThumbDragsDoNotJumpAndCanReachBothEnds() {
        int thumb=ScrollbarDrag.thumb(100,300,12);assertEquals(25,thumb);
        ScrollbarDrag drag=new ScrollbarDrag(10,110,120,300,12,45);
        assertEquals(120,drag.at(45));assertEquals(0,drag.at(-100));assertEquals(300,drag.at(1000));
        ScrollbarDrag trackClick=new ScrollbarDrag(10,110,0,300,12,90);
        assertTrue(trackClick.at(90)>200);
    }
}
