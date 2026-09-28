package dev.vibe.launcher.ui;

import dev.vibe.launcher.app.LauncherController;
import dev.vibe.launcher.app.LauncherController.State;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Text;
import dev.vibe.launcher.game.AccountVault;
import dev.vibe.launcher.game.LaunchMode;
import dev.vibe.launcher.skin.SkinService;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * The bar along the bottom of every page: account, game mode, status and the
 * play button, so a launch can be started and followed from anywhere.
 */
final class PlayBar extends Stack.Panel {
    private static final long serialVersionUID = 1L;
    static final int BAR_HEIGHT = 92;
    private final LauncherController controller;
    private final Chip account, mode;
    private final Status status;
    private final FlatButton secondary;
    private final PlayButton play;
    private final Timer shimmer;
    private double phase;

    PlayBar(LauncherController controller, Runnable showAccounts) {
        super(new Stack(false, 14, false));
        this.controller = controller;
        setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 22));

        account = new Chip(250, () -> showAccountMenu(showAccounts));
        mode = new Chip(236, this::showModeMenu);
        status = new Status();
        secondary = new FlatButton(I18n.t("Cancel"), Icons.CLOSE, FlatButton.Kind.SECONDARY, this::secondaryAction).height(44);
        play = new PlayButton();
        add(account);
        add(mode);
        add(status, Stack.FILL);
        add(secondary);
        add(play);
        shimmer = new Timer(33, event -> {
            phase += 0.033;
            status.repaint();
            play.repaint();
        });
        controller.addListener(event -> {
            switch (event) {
                case STATE: case SOURCE: case SETTINGS: case ACCOUNTS: case SKIN: refresh(); break;
                default: repaint();
            }
        });
        refresh();
    }

    /** Narrow windows keep both selectors; the mode shrinks to its icon. */
    void setCompact(boolean compact) {
        mode.compact = compact;
        account.compact = compact;
        mode.setToolTipText(compact ? I18n.t("Game mode") : null);
        revalidate();
        repaint();
    }

    /** Keyboard users land on Play first, not on a sidebar link. */
    void focusPlay() { play.requestFocusInWindow(); }

    private void refresh() {
        State state = controller.state();
        AccountVault.Account selected = controller.selectedAccount();
        AccountVault.Account automatic = controller.autoLoginAccount();
        account.image = SkinService.face(controller.skin(), 36);
        account.title = selected != null ? selected.name : automatic != null ? automatic.name : controller.displayName();
        account.subtitle = selected != null ? (selected.microsoft ? I18n.t("Microsoft account") : I18n.t("Offline account"))
                : automatic != null ? I18n.t("Vibe auto-login") : I18n.t("No account selected");
        account.enabled = true;

        LaunchMode current = controller.mode();
        mode.icon = modeIcon(current);
        mode.title = current.label;
        mode.subtitle = I18n.t(current.description);
        mode.enabled = state == State.IDLE;

        boolean busy = state == State.PREPARING || state == State.BUILDING || state == State.STOPPING;
        secondary.setVisible(busy || state == State.RUNNING);
        secondary.setEnabled(state != State.STOPPING);
        if (state == State.RUNNING || state == State.STOPPING) {
            secondary.setText(I18n.t("Stop"));
            secondary.setIcon(Icons.STOP);
            secondary.setKind(FlatButton.Kind.DANGER);
        } else {
            secondary.setText(state == State.BUILDING ? I18n.t("Stop") : I18n.t("Cancel"));
            secondary.setIcon(Icons.CLOSE);
            secondary.setKind(FlatButton.Kind.SECONDARY);
        }
        boolean animate = busy && Anim.enabled;
        if (animate && !shimmer.isRunning()) shimmer.start();
        if (!animate && shimmer.isRunning()) shimmer.stop();
        account.repaint();
        mode.repaint();
        status.repaint();
        play.repaint();
        revalidate();
    }

    private void secondaryAction() {
        State state = controller.state();
        if (state == State.RUNNING) {
            if (Dialogs.confirm(this, I18n.t("Stop Vibe?"), I18n.t("Minecraft will be closed immediately. Unsaved progress in singleplayer may be lost."), I18n.t("Stop"), true)) {
                controller.stop();
            }
        } else {
            controller.cancel();
        }
    }

    static Icons modeIcon(LaunchMode mode) {
        switch (mode) {
            case GTA7: return Icons.CITY;
            case GTA8: return Icons.CAR;
            case ACCOUNTS: return Icons.ACCOUNTS;
            default: return Icons.CUBE;
        }
    }

    private void showModeMenu() {
        if (controller.state() != State.IDLE) return;
        List<Menu.Item> items = new ArrayList<Menu.Item>();
        for (final LaunchMode option : LaunchMode.playable()) {
            items.add(new Menu.Item(option.label, I18n.t(option.description), modeIcon(option), null, option == controller.mode(),
                    () -> controller.setMode(option)));
        }
        Menu.show(mode, items, true, 300);
    }

    private void showAccountMenu(Runnable showAccounts) {
        List<Menu.Item> items = new ArrayList<Menu.Item>();
        AccountVault.Account selected = controller.selectedAccount();
        AccountVault.Account automatic = controller.autoLoginAccount();
        items.add(new Menu.Item(I18n.t("Automatic"), automatic != null ? I18n.t("Vibe logs in as {0}", automatic.name) : I18n.t("Use Vibe's auto-login"),
                Icons.SHIELD, null, selected == null, () -> controller.selectAccount(null)));
        for (final AccountVault.Account entry : controller.accounts()) {
            BufferedImage face = SkinService.face(controller.skins().current(entry.uuid, entry.name), 28);
            items.add(new Menu.Item(entry.name, entry.microsoft ? I18n.t("Microsoft account") : I18n.t("Offline account"), null, face,
                    selected != null && selected.uuid.equals(entry.uuid), () -> controller.selectAccount(entry)));
        }
        items.add(new Menu.Item(I18n.t("Manage accounts"), I18n.t("Add Microsoft or offline accounts"), Icons.USER_PLUS, null, false, showAccounts));
        Menu.show(account, items, true, 290);
    }

    @Override public Dimension getPreferredSize() { return new Dimension(600, BAR_HEIGHT); }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = Style.prepare(graphics);
        try {
            g.setColor(Style.mix(Style.background(), Style.sidebar(), 0.55));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Style.border());
            g.fillRect(0, 0, getWidth(), 1);
        } finally {
            g.dispose();
        }
    }

    /** Account or mode selector. */
    private static final class Chip extends JComponent {
        private static final long serialVersionUID = 1L;
        private final int width;
        private final Anim.Value hover = new Anim.Value(this, 0, 16);
        BufferedImage image;
        Icons icon;
        String title = "", subtitle = "";
        boolean enabled = true, compact;

        Chip(int width, Runnable action) {
            this.width = width;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { if (enabled) hover.to(1); }
                @Override public void mouseExited(MouseEvent event) { hover.to(0); }
                @Override public void mousePressed(MouseEvent event) { if (enabled) action.run(); }
            });
        }

        @Override public Dimension getPreferredSize() {
            if (!compact) return new Dimension(width, 56);
            return new Dimension(image != null ? 200 : 84, 56);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                if (!enabled) g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.55f));
                int w = getWidth(), h = getHeight();
                Style.panel(g, 0, 0, w, h, 12, Style.mix(Style.raised(), Style.hover(), hover.value), Style.border());
                if (compact && image == null && icon != null) {
                    Style.fill(g, 10, (h - 36) / 2.0, 36, 36, 10, Style.accentSoft());
                    icon.paint(g, 18, (h - 20) / 2.0, 20, Style.accent());
                    if (enabled) Icons.CHEVRON_DOWN.paint(g, w - 28, (h - 16) / 2.0, 16, Style.muted());
                    return;
                }
                int x = 10;
                if (image != null) {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.drawImage(image, x, (h - 36) / 2, 36, 36, null);
                    x += 48;
                } else if (icon != null) {
                    Style.fill(g, x, (h - 36) / 2.0, 36, 36, 10, Style.accentSoft());
                    icon.paint(g, x + 8, (h - 20) / 2.0, 20, Style.accent());
                    x += 48;
                }
                int textWidth = w - x - 34;
                Font titleFont = Style.bodyBold();
                Style.text(g, Style.ellipsize(g.getFontMetrics(titleFont), title, textWidth), x, h / 2 - 3, titleFont, Style.text());
                Style.text(g, Style.ellipsize(g.getFontMetrics(Style.small()), subtitle, textWidth), x, h / 2 + 14, Style.small(), Style.muted());
                if (enabled) Icons.CHEVRON_DOWN.paint(g, w - 28, (h - 16) / 2.0, 16, Style.muted());
            } finally {
                g.dispose();
            }
        }
    }

    /** Progress title, detail and bar; when idle, the readiness summary or the last error. */
    private final class Status extends JComponent {
        private static final long serialVersionUID = 1L;

        @Override public Dimension getPreferredSize() { return new Dimension(160, 56); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                int w = getWidth(), h = getHeight();
                State state = controller.state();
                String title, detail;
                Color titleColor = Style.text(), detailColor = Style.muted();
                boolean bar = state == State.PREPARING || state == State.BUILDING || state == State.STOPPING;
                if (state == State.IDLE) {
                    if (!controller.lastError().isEmpty()) {
                        title = I18n.t("Something went wrong");
                        detail = controller.lastError();
                        titleColor = Style.danger();
                    } else if (!controller.sourceInstalled()) {
                        title = I18n.t("Vibe is not installed yet");
                        detail = I18n.t("Press Play to download and set up everything automatically.");
                    } else {
                        title = I18n.t("Ready to play");
                        detail = summary();
                    }
                } else if (state == State.RUNNING) {
                    LaunchMode running = controller.runningMode();
                    title = running == null || running == LaunchMode.VIBE ? I18n.t("Vibe is running") : I18n.t("{0} is running", running.label);
                    detail = I18n.t("Have fun! Close Minecraft or press Stop to end the session.");
                    titleColor = Style.success();
                } else {
                    title = controller.progressTitle();
                    detail = controller.progressDetail();
                    if (controller.launchQueued() && state == State.PREPARING) detail = detail.isEmpty() ? I18n.t("Vibe starts right after this step") : detail;
                }
                Font titleFont = Style.bodyBold();
                int top = bar ? h / 2 - 12 : h / 2 - 3;
                Style.text(g, Style.ellipsize(g.getFontMetrics(titleFont), title, w), 0, top, titleFont, titleColor);
                Style.text(g, Style.ellipsize(g.getFontMetrics(Style.small()), detail, w), 0, top + 18, Style.small(), detailColor);
                if (bar) paintBar(g, 0, top + 28, w, 5, controller.progress());
            } finally {
                g.dispose();
            }
        }

        private String summary() {
            List<String> parts = new ArrayList<String>();
            String version = controller.vibeVersion();
            if (!version.isEmpty()) parts.add("Vibe " + version);
            String revision = controller.installedRevision();
            if (!revision.isEmpty()) parts.add(Text.shortSha(revision));
            if (controller.sourceUpdateAvailable()) parts.add(controller.settings().autoUpdate() ? I18n.t("update installs on launch") : I18n.t("update available"));
            else if (controller.settings().sourceCommitTime() > 0) parts.add(I18n.ago(controller.settings().sourceCommitTime()));
            return String.join("  ·  ", parts);
        }

        private void paintBar(Graphics2D g, double x, double y, double w, double h, double fraction) {
            Style.fill(g, x, y, w, h, h / 2, Style.alpha(Style.text(), 22));
            if (fraction >= 0) {
                Style.fill(g, x, y, Math.max(h, w * Math.min(1, fraction)), h, h / 2, Style.accent());
            } else {
                double segment = w * 0.28;
                double position = ((phase * 0.55) % 1.0) * (w + segment) - segment;
                Graphics2D clip = (Graphics2D) g.create();
                clip.clip(new java.awt.geom.RoundRectangle2D.Double(x, y, w, h, h, h));
                Style.fill(clip, x + position, y, segment, h, h / 2, Style.accent());
                clip.dispose();
            }
        }
    }

    /** The large call to action. */
    private final class PlayButton extends JComponent {
        private static final long serialVersionUID = 1L;
        private final Anim.Value hover = new Anim.Value(this, 0, 14);
        private boolean pressed;

        PlayButton() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hover.to(1); }
                @Override public void mouseExited(MouseEvent event) { hover.to(0); pressed = false; repaint(); }
                @Override public void mousePressed(MouseEvent event) { pressed = true; repaint(); }
                @Override public void mouseReleased(MouseEvent event) {
                    boolean fire = pressed && contains(event.getPoint());
                    pressed = false;
                    repaint();
                    if (fire) activate();
                }
            });
            addKeyListener(new java.awt.event.KeyAdapter() {
                @Override public void keyPressed(java.awt.event.KeyEvent event) {
                    if (event.getModifiersEx() != 0) return;
                    if (event.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER || event.getKeyCode() == java.awt.event.KeyEvent.VK_SPACE) activate();
                }
            });
        }

        private void activate() {
            State state = controller.state();
            if (state == State.IDLE || (state == State.PREPARING && !controller.launchQueued())) controller.play();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(212, 58); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                int w = getWidth(), h = getHeight();
                State state = controller.state();
                double inset = pressed ? 1.5 : 0;
                String label, sub;
                boolean active = state == State.IDLE || (state == State.PREPARING && !controller.launchQueued());
                if (state == State.RUNNING) { label = I18n.t("RUNNING"); sub = controller.displayName(); }
                else if (state == State.STOPPING) { label = I18n.t("STOPPING"); sub = ""; }
                else if (state == State.BUILDING || (state == State.PREPARING && controller.launchQueued())) {
                    double fraction = controller.progress();
                    label = fraction >= 0 ? Math.round(fraction * 100) + "%" : I18n.t("STARTING");
                    sub = state == State.BUILDING ? I18n.t("Building Vibe") : I18n.t("Preparing");
                } else {
                    label = controller.sourceInstalled() ? I18n.t("PLAY") : I18n.t("INSTALL & PLAY");
                    sub = controller.mode().label;
                }

                // Soft glow under the idle button.
                if (active && Anim.enabled) {
                    for (int ring = 3; ring >= 1; ring--) {
                        Style.fill(g, inset - ring * 2, inset - ring * 2 + 3, w - inset * 2 + ring * 4, h - inset * 2 + ring * 4, 16 + ring * 2,
                                Style.alpha(Style.accent(), (int) ((10 + 10 * hover.value) / ring)));
                    }
                }
                Color base = active ? Style.mix(Style.accent(), Style.accentHover(), hover.value)
                        : state == State.RUNNING ? Style.mix(Style.success(), Style.surface(), 0.78) : Style.mix(Style.accent(), Style.surface(), 0.72);
                Style.fill(g, inset, inset, w - inset * 2, h - inset * 2, 14, base);
                if (active) {
                    g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 38), 0, h, new Color(255, 255, 255, 0)));
                    g.fill(new java.awt.geom.RoundRectangle2D.Double(inset, inset, w - inset * 2, h / 2.0, 28, 28));
                }
                if (!active && (state == State.BUILDING || state == State.PREPARING)) {
                    double fraction = controller.progress();
                    Graphics2D clip = (Graphics2D) g.create();
                    clip.clip(new java.awt.geom.RoundRectangle2D.Double(inset, inset, w - inset * 2, h - inset * 2, 28, 28));
                    if (fraction >= 0) {
                        clip.setColor(Style.alpha(Style.accent(), 90));
                        clip.fillRect(0, 0, (int) (w * fraction), h);
                    } else {
                        double position = ((phase * 0.55) % 1.0) * (w * 1.6) - w * 0.3;
                        clip.setPaint(new GradientPaint((float) position, 0, Style.alpha(Style.accent(), 0), (float) (position + w * 0.3), 0, Style.alpha(Style.accent(), 90), true));
                        clip.fillRect(0, 0, w, h);
                    }
                    clip.dispose();
                }
                if (!active) Style.stroke(g, inset, inset, w - inset * 2, h - inset * 2, 14, Style.strongBorder(), 1f);
                if (isFocusOwner() && FlatButton.FocusStyle.keyboard) Style.stroke(g, 0, 0, w, h, 15, Style.text(), 2f);

                Color text = active ? Style.onAccent() : state == State.RUNNING ? Style.success() : Style.text();
                Font big = Style.font(Font.BOLD, 17f);
                FontMetrics metrics = g.getFontMetrics(big);
                int iconSize = active ? 18 : 0;
                int content = metrics.stringWidth(label) + (iconSize > 0 ? iconSize + 10 : 0);
                double x = (w - content) / 2.0;
                double labelTop = sub.isEmpty() ? (h - metrics.getHeight()) / 2.0 : h / 2.0 - metrics.getHeight() + 3;
                if (iconSize > 0) {
                    Icons.PLAY.paint(g, x, labelTop + (metrics.getHeight() - iconSize) / 2.0, iconSize, text);
                    x += iconSize + 10;
                }
                Style.text(g, label, x, labelTop + metrics.getAscent(), big, text);
                if (!sub.isEmpty()) {
                    Font small = Style.font(Font.PLAIN, 11.5f);
                    Style.textCentered(g, Style.ellipsize(g.getFontMetrics(small), sub, w - 24), w / 2.0, h / 2.0 + 3, 16, small, Style.alpha(text, 190));
                }
            } finally {
                g.dispose();
            }
        }
    }
}
