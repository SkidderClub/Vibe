package dev.vibe.launcher;

import dev.vibe.launcher.app.LauncherController;
import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.AppPaths;
import dev.vibe.launcher.core.Http;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Settings;
import dev.vibe.launcher.game.GameProfile;
import dev.vibe.launcher.ui.LauncherFrame;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.util.Arrays;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Vibe Launcher: installs, updates and starts the Vibe Forge 1.8.9 client.
 *
 * <p>The launcher keeps a private checkout of {@code SkidderClub/Vibe}, private
 * Java runtimes and Vibe's persistent game profile under its data folder, and
 * builds Vibe with Gradle right before each launch. It never handles account
 * credentials: it reads names from Vibe's encrypted vault and tells Vibe which
 * account to use through a small bridge file.</p>
 */
public final class VibeLauncher {
    public static final String VERSION = "2.0.0";
    public static final String PRODUCT = "Vibe Launcher";
    public static final String OWNER = "SkidderClub";
    public static final String REPOSITORY = "Vibe";
    public static final String BRANCH = "main";
    public static final String REPOSITORY_URL = "https://github.com/" + OWNER + "/" + REPOSITORY;
    public static final String DISCORD_URL = "https://dsc.gg/vibe-skidder-club";

    private static FileLock instanceLock;

    private VibeLauncher() { }

    public static void main(String[] args) {
        System.setProperty("java.net.useSystemProxies", "true");
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println(PRODUCT + " needs a desktop session.");
            System.exit(1);
        }
        Http.setUserAgent("VibeLauncher/" + VERSION + " (+" + REPOSITORY_URL + ")");
        final AppPaths paths = AppPaths.detect();
        final AppLog log = new AppLog(paths.logs());
        log.info(PRODUCT + " " + VERSION + " starting on Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name")
                + " " + System.getProperty("os.arch") + "; data in " + paths.root());
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> log.error("Uncaught error on " + thread.getName(), error));

        final Settings settings = new Settings(paths.settingsFile(), log);
        I18n.init(settings.language(), new GameProfile(paths.profile()).clientLanguage());

        boolean restarted = Arrays.asList(args).contains("--wait-for-lock");
        if (!acquireLock(paths, restarted ? 15000 : 0)) {
            log.info("Another launcher is already running; exiting.");
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, I18n.t("Vibe Launcher is already open."), PRODUCT, JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
            });
            return;
        }

        SwingUtilities.invokeLater(() -> {
            LauncherController controller = new LauncherController(paths, settings, log);
            LauncherFrame frame = new LauncherFrame(controller);
            frame.setVisible(true);
            controller.start();
        });
    }

    /** One launcher per data folder: two would update and build the same checkout at once. */
    private static boolean acquireLock(AppPaths paths, long waitMillis) {
        long deadline = System.currentTimeMillis() + waitMillis;
        try {
            Files.createDirectories(paths.root());
            @SuppressWarnings("resource")
            FileChannel channel = new RandomAccessFile(paths.lockFile().toFile(), "rw").getChannel();
            while (true) {
                try {
                    instanceLock = channel.tryLock();
                } catch (OverlappingFileLockException ignored) {
                    instanceLock = null;
                }
                if (instanceLock != null) return true;
                if (System.currentTimeMillis() >= deadline) {
                    channel.close();
                    return false;
                }
                Thread.sleep(250);
            }
        } catch (IOException error) {
            // Without a lock file the launcher still works; it just cannot detect a second copy.
            return true;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
