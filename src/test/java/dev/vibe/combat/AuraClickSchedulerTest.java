package dev.vibe.combat;

import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuraClickSchedulerTest {
    private AuraClickScheduler scheduler() { return new AuraClickScheduler(new Random(42)); }

    private int poll(AuraClickScheduler scheduler, long now, String mode, double cps) {
        return scheduler.poll(now, mode, cps, cps, 200, 200, 300, 300);
    }

    @Test public void normalAndButterflyPreserveConfiguredRateAtTwentyTicksPerSecond() {
        for (String mode : new String[] {"Normal", "Butterfly"}) {
            for (double cps : new double[] {1, 6, 13, 18, 20, 40}) {
                AuraClickScheduler scheduler = scheduler();
                int total = 0;
                for (long now = 0; now < 10000; now += 50) total += poll(scheduler, now, mode, cps);
                assertEquals(mode + " @ " + cps, cps * 10, total, 2.0D);
            }
        }
    }

    @Test public void butterflyAlternatesClosePairAndRecoveryGap() {
        AuraClickScheduler scheduler = scheduler();
        assertEquals(1, poll(scheduler, 0, "Butterfly", 10));
        assertEquals(0, poll(scheduler, 69, "Butterfly", 10));
        assertEquals(1, poll(scheduler, 70, "Butterfly", 10));
        assertEquals(0, poll(scheduler, 199, "Butterfly", 10));
        assertEquals(1, poll(scheduler, 200, "Butterfly", 10));
    }

    @Test public void dragHasMultipleEdgesPerTickAndAPauseBetweenBursts() {
        AuraClickScheduler scheduler = scheduler();
        assertEquals(1, poll(scheduler, 0, "Drag Clicking", 40));
        assertEquals(2, poll(scheduler, 50, "Drag Clicking", 40));
        assertEquals(2, poll(scheduler, 100, "Drag Clicking", 40));
        assertEquals(2, poll(scheduler, 150, "Drag Clicking", 40));
        assertEquals(1, poll(scheduler, 200, "Drag Clicking", 40));
        for (int now = 250; now < 500; now += 50) assertEquals(0, poll(scheduler, now, "Drag Clicking", 40));
        assertEquals(1, poll(scheduler, 500, "Drag Clicking", 40));
    }

    @Test public void lagDoesNotReplayHundredsOfClicks() {
        AuraClickScheduler scheduler = scheduler();
        poll(scheduler, 0, "Normal", 20);
        assertEquals(1, poll(scheduler, 60000, "Normal", 20));
        assertEquals(0, poll(scheduler, 60000, "Normal", 20));
    }

    @Test public void resetAndModeChangeDiscardAnOldDragPause() {
        AuraClickScheduler scheduler = scheduler();
        for (int now = 0; now <= 200; now += 50) poll(scheduler, now, "Drag Clicking", 40);
        assertFalse(scheduler.isDue(250));
        scheduler.reset();
        assertTrue(scheduler.isDue(250));
        assertEquals(1, poll(scheduler, 250, "Normal", 10));
        assertEquals(1, poll(scheduler, 260, "Butterfly", 10));
    }
}
