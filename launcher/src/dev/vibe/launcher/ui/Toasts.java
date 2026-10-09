package dev.vibe.launcher.ui;

import dev.vibe.launcher.app.LauncherController;
import dev.vibe.launcher.core.I18n;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.Timer;

/** Notifications stacked in the bottom-right corner, above the play bar. */
final class Toasts {
    private static final int TOAST_WIDTH = 360, MARGIN = 18;
    private final JLayeredPane layer;
    private final List<View> views = new ArrayList<View>();
    private final Runnable showConsole;
    private int bottomOffset = 100;

    Toasts(JLayeredPane layer, Runnable showConsole) {
        this.layer = layer;
        this.showConsole = showConsole;
    }

    void setBottomOffset(int offset) {
        bottomOffset = offset;
        layout();
    }

    void show(LauncherController.Notice notice) {
        // The same message twice in a row only refreshes the existing toast.
        for (View view : views) {
            if (view.notice.title.equals(notice.title) && String.valueOf(view.notice.message).equals(String.valueOf(notice.message))) {
                view.restart();
                return;
            }
        }
        View view = new View(notice);
        views.add(0, view);
        while (views.size() > 4) dismiss(views.get(views.size() - 1));
        layer.add(view, JLayeredPane.POPUP_LAYER);
        layout();
        view.appear();
    }

    void layout() {
        int y = layer.getHeight() - bottomOffset - MARGIN;
        for (View view : views) {
            int height = view.preferredHeight();
            y -= height;
            view.setBounds(layer.getWidth() - TOAST_WIDTH - MARGIN, y, TOAST_WIDTH, height);
            y -= 10;
        }
        layer.repaint();
    }

    private void dismiss(View view) {
        view.timer.stop();
        views.remove(view);
        layer.remove(view);
        layout();
    }

    private final class View extends JComponent {
        private static final long serialVersionUID = 1L;
        final LauncherController.Notice notice;
        final Anim.Value opacity = new Anim.Value(this, 0, 12);
        final Timer timer;
        private Rectangle actionBounds, helpBounds, closeBounds;
        private boolean hoverAction, hoverHelp, hoverClose;

        View(LauncherController.Notice notice) {
            this.notice = notice;
            int delay = notice.level == LauncherController.Notice.Level.ERROR ? 12000 : notice.actionLabel != null ? 9000 : 5500;
            timer = new Timer(delay, event -> dismiss(this));
            timer.setRepeats(false);
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { timer.stop(); }
                @Override public void mouseExited(MouseEvent event) { timer.restart(); hoverAction = false; hoverHelp = false; hoverClose = false; repaint(); }
                @Override public void mouseMoved(MouseEvent event) {
                    hoverAction = actionBounds != null && actionBounds.contains(event.getPoint());
                    hoverHelp = helpBounds != null && helpBounds.contains(event.getPoint());
                    hoverClose = closeBounds != null && closeBounds.contains(event.getPoint());
                    setCursor(Cursor.getPredefinedCursor(hoverAction || hoverHelp || hoverClose ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    repaint();
                }
                @Override public void mouseClicked(MouseEvent event) {
                    if (actionBounds != null && actionBounds.contains(event.getPoint())) {
                        dismiss(View.this);
                        if (notice.action == LauncherController.Notice.SHOW_CONSOLE || notice.action == null) showConsole.run();
                        else notice.action.run();
                    } else if (helpBounds != null && helpBounds.contains(event.getPoint())) {
                        // The toast stays: its text is what the guide is looked up for.
                        notice.help.run();
                    } else if (closeBounds != null && closeBounds.contains(event.getPoint())) {
                        dismiss(View.this);
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        void appear() { opacity.to(1); timer.start(); }
        void restart() { timer.restart(); repaint(); }

        private Color levelColor() {
            switch (notice.level) {
                case SUCCESS: return Style.success();
                case WARNING: return Style.warning();
                case ERROR: return Style.danger();
                default: return Style.accent();
            }
        }

        private Icons levelIcon() {
            switch (notice.level) {
                case SUCCESS: return Icons.CHECK;
                case WARNING: case ERROR: return Icons.WARNING;
                default: return Icons.INFO;
            }
        }

        /** Errors with a code keep their cause and their fix, so they may take more lines. */
        private int maxLines() { return notice.code != null ? 8 : 5; }

        private List<String> titleLines(FontMetrics metrics) { return Style.wrap(metrics, notice.title, TOAST_WIDTH - 86, 2); }

        int preferredHeight() {
            FontMetrics metrics = getFontMetrics(Style.small());
            FontMetrics titleMetrics = getFontMetrics(Style.bodyBold());
            int lines = notice.message == null || notice.message.isEmpty() ? 0 : Style.wrap(metrics, notice.message, TOAST_WIDTH - 86, maxLines()).size();
            int extraTitle = (titleLines(titleMetrics).size() - 1) * titleMetrics.getHeight();
            return 44 + extraTitle + lines * (metrics.getHeight() + 1) + (notice.actionLabel != null || notice.help != null ? 28 : 0);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, opacity.value))));
                int w = getWidth(), h = getHeight();
                Style.fill(g, 0, 2, w, h - 2, 14, new Color(0, 0, 0, 70));
                Style.panel(g, 0, 0, w, h - 2, 14, Style.mix(Style.surface(), Style.background(), 0.15), Style.strongBorder());
                Color color = levelColor();
                Style.fill(g, 14, 14, 30, 30, 9, Style.alpha(color, 40));
                levelIcon().paint(g, 20, 20, 18, color);
                int textX = 56, textWidth = w - textX - 30;
                Font titleFont = Style.bodyBold();
                FontMetrics titleMetrics = g.getFontMetrics(titleFont);
                int y = 30;
                List<String> title = titleLines(titleMetrics);
                for (int index = 0; index < title.size(); index++) {
                    if (index > 0) y += titleMetrics.getHeight();
                    Style.text(g, Style.ellipsize(titleMetrics, title.get(index), textWidth), textX, y, titleFont, Style.text());
                }
                if (notice.message != null && !notice.message.isEmpty()) {
                    g.setFont(Style.small());
                    FontMetrics metrics = g.getFontMetrics();
                    for (String line : Style.wrap(metrics, notice.message, w - 86, maxLines())) {
                        y += metrics.getHeight() + 1;
                        Style.text(g, line, textX, y, Style.small(), Style.muted());
                    }
                }
                closeBounds = new Rectangle(w - 30, 10, 20, 20);
                Icons.CLOSE.paint(g, w - 28, 12, 16, hoverClose ? Style.text() : Style.faint());
                Font font = Style.smallBold();
                int buttonX = textX - 2;
                if (notice.actionLabel != null) {
                    int width = g.getFontMetrics(font).stringWidth(notice.actionLabel) + 20;
                    actionBounds = new Rectangle(buttonX, y + 8, width, 24);
                    Style.fill(g, actionBounds.x, actionBounds.y, actionBounds.width, actionBounds.height, 7, Style.alpha(color, hoverAction ? 70 : 40));
                    Style.textCentered(g, notice.actionLabel, actionBounds.getCenterX(), actionBounds.y, actionBounds.height, font, Style.mix(color, Style.text(), 0.3));
                    buttonX += width + 8;
                } else {
                    actionBounds = null;
                }
                if (notice.help != null) {
                    String label = I18n.t("How to fix");
                    int width = g.getFontMetrics(font).stringWidth(label) + 20;
                    helpBounds = new Rectangle(buttonX, y + 8, width, 24);
                    Style.fill(g, helpBounds.x, helpBounds.y, helpBounds.width, helpBounds.height, 7, Style.alpha(Style.text(), hoverHelp ? 38 : 18));
                    Style.textCentered(g, label, helpBounds.getCenterX(), helpBounds.y, helpBounds.height, font, Style.text());
                } else {
                    helpBounds = null;
                }
            } finally {
                g.dispose();
            }
        }
    }
}
