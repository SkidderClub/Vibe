package dev.vibe.launcher.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JComponent;

/** Custom window title bar: drag to move, double-click to maximise, window controls on the right. */
final class TitleBar extends Stack.Panel {
    private static final long serialVersionUID = 1L;
    static final int BAR_HEIGHT = 46;
    private final LauncherFrame frame;
    private final WindowButton maximize;
    private Point dragOffset;

    TitleBar(LauncherFrame frame) {
        super(new Stack(false, 0, true));
        this.frame = frame;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        add(new Brand());
        add(Stack.glue(), Stack.FILL);
        add(new WindowButton(Icons.MINIMIZE, false, frame::minimize));
        maximize = new WindowButton(Icons.MAXIMIZE, false, frame::toggleMaximized);
        add(maximize);
        add(new WindowButton(Icons.CLOSE, true, frame::requestClose));

        MouseAdapter drag = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1 && !frame.isResizing()) dragOffset = event.getPoint();
            }
            @Override public void mouseDragged(MouseEvent event) {
                if (dragOffset == null || frame.isResizing()) return;
                Point screen = event.getLocationOnScreen();
                if (frame.isMaximizedCustom()) {
                    // Pull the window out of the maximised state under the cursor, like native title bars.
                    double ratio = dragOffset.x / (double) Math.max(1, getWidth());
                    frame.restoreFromMaximized();
                    dragOffset = new Point((int) (frame.getWidth() * ratio), dragOffset.y);
                }
                frame.setLocation(screen.x - dragOffset.x, screen.y - dragOffset.y);
            }
            @Override public void mouseReleased(MouseEvent event) {
                if (dragOffset != null) frame.snapIfAtTop(event.getLocationOnScreen());
                dragOffset = null;
            }
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1 && event.getClickCount() == 2) frame.toggleMaximized();
            }
        };
        addMouseListener(drag);
        addMouseMotionListener(drag);
    }

    void updateMaximized(boolean maximized) { maximize.icon = maximized ? Icons.RESTORE : Icons.MAXIMIZE; maximize.repaint(); }

    @Override public Dimension getPreferredSize() { return new Dimension(400, BAR_HEIGHT); }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = Style.prepare(graphics);
        try {
            g.setColor(Style.sidebar());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Style.border());
            g.fillRect(0, getHeight() - 1, getWidth(), 1);
        } finally {
            g.dispose();
        }
    }

    /** The Vibe mark and word mark. */
    private static final class Brand extends JComponent {
        private static final long serialVersionUID = 1L;
        @Override public Dimension getPreferredSize() { return new Dimension(190, BAR_HEIGHT); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                int size = 24, y = (getHeight() - size) / 2;
                LogoMark.paint(g, 0, y, size);
                Font word = Style.font(Font.BOLD, 15f);
                Style.textMiddle(g, "VIBE", size + 11, 0, getHeight(), word, Style.text());
                int wordWidth = g.getFontMetrics(word).stringWidth("VIBE");
                Font small = Style.font(Font.BOLD, 10f);
                g.setFont(small);
                String label = "LAUNCHER";
                double x = size + 11 + wordWidth + 8;
                // Letter-spaced label, the way Vibe's menus set small caps.
                for (char c : label.toCharArray()) {
                    Style.textMiddle(g, String.valueOf(c), x, 1, getHeight(), small, Style.muted());
                    x += g.getFontMetrics().charWidth(c) + 1.6;
                }
            } finally {
                g.dispose();
            }
        }
    }

    private static final class WindowButton extends FlatButton {
        private static final long serialVersionUID = 1L;
        Icons icon;
        private final boolean close;

        WindowButton(Icons icon, boolean close, Runnable action) {
            super("", icon, Kind.GHOST, action);
            this.icon = icon;
            this.close = close;
            setFocusable(false);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(48, BAR_HEIGHT); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                double h = hover.value;
                if (h > 0.01) {
                    g.setColor(close ? Style.alpha(new java.awt.Color(0xE5484D), (int) (230 * h)) : Style.alpha(Style.text(), (int) (22 * h)));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
                icon.paint(g, (getWidth() - 16) / 2.0, (getHeight() - 16) / 2.0, 16, close && h > 0.5 ? Style.text() : Style.mix(Style.muted(), Style.text(), h));
            } finally {
                g.dispose();
            }
        }
    }
}
