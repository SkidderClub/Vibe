package dev.vibe.launcher.ui;

import dev.vibe.launcher.skin.PlayerModel;
import dev.vibe.launcher.skin.SkinService;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * The rotating player on the home page: the selected account's real skin and
 * cape, framed by Vibe's default 2D ESP (corner box, health bar, name, distance
 * and held item) so the client's look is visible before the game starts.
 */
final class PlayerPreview extends JComponent {
    private static final long serialVersionUID = 1L;
    private PlayerModel model;
    private String name = "";
    private boolean esp = true;
    private boolean animate = true;
    private boolean windowVisible = true;
    private double yaw = -0.45, velocity, time;
    private boolean dragging, userMoved;
    private int lastX;
    private long lastFrame;
    private final Timer timer = new Timer(33, event -> tick());

    PlayerPreview() {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
        setToolTipText(null);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { dragging = true; lastX = event.getX(); velocity = 0; }
            @Override public void mouseDragged(MouseEvent event) {
                double delta = (event.getX() - lastX) * 0.012;
                yaw += delta;
                velocity = delta * 30;
                lastX = event.getX();
                userMoved = true;
                repaint();
            }
            @Override public void mouseReleased(MouseEvent event) { dragging = false; }
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) { yaw = -0.45; velocity = 0; userMoved = false; repaint(); }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) updateTimer();
        });
    }

    void setSkin(SkinService.Skin skin) {
        model = new PlayerModel(skin);
        repaint();
    }

    void setPlayerName(String value) { name = value == null ? "" : value; repaint(); }
    void setEsp(boolean value) { esp = value; repaint(); }

    /** Pauses all motion, e.g. while the game is running or when animations are off. */
    void setAnimate(boolean value) {
        animate = value;
        updateTimer();
    }

    /** Minimised windows stop animating: nobody can see it, so it should cost nothing. */
    void setWindowVisible(boolean value) {
        windowVisible = value;
        updateTimer();
    }

    private void updateTimer() {
        boolean run = animate && windowVisible && isShowing();
        if (run && !timer.isRunning()) {
            lastFrame = System.nanoTime();
            timer.start();
        } else if (!run && timer.isRunning()) {
            timer.stop();
        }
    }

    private void tick() {
        long now = System.nanoTime();
        double seconds = Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        time += seconds;
        if (!dragging) {
            if (Math.abs(velocity) > 0.01) {
                yaw += velocity * seconds;
                velocity *= Math.pow(0.04, seconds);
            } else if (!userMoved) {
                // A gentle sway keeps the face in view instead of spinning endlessly.
                yaw = -0.45 + Math.sin(time * 0.4) * 0.65;
            }
        }
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        if (model == null) return;
        Graphics2D g = Style.prepare(graphics);
        try {
            int w = getWidth(), h = getHeight();
            // 32 pixels of player, about 3.6 more for the ESP name above and 4.9 for the two labels below.
            double scale = Math.max(3, Math.min(h / 43.0, w * 0.42 / 16.0));
            double cx = w / 2.0, cy = h / 2.0 - scale * 0.65;

            // Accent glow and floor shadow.
            float radius = (float) (scale * 22);
            g.setPaint(new RadialGradientPaint((float) cx, (float) (cy - scale * 2), radius, new float[] { 0f, 1f },
                    new Color[] { Style.alpha(Style.accent(), 46), Style.alpha(Style.accent(), 0) }));
            g.fill(new Ellipse2D.Double(cx - radius, cy - scale * 2 - radius, radius * 2, radius * 2));
            g.setPaint(new RadialGradientPaint((float) cx, (float) (cy + scale * 16.5), (float) (scale * 9), new float[] { 0f, 1f },
                    new Color[] { new Color(0, 0, 0, 120), new Color(0, 0, 0, 0) }));
            g.fill(new Ellipse2D.Double(cx - scale * 9, cy + scale * 14.5, scale * 18, scale * 4));

            double bob = animate ? Math.sin(time * 1.6) * scale * 0.18 : 0;
            Rectangle2D bounds = model.render(g, cx, cy + bob, scale, yaw, 0.2, animate ? time : 1.2);
            if (esp) paintEsp(g, bounds, scale);
        } finally {
            g.dispose();
        }
    }

    /** Mirrors Esp2DRenderer's defaults: white corners with a dark outline, left health bar, labels. */
    private void paintEsp(Graphics2D g, Rectangle2D model, double scale) {
        double pad = scale * 1.6;
        double x = model.getX() - pad, y = model.getY() - pad, w = model.getWidth() + pad * 2, h = model.getHeight() + pad * 2;
        double corner = Math.max(10, Math.min(w, h) / 4);
        float line = (float) Math.max(1.5, scale / 5);
        g.setStroke(new BasicStroke(line + 2.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        corners(g, x, y, w, h, corner, new Color(0, 0, 0, 200));
        g.setStroke(new BasicStroke(line, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        corners(g, x, y, w, h, corner, Color.WHITE);

        double barWidth = Math.max(3, scale / 3), barX = x - barWidth - scale * 0.9;
        g.setColor(new Color(0, 0, 0, 200));
        g.fill(new Rectangle2D.Double(barX - 1.5, y - 1.5, barWidth + 3, h + 3));
        double health = 0.75;
        g.setColor(new Color(0x53E88C));
        g.fill(new Rectangle2D.Double(barX, y + h * (1 - health), barWidth, h * health));

        Font bold = Style.font(Font.BOLD, (float) Math.max(12, scale * 1.15));
        Font plain = Style.font(Font.PLAIN, (float) Math.max(11, scale * 1.0));
        shadowed(g, name.isEmpty() ? "Player" : name, x + w / 2, y - scale * 0.9, bold);
        FontMetrics metrics = g.getFontMetrics(plain);
        double below = y + h + scale * 0.9 + metrics.getAscent();
        shadowed(g, "16 m", x + w / 2, below, plain);
        shadowed(g, "Diamond Sword", x + w / 2, below + metrics.getHeight(), plain);
    }

    private static void corners(Graphics2D g, double x, double y, double w, double h, double length, Color color) {
        g.setColor(color);
        g.draw(new Line2D.Double(x, y, x + length, y));
        g.draw(new Line2D.Double(x, y, x, y + length));
        g.draw(new Line2D.Double(x + w, y, x + w - length, y));
        g.draw(new Line2D.Double(x + w, y, x + w, y + length));
        g.draw(new Line2D.Double(x, y + h, x + length, y + h));
        g.draw(new Line2D.Double(x, y + h, x, y + h - length));
        g.draw(new Line2D.Double(x + w, y + h, x + w - length, y + h));
        g.draw(new Line2D.Double(x + w, y + h, x + w, y + h - length));
    }

    private static void shadowed(Graphics2D g, String text, double cx, double baseline, Font font) {
        g.setFont(font);
        double x = cx - g.getFontMetrics().stringWidth(text) / 2.0;
        g.setColor(new Color(0, 0, 0, 200));
        g.drawString(text, (float) (x + 1), (float) (baseline + 1));
        g.setColor(Color.WHITE);
        g.drawString(text, (float) x, (float) baseline);
    }
}
