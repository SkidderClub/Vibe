package dev.vibe.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local results only: never stores session tokens or treats a server-list ping as a login. */
public final class ServerJoinHistory {
    public enum Status { UNKNOWN, JOINED, FAILED, CONNECTING }
    public static final class Result {
        public final Status status;
        public final long time;
        public final String reason;
        public Result(Status status, long time, String reason) {
            this.status = status; this.time = time; this.reason = clean(reason);
        }
    }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Result UNKNOWN = new Result(Status.UNKNOWN, 0, "No previous join");
    private final Path file;
    private final LinkedHashMap<String, Result> results = new LinkedHashMap<>();
    private String error = "";

    public ServerJoinHistory(Path file) { this.file = file; load(); }
    public static String accountKey(String uuid, String name) {
        String id = uuid == null ? "" : uuid.replace("-", "").toLowerCase(Locale.ROOT);
        return id.matches("[0-9a-f]{32}") ? "uuid:" + id : "name:" + safe(name).toLowerCase(Locale.ROOT);
    }
    public static String serverKey(String address) {
        String value = safe(address).trim().toLowerCase(Locale.ROOT);
        String host = value, port = "25565";
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end > 0) { host = value.substring(1, end); if (value.startsWith(":", end + 1)) port = value.substring(end + 2); }
        } else if (value.indexOf(':') == value.lastIndexOf(':') && value.indexOf(':') > 0) {
            int colon = value.indexOf(':'); host = value.substring(0, colon); port = value.substring(colon + 1);
        }
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        try { port = Integer.toString(Integer.parseInt(port)); } catch (NumberFormatException ignored) { }
        return (host.indexOf(':') >= 0 ? "[" + host + "]" : host) + ":" + port;
    }
    private static String key(String account, String server) { return account + "\n" + serverKey(server); }
    public synchronized Result get(String account, String server) { return results.getOrDefault(key(account, server), UNKNOWN); }
    public synchronized String error() { return error; }
    public synchronized void record(String account, String server, Status status, long now, String reason) {
        if (status != Status.JOINED && status != Status.FAILED) return;
        String key = key(account, server);
        results.remove(key); results.put(key, new Result(status, now, reason));
        while (results.size() > 2048) results.remove(results.keySet().iterator().next());
        save();
    }
    private void load() {
        if (!Files.isRegularFile(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            com.google.gson.JsonObject root = new com.google.gson.JsonParser().parse(reader).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                try {
                    Result result = JSON.fromJson(entry.getValue(), Result.class);
                    if (result != null && (result.status == Status.JOINED || result.status == Status.FAILED))
                        results.put(entry.getKey(), new Result(result.status, result.time, result.reason));
                } catch (RuntimeException ignored) { }
                if (results.size() >= 2048) break;
            }
        } catch (IOException | RuntimeException e) { error = "Previous join history could not be read."; }
    }
    private void save() {
        Path temporary = null;
        try {
            Path parent = file.toAbsolutePath().getParent(); Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, "server-results-", ".tmp");
            Files.write(temporary, JSON.toJson(results).getBytes(StandardCharsets.UTF_8));
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            error = "";
        } catch (IOException e) { error = "Join history could not be saved."; }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
    }
    private static String safe(String s) { return s == null ? "" : s; }
    private static String clean(String s) {
        String value = safe(s).replaceAll("\u00a7.", "").replaceAll("[\\p{Cntrl}&&[^\\n]]", "");
        return value.substring(0, Math.min(512, value.length()));
    }
}
