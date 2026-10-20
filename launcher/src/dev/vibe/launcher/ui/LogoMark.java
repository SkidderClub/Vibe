package dev.vibe.launcher.ui;

import java.awt.BasicStroke;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** The launcher's "V" mark, drawn in the theme accent; also used for the window icons. */
final class LogoMark {
    private LogoMark() { }

    static void paint(Graphics2D g, double x, double y, double size) {
        Graphics2D copy = (Graphics2D) g.create();
        try {
            copy.translate(x, y);
            copy.setPaint(new GradientPaint(0, 0, Style.accentHover(), (float) size, (float) size, Style.mix(Style.accent(), Style.background(), 0.35)));
            copy.fill(new RoundRectangle2D.Double(0, 0, size, size, size * 0.56, size * 0.56));
            Path2D v = new Path2D.Double();
            v.moveTo(size * 0.27, size * 0.3);
            v.lineTo(size * 0.5, size * 0.72);
            v.lineTo(size * 0.73, size * 0.3);
            copy.setColor(Style.onAccent());
            copy.setStroke(new BasicStroke((float) (size * 0.13), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            copy.draw(v);
        } finally {
            copy.dispose();
        }
    }

    static List<Image> icons() {
        List<Image> images = new ArrayList<Image>();
        for (int size : new int[] { 16, 20, 24, 32, 40, 48, 64, 128, 256 }) {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = Style.prepare(image.getGraphics());
            paint(g, 0, 0, size);
            g.dispose();
            images.add(image);
        }
        return images;
    }
}
