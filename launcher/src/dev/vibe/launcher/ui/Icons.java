package dev.vibe.launcher.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/** Line icons drawn on a 24-unit grid, so they stay sharp at every size and colour. */
public enum Icons {
    HOME, MODS, ACCOUNTS, APPEARANCE, SETTINGS, CONSOLE, PLAY, STOP, FOLDER, REFRESH, TRASH, DOWNLOAD, DISCORD, GITHUB,
    CHECK, CLOSE, MINIMIZE, MAXIMIZE, RESTORE, EXTERNAL, WARNING, INFO, CHEVRON_DOWN, PLUS, COPY, SHIELD, CUBE, CAR, CITY, USER_PLUS, BROOM, JAVA;

    public void paint(Graphics2D graphics, double x, double y, double size, Color color) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(x, y);
            g.scale(size / 24.0, size / 24.0);
            g.setColor(color);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            draw(g);
        } finally {
            g.dispose();
        }
    }

    private void draw(Graphics2D g) {
        switch (this) {
            case HOME: {
                Path2D p = new Path2D.Double();
                p.moveTo(3.5, 11); p.lineTo(12, 3.8); p.lineTo(20.5, 11);
                g.draw(p);
                Path2D body = new Path2D.Double();
                body.moveTo(5.8, 9.5); body.lineTo(5.8, 20); body.lineTo(18.2, 20); body.lineTo(18.2, 9.5);
                g.draw(body);
                g.draw(new RoundRectangle2D.Double(10, 14, 4, 6, 1, 1));
                break;
            }
            case MODS: {
                // Puzzle piece.
                Path2D p = new Path2D.Double();
                p.moveTo(4.5, 8); p.lineTo(9, 8);
                p.curveTo(8, 4.5, 14, 4.5, 13, 8);
                p.lineTo(17, 8); p.lineTo(17, 11.5);
                p.curveTo(20.5, 10.5, 20.5, 16.5, 17, 15.5);
                p.lineTo(17, 20); p.lineTo(4.5, 20); p.closePath();
                g.draw(p);
                break;
            }
            case ACCOUNTS: {
                g.draw(new Ellipse2D.Double(8, 3.8, 8, 8));
                Path2D p = new Path2D.Double();
                p.moveTo(4.5, 20.5); p.curveTo(4.5, 14.8, 19.5, 14.8, 19.5, 20.5);
                g.draw(p);
                break;
            }
            case USER_PLUS: {
                g.draw(new Ellipse2D.Double(5.5, 3.8, 8, 8));
                Path2D p = new Path2D.Double();
                p.moveTo(2.5, 20.5); p.curveTo(2.5, 15, 16.5, 15, 16.5, 20.5);
                g.draw(p);
                g.draw(new Line2D.Double(19.5, 8, 19.5, 14));
                g.draw(new Line2D.Double(16.5, 11, 22.5, 11));
                break;
            }
            case APPEARANCE: {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 3.5);
                p.curveTo(6.5, 3.5, 3.5, 7.5, 3.5, 12);
                p.curveTo(3.5, 16.8, 7.2, 20.5, 11.5, 20.5);
                p.curveTo(13.5, 20.5, 13.5, 18.3, 12.6, 17.2);
                p.curveTo(11.7, 16, 12.5, 14.5, 14, 14.5);
                p.lineTo(16.5, 14.5);
                p.curveTo(19, 14.5, 20.5, 12.8, 20.5, 10.8);
                p.curveTo(20.5, 6.6, 16.8, 3.5, 12, 3.5);
                g.draw(p);
                fillDot(g, 8, 10, 1.3); fillDot(g, 11.5, 7.2, 1.3); fillDot(g, 15.5, 8.5, 1.3);
                break;
            }
            case SETTINGS: {
                Path2D gear = new Path2D.Double();
                int teeth = 8;
                for (int i = 0; i < teeth * 2; i++) {
                    double angle = Math.PI * i / teeth - Math.PI / 2;
                    double radius = i % 2 == 0 ? 8.6 : 6.9;
                    double a0 = angle - Math.PI / teeth * 0.42, a1 = angle + Math.PI / teeth * 0.42;
                    double x0 = 12 + Math.cos(a0) * radius, y0 = 12 + Math.sin(a0) * radius;
                    double x1 = 12 + Math.cos(a1) * radius, y1 = 12 + Math.sin(a1) * radius;
                    if (i == 0) gear.moveTo(x0, y0); else gear.lineTo(x0, y0);
                    gear.lineTo(x1, y1);
                }
                gear.closePath();
                g.draw(gear);
                g.draw(new Ellipse2D.Double(9, 9, 6, 6));
                break;
            }
            case CONSOLE: {
                g.draw(new RoundRectangle2D.Double(3, 4.5, 18, 15, 3, 3));
                Path2D p = new Path2D.Double();
                p.moveTo(7, 9.5); p.lineTo(10, 12); p.lineTo(7, 14.5);
                g.draw(p);
                g.draw(new Line2D.Double(12, 15, 16.5, 15));
                break;
            }
            case PLAY: {
                Path2D p = new Path2D.Double();
                p.moveTo(7.5, 4.8); p.lineTo(19, 12); p.lineTo(7.5, 19.2); p.closePath();
                g.fill(p);
                break;
            }
            case STOP:
                g.fill(new RoundRectangle2D.Double(6, 6, 12, 12, 3, 3));
                break;
            case FOLDER: {
                Path2D p = new Path2D.Double();
                p.moveTo(3.5, 7); p.lineTo(3.5, 18.5); p.lineTo(20.5, 18.5); p.lineTo(20.5, 8.5); p.lineTo(11.5, 8.5);
                p.lineTo(9.5, 5.5); p.lineTo(3.5, 5.5); p.closePath();
                g.draw(p);
                break;
            }
            case REFRESH: {
                g.draw(new Arc2D.Double(4.5, 4.5, 15, 15, 60, 270, Arc2D.OPEN));
                Path2D p = new Path2D.Double();
                p.moveTo(15.8, 3.2); p.lineTo(16.3, 6.4); p.lineTo(13, 7.2);
                g.draw(p);
                break;
            }
            case TRASH: {
                g.draw(new Line2D.Double(4, 6.5, 20, 6.5));
                Path2D p = new Path2D.Double();
                p.moveTo(9, 6.5); p.lineTo(9.5, 3.8); p.lineTo(14.5, 3.8); p.lineTo(15, 6.5);
                g.draw(p);
                Path2D bin = new Path2D.Double();
                bin.moveTo(6, 6.5); bin.lineTo(7, 20); bin.lineTo(17, 20); bin.lineTo(18, 6.5);
                g.draw(bin);
                g.draw(new Line2D.Double(10.2, 10, 10.2, 16.5));
                g.draw(new Line2D.Double(13.8, 10, 13.8, 16.5));
                break;
            }
            case DOWNLOAD: {
                g.draw(new Line2D.Double(12, 4, 12, 15));
                Path2D p = new Path2D.Double();
                p.moveTo(7.5, 10.5); p.lineTo(12, 15); p.lineTo(16.5, 10.5);
                g.draw(p);
                g.draw(new Line2D.Double(5, 19.5, 19, 19.5));
                break;
            }
            case DISCORD: {
                Path2D p = new Path2D.Double();
                p.moveTo(7, 6.8); p.curveTo(9.5, 5.6, 14.5, 5.6, 17, 6.8);
                p.curveTo(19.3, 10, 20.4, 13.5, 20.2, 17);
                p.curveTo(18.8, 18.1, 17.3, 18.8, 15.8, 19.2); p.lineTo(14.8, 17.4);
                p.curveTo(13, 17.9, 11, 17.9, 9.2, 17.4); p.lineTo(8.2, 19.2);
                p.curveTo(6.7, 18.8, 5.2, 18.1, 3.8, 17);
                p.curveTo(3.6, 13.5, 4.7, 10, 7, 6.8); p.closePath();
                g.draw(p);
                fillDot(g, 9.4, 12.8, 1.5); fillDot(g, 14.6, 12.8, 1.5);
                break;
            }
            case GITHUB: {
                // Branch glyph: GitHub's octocat is a trademark, a neutral symbol reads fine.
                g.draw(new Ellipse2D.Double(5, 3.5, 4, 4));
                g.draw(new Ellipse2D.Double(5, 16.5, 4, 4));
                g.draw(new Ellipse2D.Double(15, 6.5, 4, 4));
                g.draw(new Line2D.Double(7, 7.5, 7, 16.5));
                Path2D p = new Path2D.Double();
                p.moveTo(17, 10.5); p.curveTo(17, 14.5, 7, 12.5, 7, 16.5);
                g.draw(p);
                break;
            }
            case CHECK: {
                Path2D p = new Path2D.Double();
                p.moveTo(5, 12.5); p.lineTo(10, 17.5); p.lineTo(19, 7);
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(p);
                break;
            }
            case CLOSE:
                g.draw(new Line2D.Double(6.5, 6.5, 17.5, 17.5));
                g.draw(new Line2D.Double(17.5, 6.5, 6.5, 17.5));
                break;
            case MINIMIZE:
                g.draw(new Line2D.Double(6.5, 12, 17.5, 12));
                break;
            case MAXIMIZE:
                g.draw(new RoundRectangle2D.Double(6.5, 6.5, 11, 11, 1.5, 1.5));
                break;
            case RESTORE: {
                g.draw(new RoundRectangle2D.Double(5.5, 8.5, 10, 10, 1.5, 1.5));
                Path2D p = new Path2D.Double();
                p.moveTo(8.5, 8.5); p.lineTo(8.5, 5.5); p.lineTo(18.5, 5.5); p.lineTo(18.5, 15.5); p.lineTo(15.5, 15.5);
                g.draw(p);
                break;
            }
            case EXTERNAL: {
                Path2D p = new Path2D.Double();
                p.moveTo(11, 5); p.lineTo(5, 5); p.lineTo(5, 19); p.lineTo(19, 19); p.lineTo(19, 13);
                g.draw(p);
                g.draw(new Line2D.Double(12, 12, 19.5, 4.5));
                Path2D arrow = new Path2D.Double();
                arrow.moveTo(14.5, 4.5); arrow.lineTo(19.5, 4.5); arrow.lineTo(19.5, 9.5);
                g.draw(arrow);
                break;
            }
            case WARNING: {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 3.8); p.lineTo(21, 19.5); p.lineTo(3, 19.5); p.closePath();
                g.draw(p);
                g.draw(new Line2D.Double(12, 9.5, 12, 13.8));
                fillDot(g, 12, 16.6, 1.2);
                break;
            }
            case INFO:
                g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
                g.draw(new Line2D.Double(12, 11, 12, 16.5));
                fillDot(g, 12, 7.8, 1.2);
                break;
            case CHEVRON_DOWN: {
                Path2D p = new Path2D.Double();
                p.moveTo(6.5, 9.5); p.lineTo(12, 15); p.lineTo(17.5, 9.5);
                g.draw(p);
                break;
            }
            case PLUS:
                g.draw(new Line2D.Double(12, 5, 12, 19));
                g.draw(new Line2D.Double(5, 12, 19, 12));
                break;
            case COPY:
                g.draw(new RoundRectangle2D.Double(8.5, 8.5, 11.5, 11.5, 3, 3));
                g.draw(pathOf(new double[] { 15.5, 8.5, 15.5, 4, 4, 4, 4, 15.5, 8.5, 15.5 }));
                break;
            case SHIELD: {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 3.5); p.lineTo(19.5, 6.5); p.curveTo(19.5, 13, 16.5, 18, 12, 20.5);
                p.curveTo(7.5, 18, 4.5, 13, 4.5, 6.5); p.closePath();
                g.draw(p);
                Path2D check = new Path2D.Double();
                check.moveTo(8.8, 12); check.lineTo(11.2, 14.4); check.lineTo(15.4, 9.6);
                g.draw(check);
                break;
            }
            case CUBE: {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 3.5); p.lineTo(19.5, 7.7); p.lineTo(19.5, 16.3); p.lineTo(12, 20.5); p.lineTo(4.5, 16.3); p.lineTo(4.5, 7.7); p.closePath();
                g.draw(p);
                g.draw(pathOf(new double[] { 4.5, 7.7, 12, 12, 19.5, 7.7 }));
                g.draw(new Line2D.Double(12, 12, 12, 20.5));
                break;
            }
            case CAR: {
                Path2D p = new Path2D.Double();
                p.moveTo(3.5, 16.5); p.lineTo(3.5, 12.5); p.lineTo(6, 11.5); p.lineTo(8, 7.5); p.lineTo(16, 7.5);
                p.lineTo(18, 11.5); p.lineTo(20.5, 12.5); p.lineTo(20.5, 16.5); p.closePath();
                g.draw(p);
                g.draw(new Ellipse2D.Double(5.5, 14.8, 3.6, 3.6));
                g.draw(new Ellipse2D.Double(14.9, 14.8, 3.6, 3.6));
                g.draw(new Line2D.Double(6, 11.5, 18, 11.5));
                break;
            }
            case CITY: {
                g.draw(new Rectangle2D.Double(3.5, 10, 6, 10.5));
                g.draw(new Rectangle2D.Double(9.5, 4, 6.5, 16.5));
                g.draw(new Rectangle2D.Double(16, 8.5, 4.5, 12));
                g.draw(new Line2D.Double(12, 7.5, 13.5, 7.5));
                g.draw(new Line2D.Double(12, 11, 13.5, 11));
                g.draw(new Line2D.Double(12, 14.5, 13.5, 14.5));
                break;
            }
            case BROOM: {
                g.draw(new Line2D.Double(19.5, 4, 12.5, 11));
                Path2D p = new Path2D.Double();
                p.moveTo(10.5, 9.5); p.lineTo(14.5, 13.5); p.lineTo(11, 20); p.lineTo(4, 13); p.closePath();
                g.draw(p);
                g.draw(new Line2D.Double(7.5, 16.5, 10, 14));
                break;
            }
            case JAVA: {
                Path2D cup = new Path2D.Double();
                cup.moveTo(5, 11); cup.lineTo(5, 16); cup.curveTo(5, 19, 7.5, 20.5, 10.5, 20.5);
                cup.curveTo(13.5, 20.5, 16, 19, 16, 16); cup.lineTo(16, 11); cup.closePath();
                g.draw(cup);
                g.draw(new Arc2D.Double(14, 12, 6, 5, -90, 180, Arc2D.OPEN));
                g.draw(pathOf(new double[] { 9, 3.5, 8, 5.5, 9.5, 7.5, 8.5, 9 }));
                g.draw(pathOf(new double[] { 12.5, 3.5, 11.5, 5.5, 13, 7.5, 12, 9 }));
                break;
            }
            default:
                break;
        }
    }

    private static void fillDot(Graphics2D g, double cx, double cy, double radius) {
        g.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
    }

    private static Shape pathOf(double[] points) {
        Path2D path = new Path2D.Double();
        path.moveTo(points[0], points[1]);
        for (int index = 2; index < points.length; index += 2) path.lineTo(points[index], points[index + 1]);
        return path;
    }
}
