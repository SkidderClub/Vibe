package dev.vibe.launcher.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** Scroll panes with a thin overlay-style scrollbar and content that tracks the viewport width. */
public final class Scroll {
    private Scroll() { }

    public static JScrollPane of(Component content) {
        JPanel holder = new WidthTracking(content);
        JScrollPane pane = new JScrollPane(holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        style(pane);
        return pane;
    }

    public static void style(JScrollPane pane) {
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setViewportBorder(null);
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        pane.getVerticalScrollBar().setUI(new ThinBar());
        pane.getVerticalScrollBar().setOpaque(false);
        pane.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        pane.getVerticalScrollBar().setUnitIncrement(22);
        pane.getHorizontalScrollBar().setUI(new ThinBar());
        pane.getHorizontalScrollBar().setOpaque(false);
        pane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 10));
    }

    /** Lets wrapped text reflow: the content is always exactly as wide as the viewport. */
    private static final class WidthTracking extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;
        private final Component content;

        WidthTracking(Component content) {
            super(null);
            this.content = content;
            setOpaque(false);
            add(content);
        }

        @Override public void doLayout() { content.setBounds(0, 0, getWidth(), getHeight()); }

        @Override public Dimension getPreferredSize() {
            int width = getParent() != null ? getParent().getWidth() : content.getPreferredSize().width;
            int height = Stack.heightOf(content, width);
            return new Dimension(width, height);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 22; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(22, visible.height - 40); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() > Stack.heightOf(content, getParent().getWidth());
        }
    }

    static final class ThinBar extends BasicScrollBarUI {
        @Override protected JButton createDecreaseButton(int orientation) { return empty(); }
        @Override protected JButton createIncreaseButton(int orientation) { return empty(); }

        private static JButton empty() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }

        @Override protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) { }

        @Override protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
            if (bounds.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g = Style.prepare(graphics);
            try {
                boolean vertical = scrollbar.getOrientation() == JScrollBar.VERTICAL;
                int alpha = isDragging ? 110 : isThumbRollover() ? 80 : 45;
                if (vertical) Style.fill(g, bounds.x + 3, bounds.y + 2, 4, bounds.height - 4, 2, Style.alpha(Style.text(), alpha));
                else Style.fill(g, bounds.x + 2, bounds.y + 3, bounds.width - 4, 4, 2, Style.alpha(Style.text(), alpha));
            } finally {
                g.dispose();
            }
        }
    }
}
