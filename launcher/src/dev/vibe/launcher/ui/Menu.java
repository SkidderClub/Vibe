package dev.vibe.launcher.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

/** A themed drop-down list of rich rows, opened above or below an anchor. */
final class Menu {
    static final class Item {
        final String title, subtitle;
        final Icons icon;
        final Image image;
        final boolean selected;
        final Runnable action;
        Item(String title, String subtitle, Icons icon, Image image, boolean selected, Runnable action) {
            this.title = title; this.subtitle = subtitle; this.icon = icon; this.image = image; this.selected = selected; this.action = action;
        }
    }

    private Menu() { }

    static void show(Component anchor, List<Item> items, boolean above, int width) {
        final JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createEmptyBorder());
        popup.setOpaque(false);
        popup.setBackground(new Color(0, 0, 0, 0));
        popup.setLightWeightPopupEnabled(true);
        Card card = Card.column(2, 6);
        card.fill(() -> Style.mix(Style.surface(), Style.background(), 0.05)).outline(Style::strongBorder).radius(12);
        for (final Item item : items) card.add(new Row(item, () -> {
            popup.setVisible(false);
            if (item.action != null) item.action.run();
        }));
        int height = card.heightFor(width);
        card.setPreferredSize(new Dimension(width, height));
        popup.add(card);
        popup.pack();
        popup.show(anchor, 0, above ? -height - 8 : anchor.getHeight() + 8);
    }

    private static final class Row extends JComponent {
        private static final long serialVersionUID = 1L;
        private final Item item;
        private boolean hover;

        Row(Item item, Runnable onClick) {
            this.item = item;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent event) { hover = false; repaint(); }
                @Override public void mouseReleased(MouseEvent event) { if (contains(event.getPoint())) onClick.run(); }
            });
        }

        @Override public Dimension getPreferredSize() { return new Dimension(200, item.subtitle == null || item.subtitle.isEmpty() ? 38 : 50); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                int w = getWidth(), h = getHeight();
                if (hover || item.selected) Style.fill(g, 0, 0, w, h, 8, hover ? Style.hover() : Style.alpha(Style.accent(), 26));
                int x = 10;
                if (item.image != null) {
                    int size = 28;
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.drawImage(item.image, x, (h - size) / 2, size, size, null);
                    x += size + 12;
                } else if (item.icon != null) {
                    Style.fill(g, x, (h - 28) / 2.0, 28, 28, 8, Style.alpha(Style.accent(), 34));
                    item.icon.paint(g, x + 5, (h - 18) / 2.0, 18, Style.accent());
                    x += 40;
                }
                int right = item.selected ? 34 : 12;
                Font titleFont = Style.bodyBold();
                if (item.subtitle == null || item.subtitle.isEmpty()) {
                    Style.textMiddle(g, Style.ellipsize(g.getFontMetrics(titleFont), item.title, w - x - right), x, 0, h, titleFont, Style.text());
                } else {
                    Style.text(g, Style.ellipsize(g.getFontMetrics(titleFont), item.title, w - x - right), x, h / 2 - 3, titleFont, Style.text());
                    Style.text(g, Style.ellipsize(g.getFontMetrics(Style.small()), item.subtitle, w - x - right), x, h / 2 + 14, Style.small(), Style.muted());
                }
                if (item.selected) Icons.CHECK.paint(g, w - 28, (h - 16) / 2.0, 16, Style.accent());
            } finally {
                g.dispose();
            }
        }
    }
}
