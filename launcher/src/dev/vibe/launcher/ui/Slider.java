package dev.vibe.launcher.ui;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.function.IntConsumer;
import javax.swing.JComponent;

/** A stepped horizontal slider. The listener fires once the value has settled. */
public final class Slider extends JComponent {
    private static final long serialVersionUID = 1L;
    private final int min, max, step;
    private int value;
    private final IntConsumer onChange, onCommit;
    private boolean dragging;

    public Slider(int min, int max, int step, int value, IntConsumer onChange, IntConsumer onCommit) {
        this.min = min;
        this.max = Math.max(min + step, max);
        this.step = step;
        this.value = clamp(value);
        this.onChange = onChange;
        this.onCommit = onCommit;
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { dragging = true; update(event.getX()); }
            @Override public void mouseDragged(MouseEvent event) { update(event.getX()); }
            @Override public void mouseReleased(MouseEvent event) { dragging = false; repaint(); onCommit.accept(Slider.this.value); }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                int delta = event.getKeyCode() == KeyEvent.VK_LEFT ? -step : event.getKeyCode() == KeyEvent.VK_RIGHT ? step : 0;
                if (delta == 0) return;
                set(Slider.this.value + delta);
                onCommit.accept(Slider.this.value);
            }
        });
    }

    private int clamp(int candidate) { return Math.max(min, Math.min(max, Math.round((candidate - min) / (float) step) * step + min)); }

    private void update(int x) {
        double t = (x - 9) / Math.max(1.0, getWidth() - 18.0);
        set((int) Math.round(min + t * (max - min)));
    }

    private void set(int candidate) {
        int next = clamp(candidate);
        if (next == value) return;
        value = next;
        onChange.accept(value);
        repaint();
    }

    public int value() { return value; }

    @Override public Dimension getPreferredSize() { return new Dimension(260, 26); }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = Style.prepare(graphics);
        try {
            double y = getHeight() / 2.0, left = 9, right = getWidth() - 9;
            double t = (value - min) / (double) (max - min);
            Style.fill(g, left, y - 3, right - left, 6, 3, Style.alpha(Style.text(), 30));
            Style.fill(g, left, y - 3, (right - left) * t, 6, 3, Style.accent());
            int ticks = (max - min) / step;
            if (ticks <= 32) {
                g.setColor(Style.alpha(Style.text(), 50));
                for (int index = 1; index < ticks; index++) {
                    double x = left + (right - left) * index / ticks;
                    if (x > left + (right - left) * t) g.fill(new Ellipse2D.Double(x - 1.2, y - 1.2, 2.4, 2.4));
                }
            }
            double knob = dragging ? 18 : 16;
            double kx = left + (right - left) * t;
            g.setColor(Style.text());
            g.fill(new Ellipse2D.Double(kx - knob / 2, y - knob / 2, knob, knob));
            g.setColor(Style.accent());
            g.fill(new Ellipse2D.Double(kx - 4, y - 4, 8, 8));
            if (isFocusOwner() && FlatButton.FocusStyle.keyboard) {
                g.setColor(Style.alpha(Style.accent(), 120));
                g.draw(new Ellipse2D.Double(kx - knob / 2 - 3, y - knob / 2 - 3, knob + 6, knob + 6));
            }
        } finally {
            g.dispose();
        }
    }
}
