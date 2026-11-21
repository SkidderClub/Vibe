package dev.vibe.hud;

import cc.rewound.modapi.packet.impl.clientbound.event.ClientboundPitEventsPacket;
import cc.rewound.modapi.packet.impl.clientbound.event.ClientboundPitEventsPacket.PitEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.util.EnumChatFormatting;

/** Interpolates authoritative countdowns between sidebar updates on the client thread. */
public final class MatchCountdowns {
    public static final String ID = "matchcountdowns";
    static final String TITLE = "Match Countdowns";
    static final String[] THEMES = {"Skeet", "Vibe", "Lavalamp", "LiquidGlass", "Adjust"};

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern MATCH_TITLE = Pattern.compile(
            "\\b(?:BED\\s*WARS|SKY\\s*WARS|BLITZ(?:\\s+SG|\\s+SURVIVAL\\s*GAMES)"
                    + "|SURVIVAL\\s*GAMES|DUELS|ZOMBIES|PIT)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIMER = Pattern.compile(
            "^(?:next event\\s*:\\s*)?(.+?)\\s*(?:\\s+in\\b:?|:|\\s)\\s*(\\d{1,3}:[0-5]\\d(?::[0-5]\\d)?|\\d{1,3}s)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT = Pattern.compile(
            "^(?:(?:diamond|emerald)(?: generator)?(?:\\s+(?:[ivx]+|upgrade))?|(?:chest\\s+)?refills?"
                    + "|bed(?:s)? (?:gone|break|destruction)|sudden death|more dragons|doom|game (?:end|over)"
                    + "|(?:game |match )?starts?|starting|deathmatch|border(?: shrink)?|(?:next )?wave(?: \\d+)?"
                    + "|next (?:minor|major)(?: event)?)$", Pattern.CASE_INSENSITIVE);

    private Object world;
    private String sidebar;
    private List<String> lines = Collections.emptyList();
    private Map<String, Timer> timers = Collections.emptyMap();
    private ClientboundPitEventsPacket lastPitEvents;
    private List<Timer> pitTimers = Collections.emptyList();

    void update(Object currentWorld, String title, List<String> rows, long now) {
        String cleanTitle = clean(title);
        if (world != currentWorld || !cleanTitle.equals(sidebar)) {
            world = currentWorld;
            sidebar = cleanTitle;
            lines = Collections.emptyList();
            timers = Collections.emptyMap();
        }
        if (world == null || !MATCH_TITLE.matcher(sidebar).find() || lines.equals(rows)) return;
        lines = rows;
        Map<String, Timer> updated = new LinkedHashMap<>();
        for (String row : rows) {
            Matcher match = TIMER.matcher(clean(row));
            if (!match.matches()) continue;
            String name = match.group(1).trim();
            if (!EVENT.matcher(name).matches()) continue;
            int seconds = seconds(match.group(2));
            String key = name.toLowerCase(Locale.ROOT);
            Timer previous = timers.get(key);
            // Scores such as kills may change while the timer still displays the same second.
            Timer next = previous != null && previous.serverSeconds == seconds
                    ? previous : new Timer(name, seconds, now + 1000L * seconds);
            updated.put(key, next);
        }
        timers = updated;
    }

    List<Timer> visible(long now) {
        if (lastPitEvents != null) {
            List<Timer> result = new ArrayList<>(pitTimers);
            result.removeIf(timer -> now - timer.deadline >= (timer.kind == Kind.ACTIVE ? 0 : 1000L));
            return result;
        }
        if (timers.isEmpty()) return Collections.emptyList();
        List<Timer> result = new ArrayList<>(timers.values());
        result.removeIf(timer -> now - timer.deadline >= 1000L);
        result.sort(Comparator.comparingLong(timer -> timer.deadline));
        return result;
    }

    void updatePit(ClientboundPitEventsPacket packet, long epochNow, long now) {
        if (packet == lastPitEvents) return;
        lastPitEvents = packet;
        if (packet == null) {
            pitTimers = Collections.emptyList();
            return;
        }
        List<Timer> result = new ArrayList<>(3);
        packet.getCurrent().ifPresent(event -> addPit(result, event, Kind.ACTIVE, event.getEndTime(), epochNow, now));
        List<Timer> upcoming = new ArrayList<>(2);
        packet.getNext(false).ifPresent(event -> addPit(upcoming, event, Kind.MINOR, event.getStartTime(), epochNow, now));
        packet.getNext(true).ifPresent(event -> addPit(upcoming, event, Kind.MAJOR, event.getStartTime(), epochNow, now));
        upcoming.sort(Comparator.comparingLong(timer -> timer.deadline));
        result.addAll(upcoming);
        pitTimers = result;
    }

    private static void addPit(List<Timer> result, PitEvent event, Kind kind, long epochTime, long epochNow, long now) {
        if (epochTime <= 0) return;
        String name = clean(event.getName());
        if (!name.isEmpty()) result.add(new Timer(name, -1, now + (epochTime - epochNow), kind));
    }

    static List<Timer> preview(long now) {
        List<Timer> result = new ArrayList<>(3);
        result.add(new Timer("Chest refill", 48, now + 48000L));
        result.add(new Timer("Diamond II", 154, now + 154000L));
        result.add(new Timer("Sudden death", 433, now + 433000L));
        return result;
    }

    private static String clean(String value) {
        String text = EnumChatFormatting.getTextWithoutFormattingCodes(value);
        return text == null ? "" : SPACES.matcher(text.replace('\u00a0', ' ').trim()).replaceAll(" ");
    }

    private static int seconds(String value) {
        if (value.endsWith("s") || value.endsWith("S")) return Integer.parseInt(value.substring(0, value.length() - 1));
        int total = 0;
        for (String part : value.split(":")) total = total * 60 + Integer.parseInt(part);
        return total;
    }

    static final class Timer {
        final String name;
        final long deadline;
        final Kind kind;
        private final int serverSeconds;

        private Timer(String name, int serverSeconds, long deadline) {
            this(name, serverSeconds, deadline, Kind.MATCH);
        }

        private Timer(String name, int serverSeconds, long deadline, Kind kind) {
            this.name = name;
            this.serverSeconds = serverSeconds;
            this.deadline = deadline;
            this.kind = kind;
        }

        long remaining(long now) {
            long remaining = deadline - now;
            return remaining <= 0 ? 0 : 1 + (remaining - 1) / 1000L;
        }

        String label(long now) {
            long seconds = remaining(now);
            if (seconds < 3600) return seconds / 60 + ":" + twoDigits(seconds % 60);
            return seconds / 3600 + ":" + twoDigits(seconds / 60 % 60) + ":" + twoDigits(seconds % 60);
        }

        private static String twoDigits(long value) {
            return value < 10 ? "0" + value : Long.toString(value);
        }
    }

    enum Kind {
        MATCH(""), ACTIVE("Live"), MINOR("Minor"), MAJOR("Major");

        final String badge;

        Kind(String badge) {
            this.badge = badge;
        }
    }
}
