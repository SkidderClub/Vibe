package dev.vibe.launcher.ui;

import dev.vibe.launcher.app.LauncherController;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Text;
import dev.vibe.launcher.install.ChangelogSection;
import dev.vibe.launcher.versions.ReleaseCatalog;
import dev.vibe.launcher.versions.ReleaseNotes;
import dev.vibe.launcher.versions.VersionStore;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JPanel;

/**
 * Published and installed Vibe versions.
 *
 * <p>Each release can be read before it is used: the notes expand in place, and
 * exactly one installed version is active in {@code mods/} at a time.</p>
 */
final class VersionsPage extends JPanel {
    private static final long serialVersionUID = 1L;
    private final LauncherController controller;
    private final Stack.Panel installed = Stack.column(8);
    private final Stack.Panel available = Stack.column(8);
    private final Label installedSummary = new Label("", Style.smallBold(), Label.Tone.MUTED);
    private final Label availableSummary = new Label("", Style.smallBold(), Label.Tone.MUTED);
    private final Label error = new Label("", Style.small(), Label.Tone.WARNING, 3);
    private final FlatButton refresh, update;
    private final Set<String> expanded = new LinkedHashSet<String>();

    VersionsPage(LauncherController controller) {
        super(new BorderLayout());
        this.controller = controller;
        setOpaque(false);

        refresh = new FlatButton(I18n.t("Refresh"), Icons.REFRESH, FlatButton.Kind.SECONDARY, controller::refreshReleases);
        update = new FlatButton(I18n.t("Update to latest"), Icons.DOWNLOAD, FlatButton.Kind.PRIMARY, controller::updateVibeNow);
        Stack.Panel body = Pages.body(18);
        body.add(Pages.header(I18n.t("Versions"), I18n.t("Download a Vibe release, switch versions and read what changed. Saves, settings and mods are shared."), update, refresh));
        body.add(error);

        Stack.Panel installedHeader = Stack.row(8);
        installedHeader.add(new Label(I18n.t("Installed"), Style.heading(), Label.Tone.TEXT));
        installedHeader.add(installedSummary, Stack.FILL);
        body.add(installedHeader);
        body.add(installed);

        Stack.Panel availableHeader = Stack.row(8);
        availableHeader.add(new Label(I18n.t("Available on GitHub"), Style.heading(), Label.Tone.TEXT));
        availableHeader.add(availableSummary, Stack.FILL);
        body.add(availableHeader);
        body.add(available);

        add(Scroll.of(body), BorderLayout.CENTER);
        controller.addListener(event -> {
            if (event == LauncherController.Event.VERSIONS || event == LauncherController.Event.STATE) rebuild();
        });
        rebuild();
    }

    private void rebuild() {
        boolean idle = controller.state() == LauncherController.State.IDLE;
        refresh.setEnabled(!controller.releasesBusy() && idle);
        refresh.setText(controller.releasesBusy() ? I18n.t("Refreshing…") : I18n.t("Refresh"));
        update.setVisible(controller.updateAvailable());
        update.setEnabled(idle);
        error.setText(controller.releasesError().isEmpty() ? ""
                : I18n.t("GitHub is not reachable right now: {0}", controller.releasesError()));

        installed.removeAll();
        List<VersionStore.Installed> versions = controller.installedVersions();
        String selected = controller.installedRevision();
        for (VersionStore.Installed version : versions) installed.add(new InstalledRow(version, version.tag.equals(selected), idle));
        installedSummary.setText(versions.isEmpty() ? I18n.t("None yet") : I18n.t("{0} downloaded", versions.size()));
        if (versions.isEmpty()) {
            Card empty = Card.column(6, 22);
            empty.add(new Label(I18n.t("No Vibe version yet"), Style.bodyBold(), Label.Tone.TEXT));
            empty.add(Label.small(I18n.t("Download a release below. Press Play afterwards and the launcher prepares Minecraft once.")));
            installed.add(empty);
        }

        available.removeAll();
        List<ReleaseCatalog.Release> releases = controller.releases();
        int index = 0;
        for (ReleaseCatalog.Release release : releases) {
            if (release.asset == null) continue;
            available.add(new ReleaseRow(release, controller.selectedVersion() != null
                    && release.tag.equals(controller.selectedVersion().tag), idle));
            index++;
        }
        availableSummary.setText(index == 0 ? I18n.t("Nothing loaded") : I18n.t("{0} releases", index));
        if (index == 0 && controller.releasesError().isEmpty()) {
            Card empty = Card.column(6, 22);
            empty.add(new Label(controller.releasesBusy() ? I18n.t("Loading releases…") : I18n.t("No releases found"), Style.bodyBold(), Label.Tone.TEXT));
            empty.add(Label.small(I18n.t("Use Refresh to read the release list from GitHub again.")));
            available.add(empty);
        }

        installed.revalidate();
        available.revalidate();
        revalidate();
        repaint();
    }

    /** A downloaded version: activate or delete it. */
    private final class InstalledRow extends Card {
        private static final long serialVersionUID = 1L;

        InstalledRow(final VersionStore.Installed version, boolean active, boolean idle) {
            super(false, 14, 12, 16);
            fill(() -> active ? Style.mix(Style.surface(), Style.accent(), 0.06) : Style.surface());
            Stack.Panel text = Stack.column(3);
            Stack.Panel title = Stack.row(8);
            title.add(new Label(version.title(), Style.bodyBold(), Label.Tone.TEXT));
            title.add(new Label(version.displayVersion(), Style.small(), Label.Tone.FAINT));
            if (active) title.add(new Label(I18n.t("ACTIVE"), Style.smallBold(), Label.Tone.ACCENT));
            if (version.prerelease) title.add(new Label(I18n.t("pre-release"), Style.small(), Label.Tone.WARNING));
            text.add(title);
            String meta = version.asset + "  ·  " + Text.bytes(version.size)
                    + (version.published > 0 ? "  ·  " + I18n.t("published {0}", I18n.ago(version.published)) : "");
            text.add(new Label(meta, Style.small(), Label.Tone.MUTED));
            add(text, Stack.FILL);

            FlatButton notes = new FlatButton(I18n.t("Notes"), Icons.INFO, FlatButton.Kind.GHOST, () -> toggle(version.tag));
            notes.height(32);
            add(notes);
            if (!active) {
                FlatButton use = new FlatButton(I18n.t("Use"), Icons.PLAY, FlatButton.Kind.SECONDARY, () -> controller.useVersion(version.tag));
                use.height(32);
                use.setEnabled(idle);
                add(use);
            }
            FlatButton remove = FlatButton.icon(Icons.TRASH, I18n.t("Remove"), () -> {
                if (Dialogs.confirm(this, I18n.t("Remove Vibe {0}?", version.displayVersion()),
                        I18n.t("The downloaded JAR is deleted. Saves, settings, accounts and mods stay."), I18n.t("Remove"), true)) {
                    controller.removeVersion(version.tag);
                }
            });
            remove.setEnabled(idle);
            add(remove);

            if (expanded.contains(version.tag)) add(notes(controller.notes(version.tag)), Stack.FILL);
        }
    }

    /** A release on GitHub: download it or read its notes first. */
    private final class ReleaseRow extends Card {
        private static final long serialVersionUID = 1L;

        ReleaseRow(final ReleaseCatalog.Release release, boolean active, boolean idle) {
            super(false, 14, 12, 16);
            fill(() -> active ? Style.mix(Style.surface(), Style.accent(), 0.06) : Style.surface());
            Stack.Panel text = Stack.column(3);
            Stack.Panel title = Stack.row(8);
            title.add(new Label(release.title(), Style.bodyBold(), Label.Tone.TEXT));
            title.add(new Label(release.version(), Style.small(), Label.Tone.FAINT));
            if (release.prerelease) title.add(new Label(I18n.t("pre-release"), Style.small(), Label.Tone.WARNING));
            if (active) title.add(new Label(I18n.t("ACTIVE"), Style.smallBold(), Label.Tone.ACCENT));
            text.add(title);
            String meta = release.tag
                    + (release.published > 0 ? "  ·  " + I18n.ago(release.published) : "")
                    + (release.asset.size > 0 ? "  ·  " + Text.bytes(release.asset.size) : "")
                    + "  ·  " + release.asset.name;
            text.add(new Label(meta, Style.small(), Label.Tone.MUTED));
            add(text, Stack.FILL);

            FlatButton notes = new FlatButton(I18n.t("Notes"), Icons.INFO, FlatButton.Kind.GHOST, () -> toggle(release.tag));
            notes.height(32);
            add(notes);
            if (!active) {
                boolean downloaded = installed(release.tag);
                FlatButton action = downloaded
                        ? new FlatButton(I18n.t("Use"), Icons.PLAY, FlatButton.Kind.SECONDARY, () -> controller.useVersion(release.tag))
                        : new FlatButton(I18n.t("Download"), Icons.DOWNLOAD, FlatButton.Kind.PRIMARY, () -> controller.installVersion(release));
                action.height(32);
                action.setEnabled(idle);
                add(action);
            }
            FlatButton github = new FlatButton(I18n.t("GitHub"), Icons.EXTERNAL, FlatButton.Kind.GHOST, () -> controller.browse(release.url));
            github.height(32);
            add(github);

            if (expanded.contains(release.tag)) add(notes(ReleaseNotes.parse(release.body)), Stack.FILL);
        }
    }

    private boolean installed(String tag) {
        VersionStore.Installed version = controller.selectedVersion();
        if (version != null && version.tag.equals(tag)) return true;
        for (VersionStore.Installed installed : controller.installedVersions()) if (installed.tag.equals(tag)) return true;
        return false;
    }

    private void toggle(String tag) {
        if (!expanded.remove(tag)) expanded.add(tag);
        rebuild();
    }

    /** Release notes in a quiet block under the row. */
    private static JPanel notes(List<ChangelogSection> sections) {
        Stack.Panel panel = Stack.column(6);
        if (sections == null || sections.isEmpty()) {
            panel.add(Label.small(I18n.t("No release notes were published for this version.")));
            return panel;
        }
        for (ChangelogSection section : sections) {
            panel.add(new Label(section.title, Style.smallBold(), Label.Tone.ACCENT));
            int shown = 0;
            for (String entry : section.entries) {
                panel.add(Label.small("•  " + entry));
                if (++shown >= 12) break;
            }
        }
        return panel;
    }
}
