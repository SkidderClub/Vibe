package dev.vibe.server;

import java.util.Locale;
import java.util.regex.Pattern;
import static dev.vibe.server.ServerJoinHistory.Status.*;

/** Captures the account at connect time. Only an entered world can earn a green tick. */
public final class ServerJoinTracker {
    private static final Pattern BAN = Pattern.compile("\\b(ban(?:ned|ishment)?|blacklist(?:ed)?|suspend(?:ed)?|gebannt|gesperrt|banni|banned)\\b");
    private final ServerJoinHistory history;
    private long serial, active, readyAt = -1;
    private String account, server;
    private boolean joined;
    public ServerJoinTracker(ServerJoinHistory history) { this.history = history; }
    public synchronized long begin(String account, String server) {
        this.account = account; this.server = server; readyAt = -1; joined = false;
        return active = ++serial;
    }
    public synchronized long active() { return active; }
    public synchronized void ready(long attempt, long now) {
        if (attempt == 0 || attempt != active || joined) return;
        if (readyAt < 0) readyAt = now;
        if (now - readyAt >= 1500) {
            joined = true; history.record(account, server, JOINED, now, "Joined successfully");
        }
    }
    public synchronized void disconnected(long attempt, String reason, long now) {
        if (attempt == 0 || attempt != active) return;
        String text = reason == null ? "Disconnected" : reason;
        boolean banned = isBanReason(text);
        if (!joined || banned) history.record(account, server, FAILED, now, text);
        cancel();
    }
    public static boolean isBanReason(String text){
        String normalized=(text==null?"":text).replaceAll("\u00a7.","").toLowerCase(Locale.ROOT);
        return !normalized.contains("not banned")&&BAN.matcher(normalized).find();
    }
    public synchronized void cancel() { active = 0; readyAt = -1; account = server = null; joined = false; }
    public synchronized ServerJoinHistory.Result get(String account, String server) {
        if (active != 0 && !joined && this.account.equals(account)
                && ServerJoinHistory.serverKey(this.server).equals(ServerJoinHistory.serverKey(server)))
            return new ServerJoinHistory.Result(CONNECTING, 0, "Connecting with this account");
        return history.get(account, server);
    }
}
