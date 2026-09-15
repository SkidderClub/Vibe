package dev.vibe.combat;

import dev.vibe.input.ClickDeadline;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClickDeadlineTest {
    @Test public void ratesAboveTenSurviveTickQuantizationAndSmallJitter() {
        for (int rate : new int[] {11, 13, 16, 19, 20}) {
            ClickDeadline schedule = new ClickDeadline();
            int count = 0;
            long delay = Math.round(1000.0 / rate);
            for (int tick = 0; tick < 200; tick++) count += schedule.poll(1000 + tick * 50 + tick % 3, () -> delay);
            assertEquals("CPS " + rate, rate * 10, count, 3);
        }
    }
    @Test public void doesNotBurstAfterReleaseOrLongStall() {
        ClickDeadline schedule = new ClickDeadline();
        assertEquals(1, schedule.poll(1000, () -> 50));
        assertEquals(0, schedule.poll(1001, () -> 50));
        assertEquals(1, schedule.poll(10000, () -> 50));
        schedule.reset();
        assertEquals(1, schedule.poll(10001, () -> 50));
    }
}
