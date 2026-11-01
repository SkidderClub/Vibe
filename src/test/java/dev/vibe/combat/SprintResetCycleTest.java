package dev.vibe.combat;

import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SprintResetCycleTest {
    @Test
    public void defaultTimingWaitsFourTicksAndStopsForTwo() {
        final SprintResetCycle cycle = new SprintResetCycle(new Random(1));
        cycle.tick(100.0D, 100, 100, 0, true, 4.0F, 2.0F, false, false);
        for (int tick = 1; tick < 4; tick++) {
            cycle.tick(100.0D + tick * 0.05D, 100 + tick, 100, 0, true, 4.0F, 2.0F, false, false);
            assertFalse(cycle.isStopping());
        }

        cycle.tick(100.2D, 104, 100, 0, true, 4.0F, 2.0F, false, false);
        assertTrue(cycle.isStopping());
        cycle.tick(100.25D, 105, 100, 0, true, 4.0F, 2.0F, false, false);
        assertTrue(cycle.isStopping());
        cycle.tick(100.3D, 106, 100, 0, true, 4.0F, 2.0F, false, false);
        assertFalse(cycle.isStopping());
    }

    @Test
    public void fractionalDelayCarriesItsRemainderIntoTheStop() {
        final SprintResetCycle cycle = new SprintResetCycle(new Random(1));
        cycle.tick(100.0D, 100, 100, 0, true, 1.25F, 1.25F, false, false);
        cycle.tick(100.05D, 101, 100, 0, true, 1.25F, 1.25F, false, false);
        assertFalse(cycle.isStopping());
        cycle.tick(100.1D, 102, 100, 0, true, 1.25F, 1.25F, false, false);
        assertTrue(cycle.isStopping());
        cycle.tick(100.15D, 103, 100, 0, true, 1.25F, 1.25F, false, false);
        assertFalse(cycle.isStopping());
    }

    @Test
    public void damageConditionRequiresTheFreshTenHurtTimeTick() {
        final SprintResetCycle cycle = new SprintResetCycle(new Random(1));
        cycle.tick(100.0D, 100, 100, 9, true, 0.0F, 2.0F, false, true);
        cycle.tick(100.05D, 101, 100, 8, true, 0.0F, 2.0F, false, true);
        assertFalse(cycle.isStopping());
        cycle.tick(100.1D, 102, 100, 10, true, 0.0F, 2.0F, false, true);
        assertFalse(cycle.isStopping());
        cycle.tick(100.15D, 103, 100, 9, true, 0.0F, 2.0F, false, true);
        assertTrue(cycle.isStopping());
    }

    @Test
    public void weaponConditionRetainsPendingHitUntilAllowed() {
        final SprintResetCycle cycle = new SprintResetCycle(new Random(1));
        cycle.tick(100.0D, 100, 100, 0, false, 0.0F, 2.0F, false, false);
        cycle.tick(100.05D, 101, 100, 0, false, 0.0F, 2.0F, false, false);
        assertFalse(cycle.isStopping());
        cycle.tick(100.1D, 102, 100, 0, true, 0.0F, 2.0F, false, false);
        cycle.tick(100.15D, 103, 100, 0, true, 0.0F, 2.0F, false, false);
        assertTrue(cycle.isStopping());
    }

    @Test
    public void hitsInsideHalfASecondDoNotStartAnotherCycle() {
        final SprintResetCycle cycle = new SprintResetCycle(new Random(1));
        cycle.tick(100.0D, 100, 100, 0, true, 0.0F, 1.0F, false, false);
        cycle.tick(100.05D, 101, 100, 0, true, 0.0F, 1.0F, false, false);
        cycle.tick(100.1D, 102, 100, 0, true, 0.0F, 1.0F, false, false);
        cycle.tick(100.2D, 104, 104, 0, true, 0.0F, 1.0F, false, false);
        cycle.tick(100.25D, 105, 104, 0, true, 0.0F, 1.0F, false, false);
        assertFalse(cycle.isStopping());
        cycle.tick(100.5D, 110, 110, 0, true, 0.0F, 1.0F, false, false);
        cycle.tick(100.55D, 111, 110, 0, true, 0.0F, 1.0F, false, false);
        assertTrue(cycle.isStopping());
    }

    @Test
    public void aHitDuringTheStopCanScheduleTheNextCycle() {
        final SprintResetCycle cycle = new SprintResetCycle(new Random(1));
        cycle.tick(100.0D, 100, 100, 0, true, 0.0F, 12.0F, false, false);
        cycle.tick(100.05D, 101, 100, 0, true, 0.0F, 12.0F, false, false);
        for (int tick = 2; tick <= 13; tick++)
            cycle.tick(100.0D + tick * 0.05D, 100 + tick, 110, 0, true, 0.0F, 12.0F, false, false);

        assertFalse(cycle.isStopping());
        cycle.tick(100.7D, 114, 110, 0, true, 0.0F, 12.0F, false, false);
        cycle.tick(100.75D, 115, 110, 0, true, 0.0F, 12.0F, false, false);
        assertTrue(cycle.isStopping());
    }

    @Test
    public void resetDiscardsStopsAndPendingHits() {
        final SprintResetCycle cycle = new SprintResetCycle(new Random(1));
        cycle.tick(100.0D, 100, 100, 0, true, 0.0F, 2.0F, false, false);
        cycle.tick(100.05D, 101, 100, 0, true, 0.0F, 2.0F, false, false);
        assertTrue(cycle.isStopping());
        cycle.reset();
        for (int tick = 0; tick < 20; tick++) {
            cycle.tick(101.0D + tick * 0.05D, 120 + tick, 100, 0, true, 0.0F, 2.0F, false, false);
            assertFalse(cycle.isStopping());
        }
    }

    @Test
    public void randomizedCyclesFinishAndRetainTheHitCooldown() {
        final SprintResetCycle cycle = new SprintResetCycle(new Random(12));
        cycle.tick(100.0D, 100, 100, 0, true, 4.0F, 2.0F, true, false);
        boolean stopped = false;
        for (int tick = 1; tick <= 12; tick++) {
            cycle.tick(100.0D + tick * 0.05D, 100 + tick, 100, 0, true, 4.0F, 2.0F, true, false);
            stopped |= cycle.isStopping();
        }

        assertTrue(stopped);
        assertFalse(cycle.isStopping());
    }
}
