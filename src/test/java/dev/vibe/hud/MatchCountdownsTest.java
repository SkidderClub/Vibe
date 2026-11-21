package dev.vibe.hud;

import cc.rewound.modapi.packet.impl.clientbound.event.ClientboundPitEventsPacket;
import cc.rewound.modapi.packet.impl.clientbound.event.ClientboundPitEventsPacket.PitEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MatchCountdownsTest {
    private final MatchCountdowns countdowns = new MatchCountdowns();
    private final Object world = new Object();

    @Test
    public void readsFormattedSidebarAndSortsCountdowns() {
        countdowns.update(world, "§eBED WARS", Arrays.asList(
                "§bDiamond II §fin §a5:42",
                "Next Event: §aRefill §f0:37",
                "Game ended: 19:57",
                "Kills: 7"), 1000);

        List<MatchCountdowns.Timer> timers = countdowns.visible(2200);
        assertEquals(2, timers.size());
        assertEquals("Refill", timers.get(0).name);
        assertEquals("0:36", timers.get(0).label(2200));
        assertEquals("Diamond II", timers.get(1).name);
        assertEquals("5:41", timers.get(1).label(2200));
    }

    @Test
    public void unchangedReadingExpiresAndNewServerValueResyncs() {
        countdowns.update(world, "SKYWARS", Arrays.asList("Refill: 0:02", "Kills: 0"), 1000);
        countdowns.update(world, "SKYWARS", Arrays.asList("Refill: 0:02", "Kills: 1"), 2200);
        assertEquals("0:01", countdowns.visible(2200).get(0).label(2200));
        assertEquals("0:00", countdowns.visible(3200).get(0).label(3200));
        assertTrue(countdowns.visible(4000).isEmpty());
        countdowns.update(world, "SKYWARS", Collections.singletonList("Refill: 2:59"), 5000);
        assertEquals("2:59", countdowns.visible(5000).get(0).label(5000));
    }

    @Test
    public void clearsOnWorldSidebarAndTimerChanges() {
        List<String> rows = Collections.singletonList("Sudden Death in 1:05");
        countdowns.update(world, "BED WARS", rows, 1000);
        countdowns.update(new Object(), "BED WARS", rows, 5000);
        assertEquals(65, countdowns.visible(5000).get(0).remaining(5000));
        countdowns.update(world, "Lobby", rows, 6000);
        assertTrue(countdowns.visible(6000).isEmpty());
        countdowns.update(world, "BED WARS", rows, 7000);
        countdowns.update(world, "BED WARS", Collections.emptyList(), 7100);
        assertTrue(countdowns.visible(7100).isEmpty());
    }

    @Test
    public void handlesShortHourlyAndMalformedTimers() {
        countdowns.update(world, "THE PIT", Arrays.asList("Next minor: 8s", "Next major event: 1:07:41",
                "Next major: 8:75", "Status: Overdrive"), 1000);
        List<MatchCountdowns.Timer> timers = countdowns.visible(1000);
        assertEquals(2, timers.size());
        assertEquals("0:08", timers.get(0).label(1000));
        assertEquals("1:07:41", timers.get(1).label(1000));
    }

    @Test
    public void reschedulesPitEventsAndSeparatesRunningFromUpcoming() {
        PitEvent active = new PitEvent("spire", "Spire", true, 120000, 185000);
        PitEvent minor = new PitEvent("koth", "King of the Hill", false, 187000, 0);
        PitEvent major = new PitEvent("raffle", "Raffle", true, 429000, 0);
        countdowns.updatePit(new ClientboundPitEventsPacket(active, Arrays.asList(major, minor)), 180000, 1000);
        List<MatchCountdowns.Timer> timers = countdowns.visible(2000);
        assertEquals(3, timers.size());
        assertEquals(MatchCountdowns.Kind.ACTIVE, timers.get(0).kind);
        assertEquals("0:04", timers.get(0).label(2000));
        assertEquals(MatchCountdowns.Kind.MINOR, timers.get(1).kind);
        assertEquals(MatchCountdowns.Kind.MAJOR, timers.get(2).kind);
        assertEquals(2, countdowns.visible(6000).size());

        minor = new PitEvent("koth", "King of the Hill", false, 247000, 0);
        countdowns.updatePit(new ClientboundPitEventsPacket(null, Arrays.asList(major, minor)), 187000, 8000);
        assertEquals("1:00", countdowns.visible(8000).get(0).label(8000));
        countdowns.updatePit(null, 187000, 8000);
        assertTrue(countdowns.visible(8000).isEmpty());
    }
}
