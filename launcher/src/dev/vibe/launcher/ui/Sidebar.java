package dev.vibe.launcher.ui;

import dev.vibe.launcher.VibeLauncher;
import dev.vibe.launcher.app.LauncherController;
import dev.vibe.launcher.core.I18n;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JComponent;

/** Page navigation with badges, community links and the launcher version. */
final class Sidebar extends Stack.Panel {
    private static final long serialVersionUID = 1L;
    static final int WIDE = 216, COMPACT = 72;
    private final Map<Page, Item> items = new EnumMap<Page, Item>(Page.class);
    private final LinkButton discord, github;
    private final Label version;
    private boolean compact;

    Sidebar(LauncherController controller, Consumer<Page> navigate) {
        super(new Stack(true, 4, true));
        setBorder(BorderFactory.createEmptyBorder(16, 12, 14, 12));
        for (final Page page : Page.values()) {
            Item item = new Item(page, () -> navigate.accept(page));
            items.put(page, item);
            add(item);
        }
        items.get(Page.MODS).badge = () -> {
            int enabled = 0;
            for (dev.vibe.launcher.game.ModLibrary.Mod mod : controller.mods()) if (mod.enabled) enabled++;
            return enabled == 0 ? "" : Integer.toString(enabled);
        };
        items.get(Page.CONSOLE).alert = () -> controller.console().unseenErrors() > 0;
        add(Stack.glue(), Stack.FILL);
        discord = new LinkButton(Icons.DISCORD, "Discord", () -> controller.browse(VibeLauncher.DISCORD_URL));
        github = new LinkButton(Icons.GITHUB, "GitHub", () -> controller.browse(VibeLauncher.REPOSITORY_URL));
        add(discord);
        add(github);
        version = new Label(I18n.t("Launcher {0}", VibeLauncher.VERSION), Style.small(), Label.Tone.FAINT);
        version.setBorder(BorderFactory.createEmptyBorder(6, 12, 0, 0));
        add(version);
    }

    void select(Page page) {
        for (Map.Entry<Page, Item> entry : items.entrySet()) entry.getValue().setActive(entry.getKey() == page);
    }

    void setCompact(boolean value) {
        if (compact == value) return;
        compact = value;
        version.setVisible(!compact);
        setBorder(BorderFactory.createEmptyBorder(16, compact ? 10 : 12, 14, compact ? 10 : 12));
        revalidate();
        repaint();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(compact ? COMPACT : WIDE, 400); }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = Style.prepare(graphics);
        try {
            g.setColor(Style.sidebar());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Style.border());
            g.fillRect(getWidth() - 1, 0, 1, getHeight());
        } finally {
            g.dispose();
        }
    }

    private final class Item extends JComponent {
        private static final long serialVersionUID = 1L;
        private final Page page;
        private final Anim.Value hover = new Anim.Value(this, 0, 16);
        private final Anim.Value active = new Anim.Value(this, 0, 14);
        Supplier<String> badge = () -> "";
        Supplier<Boolean> alert = () -> false;

        Item(Page page, Runnable action) {
            this.page = page;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(I18n.t(page.label));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hover.to(1); }
                @Override public void mouseExited(MouseEvent event) { hover.to(0); }
                @Override public void mousePressed(MouseEvent event) { action.run(); }
            });
        }

        void setActive(boolean value) { active.to(value ? 1 : 0); }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 42); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                int w = getWidth(), h = getHeight();
                double a = active.value, o = hover.value;
                if (a > 0.01 || o > 0.01) {
                    Style.fill(g, 0, 0, w, h, 10, Style.alpha(Style.accent(), (int) (34 * a)));
                    if (o > 0.01) Style.fill(g, 0, 0, w, h, 10, Style.alpha(Style.text(), (int) (12 * o * (1 - a))));
                }
                if (a > 0.01) Style.fill(g, 0, h / 2.0 - 9 * a, 3, 18 * a, 1.5, Style.accent());
                java.awt.Color color = Style.mix(Style.mix(Style.muted(), Style.text(), o), Style.accent(), a);
                double iconX = compact ? (w - 20) / 2.0 : 14;
                page.icon.paint(g, iconX, (h - 20) / 2.0, 20, color);
                if (alert.get()) {
                    g.setColor(Style.danger());
                    g.fill(new java.awt.geom.Ellipse2D.Double(iconX + 15, (h - 20) / 2.0 - 2, 8, 8));
                }
                if (compact) return;
                Font font = a > 0.5 ? Style.bodyBold() : Style.body();
                Style.textMiddle(g, I18n.t(page.label), 46, 0, h, font, Style.mix(Style.mix(Style.muted(), Style.text(), o), Style.text(), a));
                String count = badge.get();
                if (!count.isEmpty()) {
                    Font small = Style.smallBold();
                    int width = Math.max(22, g.getFontMetrics(small).stringWidth(count) + 12);
                    Style.fill(g, w - width - 10, (h - 20) / 2.0, width, 20, 10, Style.alpha(Style.text(), 20));
                    Style.textCentered(g, count, w - width / 2.0 - 10, (h - 20) / 2.0, 20, small, Style.muted());
                }
            } finally {
                g.dispose();
            }
        }
    }

    private final class LinkButton extends FlatButton {
        private static final long serialVersionUID = 1L;
        private final Icons glyph;
        private final String label;

        LinkButton(Icons icon, String label, Runnable action) {
            super(label, icon, Kind.GHOST, action);
            this.glyph = icon;
            this.label = label;
            setToolTipText(label);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 36); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                int w = getWidth(), h = getHeight();
                if (hover.value > 0.01) Style.fill(g, 0, 0, w, h, 9, Style.alpha(Style.text(), (int) (14 * hover.value)));
                java.awt.Color color = Style.mix(Style.muted(), Style.text(), hover.value);
                double iconX = compact ? (w - 18) / 2.0 : 15;
                glyph.paint(g, iconX, (h - 18) / 2.0, 18, color);
                if (compact) return;
                Style.textMiddle(g, label, 46, 0, h, Style.body(), color);
                Icons.EXTERNAL.paint(g, w - 26, (h - 13) / 2.0, 13, Style.alpha(color, 150));
            } finally {
                g.dispose();
            }
        }
    }
}
