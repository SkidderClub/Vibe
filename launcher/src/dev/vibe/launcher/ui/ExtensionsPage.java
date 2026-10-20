package dev.vibe.launcher.ui;

import dev.vibe.launcher.app.LauncherController;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Text;
import dev.vibe.launcher.game.ExtensionLibrary;
import java.awt.BorderLayout;
import java.io.File;
import java.util.Arrays;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Extension switches use the same mods folder and persist across client updates. */
final class ExtensionsPage extends JPanel {
    private static final long serialVersionUID = 1L;
    private final LauncherController controller;
    private final Stack.Panel list = Stack.column(10);
    private final Label dropHint = new Label("", Style.bodyBold(), Label.Tone.TEXT);

    ExtensionsPage(LauncherController controller) {
        super(new BorderLayout());
        this.controller = controller;
        setOpaque(false);
        Stack.Panel body = Pages.body(18);
        FlatButton add = new FlatButton(I18n.t("Add JAR"), Icons.PLUS, FlatButton.Kind.PRIMARY, this::chooseFiles);
        FlatButton folder = new FlatButton(I18n.t("Open folder"), Icons.FOLDER, FlatButton.Kind.SECONDARY,
                () -> controller.openPath(controller.modsFolder()));
        body.add(Pages.header(I18n.t("Extensions"), I18n.t("Choose what Vibe includes. Changes take effect on the next launch."), folder, add));
        Card drop = Card.column(8, 22);
        drop.add(dropHint);
        drop.add(Label.small(I18n.t("Install custom extensions from a JAR file.")));
        drop.add(new FlatButton(I18n.t("Browse for a JAR"), Icons.DOWNLOAD, FlatButton.Kind.GHOST, this::chooseFiles));
        body.add(drop);
        body.add(list);
        add(Scroll.of(body), BorderLayout.CENTER);
        controller.addListener(event -> { if (event == LauncherController.Event.MODS) rebuild(); });
        setDragActive(false);
        rebuild();
    }

    void setDragActive(boolean active) {
        dropHint.setText(I18n.t(active ? "Release to install the extension" : "Drop extension JARs anywhere in this window"));
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle(I18n.t("Add JAR"));
        chooser.setFileFilter(new FileNameExtensionFilter(I18n.t("Vibe extensions (*.jar)"), "jar"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] files = chooser.getSelectedFiles();
            controller.importMods(Arrays.asList(files));
        }
    }

    private void rebuild() {
        list.removeAll();
        for (ExtensionLibrary.Entry extension : controller.extensions()) list.add(new ExtensionRow(extension));
        list.revalidate();
        list.repaint();
    }

    private final class ExtensionRow extends Card {
        private static final long serialVersionUID = 1L;

        ExtensionRow(ExtensionLibrary.Entry extension) {
            super(false, 14, 16, 18);
            Stack.Panel text = Stack.column(5);
            Stack.Panel title = Stack.row(10);
            title.add(new Label(extension.name, Style.bodyBold(), Label.Tone.TEXT));
            if (extension.installed != null && !extension.installed.version.isEmpty())
                title.add(new Label(extension.installed.version, Style.small(), Label.Tone.FAINT));
            text.add(title);
            if (!extension.description.isEmpty()) text.add(Label.small(I18n.t(extension.description)));
            String status = extension.installed == null
                    ? (extension.enabled ? "Ready on next launch" : extension.bundled ? "Available with Vibe" : "Not installed")
                    : extension.enabled ? "Enabled" : "Disabled";
            text.add(new Label(I18n.t(status), Style.smallBold(), extension.enabled ? Label.Tone.TEXT : Label.Tone.MUTED));
            if (extension.installed != null) {
                text.add(new Label(extension.installed.fileName + "  ·  " + Text.bytes(extension.installed.size), Style.small(), Label.Tone.FAINT));
                if (!extension.installed.requiredVibeVersion.isEmpty()) text.add(new Label(
                        I18n.t("Requires Vibe {0}", extension.installed.requiredVibeVersion), Style.small(), Label.Tone.FAINT));
            }
            add(text, Stack.FILL);
            Toggle toggle = new Toggle(extension.enabled, on -> controller.setExtensionEnabled(extension, on));
            toggle.setToolTipText(I18n.t(extension.enabled ? "Disable" : "Enable"));
            add(toggle);
            if (extension.installed != null) add(FlatButton.icon(Icons.TRASH, I18n.t("Remove"), () -> {
                if (Dialogs.confirm(this, I18n.t("Remove {0}?", extension.name),
                        I18n.t("The file is moved to the recycle bin where supported, otherwise deleted."), I18n.t("Remove"), true))
                    controller.deleteExtension(extension);
            }));
        }
    }
}
