package dev.vibe.launcher.ui;

import dev.vibe.launcher.app.ConsoleBuffer;
import dev.vibe.launcher.app.LauncherController;
import dev.vibe.launcher.core.I18n;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/** Live Gradle and Minecraft output plus launcher messages. */
final class ConsolePage extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int MAX_LINES = 6000;
    private final LauncherController controller;
    private final JTextPane text = new JTextPane();
    private final JScrollPane scroll;

    ConsolePage(LauncherController controller) {
        super(new BorderLayout());
        this.controller = controller;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Pages.PADDING - 2, Pages.PADDING, Pages.PADDING, Pages.PADDING));

        FlatButton copy = FlatButton.icon(Icons.COPY, I18n.t("Copy everything"), this::copyAll);
        FlatButton openLog = new FlatButton(I18n.t("Game log"), Icons.FOLDER, FlatButton.Kind.SECONDARY, () -> {
            Path log = controller.currentGameLog();
            controller.openPath(log != null ? log : controller.paths().gameLogs());
        });
        FlatButton launcherLog = new FlatButton(I18n.t("Launcher log"), Icons.FOLDER, FlatButton.Kind.SECONDARY, () -> controller.openPath(controller.logFile()));
        FlatButton clear = FlatButton.icon(Icons.BROOM, I18n.t("Clear"), () -> controller.console().clear());
        Stack.Panel header = Pages.header(I18n.t("Console"), I18n.t("Build output, Minecraft's log and launcher messages. Errors are highlighted."), copy, clear, launcherLog, openLog);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        add(header, BorderLayout.NORTH);

        text.setEditable(false);
        text.setOpaque(false);
        text.setFont(Style.mono(12.5f));
        text.setForeground(Style.text());
        text.setSelectionColor(Style.alpha(Style.accent(), 90));
        text.setMargin(new Insets(14, 16, 14, 16));
        ((DefaultCaret) text.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        JPanel frame = new JPanel(new BorderLayout()) {
            private static final long serialVersionUID = 1L;
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = Style.prepare(graphics);
                Style.panel(g, 0, 0, getWidth(), getHeight(), 12, Style.mix(Style.background(), Color.BLACK, 0.35), Style.border());
                g.dispose();
            }
        };
        frame.setOpaque(false);
        frame.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        scroll = new JScrollPane(text);
        Scroll.style(scroll);
        scroll.getHorizontalScrollBar().setUnitIncrement(22);
        frame.add(scroll);
        add(frame, BorderLayout.CENTER);

        append(controller.console().snapshot());
        controller.console().addListener(new ConsoleBuffer.Listener() {
            @Override public void appended(List<String> lines) { append(lines); }
            @Override public void cleared() { text.setText(""); }
        });
        controller.addListener(event -> { if (event == LauncherController.Event.THEME) text.setSelectionColor(Style.alpha(Style.accent(), 90)); });
    }

    void shown() {
        controller.console().markSeen();
        scrollToEnd();
    }

    private void append(List<String> lines) {
        // Keep following new output only while the view is already at the bottom.
        JScrollBar bar = scroll.getVerticalScrollBar();
        boolean follow = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 40;
        Document document = text.getDocument();
        try {
            for (String line : lines) document.insertString(document.getLength(), line + "\n", style(line));
            Element root = document.getDefaultRootElement();
            int excess = root.getElementCount() - MAX_LINES;
            if (excess > 0) document.remove(0, root.getElement(excess - 1).getEndOffset());
        } catch (BadLocationException ignored) {
            // Only possible if the document changed concurrently; the next batch continues.
        }
        if (follow) scrollToEnd();
    }

    private void scrollToEnd() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private static SimpleAttributeSet style(String line) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        Color color;
        if (ConsoleBuffer.isError(line)) color = Style.danger();
        else if (ConsoleBuffer.isWarning(line)) color = Style.warning();
        else if (line.startsWith("[Launcher]")) color = Style.accent();
        else if (line.startsWith("> Task") || line.startsWith("[Vibe]") || line.startsWith("BUILD")) color = Style.mix(Style.accent(), Style.text(), 0.5);
        else color = Style.mix(Style.muted(), Style.text(), 0.35);
        StyleConstants.setForeground(attributes, color);
        if (line.startsWith("[Launcher]") || line.startsWith("BUILD") || line.startsWith("FAILURE")) StyleConstants.setBold(attributes, true);
        return attributes;
    }

    private void copyAll() {
        StringBuilder all = new StringBuilder();
        for (String line : controller.console().snapshot()) all.append(line).append('\n');
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(all.toString()), null);
    }
}
