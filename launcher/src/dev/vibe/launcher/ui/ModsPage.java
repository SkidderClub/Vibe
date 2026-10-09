package dev.vibe.launcher.ui;

import dev.vibe.launcher.app.LauncherController;
import dev.vibe.launcher.core.ErrorCode;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Text;
import dev.vibe.launcher.game.ModLibrary;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.Arrays;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Custom Forge 1.8.9 mods in Vibe's profile. */
final class ModsPage extends JPanel {
    private static final long serialVersionUID = 1L;
    private final LauncherController controller;
    private final Stack.Panel list = Stack.column(8);
    private final Label summary = new Label("", Style.smallBold(), Label.Tone.MUTED);
    private boolean dragActive;

    ModsPage(LauncherController controller) {
        super(new BorderLayout());
        this.controller = controller;
        setOpaque(false);

        Stack.Panel body = Pages.body(18);
        FlatButton add = new FlatButton(I18n.t("Add mods"), Icons.PLUS, FlatButton.Kind.PRIMARY, this::chooseFiles);
        FlatButton folder = new FlatButton(I18n.t("Open folder"), Icons.FOLDER, FlatButton.Kind.SECONDARY,
                () -> controller.openPath(controller.modsFolder()));
        body.add(Pages.header(I18n.t("Mods"), I18n.t("Add your own Forge 1.8.9 mods. They are loaded together with Vibe; OptiFine is already included."), folder, add));
        body.add(new DropZone());
        Stack.Panel listHeader = Stack.row(8);
        listHeader.add(new Label(I18n.t("Installed mods"), Style.heading(), Label.Tone.TEXT));
        listHeader.add(summary, Stack.FILL);
        body.add(listHeader);
        body.add(list);
        add(Scroll.of(body), BorderLayout.CENTER);

        controller.addListener(event -> { if (event == LauncherController.Event.MODS) rebuild(); });
        rebuild();
    }

    void setDragActive(boolean active) {
        dragActive = active;
        repaint();
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle(I18n.t("Add mods"));
        chooser.setFileFilter(new FileNameExtensionFilter(I18n.t("Forge mods (*.jar, *.zip)"), "jar", "zip"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] files = chooser.getSelectedFiles();
            controller.importMods(Arrays.asList(files));
        }
    }

    private void rebuild() {
        list.removeAll();
        int enabled = 0;
        for (ModLibrary.Mod mod : controller.mods()) {
            list.add(new ModRow(mod));
            if (mod.enabled) enabled++;
        }
        int total = controller.mods().size();
        summary.setText(total == 0 ? "" : I18n.t("{0} of {1} enabled", enabled, total));
        if (total == 0) {
            Card empty = Card.column(6, 22);
            empty.add(new Label(I18n.t("No custom mods yet"), Style.bodyBold(), Label.Tone.TEXT));
            empty.add(Label.small(I18n.t("Drop .jar files onto the launcher or use Add mods. Vibe and OptiFine do not need to be added.")));
            list.add(empty);
        }
        list.revalidate();
        list.repaint();
    }

    /** Dashed drop target; the whole window accepts drops, this shows where they go. */
    private final class DropZone extends JComponent {
        private static final long serialVersionUID = 1L;
        private final Anim.Value hover = new Anim.Value(this, 0, 14);

        DropZone() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hover.to(1); }
                @Override public void mouseExited(MouseEvent event) { hover.to(0); }
                @Override public void mouseClicked(MouseEvent event) { chooseFiles(); }
            });
        }

        @Override public Dimension getPreferredSize() { return new Dimension(400, 132); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                int w = getWidth(), h = getHeight();
                double t = Math.max(hover.value * 0.6, dragActive ? 1 : 0);
                Style.fill(g, 0, 0, w, h, 14, Style.mix(Style.alpha(Style.surface(), 150), Style.alpha(Style.accent(), 36), t));
                g.setColor(Style.mix(Style.strongBorder(), Style.accent(), t));
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[] { 7f, 6f }, 0f));
                g.draw(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 28, 28));
                Icons.DOWNLOAD.paint(g, w / 2.0 - 15, 24, 30, Style.mix(Style.muted(), Style.accent(), 0.4 + 0.6 * t));
                String title = dragActive ? I18n.t("Release to add the mods") : I18n.t("Drop mods here or click to browse");
                Style.textCentered(g, title, w / 2.0, 62, 22, Style.bodyBold(), Style.text());
                Style.textCentered(g, I18n.t("Forge 1.8.9 .jar files · duplicates, OptiFine and Fabric mods are skipped"), w / 2.0, 86, 18, Style.small(), Style.muted());
            } finally {
                g.dispose();
            }
        }
    }

    private final class ModRow extends Card {
        private static final long serialVersionUID = 1L;

        ModRow(final ModLibrary.Mod mod) {
            super(false, 14, 12, 16);
            fill(() -> mod.enabled ? Style.surface() : Style.alpha(Style.surface(), 150));
            add(new Avatar(mod));
            Stack.Panel text = Stack.column(3);
            Stack.Panel titleRow = Stack.row(8);
            titleRow.add(new Label(mod.name, Style.bodyBold(), mod.enabled ? Label.Tone.TEXT : Label.Tone.MUTED));
            if (!mod.version.isEmpty()) titleRow.add(new Label(mod.version, Style.small(), Label.Tone.FAINT));
            if (mod.wrongVersion()) {
                Label warning = new Label(I18n.t("for Minecraft {0}", mod.minecraftVersion), Style.smallBold(), Label.Tone.WARNING);
                warning.setToolTipText(I18n.t("This mod says it was made for another Minecraft version and may not load.")
                        + " (" + ErrorCode.MOD_WRONG_VERSION.id() + ")");
                titleRow.add(warning);
            }
            text.add(titleRow);
            String detail = mod.fileName + "  ·  " + Text.bytes(mod.size) + (mod.authors.isEmpty() ? "" : "  ·  " + mod.authors);
            text.add(new Label(detail, Style.small(), Label.Tone.MUTED));
            if (!mod.description.isEmpty()) text.add(new Label(mod.description, Style.small(), Label.Tone.FAINT));
            add(text, Stack.FILL);
            Toggle toggle = new Toggle(mod.enabled, on -> controller.setModEnabled(mod, on));
            toggle.setToolTipText(mod.enabled ? I18n.t("Disable") : I18n.t("Enable"));
            add(toggle);
            add(FlatButton.icon(Icons.TRASH, I18n.t("Remove"), () -> {
                if (Dialogs.confirm(this, I18n.t("Remove {0}?", mod.name), I18n.t("The file is moved to the recycle bin where supported, otherwise deleted."), I18n.t("Remove"), true)) {
                    controller.deleteMod(mod);
                }
            }));
        }
    }

    /** Initial of the mod in a coloured tile, tinted from the name so rows are easy to tell apart. */
    private static final class Avatar extends JComponent {
        private static final long serialVersionUID = 1L;
        private final ModLibrary.Mod mod;
        Avatar(ModLibrary.Mod mod) { this.mod = mod; }
        @Override public Dimension getPreferredSize() { return new Dimension(40, 40); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                float hue = (mod.name.hashCode() & 0xFFFF) / 65535f;
                java.awt.Color tint = java.awt.Color.getHSBColor(hue, 0.45f, 0.95f);
                Style.fill(g, 0, 0, 40, 40, 11, Style.alpha(tint, mod.enabled ? 60 : 25));
                String initial = mod.name.isEmpty() ? "?" : mod.name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
                Style.textCentered(g, initial, 20, 0, 40, Style.font(Font.BOLD, 16f), mod.enabled ? tint : Style.muted());
            } finally {
                g.dispose();
            }
        }
    }
}
