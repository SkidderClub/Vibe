package dev.vibe.launcher.ui;

import dev.vibe.launcher.Check;
import dev.vibe.launcher.app.LauncherController;
import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.AppPaths;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Settings;
import dev.vibe.launcher.game.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Headless page rendering plus a real switch -> controller -> persisted choice check. */
public final class ExtensionUiTests {
    private ExtensionUiTests() { }

    public static void run(Check check, Path temp) {
        check.test("extension page renders at both window sizes and persists its switch", () -> {
            String previousHome = System.getProperty("vibe.launcher.home");
            System.setProperty("vibe.launcher.home", temp.resolve("ui-profile").toString());
            AppPaths paths;
            try { paths = AppPaths.detect(); }
            finally {
                if (previousHome == null) System.clearProperty("vibe.launcher.home");
                else System.setProperty("vibe.launcher.home", previousHome);
            }
            AppLog log = new AppLog(paths.logs());
            LauncherController controller = new LauncherController(paths, new Settings(paths.settingsFile(), log), log);
            try {
                Path output = Paths.get(System.getProperty("vibe.launcher.renderDir", "build/launcher-extension-ui"));
                Files.createDirectories(output);
                final ExtensionsPage[] page = new ExtensionsPage[1];
                SwingUtilities.invokeAndWait(() -> {
                    I18n.init("en", ""); Anim.enabled = false; Style.setTheme(Theme.LAVENDER);
                    page[0] = new ExtensionsPage(controller);
                    List<Toggle> switches = new ArrayList<Toggle>();
                    collect(page[0], Toggle.class, switches);
                    Check.equal(2, switches.size());
                    Check.isTrue(switches.get(0).isOn() && !switches.get(1).isOn(), "Cosmetica is on by default; example is off");
                    List<FlatButton> buttons = new ArrayList<FlatButton>();
                    collect(page[0], FlatButton.class, buttons);
                    Check.isTrue(buttons.stream().anyMatch(button -> "Add JAR".equals(button.getText())), "generic Add JAR button is present");
                    Check.equal(2, controller.extensions().size());
                    render(controller, page[0], 1240, 780, output.resolve("extensions-wide.png"));
                    render(controller, page[0], 940, 600, output.resolve("extensions-compact.png"));
                });
                CountDownLatch changed = new CountDownLatch(1);
                controller.addListener(event -> { if (event == LauncherController.Event.MODS) changed.countDown(); });
                SwingUtilities.invokeAndWait(() -> {
                    List<Toggle> switches = new ArrayList<Toggle>(); collect(page[0], Toggle.class, switches);
                    switches.get(0).dispatchEvent(new MouseEvent(switches.get(0), MouseEvent.MOUSE_CLICKED,
                            System.currentTimeMillis(), 0, 10, 10, 1, false));
                });
                Check.isTrue(changed.await(5, TimeUnit.SECONDS), "controller applied the switch");
                Check.isTrue(!controller.extensions().get(0).enabled, "Cosmetica opt-out persists for next launch");
                SwingUtilities.invokeAndWait(() -> {
                    render(controller, page[0], 1240, 780, output.resolve("extensions-disabled.png"));
                    I18n.init("de", "");
                    render(controller, new ExtensionsPage(controller), 940, 600, output.resolve("extensions-german.png"));
                    I18n.init("en", "");
                });
            } finally { controller.shutdown(); }
        });
    }

    private static <T> void collect(Container root, Class<T> type, List<T> result) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container) collect((Container) child, type, result);
        }
    }

    private static void layout(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) if (child instanceof Container) layout((Container) child);
    }

    private static void render(LauncherController controller, ExtensionsPage page, int width, int height, Path file) {
        JPanel shell = new JPanel(new BorderLayout()); shell.setBackground(Style.background());
        Sidebar sidebar = new Sidebar(controller, ignored -> { }); sidebar.setCompact(width < 1100); sidebar.select(Page.EXTENSIONS);
        shell.add(sidebar, BorderLayout.WEST); shell.add(page, BorderLayout.CENTER);
        shell.setSize(width, height); layout(shell); layout(shell);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { shell.printAll(graphics); }
        finally { graphics.dispose(); }
        try { ImageIO.write(image, "png", file.toFile()); }
        catch (java.io.IOException error) { throw new RuntimeException(error); }
    }
}
