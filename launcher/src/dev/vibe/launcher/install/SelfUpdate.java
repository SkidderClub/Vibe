package dev.vibe.launcher.install;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Finishes a launcher update in a separate JVM, started from a copy of the old JAR:
 * waits until the old launcher has released its instance lock, replaces the JAR
 * and starts the launcher again. Paths arrive as arguments, so any characters in
 * them are safe, and the launcher is restarted even if the replacement failed.
 */
public final class SelfUpdate {
    private SelfUpdate() { }

    /** Arguments: staged JAR, launcher JAR, java executable, instance lock file. */
    public static void main(String[] args) throws Exception {
        if (args.length != 4) return;
        Path staged = Paths.get(args[0]), current = Paths.get(args[1]), lock = Paths.get(args[3]);
        String java = args[2];
        waitForLock(lock, 60000);
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                replace(staged, current);
                break;
            } catch (IOException retry) {
                Thread.sleep(1000);
            }
        }
        new ProcessBuilder(java, "-jar", current.toString(), "--wait-for-lock").start();
    }

    private static void replace(Path staged, Path current) throws IOException {
        Path temporary = current.resolveSibling(current.getFileName() + ".new");
        Files.copy(staged, temporary, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(temporary, current, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, current, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** The old launcher holds this lock until its JVM exits. */
    private static void waitForLock(Path file, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try {
                FileChannel channel = new RandomAccessFile(file.toFile(), "rw").getChannel();
                try {
                    FileLock lock = channel.tryLock();
                    if (lock != null) {
                        lock.release();
                        return;
                    }
                } finally {
                    channel.close();
                }
            } catch (Exception ignored) {
                // Retry until the deadline.
            }
            Thread.sleep(250);
        }
    }
}
