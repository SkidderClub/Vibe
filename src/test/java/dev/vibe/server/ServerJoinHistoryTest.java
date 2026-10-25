package dev.vibe.server;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static dev.vibe.server.ServerJoinHistory.Status.*;

public class ServerJoinHistoryTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path path() { return temp.getRoot().toPath().resolve("vibe/server-results.json"); }
    @Test public void accountAndServerKeysAreStable() {
        assertEquals(ServerJoinHistory.accountKey("12345678-1234-1234-1234-123456789ABC", "One"), ServerJoinHistory.accountKey("12345678123412341234123456789abc", "Renamed"));
        assertEquals("name:player", ServerJoinHistory.accountKey("not-a-uuid", "Player"));
        assertEquals(ServerJoinHistory.serverKey("Example.org."), ServerJoinHistory.serverKey("EXAMPLE.ORG:25565"));
        assertEquals("[::1]:25565", ServerJoinHistory.serverKey("::1"));
        assertEquals("[::1]:25566", ServerJoinHistory.serverKey("[::1]:25566"));
        assertNotEquals(ServerJoinHistory.serverKey("example.org"), ServerJoinHistory.serverKey("example.org:25566"));
    }
    @Test public void resultsSurviveRestartAndStayIsolated() {
        ServerJoinHistory history = new ServerJoinHistory(path());
        history.record("a", "Example.org", JOINED, 123, "Welcome");
        history.record("b", "example.org", FAILED, 124, "Banned");
        history = new ServerJoinHistory(path());
        assertEquals(JOINED, history.get("a", "EXAMPLE.ORG:25565").status);
        assertEquals(FAILED, history.get("b", "example.org").status);
        assertEquals(UNKNOWN, history.get("a", "other.org").status);
        assertEquals(123, history.get("a", "example.org").time);
    }
    @Test public void corruptHistoryDoesNotBreakTheBrowser() throws Exception {
        Files.createDirectories(path().getParent()); Files.write(path(), "{broken".getBytes(StandardCharsets.UTF_8));
        ServerJoinHistory history = new ServerJoinHistory(path());
        assertEquals(UNKNOWN, history.get("a", "x").status); assertFalse(history.error().isEmpty());
        history.record("a", "x", FAILED, 100, "Connection refused");
        assertEquals(FAILED, new ServerJoinHistory(path()).get("a", "x").status); assertTrue(history.error().isEmpty());
    }
    @Test public void onlyCompletedResultsAreSavedAndReasonsAreBounded() throws Exception {
        ServerJoinHistory history = new ServerJoinHistory(path()); history.record("a", "x", CONNECTING, 0, "Pending");
        assertFalse(Files.exists(path()));
        history.record("a", "x", FAILED, 0, "\u00a7c" + new String(new char[1000]).replace('\0', 'x'));
        assertEquals(512, history.get("a", "x").reason.length());
        try (java.util.stream.Stream<Path> files = Files.list(path().getParent())) { assertEquals(1, files.count()); }
    }
    @Test public void stableWorldEntryIsRequiredForGreenTick() {
        ServerJoinHistory h = new ServerJoinHistory(path()); ServerJoinTracker t = new ServerJoinTracker(h);
        long a = t.begin("captured-account", "server");
        assertEquals(CONNECTING, t.get("captured-account", "server").status);
        assertEquals(UNKNOWN, h.get("captured-account", "server").status);
        t.ready(a, 1000); t.ready(a, 2499); assertEquals(UNKNOWN, h.get("captured-account", "server").status);
        t.ready(a, 2500); assertEquals(JOINED, h.get("captured-account", "server").status);
        assertEquals(UNKNOWN, h.get("another-account", "server").status);
    }
    @Test public void failedJoinAndEarlyBanAreRed() {
        ServerJoinHistory h = new ServerJoinHistory(path()); ServerJoinTracker t = new ServerJoinTracker(h);
        long a = t.begin("a", "s"); t.disconnected(a, "Authentication failed", 100);
        assertEquals(FAILED, h.get("a", "s").status);
        a = t.begin("b", "s"); t.ready(a, 0); t.disconnected(a, "You are permanently banned", 10);
        assertEquals(FAILED, h.get("b", "s").status);
    }
    @Test public void aBanAfterWorldEntryReplacesSuccessButOrdinaryDisconnectDoesNot() {
        ServerJoinHistory h = new ServerJoinHistory(path()); ServerJoinTracker t = new ServerJoinTracker(h);
        long a = t.begin("a", "s"); t.ready(a, 0); t.ready(a, 1500); t.disconnected(a, "Server restarting", 2000);
        assertEquals(JOINED, h.get("a", "s").status);
        a = t.begin("a", "s"); t.ready(a, 0); t.ready(a, 1500); t.disconnected(a, "\u00a7cYou have been banned!", 2000);
        assertEquals(FAILED, h.get("a", "s").status);
    }
    @Test public void manualCancelPreservesPreviousResultAndIgnoresStaleAttempt() {
        ServerJoinHistory h = new ServerJoinHistory(path()); ServerJoinTracker t = new ServerJoinTracker(h);
        h.record("a", "s", JOINED, 100, "Welcome");
        long first = t.begin("a", "s"); t.cancel(); assertEquals(JOINED, t.get("a", "s").status);
        long second = t.begin("b", "s"); t.ready(first, 0); t.ready(first, 2000); t.disconnected(first, "Banned", 2000);
        assertEquals(CONNECTING, t.get("b", "s").status); t.disconnected(second, "Timed out", 2000);
        assertEquals(FAILED, h.get("b", "s").status); assertEquals(JOINED, h.get("a", "s").status);
    }
    @Test public void banSubstringsDoNotProduceFalseBanResults() {
        for (String reason : new String[] {"Banner service is restarting", "You are not banned", "Bananas"}) {
            ServerJoinHistory h = new ServerJoinHistory(path()); ServerJoinTracker t = new ServerJoinTracker(h);
            long a = t.begin("a", "s"); t.ready(a, 0); t.ready(a, 1500); t.disconnected(a, reason, 2000);
            assertEquals(reason, JOINED, h.get("a", "s").status);
        }
    }
}
