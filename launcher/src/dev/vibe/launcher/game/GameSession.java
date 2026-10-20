package dev.vibe.launcher.game;

import dev.vibe.launcher.core.ErrorCode;
import dev.vibe.launcher.core.FileUtil;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.LauncherException;
import dev.vibe.launcher.core.Platform;
import dev.vibe.launcher.core.Text;
import java.io.File;
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
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * One Gradle {@code runClient} run: builds Vibe and starts Forge 1.8.9 with
 * OptiFine in the persistent profile.
 *
 * <p>Output goes straight into a log file instead of a pipe, so Minecraft keeps
 * running even if the launcher is closed; the launcher tails that file for its
 * console and progress. Vibe writes {@code VIBE_LAUNCH_READY_FILE} when its first
 * screen is drawn, which is how the launcher knows the game window is up.</p>
 */
public final class GameSession {
    public enum Stage { CONFIGURING, COMPILING, PACKAGING, OPTIFINE, ASSETS, STARTING, RUNNING }

    public interface Listener {
        void output(List<String> lines);
        void stage(Stage stage);
        /** @param code the exit code, or {@code null} when it is unknown (re-attached session) */
        void exited(Integer code, boolean reachedGame, String failure);
    }

    public static final class Request {
        public Path projectRoot, java8, jdk21, logFile, readyFile, sessionFile;
        public int memoryMb;
    }

    private final Path logFile, readyFile, sessionFile, projectRoot;
    private final Listener listener;
    private final Process process;
    private final long pid;
    private volatile boolean reachedGame;
    private volatile boolean stopping;
    private volatile Stage stage;
    private final List<String> failure = new ArrayList<String>();
    private boolean collectingFailure;
    private final FailureAnalyzer analyzer = new FailureAnalyzer();

    private GameSession(Path projectRoot, Path logFile, Path readyFile, Path sessionFile, Listener listener, Process process, long pid) {
        this.projectRoot = projectRoot;
        this.logFile = logFile;
        this.readyFile = readyFile;
        this.sessionFile = sessionFile;
        this.listener = listener;
        this.process = process;
        this.pid = pid;
    }

    public static GameSession start(Request request, Listener listener) throws IOException {
        Files.createDirectories(request.logFile.getParent());
        Files.createDirectories(request.readyFile.getParent());
        Files.deleteIfExists(request.readyFile);

        // Start the Gradle wrapper directly, as gradlew(.bat) would: no cmd.exe or sh in between
        // means no shell quoting, no LF-only batch file quirks, and destroy() reaches Gradle itself.
        Path wrapper = request.projectRoot.resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.jar");
        if (!Files.isRegularFile(wrapper)) throw new LauncherException(ErrorCode.SOURCE_DAMAGED, I18n.t("The Vibe source has no Gradle wrapper ({0}).", wrapper));
        Path java = Platform.javaExecutable(request.jdk21, false);
        if (!Files.isRegularFile(java)) throw new LauncherException(ErrorCode.JAVA_BROKEN, I18n.t("Java 21 is missing at {0}.", java));
        List<String> command = new ArrayList<String>();
        command.add(java.toString());
        command.add("-Xmx64m");
        command.add("-Xms64m");
        command.add("-Dorg.gradle.appname=gradlew");
        command.add("-classpath");
        command.add(wrapper.toString());
        command.add("org.gradle.wrapper.GradleWrapperMain");
        command.add("runClient");
        command.add("-PvibeOptifine");
        command.add("-PvibePersistentRun");
        command.add("-PvibeMaxMemory=" + request.memoryMb);
        command.add("--no-daemon");
        command.add("--console=plain");
        // Toolchains come from environment variables so installation paths never need shell quoting.
        command.add("-Porg.gradle.java.installations.fromEnv=VIBE_JAVA8_HOME,VIBE_JDK21_HOME");
        command.add("-Porg.gradle.java.installations.auto-download=false");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(request.projectRoot.toFile());
        Map<String, String> environment = builder.environment();
        put(environment, "JAVA_HOME", request.jdk21.toString());
        String path = get(environment, "PATH");
        put(environment, "PATH", request.jdk21.resolve("bin") + File.pathSeparator + (path == null ? "" : path));
        environment.put("VIBE_JAVA8", request.java8.toString());
        environment.put("VIBE_JAVA8_HOME", request.java8.toString());
        environment.put("VIBE_JDK21_HOME", request.jdk21.toString());
        environment.put("VIBE_LAUNCH_READY_FILE", request.readyFile.toString());
        environment.put("VIBE_NO_PAUSE", "1");
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(request.logFile.toFile()));
        builder.redirectInput(ProcessBuilder.Redirect.from(Platform.nullDevice()));

        long offset = Files.exists(request.logFile) ? Files.size(request.logFile) : 0;
        Process process;
        try {
            process = builder.start();
        } catch (IOException error) {
            // "Cannot run program": blocked by an antivirus, not executable, or a broken runtime.
            throw new LauncherException(ErrorCode.JAVA_START, I18n.t("Java 21 could not be started: {0}", Text.describe(error)), error);
        }
        long pid = Platform.pid(process);
        GameSession session = new GameSession(request.projectRoot, request.logFile, request.readyFile, request.sessionFile, listener, process, pid);
        session.writeSessionFile();
        session.startThreads(offset);
        return session;
    }

    /**
     * ProcessBuilder's environment is a case-sensitive map even on Windows, where the
     * variable is usually spelled "Path"; adding "PATH" next to it would hide it.
     */
    static String get(Map<String, String> environment, String name) {
        for (Map.Entry<String, String> entry : environment.entrySet()) if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        return null;
    }

    static void put(Map<String, String> environment, String name, String value) {
        String key = name;
        for (String existing : new ArrayList<String>(environment.keySet())) {
            if (!existing.equalsIgnoreCase(name)) continue;
            if (key.equals(name)) key = existing;
            else environment.remove(existing);
        }
        environment.put(key, value);
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
        Path log = new File(values.getProperty("log", "")).toPath();
        Path ready = new File(values.getProperty("ready", "")).toPath();
        GameSession session = new GameSession(null, log, ready, sessionFile, listener, null, pid);
        session.reachedGame = Files.exists(ready);
        // Replaying the log tail must not move a running game back to a build stage.
        if (session.reachedGame) session.stage = Stage.RUNNING;
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
        // Java 8 cannot see child processes: find Minecraft by path first, while the session is still open.
        if (!Platform.processApiAvailable() && projectRoot != null) Platform.destroyGameProcesses(projectRoot);
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
                listener.exited(code, reachedGame, failureSummary());
            }
        }, "Vibe process");
        waiter.setDaemon(true);
        waiter.start();
    }

    private synchronized String failureSummary() {
        if (failure.isEmpty()) return "";
        StringBuilder text = new StringBuilder();
        for (String line : failure) {
            if (text.length() > 0) text.append('\n');
            text.append(line);
        }
        return text.toString();
    }

    private void inspect(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("> Configure project") && stage == null) setStage(Stage.CONFIGURING);
        else if (trimmed.startsWith("> Task :")) {
            String task = trimmed.substring(8).split("\\s+")[0].toLowerCase(Locale.ROOT);
            if (task.equals("runclient")) setStage(Stage.STARTING);
            else if (task.equals("prerunclient") || task.contains("assets") || task.contains("natives")) setStage(Stage.ASSETS);
            else if (task.equals("prepareoptifine")) setStage(Stage.OPTIFINE);
            else if (task.endsWith("jar") || task.startsWith("remap")) setStage(Stage.PACKAGING);
            else if (task.contains("compile") || task.contains("resources") || task.contains("classes") || task.startsWith("generate")) setStage(Stage.COMPILING);
            else if (stage == null) setStage(Stage.CONFIGURING);
        }
        Stage current = stage;
        analyzer.accept(line, current != null && current.ordinal() >= Stage.STARTING.ordinal());
        synchronized (this) {
            if (trimmed.startsWith("* What went wrong")) { collectingFailure = true; failure.clear(); return; }
            if (collectingFailure) {
                // The deepest cause comes last, e.g. "repo.maven.apache.org: Name or service not known".
                if (trimmed.startsWith("* Try") || trimmed.startsWith("* Get more help") || failure.size() >= 16) collectingFailure = false;
                else if (!trimmed.isEmpty()) failure.add(trimmed.startsWith("> ") ? trimmed.substring(2) : trimmed);
            }
        }
    }

    private void setStage(Stage next) {
        if (stage != null && next.ordinal() <= stage.ordinal()) return;
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
            for (String line : lines) inspect(line);
            listener.output(lines);
        }
    }
}
