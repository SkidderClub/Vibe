package dev.vibe.launcher.ui;

import dev.vibe.launcher.app.LauncherController;
import dev.vibe.launcher.core.ErrorCode;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.game.AccountVault;
import dev.vibe.launcher.skin.SkinService;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Accounts from Vibe's encrypted vault. The launcher only chooses which one Vibe
 * signs in with; adding accounts and Microsoft sign-in happen in Vibe's Alt Manager.
 */
final class AccountsPage extends JPanel {
    private static final long serialVersionUID = 1L;
    private final LauncherController controller;
    private final Stack.Panel list = Stack.column(8);

    AccountsPage(LauncherController controller) {
        super(new BorderLayout());
        this.controller = controller;
        setOpaque(false);

        Stack.Panel body = Pages.body(18);
        FlatButton manage = new FlatButton(I18n.t("Add account"), Icons.USER_PLUS, FlatButton.Kind.PRIMARY, this::openAltManager);
        manage.setToolTipText(I18n.t("Starts Vibe straight into its Alt Manager for Microsoft or offline sign-in."));
        FlatButton refresh = FlatButton.icon(Icons.REFRESH, I18n.t("Reload"), controller::reloadAccounts);
        body.add(Pages.header(I18n.t("Accounts"), I18n.t("Choose the account Vibe signs in with. New accounts are added in Vibe's own Alt Manager."), refresh, manage));

        Card info = Card.row(14, 16);
        info.fill(() -> Style.alpha(Style.accent(), 22)).outline(() -> Style.alpha(Style.accent(), 60));
        info.add(new Pages.IconBadge(Icons.SHIELD, 34));
        info.add(new Label(I18n.t("Sign-in tokens stay in Vibe's encrypted vault. The launcher only reads names and passes your choice to Vibe; it never sees or stores passwords or tokens."),
                Style.small(), Label.Tone.TEXT, 3), Stack.FILL);
        body.add(info);
        body.add(list);
        add(Scroll.of(body), BorderLayout.CENTER);

        controller.addListener(event -> {
            if (event == LauncherController.Event.ACCOUNTS) rebuild();
            else if (event == LauncherController.Event.SKIN || event == LauncherController.Event.STATE) list.repaint();
        });
        rebuild();
    }

    private void openAltManager() {
        if (controller.state() != LauncherController.State.IDLE) return;
        controller.openAltManager();
    }

    private void rebuild() {
        list.removeAll();
        AccountVault.Account selected = controller.selectedAccount();
        AccountVault.Account automatic = controller.autoLoginAccount();
        list.add(new Row(null, I18n.t("Automatic"), automatic != null ? I18n.t("Vibe signs in as {0} (Auto Login)", automatic.name)
                : I18n.t("Vibe uses its Auto Login account, or the last session"), selected == null));
        if (!controller.accountsError().isEmpty()) {
            Card error = Card.column(6, 18);
            error.outline(() -> Style.alpha(Style.danger(), 90));
            ErrorCode code = controller.accountsCode() == null ? ErrorCode.ACCOUNT_VAULT : controller.accountsCode();
            error.add(new Label(code.id() + " \u00b7 " + I18n.t("The account vault could not be read"), Style.bodyBold(), Label.Tone.DANGER));
            error.add(Label.small(controller.accountsError() + " " + code.hint()));
            Stack.Panel actions = Stack.row(8);
            actions.add(new FlatButton(I18n.t("How to fix"), Icons.EXTERNAL, FlatButton.Kind.SECONDARY, () -> controller.openHelp(code)));
            error.add(actions);
            list.add(error);
        } else if (controller.accounts().isEmpty()) {
            Card empty = Card.column(6, 22);
            empty.add(new Label(I18n.t("No saved accounts yet"), Style.bodyBold(), Label.Tone.TEXT));
            empty.add(Label.small(I18n.t("Press Add account: Vibe opens its Alt Manager, where you can sign in with Microsoft or create an offline profile. Back here, pick the account to play with.")));
            list.add(empty);
        }
        for (AccountVault.Account account : controller.accounts()) {
            String subtitle = (account.microsoft ? I18n.t("Microsoft account") : I18n.t("Offline account"))
                    + (account.autoLogin ? "  ·  " + I18n.t("Auto Login") : "");
            list.add(new Row(account, account.name, subtitle, selected != null && selected.uuid.equals(account.uuid)));
            controller.skins().load(account.uuid, account.name, account.microsoft, skin -> list.repaint());
        }
        list.revalidate();
        list.repaint();
    }

    private final class Row extends JComponent {
        private static final long serialVersionUID = 1L;
        private final AccountVault.Account account;
        private final String title, subtitle;
        private final boolean selected;
        private final Anim.Value hover = new Anim.Value(this, 0, 16);

        Row(AccountVault.Account account, String title, String subtitle, boolean selected) {
            this.account = account;
            this.title = title;
            this.subtitle = subtitle;
            this.selected = selected;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hover.to(1); }
                @Override public void mouseExited(MouseEvent event) { hover.to(0); }
                @Override public void mouseClicked(MouseEvent event) { controller.selectAccount(Row.this.account); }
            });
        }

        @Override public Dimension getPreferredSize() { return new Dimension(400, 68); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = Style.prepare(graphics);
            try {
                int w = getWidth(), h = getHeight();
                java.awt.Color fill = selected ? Style.mix(Style.surface(), Style.accent(), 0.12) : Style.mix(Style.surface(), Style.hover(), hover.value);
                Style.panel(g, 0, 0, w, h, 12, fill, selected ? Style.alpha(Style.accent(), 150) : Style.border());
                int x = 16;
                if (account == null) {
                    Style.fill(g, x, (h - 40) / 2.0, 40, 40, 10, Style.accentSoft());
                    Icons.SHIELD.paint(g, x + 9, (h - 22) / 2.0, 22, Style.accent());
                } else {
                    SkinService.Skin skin = controller.skins().current(account.uuid, account.name);
                    BufferedImage face = SkinService.face(skin, 40);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.drawImage(face, x, (h - 40) / 2, 40, 40, null);
                }
                x += 56;
                int textWidth = w - x - 140;
                Style.text(g, Style.ellipsize(g.getFontMetrics(Style.heading()), title, textWidth), x, h / 2 - 3, Style.heading(), Style.text());
                Style.text(g, Style.ellipsize(g.getFontMetrics(Style.small()), subtitle, textWidth), x, h / 2 + 16, Style.small(), Style.muted());
                if (selected) {
                    String label = I18n.t("Selected");
                    int width = g.getFontMetrics(Style.smallBold()).stringWidth(label) + 38;
                    Style.fill(g, w - width - 16, (h - 26) / 2.0, width, 26, 13, Style.accent());
                    Icons.CHECK.paint(g, w - width - 8, (h - 14) / 2.0, 14, Style.onAccent());
                    Style.textMiddle(g, label, w - width + 10, (h - 26) / 2.0, 26, Style.smallBold(), Style.onAccent());
                } else if (hover.value > 0.05) {
                    String label = I18n.t("Use this account");
                    int width = g.getFontMetrics(Style.smallBold()).stringWidth(label);
                    Style.textMiddle(g, label, w - width - 20, 0, h, Style.smallBold(), Style.alpha(Style.accent(), (int) (255 * hover.value)));
                }
            } finally {
                g.dispose();
            }
        }
    }
}
