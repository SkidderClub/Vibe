package dev.vibe.launcher.ui;

import dev.vibe.launcher.game.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Design tokens derived from the active Vibe theme, plus painting helpers.
 * Components read colours at paint time, so switching the theme only needs a repaint.
 */
public final class Style {
    private static volatile Theme theme = Theme.LAVENDER;
    private static final String FAMILY = pick("Segoe UI Variable Text", "Segoe UI", "SF Pro Text", "Helvetica Neue", "Inter",
            "Noto Sans", "Ubuntu", "Cantarell", "DejaVu Sans", Font.SANS_SERIF);
    private static final String MONO = pick("Cascadia Mono", "Consolas", "JetBrains Mono", "SF Mono", "Menlo", "Ubuntu Mono",
            "DejaVu Sans Mono", Font.MONOSPACED);
    private static final Map<String, Font> FONTS = new HashMap<String, Font>();

    public static final int RADIUS = 12;

    private Style() { }

    public static void setTheme(Theme next) { theme = next; }
    public static Theme theme() { return theme; }

    // ---- colours ---------------------------------------------------------

    public static Color background() { return new Color(theme.background); }
    public static Color sidebar() { return mix(background(), Color.BLACK, 0.3); }
    public static Color surface() { return new Color(theme.surface); }
    public static Color raised() { return mix(surface(), Color.WHITE, 0.035); }
    public static Color hover() { return mix(surface(), Color.WHITE, 0.07); }
    public static Color border() { return new Color(255, 255, 255, 20); }
    public static Color strongBorder() { return new Color(255, 255, 255, 38); }
    public static Color text() { return new Color(0xF3F1F8); }
    public static Color muted() { return new Color(theme.muted); }
    public static Color faint() { return mix(muted(), background(), 0.38); }
    public static Color accent() { return new Color(theme.accent); }
    public static Color accentHover() { return mix(accent(), Color.WHITE, 0.14); }
    public static Color accentSoft() { return alpha(accent(), 40); }
    public static Color onAccent() { return new Color(0x15141B); }
    public static Color danger() { return new Color(0xFF6F7D); }
    public static Color success() { return new Color(0x5FE0A5); }
    public static Color warning() { return new Color(0xF4C66E); }

    public static Color mix(Color a, Color b, double t) {
        double u = Math.max(0, Math.min(1, t));
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * u),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * u),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * u),
                (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * u));
    }

    public static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    // ---- type ------------------------------------------------------------

    public static Font font(int style, float size) {
        String key = style + ":" + size;
        synchronized (FONTS) {
            Font font = FONTS.get(key);
            if (font == null) {
                font = new Font(FAMILY, style, 12).deriveFont(size);
                FONTS.put(key, font);
            }
            return font;
        }
    }

    public static Font mono(float size) {
        String key = "mono:" + size;
        synchronized (FONTS) {
            Font font = FONTS.get(key);
            if (font == null) {
                font = new Font(MONO, Font.PLAIN, 12).deriveFont(size);
                FONTS.put(key, font);
            }
            return font;
        }
    }

    public static Font title() { return font(Font.BOLD, 24f); }
    public static Font heading() { return font(Font.BOLD, 15f); }
    public static Font body() { return font(Font.PLAIN, 13f); }
    public static Font bodyBold() { return font(Font.BOLD, 13f); }
    public static Font small() { return font(Font.PLAIN, 11.5f); }
    public static Font smallBold() { return font(Font.BOLD, 11f); }

    private static String pick(String... names) {
        Set<String> available;
        try { available = new HashSet<String>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())); }
        catch (Throwable ignored) { available = new HashSet<String>(); }
        for (String name : names) if (available.contains(name)) return name;
        return names[names.length - 1];
    }

    // ---- painting --------------------------------------------------------

    public static Graphics2D prepare(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Integer metrics keep painted text identical to what layout measured.
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    public static void fill(Graphics2D g, double x, double y, double w, double h, double radius, Color color) {
        g.setColor(color);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, radius * 2, radius * 2));
    }

    public static void stroke(Graphics2D g, double x, double y, double w, double h, double radius, Color color, float width) {
        g.setColor(color);
        g.setStroke(new BasicStroke(width));
        double inset = width / 2.0;
        g.draw(new RoundRectangle2D.Double(x + inset, y + inset, w - width, h - width, radius * 2 - width, radius * 2 - width));
    }

    /** A rounded panel with a hairline border. */
    public static void panel(Graphics2D g, double x, double y, double w, double h, double radius, Color fill, Color border) {
        fill(g, x, y, w, h, radius, fill);
        if (border != null) stroke(g, x, y, w, h, radius, border, 1f);
    }

    public static void text(Graphics2D g, String text, double x, double baseline, Font font, Color color) {
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, (float) x, (float) baseline);
    }

    /** Draws text vertically centred in a box of height {@code h} starting at {@code y}. */
    public static void textMiddle(Graphics2D g, String text, double x, double y, double h, Font font, Color color) {
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        text(g, text, x, y + (h - metrics.getHeight()) / 2.0 + metrics.getAscent(), font, color);
    }

    public static void textCentered(Graphics2D g, String text, double cx, double y, double h, Font font, Color color) {
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        textMiddle(g, text, cx - metrics.stringWidth(text) / 2.0, y, h, font, color);
    }

    public static String ellipsize(FontMetrics metrics, String text, int width) {
        if (text == null) return "";
        if (metrics.stringWidth(text) <= width) return text;
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + ellipsis) > width) end--;
        return text.substring(0, end).trim() + ellipsis;
    }

    /** Word-wraps text; explicit line breaks are kept. Overflowing text ends in an ellipsis. */
    public static List<String> wrap(FontMetrics metrics, String text, int width, int maxLines) {
        List<String> lines = new ArrayList<String>();
        if (text == null || text.isEmpty()) return lines;
        for (String paragraph : text.split("\n")) {
            String line = "";
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (line.isEmpty() || metrics.stringWidth(candidate) <= width) line = candidate;
                else { lines.add(line); line = word; }
            }
            lines.add(line);
        }
        boolean overflow = lines.size() > maxLines;
        if (overflow) lines = new ArrayList<String>(lines.subList(0, maxLines));
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (overflow && index == lines.size() - 1) {
                while (!line.isEmpty() && metrics.stringWidth(line + "\u2026") > width) line = line.substring(0, line.length() - 1);
                lines.set(index, line.trim() + "\u2026");
            } else if (metrics.stringWidth(line) > width) {
                lines.set(index, ellipsize(metrics, line, width));
            }
        }
        return lines;
    }
}
