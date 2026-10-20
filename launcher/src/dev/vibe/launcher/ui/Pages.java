package dev.vibe.launcher.ui;

import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JComponent;

/** Shared building blocks for the pages. */
final class Pages {
    static final int PADDING = 30;

    private Pages() { }

    /** Page title and description on the left, actions on the right. */
    static Stack.Panel header(String title, String description, JComponent... actions) {
        Stack.Panel row = Stack.row(10);
        Stack.Panel text = Stack.column(6);
        text.add(Label.title(title));
        text.add(new Label(description, Style.body(), Label.Tone.MUTED, 3).preferredWidth(560));
        row.add(text, Stack.FILL);
        for (JComponent action : actions) row.add(action);
        return row;
    }

    /** A root column with the standard page padding. */
    static Stack.Panel body(int gap) {
        Stack.Panel body = Stack.column(gap);
        body.setBorder(BorderFactory.createEmptyBorder(PADDING - 2, PADDING, PADDING, PADDING));
        return body;
    }

    /** Title and description on the left, a control on the right. */
    static Stack.Panel setting(String title, String description, Component control) {
        Stack.Panel row = Stack.row(18);
        Stack.Panel text = Stack.column(3);
        text.add(new Label(title, Style.bodyBold(), Label.Tone.TEXT));
        if (description != null && !description.isEmpty()) text.add(new Label(description, Style.small(), Label.Tone.MUTED, 4).preferredWidth(520));
        row.add(text, Stack.FILL);
        if (control != null) row.add(control);
        return row;
    }

    /** A section card with a small heading. */
    static Card section(String title, Icons icon) {
        Card card = Card.column(16, 22);
        Stack.Panel heading = Stack.row(10);
        heading.add(new IconBadge(icon));
        heading.add(Label.heading(title));
        card.add(heading);
        return card;
    }

    /** Thin divider between rows inside a card. */
    static JComponent divider() {
        JComponent line = new JComponent() {
            private static final long serialVersionUID = 1L;
            @Override protected void paintComponent(java.awt.Graphics g) {
                g.setColor(Style.border());
                g.fillRect(0, getHeight() / 2, getWidth(), 1);
            }
        };
        line.setPreferredSize(new java.awt.Dimension(10, 1));
        return line;
    }

    /** A rounded square holding an icon in the accent colour. */
    static final class IconBadge extends JComponent {
        private static final long serialVersionUID = 1L;
        private final Icons icon;
        private final int size;
        IconBadge(Icons icon) { this(icon, 30); }
        IconBadge(Icons icon, int size) { this.icon = icon; this.size = size; }
        @Override public java.awt.Dimension getPreferredSize() { return new java.awt.Dimension(size, size); }
        @Override protected void paintComponent(java.awt.Graphics graphics) {
            java.awt.Graphics2D g = Style.prepare(graphics);
            try {
                Style.fill(g, 0, 0, size, size, size * 0.3, Style.accentSoft());
                double inner = size * 0.58;
                icon.paint(g, (size - inner) / 2, (size - inner) / 2, inner, Style.accent());
            } finally {
                g.dispose();
            }
        }
    }
}
