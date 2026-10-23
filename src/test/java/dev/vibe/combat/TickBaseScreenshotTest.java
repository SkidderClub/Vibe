package dev.vibe.combat;

import dev.vibe.module.impl.combat.TickBaseModule;
import org.junit.Test;
import static org.junit.Assert.*;

public class TickBaseScreenshotTest {
    @Test public void screenshotPauseUsesRealTickTimeAndIsBounded() {
        assertEquals(0,TickBaseModule.screenshotDelayMillis(-1));
        assertEquals(50,TickBaseModule.screenshotDelayMillis(1));
        assertEquals(200,TickBaseModule.screenshotDelayMillis(4));
        assertEquals(250,TickBaseModule.screenshotDelayMillis(20));
    }
}
