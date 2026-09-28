package dev.vibe.launcher.ui;

import dev.vibe.launcher.app.LauncherController;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.game.Theme;
import dev.vibe.launcher.install.LauncherUpdater;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Theme presets shared with Vibe's main menu, plus launcher look-and-feel preferences. */
final class AppearancePage extends JPanel {
    private static final long serialVersionUID = 1L;
    private final LauncherController controller;
    private final JPanel grid = new JPanel(new GridLayout(0, 3, 14, 14)) {
        private static final long serialVersionUID = 1L;
        @Override public Dimension getPreferredSize() {
            int columns = ((GridLayout) getLayout()).getColumns();
            int rows = (Theme.values().length + columns - 1) / columns;
            return new Dimension(600, rows * 132 + (rows - 1) * 14);
        }
    };

    AppearancePage(LauncherController controller) {
        super(new BorderLayout());
        this.controller = controller;
        setOpaque(false);
        grid.setOpaque(false);
        for (Theme theme : Theme.values()) grid.add(new ThemeCard(theme));

        Stack.Panel body = Pages.body(18);
        body.add(Pages.header(I18n.t("Appearance"), I18n.t("The six colour presets of Vibe's main menu. Your choice is shared, so the game and the launcher always match.")));
        body.add(grid);

        Card preferences = Pages.section(I18n.t("Launcher"), Icons.APPEARANCE);
        Toggle animations = new Toggle(controller.settings().animations(), on -> {
            controller.settings().setAnimations(on);
            Anim.enabled = on;
            controller.settingsChanged();
        });
        preferences.add(Pages.setting(I18n.t("Animations"), I18n.t("Rotate the player and animate transitions. Motion always pauses while Minecraft is running."), animations));
        preferences.add(Pages.divider());
        Toggle esp = new Toggle(controller.settings().espPreview(), on -> {
            controller.settings().setEspPreview(on);
            controller.settingsChanged();
        });
        preferences.add(Pages.setting(I18n.t("ESP preview"), I18n.t("Frame the home page player with Vibe's default 2D ESP."), esp));
        preferences.add(Pages.divider());
        String[] languages = { I18n.t("Automatic"), "English", "Deutsch" };
        String current = controller.settings().language();
        Segmented language = new Segmented(languages, "en".equals(current) ? 1 : "de".equals(current) ? 2 : 0, index -> {
            controller.settings().setLanguage(index == 1 ? "en" : index == 2 ? "de" : "auto");
            if (Dialogs.confirm(this, I18n.t("Restart the launcher?"), I18n.t("The new language is used after a restart."), I18n.t("Restart now"), false)) {
                try {
                    LauncherUpdater.restart();
                    System.exit(0);
                } catch (Exception error) {
                    controller.log().warn("Restart failed", error);
                }
            }
        });
        preferences.add(Pages.setting(I18n.t("Language"), I18n.t("Automatic follows the language chosen in Vibe, then your system."), language));
        body.add(preferences);
        add(Scroll.of(body), BorderLayout.CENTER);

        controller.addListener(event -> {
            if (event == LauncherController.Event.THEME) grid.repaint();
            if (event == LauncherController.Event.SETTINGS) esp.setOn(controller.settings().espPreview());
        });
    }

    void setColumns(int columns) {
        ((GridLayout) grid.getLayout()).setColumns(columns);
        Container parent = grid.getParent();
        if (parent != null) parent.revalidate();
    }

    /** A miniature of the launcher in the theme's colours. */
    private final class ThemeCard extends JComponent {
        private static final long serialVersionUID = 1L;
        private final Theme theme;
        private final Anim.Value hover = new Anim.Value(this, 0, 16);

        ThemeCard(Theme theme) {
            this.theme = theme;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hover.to(1); }
                @Override public void mouseExited(MouseEvent event) { hover.to(0); }
                @Override public void mouseClicked(MouseEvent event) { controller.setTheme(ThemeCard.this.theme); }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                int w = getWidth(), h = getHeight();
                boolean selected = controller.theme() == theme;
                Color accent = new Color(theme.accent), background = new Color(theme.background), surface = new Color(theme.surface), muted = new Color(theme.muted);
                double lift = hover.value * 2;
                Style.panel(g, 0, 2 - lift, w, h - 2, 14, background, selected ? accent : Style.mix(Style.border(), accent, hover.value * 0.6));
                if (selected) Style.stroke(g, 0, 2 - lift, w, h - 2, 14, accent, 2f);
                // Mini window: sidebar, content lines and a play button.
                double top = 2 - lift + 12, left = 12, innerW = w - 24, innerH = h - 54;
                Style.fill(g, left, top, innerW, innerH, 8, Style.mix(background, Color.BLACK, 0.2));
                Style.fill(g, left, top, innerW * 0.22, innerH, 8, Style.mix(background, Color.BLACK, 0.35));
                for (int index = 0; index < 4; index++) {
                    Style.fill(g, left + 8, top + 10 + index * 12, innerW * 0.22 - 16, 5, 2.5, index == 0 ? accent : Style.alpha(muted, 110));
                }
                Style.fill(g, left + innerW * 0.3, top + 10, innerW * 0.4, 7, 3.5, Style.alpha(Color.WHITE, 200));
                Style.fill(g, left + innerW * 0.3, top + 23, innerW * 0.55, 5, 2.5, Style.alpha(muted, 150));
                Style.fill(g, left + innerW * 0.3, top + innerH - 22, innerW * 0.62, 14, 5, surface);
                Style.fill(g, left + innerW - innerW * 0.26 - 6, top + innerH - 22, innerW * 0.26, 14, 5, accent);
                g.setColor(accent);
                g.fill(new Ellipse2D.Double(14, h - 30 - lift, 14, 14));
                Style.text(g, theme.label, 36, h - 18 - lift, Style.bodyBold(), Color.WHITE);
                if (selected) {
                    Icons.CHECK.paint(g, w - 30, h - 32 - lift, 16, accent);
                }
            } finally {
                g.dispose();
            }
        }
    }
}
