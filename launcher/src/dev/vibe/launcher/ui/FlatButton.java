package dev.vibe.launcher.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;

/** Rounded button with hover and press feedback, usable with mouse and keyboard. */
public class FlatButton extends JComponent {
    private static final long serialVersionUID = 1L;

    public enum Kind { PRIMARY, SECONDARY, GHOST, DANGER, SOFT }

    private String text;
    private Icons icon;
    private Kind kind;
    private Runnable action;
    private int height = 36;
    private boolean pressed;
    private boolean selected;
    protected final Anim.Value hover = new Anim.Value(this, 0, 16);

    public FlatButton(String text, Icons icon, Kind kind, Runnable action) {
        this.text = text == null ? "" : text;
        this.icon = icon;
        this.kind = kind;
        this.action = action;
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { if (isEnabled()) hover.to(1); }
            @Override public void mouseExited(MouseEvent event) { hover.to(0); pressed = false; repaint(); }
            @Override public void mousePressed(MouseEvent event) {
                if (!isEnabled() || event.getButton() != MouseEvent.BUTTON1) return;
                pressed = true;
                repaint();
            }
            @Override public void mouseReleased(MouseEvent event) {
                boolean fire = pressed && contains(event.getPoint());
                pressed = false;
                repaint();
                if (fire) click();
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                // Ctrl+Enter and friends are window shortcuts, not a press of the focused button.
                if (event.getModifiersEx() != 0 || event.isConsumed()) return;
                if (event.getKeyCode() == KeyEvent.VK_SPACE || event.getKeyCode() == KeyEvent.VK_ENTER) click();
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { repaint(); }
            @Override public void focusLost(FocusEvent event) { repaint(); }
        });
    }

    public static FlatButton icon(Icons icon, String tooltip, Runnable action) {
        FlatButton button = new FlatButton("", icon, Kind.GHOST, action);
        button.setToolTipText(tooltip);
        return button;
    }

    public void click() { if (isEnabled() && action != null) action.run(); }

    public FlatButton height(int value) { height = value; revalidate(); return this; }
    public void setAction(Runnable next) { action = next; }
    public void setText(String value) { text = value == null ? "" : value; revalidate(); repaint(); }
    public String getText() { return text; }
    public void setIcon(Icons value) { icon = value; repaint(); }
    public void setKind(Kind value) { kind = value; repaint(); }
    public void setSelected(boolean value) { selected = value; repaint(); }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        if (!enabled) hover.set(0);
        repaint();
    }

    protected Font font() { return height >= 40 ? Style.font(Font.BOLD, 14f) : Style.font(Font.BOLD, 12.5f); }

    @Override public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics metrics = getFontMetrics(font());
        int iconSize = iconSize();
        if (text.isEmpty()) return new Dimension(height, height);
        int width = metrics.stringWidth(text) + (height >= 40 ? 44 : 30) + (icon == null ? 0 : iconSize + 8);
        return new Dimension(width, height);
    }

    private int iconSize() { return height >= 40 ? 18 : 16; }

    protected Color background() {
        double h = hover.value;
        switch (kind) {
            case PRIMARY: return Style.mix(Style.accent(), Style.accentHover(), h);
            case DANGER: return Style.alpha(Style.danger(), (int) (38 + 40 * h));
            case SOFT: return Style.alpha(Style.accent(), (int) ((selected ? 60 : 32) + 30 * h));
            case GHOST: return Style.alpha(Style.text(), (int) ((selected ? 22 : 0) + 18 * h));
            default: return Style.mix(Style.raised(), Style.hover(), h);
        }
    }

    protected Color foreground() {
        switch (kind) {
            case PRIMARY: return Style.onAccent();
            case DANGER: return Style.mix(Style.danger(), Style.text(), 0.25);
            case SOFT: return Style.mix(Style.accent(), Style.text(), 0.25);
            case GHOST: return Style.mix(Style.muted(), Style.text(), Math.max(hover.value, selected ? 1 : 0));
            default: return Style.text();
        }
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = Style.prepare(graphics);
        try {
            if (!isEnabled()) g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.45f));
            int w = getWidth(), h = getHeight();
            double inset = pressed ? 1 : 0;
            int radius = Math.min(10, h / 2);
            Style.fill(g, inset, inset, w - inset * 2, h - inset * 2, radius, background());
            if (kind == Kind.SECONDARY) Style.stroke(g, inset, inset, w - inset * 2, h - inset * 2, radius, Style.border(), 1f);
            if (isFocusOwner() && FocusStyle.keyboard) Style.stroke(g, 0, 0, w, h, radius, Style.alpha(Style.accent(), 200), 2f);
            Color color = foreground();
            Font font = font();
            FontMetrics metrics = g.getFontMetrics(font);
            int iconSize = iconSize();
            if (text.isEmpty()) {
                if (icon != null) icon.paint(g, (w - iconSize) / 2.0, (h - iconSize) / 2.0, iconSize, color);
                return;
            }
            int content = metrics.stringWidth(text) + (icon == null ? 0 : iconSize + 8);
            double x = (w - content) / 2.0;
            if (icon != null) {
                icon.paint(g, x, (h - iconSize) / 2.0, iconSize, color);
                x += iconSize + 8;
            }
            Style.textMiddle(g, Style.ellipsize(metrics, text, w - 16), x, 0, h, font, color);
        } finally {
            g.dispose();
        }
    }

    /** Focus rings only after keyboard navigation, not after mouse clicks. */
    static final class FocusStyle {
        static volatile boolean keyboard;
        private FocusStyle() { }
    }
}
