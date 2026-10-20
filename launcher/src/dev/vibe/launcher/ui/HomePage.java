package dev.vibe.launcher.ui;

import dev.vibe.launcher.VibeLauncher;
import dev.vibe.launcher.app.LauncherController;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Text;
import dev.vibe.launcher.install.ChangelogSection;
import dev.vibe.launcher.install.SourceManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** Home: the player preview with Vibe's ESP, a greeting and what changed in Vibe. */
final class HomePage extends JPanel {
    private static final long serialVersionUID = 1L;
    private final LauncherController controller;
    private final PlayerPreview preview = new PlayerPreview();
    private final Label greeting = new Label("", Style.font(Font.BOLD, 30f), Label.Tone.TEXT);
    private final Stack.Panel chips = Stack.row(8);
    private final Stack.Panel news = Stack.column(10);
    private final Stack.Panel newsColumn;
    private final Segmented newsTabs;
    private int tab;

    HomePage(LauncherController controller) {
        super(new BorderLayout());
        this.controller = controller;
        setOpaque(false);

        JPanel hero = new JPanel(null) {
            private static final long serialVersionUID = 1L;
            @Override public void doLayout() {
                Component header = getComponent(0);
                Dimension size = header.getPreferredSize();
                header.setBounds(30, 26, Math.min(getWidth() - 60, Math.max(size.width, 360)), size.height);
                Component toggle = getComponent(1);
                Dimension toggleSize = toggle.getPreferredSize();
                toggle.setBounds(30, getHeight() - toggleSize.height - 22, toggleSize.width, toggleSize.height);
                // The player stands below the greeting so the ESP name never collides with it.
                int top = 26 + size.height + 8;
                preview.setBounds(0, top, getWidth(), Math.max(0, getHeight() - top - 8));
            }
        };
        hero.setOpaque(false);
        Stack.Panel header = Stack.column(6);
        Label welcome = new Label(I18n.t("WELCOME BACK"), Style.font(Font.BOLD, 11.5f), Label.Tone.ACCENT);
        header.add(welcome);
        header.add(greeting);
        header.add(Stack.space(4));
        header.add(chips);
        hero.add(header);
        FlatButton espToggle = new FlatButton(I18n.t("ESP preview"), Icons.SHIELD, FlatButton.Kind.GHOST, null).height(32);
        espToggle.setAction(() -> {
            boolean next = !controller.settings().espPreview();
            controller.settings().setEspPreview(next);
            espToggle.setSelected(next);
            preview.setEsp(next);
        });
        espToggle.setSelected(controller.settings().espPreview());
        espToggle.setToolTipText(I18n.t("Show Vibe's default 2D ESP around the preview. Drag the player to rotate it."));
        hero.add(espToggle);
        hero.add(preview);
        add(hero, BorderLayout.CENTER);

        newsColumn = Stack.column(0);
        newsColumn.setBorder(BorderFactory.createEmptyBorder(24, 0, 24, 24));
        Card card = new Card(true, 12, 18, 18);
        card.fill(() -> Style.alpha(Style.surface(), 235));
        card.add(Label.heading(I18n.t("What's new")));
        newsTabs = new Segmented(new String[] { I18n.t("Changelog"), I18n.t("Commits") }, 0, index -> { tab = index; rebuildNews(); });
        card.add(newsTabs);
        JScrollPane scroll = Scroll.of(news);
        card.add(scroll, Stack.FILL);
        newsColumn.add(card, Stack.FILL);
        add(newsColumn, BorderLayout.EAST);

        preview.setEsp(controller.settings().espPreview());
        controller.addListener(event -> {
            switch (event) {
                case SKIN: case ACCOUNTS: refreshPlayer(); break;
                case SOURCE: refreshChips(); rebuildNews(); break;
                case STATE: case SETTINGS: refreshMotion(); break;
                default: break;
            }
        });
        refreshPlayer();
        refreshChips();
        rebuildNews();
        refreshMotion();
    }

    void setWindowVisible(boolean visible) { preview.setWindowVisible(visible); }

    void setCompact(boolean compact) {
        newsColumn.setPreferredSize(new Dimension(compact ? 320 : 390, 10));
        revalidate();
    }

    private void refreshPlayer() {
        preview.setSkin(controller.skin());
        preview.setPlayerName(controller.displayName());
        greeting.setText(controller.displayName());
    }

    private void refreshMotion() {
        boolean running = controller.state() == LauncherController.State.RUNNING;
        preview.setAnimate(controller.settings().animations() && !running);
    }

    private void refreshChips() {
        chips.removeAll();
        chips.add(new Chip("Minecraft 1.8.9", Icons.CUBE));
        chips.add(new Chip("Forge + OptiFine", Icons.MODS));
        String version = controller.vibeVersion();
        String revision = Text.shortSha(controller.installedRevision());
        if (!version.isEmpty()) chips.add(new Chip("Vibe " + version + (revision.isEmpty() ? "" : " · " + revision), Icons.SHIELD));
        chips.revalidate();
        chips.repaint();
    }

    private void rebuildNews() {
        news.removeAll();
        if (tab == 0) {
            List<ChangelogSection> sections = controller.changelog();
            if (sections.isEmpty()) news.add(Label.body(controller.sourceInstalled() ? I18n.t("No changelog found in this Vibe version.")
                    : I18n.t("The changelog appears once Vibe has been downloaded.")));
            for (ChangelogSection section : sections) {
                Label heading = new Label(section.title, Style.smallBold(), Label.Tone.ACCENT);
                heading.setBorder(BorderFactory.createEmptyBorder(news.getComponentCount() == 0 ? 0 : 8, 0, 0, 0));
                news.add(heading);
                int shown = 0;
                for (String entry : section.entries) {
                    news.add(new Bullet(entry));
                    if (++shown >= 12) break;
                }
            }
        } else {
            SourceManager.Remote remote = controller.remote();
            if (remote == null) {
                news.add(Label.body(controller.remoteError().isEmpty() ? I18n.t("Loading commits from GitHub…")
                        : I18n.t("GitHub is not reachable right now: {0}", controller.remoteError())));
            } else {
                String installed = controller.installedRevision();
                for (SourceManager.Commit commit : remote.commits) news.add(new CommitRow(commit, commit.sha.equals(installed)));
            }
        }
        news.revalidate();
        news.repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = Style.prepare(graphics);
        try {
            int w = getWidth(), h = getHeight();
            g.setPaint(new GradientPaint(0, 0, Style.mix(Style.background(), Style.accent(), 0.06), 0, h, Style.background()));
            g.fillRect(0, 0, w, h);
            // Static waves: the launcher's take on Vibe's menu shader, without burning CPU.
            g.setStroke(new java.awt.BasicStroke(1.3f));
            for (int wave = 0; wave < 4; wave++) {
                Path2D path = new Path2D.Double();
                for (int x = 0; x <= w; x += 6) {
                    double y = h * (0.62 + wave * 0.05) + Math.sin(x * 0.0085 + wave * 1.7) * (26 + wave * 10) + Math.cos(x * 0.0035 + wave) * 16;
                    if (x == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                g.setColor(Style.alpha(Style.accent(), 16 + wave * 5));
                g.draw(path);
            }
        } finally {
            g.dispose();
        }
    }

    /** A small rounded fact chip under the greeting. */
    private static final class Chip extends JComponent {
        private static final long serialVersionUID = 1L;
        private final String text;
        private final Icons icon;
        Chip(String text, Icons icon) { this.text = text; this.icon = icon; }
        @Override public Dimension getPreferredSize() { return new Dimension(getFontMetrics(Style.smallBold()).stringWidth(text) + 40, 28); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                Style.panel(g, 0, 0, getWidth(), getHeight(), 14, Style.alpha(Style.surface(), 220), Style.border());
                icon.paint(g, 10, (getHeight() - 14) / 2.0, 14, Style.accent());
                Style.textMiddle(g, text, 30, 0, getHeight(), Style.smallBold(), Style.text());
            } finally {
                g.dispose();
            }
        }
    }

    /** A changelog entry with a bullet. */
    private static final class Bullet extends JComponent implements Stack.Wrapping {
        private static final long serialVersionUID = 1L;
        private final String text;
        Bullet(String text) { this.text = text; }

        @Override public int heightFor(int width) {
            FontMetrics metrics = getFontMetrics(Style.small());
            return Style.wrap(metrics, text, Math.max(40, width - 16), 5).size() * (metrics.getHeight() + 1);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(300, heightFor(300)); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                g.setFont(Style.small());
                FontMetrics metrics = g.getFontMetrics();
                g.setColor(Style.alpha(Style.accent(), 180));
                g.fill(new Ellipse2D.Double(2, metrics.getAscent() / 2.0 + 1, 5, 5));
                int y = metrics.getAscent();
                for (String line : Style.wrap(metrics, text, Math.max(40, getWidth() - 16), 5)) {
                    Style.text(g, line, 16, y, Style.small(), Style.mix(Style.muted(), Style.text(), 0.35));
                    y += metrics.getHeight() + 1;
                }
            } finally {
                g.dispose();
            }
        }
    }

    /** A commit on main; click opens it on GitHub. */
    private final class CommitRow extends JComponent {
        private static final long serialVersionUID = 1L;
        private final SourceManager.Commit commit;
        private final boolean installed;
        private final Anim.Value hover = new Anim.Value(this, 0, 16);

        CommitRow(SourceManager.Commit commit, boolean installed) {
            this.commit = commit;
            this.installed = installed;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(I18n.t("Open on GitHub"));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hover.to(1); }
                @Override public void mouseExited(MouseEvent event) { hover.to(0); }
                @Override public void mouseClicked(MouseEvent event) { controller.browse(VibeLauncher.REPOSITORY_URL + "/commit/" + commit.sha); }
            });
        }

        @Override public Dimension getPreferredSize() { return new Dimension(300, 56); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                int w = getWidth(), h = getHeight();
                Style.fill(g, 0, 0, w, h, 10, Style.alpha(Style.text(), (int) (6 + 10 * hover.value)));
                Color dot = installed ? Style.success() : Style.alpha(Style.accent(), 170);
                g.setColor(dot);
                g.fill(new Ellipse2D.Double(12, 15, 8, 8));
                int textWidth = w - 44;
                Style.text(g, Style.ellipsize(g.getFontMetrics(Style.bodyBold()), commit.message.isEmpty() ? Text.shortSha(commit.sha) : commit.message, textWidth),
                        30, 23, Style.bodyBold(), Style.text());
                String meta = commit.author + "  ·  " + I18n.ago(commit.time) + "  ·  " + Text.shortSha(commit.sha)
                        + (installed ? "  ·  " + I18n.t("installed") : "");
                Style.text(g, Style.ellipsize(g.getFontMetrics(Style.small()), meta, textWidth), 30, 42, Style.small(), installed ? Style.success() : Style.muted());
            } finally {
                g.dispose();
            }
        }
    }
}
