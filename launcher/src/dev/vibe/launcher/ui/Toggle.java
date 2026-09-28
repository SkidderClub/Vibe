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
import java.util.function.Consumer;
import javax.swing.JComponent;

/** An animated on/off switch. */
public final class Toggle extends JComponent {
    private static final long serialVersionUID = 1L;
    private boolean on;
    private final Consumer<Boolean> listener;
    private final Anim.Value knob;

    public Toggle(boolean on, Consumer<Boolean> listener) {
        this.on = on;
        this.listener = listener;
        this.knob = new Anim.Value(this, on ? 1 : 0, 18);
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { if (isEnabled()) flip(); }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                if (event.getModifiersEx() != 0) return;
                if (event.getKeyCode() == KeyEvent.VK_SPACE || event.getKeyCode() == KeyEvent.VK_ENTER) flip();
            }
        });
    }

    private void flip() {
        setOn(!on);
        listener.accept(on);
    }

    public boolean isOn() { return on; }

    public void setOn(boolean value) {
        on = value;
        knob.to(on ? 1 : 0);
    }

    @Override public Dimension getPreferredSize() { return new Dimension(40, 24); }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = Style.prepare(graphics);
        try {
            double w = 40, h = 22, x = (getWidth() - w) / 2.0, y = (getHeight() - h) / 2.0;
            double t = knob.value;
            Style.fill(g, x, y, w, h, h / 2, Style.mix(Style.alpha(Style.text(), 34), Style.accent(), t));
            double size = h - 6;
            double kx = x + 3 + (w - size - 6) * t;
            g.setColor(Style.mix(Style.text(), Style.onAccent(), t * 0.1));
            g.fill(new Ellipse2D.Double(kx, y + 3, size, size));
            if (isFocusOwner() && FlatButton.FocusStyle.keyboard) Style.stroke(g, x - 2, y - 2, w + 4, h + 4, (h + 4) / 2, Style.accent(), 1.5f);
        } finally {
            g.dispose();
        }
    }
}
