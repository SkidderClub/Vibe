package dev.vibe.launcher.ui;

import dev.vibe.launcher.VibeLauncher;
import dev.vibe.launcher.app.LauncherController;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Settings;
import dev.vibe.launcher.core.Text;
import dev.vibe.launcher.install.LauncherUpdater;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.nio.file.Path;
import javax.swing.JPanel;

/** Game options, updates, Java runtimes, folders and troubleshooting. */
final class SettingsPage extends JPanel {
    private static final long serialVersionUID = 1L;
    private final LauncherController controller;
    private final Label memoryValue = new Label("", Style.bodyBold(), Label.Tone.ACCENT);
    private final Label sourceStatus = new Label("", Style.small(), Label.Tone.MUTED, 3);
    private final Label launcherStatus = new Label("", Style.small(), Label.Tone.MUTED, 2);
    private final Label java8 = new Label("", Style.small(), Label.Tone.MUTED);
    private final Label jdk21 = new Label("", Style.small(), Label.Tone.MUTED);
    private final FlatButton updateVibe, updateLauncher;
    private final JPanel folders = new JPanel(new GridLayout(0, 3, 10, 10));

    SettingsPage(LauncherController controller) {
        super(new BorderLayout());
        this.controller = controller;
        setOpaque(false);
        Settings settings = controller.settings();

        Stack.Panel body = Pages.body(18);
        body.add(Pages.header(I18n.t("Settings"), I18n.t("How Vibe starts, how it stays up to date and where its files live.")));

        // Game
        Card game = Pages.section(I18n.t("Game"), Icons.CUBE);
        int maxMemory = Settings.maxMemoryMb();
        Slider memory = new Slider(1024, maxMemory, 256, settings.memoryMb(), value -> memoryValue.setText(memoryLabel(value)), settings::setMemoryMb);
        memoryValue.setText(memoryLabel(settings.memoryMb()));
        Stack.Panel memoryControl = Stack.row(14);
        memoryControl.add(memory);
        memoryControl.add(memoryValue);
        memoryValue.setPreferredSize(new java.awt.Dimension(64, 24));
        game.add(Pages.setting(I18n.t("Memory"), I18n.t("Maximum RAM for Minecraft. 3–4 GB suit Vibe with shaders and cosmetics; more rarely helps."), memoryControl));
        game.add(Pages.divider());
        Settings.AfterLaunch after = settings.afterLaunch();
        Segmented afterLaunch = new Segmented(new String[] { I18n.t("Keep open"), I18n.t("Minimize"), I18n.t("Close") }, after.ordinal(),
                index -> settings.setAfterLaunch(Settings.AfterLaunch.values()[index]));
        game.add(Pages.setting(I18n.t("When Minecraft starts"), I18n.t("Closing the launcher never closes the game."), afterLaunch));
        body.add(game);

        // Updates
        Card updates = Pages.section(I18n.t("Updates"), Icons.DOWNLOAD);
        Toggle auto = new Toggle(settings.autoUpdate(), on -> { settings.setAutoUpdate(on); refresh(); });
        updates.add(Pages.setting(I18n.t("Update Vibe automatically"), I18n.t("Before each launch, download the newest Vibe from GitHub. Only changed files are downloaded."), auto));
        updates.add(Pages.divider());
        updateVibe = new FlatButton(I18n.t("Update now"), Icons.DOWNLOAD, FlatButton.Kind.PRIMARY, controller::updateVibeNow);
        FlatButton check = new FlatButton(I18n.t("Check now"), Icons.REFRESH, FlatButton.Kind.SECONDARY, controller::checkForUpdates);
        Stack.Panel sourceButtons = Stack.row(8);
        sourceButtons.add(updateVibe);
        sourceButtons.add(check);
        Stack.Panel sourceRow = Stack.row(18);
        Stack.Panel sourceText = Stack.column(3);
        sourceText.add(new Label(I18n.t("Vibe"), Style.bodyBold(), Label.Tone.TEXT));
        sourceText.add(sourceStatus);
        sourceRow.add(sourceText, Stack.FILL);
        sourceRow.add(sourceButtons);
        updates.add(sourceRow);
        updates.add(Pages.divider());
        updateLauncher = new FlatButton(I18n.t("Install update"), Icons.DOWNLOAD, FlatButton.Kind.PRIMARY, controller::installLauncherUpdate);
        Stack.Panel launcherRow = Stack.row(18);
        Stack.Panel launcherText = Stack.column(3);
        launcherText.add(new Label(I18n.t("Launcher {0}", VibeLauncher.VERSION), Style.bodyBold(), Label.Tone.TEXT));
        launcherText.add(launcherStatus);
        launcherRow.add(launcherText, Stack.FILL);
        launcherRow.add(updateLauncher);
        updates.add(launcherRow);
        body.add(updates);

        // Java
        Card java = Pages.section(I18n.t("Java"), Icons.JAVA);
        java.add(Label.small(I18n.t("The launcher keeps its own Eclipse Temurin runtimes, verified by checksum: Java 8 runs Minecraft 1.8.9, Java 21 builds Vibe.")));
        Stack.Panel javaRow = Stack.row(18);
        Stack.Panel javaText = Stack.column(4);
        javaText.add(java8);
        javaText.add(jdk21);
        javaRow.add(javaText, Stack.FILL);
        javaRow.add(new FlatButton(I18n.t("Reinstall"), Icons.REFRESH, FlatButton.Kind.SECONDARY, () -> {
            if (Dialogs.confirm(this, I18n.t("Reinstall Java?"), I18n.t("Both runtimes are deleted and downloaded again (about 250 MB)."), I18n.t("Reinstall"), false)) controller.repairRuntimes();
        }));
        java.add(javaRow);
        body.add(java);

        // Folders
        Card files = Pages.section(I18n.t("Folders"), Icons.FOLDER);
        folders.setOpaque(false);
        addFolder(I18n.t("Game folder"), controller.profile().directory());
        addFolder(I18n.t("Mods"), controller.profile().mods());
        addFolder(I18n.t("Resource packs"), controller.profile().resourcePacks());
        addFolder(I18n.t("Screenshots"), controller.profile().screenshots());
        addFolder(I18n.t("Crash reports"), controller.profile().crashReports());
        addFolder(I18n.t("Launcher data"), controller.paths().root());
        files.add(folders);
        files.add(Pages.divider());
        files.add(Pages.setting(I18n.t("Import from Minecraft"), I18n.t("Copy options.txt and resource packs from your normal .minecraft folder. Nothing in Vibe's profile is overwritten."),
                new FlatButton(I18n.t("Import"), Icons.DOWNLOAD, FlatButton.Kind.SECONDARY, controller::importMinecraftSettings)));
        body.add(files);

        // Troubleshooting
        Card repair = Pages.section(I18n.t("Troubleshooting"), Icons.WARNING);
        repair.add(Pages.setting(I18n.t("Clear build cache"), I18n.t("Fixes builds that fail after an update. The next launch compiles Vibe from scratch."),
                new FlatButton(I18n.t("Clear"), Icons.BROOM, FlatButton.Kind.SECONDARY, controller::cleanBuild)));
        repair.add(Pages.divider());
        repair.add(Pages.setting(I18n.t("Reinstall Vibe"), I18n.t("Downloads the complete Vibe source again. Worlds, settings, accounts and mods are kept."),
                new FlatButton(I18n.t("Reinstall"), Icons.REFRESH, FlatButton.Kind.SECONDARY, () -> {
                    if (Dialogs.confirm(this, I18n.t("Reinstall Vibe?"), I18n.t("The source is downloaded again. Your game profile stays untouched."), I18n.t("Reinstall"), false)) controller.reinstallSource();
                })));
        repair.add(Pages.divider());
        repair.add(Pages.setting(I18n.t("Launcher log"), Text.shorten(controller.logFile().toString(), 90),
                new FlatButton(I18n.t("Open"), Icons.EXTERNAL, FlatButton.Kind.SECONDARY, () -> controller.openPath(controller.logFile()))));
        body.add(repair);

        add(Scroll.of(body), BorderLayout.CENTER);
        controller.addListener(event -> {
            switch (event) {
                case SOURCE: case LAUNCHER_UPDATE: case STATE: case SETTINGS: refresh(); break;
                default: break;
            }
        });
        refresh();
    }

    void setColumns(int columns) {
        ((GridLayout) folders.getLayout()).setColumns(columns);
        folders.revalidate();
    }

    private void addFolder(String label, Path path) {
        FlatButton button = new FlatButton(label, Icons.FOLDER, FlatButton.Kind.SECONDARY, () -> controller.openPath(path));
        button.setToolTipText(path.toString());
        folders.add(button);
    }

    private static String memoryLabel(int megabytes) {
        return megabytes % 1024 == 0 ? megabytes / 1024 + " GB" : String.format(java.util.Locale.ROOT, "%.2f GB", megabytes / 1024.0).replace(".00", "");
    }

    private void refresh() {
        boolean idle = controller.state() == LauncherController.State.IDLE;
        String revision = controller.installedRevision();
        StringBuilder source = new StringBuilder();
        if (!controller.sourceInstalled()) {
            source.append(I18n.t("Not installed yet. It is downloaded on the first launch."));
        } else {
            source.append(I18n.t("Installed: {0}", Text.shortSha(revision)));
            String message = controller.settings().sourceMessage();
            if (!message.isEmpty()) source.append(" · “").append(Text.shorten(message, 60)).append("”");
            long time = controller.settings().sourceCommitTime();
            if (time > 0) source.append(" · ").append(I18n.ago(time));
            source.append('\n');
            if (controller.remote() == null) source.append(controller.remoteError().isEmpty() ? I18n.t("Checking GitHub…") : I18n.t("GitHub not reachable: {0}", controller.remoteError()));
            else if (controller.sourceUpdateAvailable()) source.append(I18n.t("A newer version is available: {0}", Text.shortSha(controller.remote().head)));
            else source.append(I18n.t("Up to date."));
        }
        sourceStatus.setText(source.toString());
        sourceStatus.setTone(controller.sourceUpdateAvailable() ? Label.Tone.WARNING : Label.Tone.MUTED);
        updateVibe.setVisible(controller.sourceUpdateAvailable() && !controller.settings().autoUpdate());
        updateVibe.setEnabled(idle);

        LauncherUpdater.Release release = controller.launcherUpdate();
        launcherStatus.setText(controller.launcherUpdateStatus().isEmpty() ? I18n.t("Checking GitHub…") : controller.launcherUpdateStatus());
        updateLauncher.setVisible(release != null);
        updateLauncher.setEnabled(idle);

        String java8Home = controller.settings().java8Home(), jdk21Home = controller.settings().jdk21Home();
        java8.setText("Java 8:  " + (java8Home.isEmpty() ? I18n.t("installed on first launch") : java8Home));
        jdk21.setText("Java 21:  " + (jdk21Home.isEmpty() ? I18n.t("installed on first launch") : jdk21Home));
        revalidate();
        repaint();
    }
}
