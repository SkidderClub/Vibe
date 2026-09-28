package dev.vibe.launcher.app;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;

/**
 * The console history: launcher messages and game output, capped in size.
 * Appends from any thread are coalesced into one UI update per frame.
 */
public final class ConsoleBuffer {
    public interface Listener {
        void appended(List<String> lines);
        void cleared();
    }

    private static final int LIMIT = 6000;
    private final ArrayDeque<String> lines = new ArrayDeque<String>();
    private final List<String> pending = new ArrayList<String>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private boolean scheduled;
    private volatile int errors;

    public void addListener(Listener listener) { listeners.add(listener); }

    public synchronized List<String> snapshot() { return new ArrayList<String>(lines); }

    public int unseenErrors() { return errors; }
    public void markSeen() { errors = 0; }

    public void append(String line) {
        List<String> one = new ArrayList<String>(1);
        one.add(line);
        append(one);
    }

    public void append(List<String> added) {
        synchronized (this) {
            for (String line : added) {
                lines.addLast(line);
                if (lines.size() > LIMIT) lines.removeFirst();
                if (isError(line)) errors++;
            }
            pending.addAll(added);
            if (scheduled) return;
            scheduled = true;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                List<String> batch;
                synchronized (ConsoleBuffer.this) {
                    batch = new ArrayList<String>(pending);
                    pending.clear();
                    scheduled = false;
                }
                for (Listener listener : listeners) listener.appended(batch);
            }
        });
    }

    public void clear() {
        synchronized (this) {
            lines.clear();
            pending.clear();
            errors = 0;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() { for (Listener listener : listeners) listener.cleared(); }
        });
    }

    public static boolean isError(String line) {
        return line.contains("/ERROR]") || line.contains("[ERROR]") || line.startsWith("FAILURE:") || line.contains("BUILD FAILED")
                || line.contains("Exception in thread") || line.startsWith("[Launcher] ERROR");
    }

    public static boolean isWarning(String line) {
        return line.contains("/WARN]") || line.contains("[WARN]") || line.startsWith("[Launcher] WARN");
    }
}
