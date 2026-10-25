package dev.vibe.launcher.game;

import dev.vibe.launcher.core.ErrorCode;
import dev.vibe.launcher.core.FileUtil;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.LauncherException;
import dev.vibe.launcher.core.Platform;
import dev.vibe.launcher.core.Text;
import dev.vibe.launcher.minecraft.LaunchCommand;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * One running Minecraft: a direct Java process that Forge 1.8.9 starts with
 * OptiFine and the Vibe JAR from {@code mods/}.
 *
 * <p>Output goes straight into a log file instead of a pipe, so Minecraft keeps
 * running even if the launcher is closed; the launcher tails that file for its
 * console. Vibe writes {@code VIBE_LAUNCH_READY_FILE} when its first screen is
 * drawn, which is how the launcher knows the game window is up.</p>
 */
public final class GameSession {
    public enum Stage { STARTING, RUNNING }

    public interface Listener {
        void output(List<String> lines);
        void stage(Stage stage);
        /** @param code the exit code, or {@code null} when it is unknown (re-attached session) */
        void exited(Integer code, boolean reachedGame, String failure);
    }

    public static final class Request {
        public LaunchCommand.Launch launch;
        public Path logFile, readyFile, sessionFile;
    }

    private final Path logFile, readyFile, sessionFile;
    private final Listener listener;
    private final Process process;
    private final long pid;
    private volatile boolean reachedGame;
    private volatile boolean stopping;
    private volatile Stage stage;
    private final FailureAnalyzer analyzer = new FailureAnalyzer();

    private GameSession(Path logFile, Path readyFile, Path sessionFile, Listener listener, Process process, long pid) {
        this.logFile = logFile;
        this.readyFile = readyFile;
        this.sessionFile = sessionFile;
        this.listener = listener;
        this.process = process;
        this.pid = pid;
    }

    public static GameSession start(Request request, Listener listener) throws IOException {
        if (request.launch == null || request.launch.command.isEmpty()) {
            throw new LauncherException(ErrorCode.JAVA_START, I18n.t("Minecraft could not be started."));
        }
        Files.createDirectories(request.logFile.getParent());
        Files.createDirectories(request.readyFile.getParent());
        Files.deleteIfExists(request.readyFile);

        ProcessBuilder builder = new ProcessBuilder(request.launch.command);
        builder.directory(request.launch.workDir.toFile());
        builder.environment().putAll(request.launch.environment);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(request.logFile.toFile()));
        builder.redirectInput(ProcessBuilder.Redirect.from(Platform.nullDevice()));

        long offset = Files.exists(request.logFile) ? Files.size(request.logFile) : 0;
        Process process;
        try {
            process = builder.start();
        } catch (IOException error) {
            // "Cannot run program": blocked by an antivirus, not executable, or a broken runtime.
            throw new LauncherException(ErrorCode.JAVA_START, I18n.t("Minecraft could not be started: {0}", Text.describe(error)), error);
        }
        long pid = Platform.pid(process);
        GameSession session = new GameSession(request.logFile, request.readyFile, request.sessionFile, listener, process, pid);
        session.writeSessionFile();
        session.setStage(Stage.STARTING);
        session.startThreads(offset);
        return session;
    }

    /** Picks up a game started by an earlier launcher run (Java 9+ only). */
    public static GameSession reattach(Path sessionFile, Listener listener) {
        if (!Files.isRegularFile(sessionFile)) return null;
        Properties values = FileUtil.readProperties(sessionFile);
        long pid, started;
        try {
            pid = Long.parseLong(values.getProperty("pid", "-1"));
            started = Long.parseLong(values.getProperty("started", "0"));
        } catch (NumberFormatException ignored) {
            pid = -1;
            started = 0;
        }
        // The recorded start time must match: Windows reuses PIDs quickly, and an unrelated
        // process with the same PID must never be shown as Vibe or stopped by the launcher.
        long processStart;
        try { processStart = Long.parseLong(values.getProperty("processStart", "-1")); } catch (NumberFormatException ignored) { processStart = -1; }
        long actualStart = Platform.startTime(pid);
        boolean same = processStart > 0 && actualStart > 0 && Math.abs(actualStart - processStart) < 2000;
        boolean stale = System.currentTimeMillis() - started > 24L * 60L * 60L * 1000L;
        if (pid <= 0 || stale || !same || !Platform.isAlive(pid)) {
            try { Files.deleteIfExists(sessionFile); } catch (IOException ignored) { /* stale marker */ }
            return null;
        }
        Path log = new java.io.File(values.getProperty("log", "")).toPath();
        Path ready = new java.io.File(values.getProperty("ready", "")).toPath();
        GameSession session = new GameSession(log, ready, sessionFile, listener, null, pid);
        session.reachedGame = Files.exists(ready);
        session.stage = session.reachedGame ? Stage.RUNNING : Stage.STARTING;
        long offset = 0;
        try { offset = Files.exists(log) ? Math.max(0, Files.size(log) - 64 * 1024) : 0; } catch (IOException ignored) { /* start at 0 */ }
        session.startThreads(offset);
        return session;
    }

    public Path logFile() { return logFile; }
    public boolean reachedGame() { return reachedGame; }
    public Stage currentStage() { return stage; }
    /** Known problems seen in the output; asked for a diagnosis when the run fails. */
    public FailureAnalyzer analyzer() { return analyzer; }

    public void stop() {
        stopping = true;
        if (process != null) Platform.destroyTree(process);
        else Platform.destroyTree(pid);
    }

    public boolean isStopping() { return stopping; }

    private void writeSessionFile() {
        if (pid <= 0) return;
        Properties values = new Properties();
        values.setProperty("pid", Long.toString(pid));
        values.setProperty("log", logFile.toString());
        values.setProperty("ready", readyFile.toString());
        values.setProperty("started", Long.toString(System.currentTimeMillis()));
        values.setProperty("processStart", Long.toString(Platform.startTime(pid)));
        try { FileUtil.writeProperties(sessionFile, values, "Running Vibe session"); } catch (IOException ignored) { /* optional */ }
    }

    private void startThreads(long offset) {
        final Tailer tailer = new Tailer(offset);
        Thread output = new Thread(tailer, "Vibe output");
        output.setDaemon(true);
        output.start();
        Thread waiter = new Thread(new Runnable() {
            @Override public void run() {
                Integer code = null;
                try {
                    if (process != null) code = process.waitFor();
                    else while (Platform.isAlive(pid)) Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                tailer.finish();
                try { Files.deleteIfExists(sessionFile); } catch (IOException ignored) { /* stale marker is cleaned later */ }
                listener.exited(code, reachedGame, "");
            }
        }, "Vibe process");
        waiter.setDaemon(true);
        waiter.start();
    }

    private void setStage(Stage next) {
        if (stage == next) return;
        stage = next;
        listener.stage(next);
    }

    /** Follows the log file, decoding UTF-8 across read boundaries. */
    private final class Tailer implements Runnable {
        private long position;
        private volatile boolean finished;
        private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
        private final ByteBuffer bytes = ByteBuffer.allocate(64 * 1024);
        private final CharBuffer chars = CharBuffer.allocate(64 * 1024);
        private final StringBuilder partial = new StringBuilder();
        private boolean afterCarriageReturn;

        Tailer(long offset) { this.position = offset; }

        void finish() {
            synchronized (this) {
                finished = true;
                if (!reachedGame && Files.exists(readyFile)) reachedGame = true;
                poll();
                if (partial.length() > 0) {
                    List<String> last = new ArrayList<String>();
                    last.add(partial.toString());
                    partial.setLength(0);
                    deliver(last);
                }
            }
        }

        @Override public void run() {
            while (!finished) {
                synchronized (this) {
                    // Under the lock, so a game that already exited is never reported as running.
                    if (finished) return;
                    poll();
                    if (!reachedGame && Files.exists(readyFile)) {
                        reachedGame = true;
                        setStage(Stage.RUNNING);
                    }
                }
                try { Thread.sleep(120); } catch (InterruptedException ignored) { return; }
            }
        }

        private void poll() {
            if (!Files.exists(logFile)) return;
            List<String> lines = new ArrayList<String>();
            try {
                RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r");
                try {
                    if (file.length() < position) position = 0;
                    file.seek(position);
                    while (true) {
                        int read = file.read(bytes.array(), bytes.position(), bytes.remaining());
                        if (read <= 0) break;
                        position += read;
                        bytes.position(bytes.position() + read);
                        bytes.flip();
                        decoder.decode(bytes, chars, false);
                        bytes.compact();
                        chars.flip();
                        while (chars.hasRemaining()) {
                            char c = chars.get();
                            // A lone \r redraws a progress line in a terminal; treat it as a line of its own.
                            if (c == '\n' && afterCarriageReturn) {
                                afterCarriageReturn = false;
                            } else if (c == '\n' || c == '\r') {
                                lines.add(partial.toString());
                                partial.setLength(0);
                                afterCarriageReturn = c == '\r';
                            } else {
                                afterCarriageReturn = false;
                                if (partial.length() < 8192) partial.append(c);
                            }
                        }
                        chars.clear();
                    }
                } finally {
                    file.close();
                }
            } catch (IOException ignored) {
                // The file may be briefly locked; the next poll continues.
            }
            if (!lines.isEmpty()) deliver(lines);
        }

        private void deliver(List<String> lines) {
            for (String line : lines) analyzer.accept(line);
            listener.output(lines);
        }
    }
}
