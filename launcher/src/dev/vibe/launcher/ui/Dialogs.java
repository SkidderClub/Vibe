package dev.vibe.launcher.ui;

import dev.vibe.launcher.core.I18n;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Themed modal dialogs. */
final class Dialogs {
    private Dialogs() { }

    /** Rounded, shadowed windows need per-pixel translucency, which some Linux desktops lack. */
    static boolean translucencySupported() {
        try {
            return java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .isWindowTranslucencySupported(java.awt.GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean confirm(Component parent, String title, String message, String confirmLabel, boolean danger) {
        final boolean[] result = { false };
        Window owner = SwingUtilities.getWindowAncestor(parent);
        final JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        boolean translucent = translucencySupported();
        if (translucent) dialog.setBackground(new Color(0, 0, 0, 0));

        Card card = Card.column(14, 24);
        card.fill(() -> Style.mix(Style.surface(), Style.background(), 0.1)).outline(Style::strongBorder).radius(16);
        card.add(Label.heading(title));
        card.add(new Label(message, Style.body(), Label.Tone.MUTED, 8).preferredWidth(380));
        Stack.Panel buttons = Stack.row(10);
        buttons.add(Stack.glue(), Stack.FILL);
        FlatButton cancel = new FlatButton(I18n.t("Cancel"), null, FlatButton.Kind.SECONDARY, dialog::dispose);
        FlatButton ok = new FlatButton(confirmLabel, null, danger ? FlatButton.Kind.DANGER : FlatButton.Kind.PRIMARY, () -> {
            result[0] = true;
            dialog.dispose();
        });
        buttons.add(cancel);
        buttons.add(ok);
        card.add(Stack.space(2));
        card.add(buttons);

        JPanel root = new JPanel(new BorderLayout()) {
            private static final long serialVersionUID = 1L;
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = Style.prepare(graphics);
                if (isOpaque()) {
                    g.setColor(getBackground());
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
                Style.fill(g, 4, 6, getWidth() - 8, getHeight() - 8, 18, new Color(0, 0, 0, 90));
                g.dispose();
            }
        };
        root.setOpaque(!translucent);
        root.setBackground(Style.background());
        root.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));
        root.add(card);
        dialog.setContentPane(root);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        root.getActionMap().put("cancel", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(ActionEvent event) { dialog.dispose(); }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ok");
        root.getActionMap().put("ok", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(ActionEvent event) { ok.click(); }
        });
        int width = 440;
        dialog.setSize(width, Math.max(160, card.heightFor(width - 8) + 14));
        dialog.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(ok::requestFocusInWindow);
        dialog.setVisible(true);
        return result[0];
    }
}
