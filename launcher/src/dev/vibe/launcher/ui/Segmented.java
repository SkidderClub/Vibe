package dev.vibe.launcher.ui;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
import javax.swing.JComponent;

/** A row of mutually exclusive options with a sliding highlight. */
public final class Segmented extends JComponent {
    private static final long serialVersionUID = 1L;
    private final String[] options;
    private final IntConsumer listener;
    private int selected;
    private int hovered = -1;
    private final Anim.Value slide;

    public Segmented(String[] options, int selected, IntConsumer listener) {
        this.options = options;
        this.selected = selected;
        this.listener = listener;
        this.slide = new Anim.Value(this, selected, 16);
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) { hovered = indexAt(event.getX()); repaint(); }
            @Override public void mouseExited(MouseEvent event) { hovered = -1; repaint(); }
            @Override public void mousePressed(MouseEvent event) { choose(indexAt(event.getX())); }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_LEFT) choose(Math.max(0, Segmented.this.selected - 1));
                if (event.getKeyCode() == KeyEvent.VK_RIGHT) choose(Math.min(options.length - 1, Segmented.this.selected + 1));
            }
        });
    }

    private int indexAt(int x) { return Math.max(0, Math.min(options.length - 1, x * options.length / Math.max(1, getWidth()))); }

    private void choose(int index) {
        if (index == selected) return;
        selected = index;
        slide.to(index);
        listener.accept(index);
    }

    public void setSelected(int index) {
        selected = index;
        slide.to(index);
    }

    @Override public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(Style.font(Font.BOLD, 12f));
        int widest = 0;
        for (String option : options) widest = Math.max(widest, metrics.stringWidth(option));
        return new Dimension((widest + 28) * options.length + 6, 34);
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = Style.prepare(graphics);
        try {
            int w = getWidth(), h = getHeight();
            Style.fill(g, 0, 0, w, h, 10, Style.alpha(Style.text(), 14));
            double segment = (w - 6) / (double) options.length;
            if (hovered >= 0 && hovered != selected) Style.fill(g, 3 + segment * hovered, 3, segment, h - 6, 8, Style.alpha(Style.text(), 12));
            Style.fill(g, 3 + segment * slide.value, 3, segment, h - 6, 8, Style.raised());
            Style.stroke(g, 3 + segment * slide.value, 3, segment, h - 6, 8, Style.strongBorder(), 1f);
            Font font = Style.font(Font.BOLD, 12f);
            for (int index = 0; index < options.length; index++) {
                boolean active = index == selected;
                Style.textCentered(g, Style.ellipsize(g.getFontMetrics(font), options[index], (int) segment - 12),
                        3 + segment * index + segment / 2, 0, h, font, active ? Style.text() : Style.muted());
            }
            if (isFocusOwner() && FlatButton.FocusStyle.keyboard) Style.stroke(g, 0, 0, w, h, 10, Style.accent(), 1.5f);
        } finally {
            g.dispose();
        }
    }
}
