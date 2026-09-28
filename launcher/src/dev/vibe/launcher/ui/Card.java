package dev.vibe.launcher.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.function.Supplier;
import javax.swing.BorderFactory;

/** A rounded surface that stacks its content. */
public class Card extends Stack.Panel {
    private static final long serialVersionUID = 1L;
    private Supplier<Color> fill = Style::surface;
    private Supplier<Color> border = Style::border;
    private int radius = Style.RADIUS;

    public Card(boolean vertical, int gap, int padding) { this(vertical, gap, padding, padding); }

    public Card(boolean vertical, int gap, int vertialPadding, int horizontalPadding) {
        super(new Stack(vertical, gap, vertical));
        setBorder(BorderFactory.createEmptyBorder(vertialPadding, horizontalPadding, vertialPadding, horizontalPadding));
    }

    public static Card column(int gap, int padding) { return new Card(true, gap, padding); }
    public static Card row(int gap, int padding) { return new Card(false, gap, padding); }

    public Card fill(Supplier<Color> color) { fill = color; repaint(); return this; }
    public Card outline(Supplier<Color> color) { border = color; repaint(); return this; }
    public Card radius(int value) { radius = value; repaint(); return this; }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = Style.prepare(graphics);
        try {
            Style.panel(g, 0, 0, getWidth(), getHeight(), radius, fill.get(), border == null ? null : border.get());
        } finally {
            g.dispose();
        }
    }
}
