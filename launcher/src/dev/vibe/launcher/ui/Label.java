package dev.vibe.launcher.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JComponent;

/** Theme-aware text that wraps to its width. Colours are resolved at paint time. */
public final class Label extends JComponent implements Stack.Wrapping {
    public enum Tone { TEXT, MUTED, FAINT, ACCENT, DANGER, SUCCESS, WARNING }

    private String text;
    private Font font;
    private Tone tone;
    private int maxLines;
    private int preferredWidth = 420;
    private Supplier<Color> color;

    public Label(String text, Font font, Tone tone) { this(text, font, tone, 1); }

    public Label(String text, Font font, Tone tone, int maxLines) {
        this.text = text == null ? "" : text;
        this.font = font;
        this.tone = tone;
        this.maxLines = maxLines;
        setOpaque(false);
    }

    public static Label title(String text) { return new Label(text, Style.title(), Tone.TEXT); }
    public static Label heading(String text) { return new Label(text, Style.heading(), Tone.TEXT); }
    public static Label body(String text) { return new Label(text, Style.body(), Tone.MUTED, 6); }
    public static Label small(String text) { return new Label(text, Style.small(), Tone.MUTED, 4); }

    public Label preferredWidth(int width) { this.preferredWidth = width; return this; }
    public Label color(Supplier<Color> supplier) { this.color = supplier; repaint(); return this; }

    public void setText(String value) {
        String next = value == null ? "" : value;
        if (next.equals(text)) return;
        text = next;
        revalidate();
        repaint();
    }

    public String getText() { return text; }
    public void setTone(Tone next) { tone = next; repaint(); }
    public void setLabelFont(Font next) { font = next; revalidate(); repaint(); }

    private Color resolve() {
        if (color != null) return color.get();
        switch (tone) {
            case MUTED: return Style.muted();
            case FAINT: return Style.faint();
            case ACCENT: return Style.accent();
            case DANGER: return Style.danger();
            case SUCCESS: return Style.success();
            case WARNING: return Style.warning();
            default: return Style.text();
        }
    }

    private FontMetrics metrics() { return getFontMetrics(font); }

    @Override public int heightFor(int width) {
        java.awt.Insets insets = getInsets();
        FontMetrics metrics = metrics();
        int inner = Math.max(20, width - insets.left - insets.right);
        int lines = maxLines <= 1 ? 1 : Math.max(1, Style.wrap(metrics, text, inner, maxLines).size());
        return lines * lineHeight(metrics) + insets.top + insets.bottom;
    }

    private int lineHeight(FontMetrics metrics) { return metrics.getHeight() + (maxLines > 1 ? 2 : 0); }

    @Override public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics metrics = metrics();
        java.awt.Insets insets = getInsets();
        int width = (maxLines <= 1 ? metrics.stringWidth(text) + 2 : Math.min(preferredWidth, metrics.stringWidth(text) + 2)) + insets.left + insets.right;
        return new Dimension(width, heightFor(Math.max(1, width)));
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = Style.prepare(graphics);
        try {
            java.awt.Insets insets = getInsets();
            g.translate(insets.left, insets.top);
            int width = getWidth() - insets.left - insets.right, height = getHeight() - insets.top - insets.bottom;
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            g.setColor(resolve());
            if (maxLines <= 1) {
                String shown = Style.ellipsize(metrics, text, width);
                g.drawString(shown, 0, (height - metrics.getHeight()) / 2 + metrics.getAscent());
                return;
            }
            List<String> lines = Style.wrap(metrics, text, width, maxLines);
            int y = metrics.getAscent();
            for (String line : lines) {
                g.drawString(line, 0, y);
                y += lineHeight(metrics);
            }
        } finally {
            g.dispose();
        }
    }
}
