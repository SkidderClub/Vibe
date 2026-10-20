package dev.vibe.launcher.core;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CopyOnWriteArrayList;

/** Small rolling launcher log. Game output is written to a separate file per session. */
public final class AppLog {
    public interface Listener { void logged(String level, String message); }

    private static final long MAX_BYTES = 2L * 1024L * 1024L;
    private final Path file;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public AppLog(Path directory) {
        file = directory.resolve("launcher.log");
        try {
            Files.createDirectories(directory);
            if (Files.exists(file) && Files.size(file) > MAX_BYTES) {
                Files.move(file, directory.resolve("launcher.1.log"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Logging is diagnostic only; the launcher keeps working without it.
        }
    }

    public Path file() { return file; }
    public void addListener(Listener listener) { listeners.add(listener); }

    public void info(String message) { write("INFO", message, null); }
    public void warn(String message) { write("WARN", message, null); }
    public void warn(String message, Throwable error) { write("WARN", message, error); }
    public void error(String message, Throwable error) { write("ERROR", message, error); }

    private synchronized void write(String level, String message, Throwable error) {
        String clean = message == null ? "" : message.replace('\r', ' ').replace('\n', ' ');
        StringBuilder line = new StringBuilder()
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
                .append(" [").append(level).append("] ").append(clean).append(System.lineSeparator());
        if (error != null) {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            line.append(trace.toString().trim()).append(System.lineSeparator());
        }
        try {
            Files.createDirectories(file.getParent());
            OutputStream output = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            try { output.write(line.toString().getBytes(StandardCharsets.UTF_8)); } finally { output.close(); }
        } catch (IOException ignored) {
            // See constructor.
        }
        String shown = error == null ? clean : clean + ": " + Text.describe(error);
        for (Listener listener : listeners) listener.logged(level, shown);
    }
}
