package dev.vibe.launcher;

import dev.vibe.launcher.app.LauncherController;
import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.AppPaths;
import dev.vibe.launcher.core.ErrorCode;
import dev.vibe.launcher.core.FileUtil;
import dev.vibe.launcher.core.Http;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Settings;
import dev.vibe.launcher.core.Text;
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
            System.err.println(PRODUCT + " needs a desktop session (" + ErrorCode.NO_DESKTOP.id() + "). " + ErrorCode.NO_DESKTOP.englishTitle() + ".");
            System.exit(1);
        }
        Http.setUserAgent("VibeLauncher/" + VERSION + " (+" + REPOSITORY_URL + ")");
        final AppPaths paths = AppPaths.detect();
        final AppLog log = new AppLog(paths.logs());
        log.info(PRODUCT + " " + VERSION + " starting on Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name")
                + " " + System.getProperty("os.arch") + "; data in " + paths.root());
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> log.error(ErrorCode.UNEXPECTED.id() + " Uncaught error on " + thread.getName(), error));

        final Settings settings = new Settings(paths.settingsFile(), log);
        I18n.init(settings.language(), new GameProfile(paths.profile()).clientLanguage());

        // Without a writable data folder nothing can be installed; say so instead of failing later.
        IOException unwritable = checkWritable(paths);
        if (unwritable != null) {
            log.error(ErrorCode.DATA_FOLDER.id() + " The data folder " + paths.root() + " is not writable", unwritable);
            fatal(ErrorCode.DATA_FOLDER, I18n.t("The launcher cannot write to {0}.", paths.root()) + "\n" + Text.describe(unwritable));
            return;
        }

        boolean restarted = Arrays.asList(args).contains("--wait-for-lock");
        if (!acquireLock(paths, restarted ? 15000 : 0)) {
            log.info(ErrorCode.ALREADY_RUNNING.id() + " Another launcher is already running; exiting.");
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, I18n.t("Vibe Launcher is already open.") + " (" + ErrorCode.ALREADY_RUNNING.id() + ")\n\n"
                        + ErrorCode.ALREADY_RUNNING.hint(), PRODUCT, JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
            });
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                LauncherController controller = new LauncherController(paths, settings, log);
                LauncherFrame frame = new LauncherFrame(controller);
                frame.setVisible(true);
                controller.start();
            } catch (RuntimeException | Error error) {
                log.error(ErrorCode.STARTUP_FAILED.id() + " The launcher could not start", error);
                fatal(ErrorCode.STARTUP_FAILED, Text.describe(error) + "\n" + I18n.t("Launcher log: {0}", log.file()));
            }
        });
    }

    /** Creates the data folder and writes a probe file; returns the failure, if any. */
    private static IOException checkWritable(AppPaths paths) {
        try {
            Files.createDirectories(paths.root());
            java.nio.file.Path probe = paths.root().resolve(".write-test");
            FileUtil.writeText(probe, "ok");
            Files.deleteIfExists(probe);
            return null;
        } catch (IOException error) {
            return error;
        } catch (RuntimeException error) {
            return new IOException(Text.describe(error), error);
        }
    }

    /** An error dialog for problems that stop the launcher before its window exists, then exit. */
    private static void fatal(final ErrorCode code, final String detail) {
        Runnable show = () -> {
            JOptionPane.showMessageDialog(null, code.heading() + "\n\n" + detail + "\n\n" + code.hint() + "\n\n" + I18n.t("Help: {0}", code.helpUrl()),
                    PRODUCT, JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        };
        if (SwingUtilities.isEventDispatchThread()) show.run();
        else SwingUtilities.invokeLater(show);
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
