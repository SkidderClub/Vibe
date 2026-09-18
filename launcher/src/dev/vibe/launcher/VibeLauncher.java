package dev.vibe.launcher;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;

/**
 * A dependency-free Java 8 launcher for the Vibe Forge profile.
 *
 * <p>The launcher deliberately does not store account tokens itself. It reads only
 * the display name and UUID from Vibe's existing encrypted account vault, then
 * writes the selected identity to the profile's launcher bridge preferences. The
 * game process performs the actual Microsoft token refresh through Vibe's normal
 * Account Manager.</p>
 */
public final class VibeLauncher {
    public static final String VERSION = "1.0.0";
    private static final String PRODUCT = "Vibe Launcher";

    private VibeLauncher() { }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println(PRODUCT + " needs a desktop environment.");
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                AppLog log = new AppLog();
                LauncherSettings settings = new LauncherSettings(log);
                LauncherFrame frame = new LauncherFrame(settings, log);
                frame.setVisible(true);
            }
        });
    }

    enum Theme {
        LAVENDER("Lavender", 0xFFB4A0FF, 0xFF111218, 0xFF191B24, 0xFF9398AD),
        OCEAN("Ocean", 0xFF79BFFF, 0xFF10151C, 0xFF18222D, 0xFF91A4B8),
        MINT("Mint", 0xFF7DDDC3, 0xFF101817, 0xFF182522, 0xFF91AAA3),
        ROSE("Rose", 0xFFF0A0BA, 0xFF191216, 0xFF281C23, 0xFFB099A5),
        AMBER("Amber", 0xFFE8BF7A, 0xFF191611, 0xFF272219, 0xFFAEA28D),
        GRAPHITE("Graphite", 0xFFBEC5D0, 0xFF131416, 0xFF202226, 0xFF9A9FA8);

        final String label;
        final int accent;
        final int background;
        final int surface;
        final int muted;
        Theme(String label, int accent, int background, int surface, int muted) {
            this.label = label; this.accent = accent; this.background = background;
            this.surface = surface; this.muted = muted;
        }
        static Theme safeValueOf(String name) {
            try { return Theme.valueOf(name); } catch (Exception ignored) { return LAVENDER; }
        }
    }

    static final class LauncherFrame extends JFrame {
        LauncherFrame(LauncherSettings settings, AppLog log) {
            super(PRODUCT + " " + VERSION);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setMinimumSize(new Dimension(950, 630));
            setSize(1200, 760);
            setLocationByPlatform(true);
            setContentPane(new LauncherPanel(settings, log));
        }
    }

    static final class LauncherPanel extends JPanel {
        private static final int PLAY = 0, MODS = 1, ACCOUNTS = 2, THEMES = 3, SETTINGS = 4;
        private final LauncherSettings settings;
        private final AppLog log;
        private int page = PLAY;
        private Theme theme;
        private float phase;
        private float modelYaw = -0.20F;
        private int dragX;
        private volatile String status = "Ready. Vibe is downloaded and built automatically.";
        private volatile String updateMessage = "Checking GitHub for launcher updates…";
        private final List<HitArea> hits = new ArrayList<HitArea>();
        private List<AccountSummary> accounts = Collections.emptyList();
        private volatile boolean launchInProgress;
        private volatile boolean preparationInProgress;

        LauncherPanel(LauncherSettings settings, AppLog log) {
            this.settings = settings;
            this.log = log;
            this.theme = settings.getTheme();
            setOpaque(true);
            setTransferHandler(new ModsTransferHandler(this));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) { mouseDown(event); }
                @Override public void mouseReleased(MouseEvent event) { mouseUp(event); }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseDragged(MouseEvent event) { mouseDrag(event); }
                @Override public void mouseMoved(MouseEvent event) { updateCursor(event.getX(), event.getY()); }
            });
            new Timer(16, event -> { phase += 0.045F; repaint(); }).start();
            reloadAccounts();
            checkForUpdates(false);
            prepareInstallation();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                hits.clear();
                drawShell(g);
                switch (page) {
                    case MODS: drawMods(g); break;
                    case ACCOUNTS: drawAccounts(g); break;
                    case THEMES: drawThemes(g); break;
                    case SETTINGS: drawSettings(g); break;
                    default: drawPlay(g); break;
                }
            } finally { g.dispose(); }
        }

        private void drawShell(Graphics2D g) {
            int w = getWidth(), h = getHeight();
            g.setColor(color(theme.background)); g.fillRect(0, 0, w, h);
            // The subtle moving waves are the launcher equivalent of Vibe's menu shader.
            g.setStroke(new BasicStroke(1.3F));
            for (int wave = 0; wave < 3; wave++) {
                GeneralPath path = new GeneralPath();
                for (int x = 0; x <= w; x += 8) {
                    double y = h * 0.58 + Math.sin(x * 0.010 + phase + wave * 1.9) * (38 + wave * 15)
                            + Math.cos(x * 0.004 + phase * .4F + wave) * 19;
                    if (x == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                g.setColor(withAlpha(theme.accent, 25 + wave * 8)); g.draw(path);
            }
            g.setColor(withAlpha(0xFF000000, 100)); g.fillRect(0, 0, 78, h);
            g.setColor(withAlpha(theme.surface, 240)); g.fillRect(0, 0, 76, h);
            g.setColor(withAlpha(theme.accent, 130)); g.fillRect(75, 0, 1, h);
            g.setColor(withAlpha(theme.surface, 245)); g.fillRect(76, 0, w - 76, 68);
            g.setColor(withAlpha(theme.accent, 70)); g.fillRect(76, 67, w - 76, 1);

            font(g, Font.BOLD, 20); text(g, "VIBE", 23, 42, 0xFFF6F3FF);
            font(g, Font.PLAIN, 10); text(g, "LAUNCHER", 18, 56, theme.accent);
            font(g, Font.BOLD, 18); text(g, "VIBE CLIENT", 104, 36, 0xFFF5F2FA);
            font(g, Font.PLAIN, 12); text(g, "Forge 1.8.9  •  " + VERSION, 105, 53, theme.muted);

            String selected = settings.getSelectedName();
            String account = selected.isEmpty() ? "No account selected" : selected;
            pill(g, w - 244, 19, 142, 31, account, false, "accounts");
            pill(g, w - 91, 19, 67, 31, "LOGS", false, "logs");

            String[] labels = {"PLAY", "MODS", "ALTS", "THEMES", "SETTINGS"};
            String[] icons = {"▶", "▦", "●", "◈", "⚙"};
            for (int i = 0; i < labels.length; i++) {
                int y = 97 + i * 72;
                boolean active = page == i;
                if (active) round(g, 13, y, 50, 50, 12, withAlpha(theme.accent, 45), theme.accent);
                font(g, Font.BOLD, 18); centered(g, icons[i], 38, y + 24, active ? 0xFFFFFFFF : theme.muted);
                font(g, Font.BOLD, 9); centered(g, labels[i], 38, y + 40, active ? 0xFFFFFFFF : theme.muted);
                hits.add(new HitArea(new java.awt.Rectangle(8, y - 3, 60, 57), "page:" + i));
            }
        }

        private void drawPlay(Graphics2D g) {
            int w = getWidth(), h = getHeight();
            int right = Math.max(245, Math.min(286, w / 4));
            int stageLeft = 76, stageRight = w - right;
            font(g, Font.BOLD, 13); text(g, "VIBE HOME", stageLeft + 27, 98, theme.accent);
            font(g, Font.PLAIN, 13); text(g, status, stageLeft + 27, 120, theme.muted);
            g.setColor(withAlpha(theme.accent, 65)); g.drawLine(stageRight, 69, stageRight, h);
            drawPlayerPreview(g, (stageLeft + stageRight) / 2, h / 2 + 10);
            int buttonW = Math.min(335, stageRight - stageLeft - 90);
            int buttonX = (stageLeft + stageRight - buttonW) / 2;
            actionButton(g, buttonX, h - 155, buttonW, 54, "LAUNCH VIBE", "Forge 1.8.9 • OptiFine included", "launch", true);
            actionButton(g, buttonX, h - 91, buttonW, 36, "LAUNCH GTA7", "Start the separate GTA7 entry route", "gta", false);
            drawNews(g, stageRight + 20, 97, right - 38, h - 125);
        }

        private void drawPlayerPreview(Graphics2D g, int cx, int cy) {
            int bob = (int) (Math.sin(phase * 1.4F) * 5);
            int scale = Math.max(2, Math.min(4, getHeight() / 210));
            int bodyW = (int) (30 * scale * (0.74 + .26 * Math.abs(Math.cos(modelYaw))));
            int skew = (int) (Math.sin(modelYaw) * 16 * scale);
            int head = 26 * scale;
            int x = cx - bodyW / 2 + skew / 3;
            int y = cy - head - 55 * scale + bob;
            // drop shadow
            g.setColor(withAlpha(0xFF000000, 92)); g.fillOval(cx - 44 * scale, cy + 29 * scale + bob, 88 * scale, 13 * scale);
            // cape / cosmetic wings
            g.setColor(withAlpha(theme.accent, 175)); g.fillRoundRect(x + bodyW - 6 * scale, y + head + 8 * scale, 17 * scale, 48 * scale, 7, 7);
            g.setColor(withAlpha(0xFFFFD976, 220)); g.fillOval(x + bodyW / 2 - 10 * scale, y - 8 * scale, 20 * scale, 12 * scale);
            // legs
            g.setColor(new Color(0xFF202331, true)); g.fillRect(x + 4 * scale, y + head + 45 * scale, bodyW / 2 - 3 * scale, 33 * scale);
            g.fillRect(x + bodyW / 2 + 1 * scale, y + head + 45 * scale, bodyW / 2 - 4 * scale, 33 * scale);
            // hoodie torso / arms
            g.setColor(new Color(0xFF3E3557, true)); g.fillRoundRect(x, y + head, bodyW, 48 * scale, 5 * scale, 5 * scale);
            g.setColor(color(theme.accent)); g.fillRect(x + 4 * scale, y + head + 16 * scale, bodyW - 8 * scale, 8 * scale);
            g.setColor(new Color(0xFFF1C8AC, true));
            g.fillRoundRect(x - 9 * scale, y + head + 6 * scale, 11 * scale, 37 * scale, 5, 5);
            g.fillRoundRect(x + bodyW - 2 * scale, y + head + 6 * scale, 11 * scale, 37 * scale, 5, 5);
            // face / hair
            g.setColor(new Color(0xFFF2C7A8, true)); g.fillRoundRect(x, y, head, head, 5 * scale, 5 * scale);
            g.setColor(new Color(0xFF33283B, true));
            g.fillRoundRect(x - 2 * scale, y - 2 * scale, head + 4 * scale, 9 * scale, 4 * scale, 4 * scale);
            g.fillRect(x, y + 5 * scale, 7 * scale, 12 * scale);
            g.fillRect(x + head - 7 * scale, y + 5 * scale, 7 * scale, 12 * scale);
            g.setColor(new Color(0xFFF4F6FB, true)); g.fillRect(x + 6 * scale, y + 12 * scale, 5 * scale, 4 * scale); g.fillRect(x + head - 11 * scale, y + 12 * scale, 5 * scale, 4 * scale);
            // Vibe's default 2D ESP: white outlined corner box, left green health
            // bar, name above, then distance and held item below. This deliberately
            // uses Vibe's ESP palette rather than the launcher's accent colour.
            int boxX = x - 6 * scale, boxY = y - 4 * scale;
            int boxW = bodyW + 12 * scale, boxH = head + 84 * scale;
            drawVibeEspOverlay(g, boxX, boxY, boxW, boxH,
                    settings.getSelectedName().isEmpty() ? "Steve" : settings.getSelectedName());
            hits.add(new HitArea(new java.awt.Rectangle(cx - 150, cy - 205, 300, 360), "model"));
        }

        /** Mirrors Esp2DRenderer's default corner/health/name/distance/item layout. */
        private void drawVibeEspOverlay(Graphics2D g, int x, int y, int width, int height, String name) {
            int corner = Math.max(8, Math.min(width, height) / 4);
            float espScale = Math.min(2.5F, height / 180.0F); // EspLayout.scale(height, 1)
            g.setStroke(new BasicStroke(3.5F * espScale, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            drawEspCorners(g, x, y, width, height, corner, 0xCF000000);
            g.setStroke(new BasicStroke(1.5F * espScale, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            drawEspCorners(g, x, y, width, height, corner, 0xFFFFFFFF);

            // Vibe's default health bar is left-aligned, outlined black and filled
            // bottom-up from its default green health colour. Preview health: 15/20.
            int outline = Math.max(1, Math.round(espScale));
            int healthW = Math.max(3, Math.round(2 * espScale));
            int healthX = x - Math.round(3 * espScale) - outline - healthW, healthY = y;
            int health = (int) (height * .75F);
            g.setColor(color(0xCF000000));
            g.fillRect(healthX - outline, healthY - outline, healthW + 2 * outline, outline);
            g.fillRect(healthX - outline, healthY + height, healthW + 2 * outline, outline);
            g.fillRect(healthX - outline, healthY, outline, height);
            g.fillRect(healthX + healthW, healthY, outline, height);
            g.setColor(color(0xFF53E88C)); g.fillRect(healthX, healthY + height - health, healthW, health);

            int textSize = Math.max(9, Math.round(9 * espScale));
            int gap = Math.round(3 * espScale), textPad = Math.round(espScale);
            font(g, Font.BOLD, textSize);
            drawShadowedCentered(g, name, x + width / 2, y - gap - textPad);
            font(g, Font.PLAIN, textSize);
            int distanceBase = y + height + gap + textPad + textSize;
            drawShadowedCentered(g, "16 m", x + width / 2, distanceBase);
            drawShadowedCentered(g, "Diamond Sword", x + width / 2, distanceBase + textSize + gap + textPad);
        }

        private void drawEspCorners(Graphics2D g, int x, int y, int width, int height, int length, int rgb) {
            g.setColor(color(rgb));
            g.drawLine(x, y, x + length, y); g.drawLine(x, y, x, y + length);
            g.drawLine(x + width, y, x + width - length, y); g.drawLine(x + width, y, x + width, y + length);
            g.drawLine(x, y + height, x + length, y + height); g.drawLine(x, y + height, x, y + height - length);
            g.drawLine(x + width, y + height, x + width - length, y + height); g.drawLine(x + width, y + height, x + width, y + height - length);
        }

        private void drawShadowedCentered(Graphics2D g, String value, int x, int y) {
            centered(g, value, x + 1, y + 1, 0xFF000000);
            centered(g, value, x, y, 0xFFFFFFFF);
        }

        private void drawNews(Graphics2D g, int x, int y, int width, int height) {
            font(g, Font.BOLD, 12); text(g, "NEWS", x, y, 0xFFF5F2FA);
            g.setColor(withAlpha(theme.accent, 80)); g.drawLine(x, y + 12, x + width, y + 12);
            newsCard(g, x, y + 27, width, "VIBE LAUNCHER", "A polished Forge profile in one place.");
            newsCard(g, x, y + 114, width, "SAFE UPDATES", updateMessage);
            newsCard(g, x, y + 201, width, "COSMETICS + ESP", "Preview cosmetic layers and tactical overlay.");
            font(g, Font.PLAIN, 10); text(g, "Logs: " + log.getFile().getFileName(), x, Math.min(getHeight() - 20, y + height - 10), theme.muted);
        }

        private void newsCard(Graphics2D g, int x, int y, int width, String title, String message) {
            round(g, x, y, width, 73, 10, withAlpha(theme.surface, 235), withAlpha(theme.accent, 80));
            font(g, Font.BOLD, 10); text(g, title, x + 11, y + 19, theme.accent);
            font(g, Font.PLAIN, 11); drawWrapped(g, message, x + 11, y + 37, width - 22, 14, theme.muted, 2);
        }

        private void drawMods(Graphics2D g) {
            int x = 108, y = 102, contentW = getWidth() - 150;
            heading(g, "CUSTOM MODS", "Drop Forge 1.8.9 .jar files here. They are copied into Vibe's isolated profile.", x, y);
            int dropY = y + 62;
            round(g, x, dropY, contentW, 126, 14, withAlpha(theme.surface, 225), withAlpha(theme.accent, 175));
            font(g, Font.BOLD, 24); centered(g, "⇩", x + contentW / 2, dropY + 45, theme.accent);
            font(g, Font.BOLD, 14); centered(g, "DROP CUSTOM MODS", x + contentW / 2, dropY + 71, 0xFFF5F2FA);
            font(g, Font.PLAIN, 11); centered(g, "Only .jar files are accepted. Existing files are never overwritten silently.", x + contentW / 2, dropY + 91, theme.muted);
            hits.add(new HitArea(new java.awt.Rectangle(x, dropY, contentW, 126), "chooseMods"));
            actionButton(g, x + contentW - 160, dropY + 145, 160, 34, "OPEN MODS FOLDER", "", "openMods", false);
            font(g, Font.BOLD, 12); text(g, "INSTALLED CUSTOM MODS", x, dropY + 177, 0xFFF5F2FA);
            List<Path> mods = customMods();
            int rowY = dropY + 194;
            if (mods.isEmpty()) {
                round(g, x, rowY, contentW, 54, 10, withAlpha(theme.surface, 180), withAlpha(theme.muted, 80));
                font(g, Font.PLAIN, 12); text(g, "No custom mods yet. OptiFine is requested automatically on every Vibe launch.", x + 14, rowY + 31, theme.muted);
            } else for (Path mod : mods) {
                round(g, x, rowY, contentW, 42, 9, withAlpha(theme.surface, 220), withAlpha(theme.accent, 45));
                font(g, Font.BOLD, 12); text(g, mod.getFileName().toString(), x + 14, rowY + 25, 0xFFF5F2FA);
                font(g, Font.PLAIN, 10); text(g, safeSize(mod), x + contentW - 66, rowY + 25, theme.muted);
                rowY += 49;
                if (rowY > getHeight() - 52) break;
            }
            font(g, Font.PLAIN, 11); text(g, "Profile: " + settings.profileDirectory(), x, getHeight() - 25, theme.muted);
        }

        private void drawAccounts(Graphics2D g) {
            int x = 108, y = 102, contentW = getWidth() - 150;
            heading(g, "ALT MANAGER", "Uses Vibe's existing encrypted account vault. Refresh tokens never enter launcher logs or settings.", x, y);
            actionButton(g, x + contentW - 198, y - 18, 198, 34, "MANAGE IN VIBE", "Open Vibe's Microsoft sign-in", "manageAccounts", false);
            int rowY = y + 58;
            if (accounts.isEmpty()) {
                round(g, x, rowY, contentW, 142, 14, withAlpha(theme.surface, 225), withAlpha(theme.accent, 80));
                font(g, Font.BOLD, 16); text(g, "No Vibe accounts found", x + 22, rowY + 37, 0xFFF5F2FA);
                font(g, Font.PLAIN, 12); drawWrapped(g, "Select MANAGE IN VIBE to add a Microsoft or offline account through the client. Return here to choose which saved account launches next.", x + 22, rowY + 62, contentW - 44, 18, theme.muted, 3);
            } else {
                for (AccountSummary account : accounts) {
                    boolean selected = account.uuid.equals(settings.getSelectedUuid());
                    round(g, x, rowY, contentW, 58, 11, selected ? withAlpha(theme.accent, 48) : withAlpha(theme.surface, 225), selected ? color(theme.accent) : withAlpha(theme.accent, 65));
                    g.setColor(withAlpha(theme.accent, selected ? 220 : 105)); g.fillRoundRect(x + 14, rowY + 13, 31, 31, 9, 9);
                    font(g, Font.BOLD, 16); centered(g, account.name.substring(0, 1).toUpperCase(Locale.ROOT), x + 29, rowY + 26, 0xFFFFFFFF);
                    font(g, Font.BOLD, 13); text(g, account.name, x + 58, rowY + 25, 0xFFF5F2FA);
                    font(g, Font.PLAIN, 10); text(g, account.microsoft ? "Microsoft account" : "Offline account", x + 58, rowY + 42, theme.muted);
                    font(g, Font.BOLD, 11); text(g, selected ? "SELECTED" : "USE THIS ACCOUNT", x + contentW - (selected ? 92 : 130), rowY + 33, selected ? theme.accent : theme.muted);
                    hits.add(new HitArea(new java.awt.Rectangle(x, rowY, contentW, 58), "selectAccount:" + account.uuid + ":" + account.name));
                    rowY += 67;
                }
            }
            actionButton(g, x, Math.min(getHeight() - 76, rowY + 12), 152, 34, "REFRESH LIST", "", "refreshAccounts", false);
        }

        private void drawThemes(Graphics2D g) {
            int x = 108, y = 102, contentW = getWidth() - 150;
            heading(g, "VIBE THEMES", "These are the exact theme presets used by Vibe's main menu and account screens.", x, y);
            int cardW = Math.max(185, (contentW - 16) / 3), cardH = 116;
            for (int i = 0; i < Theme.values().length; i++) {
                Theme candidate = Theme.values()[i];
                int cx = x + (i % 3) * (cardW + 8), cy = y + 60 + (i / 3) * (cardH + 10);
                boolean selected = candidate == theme;
                round(g, cx, cy, cardW, cardH, 13, color(candidate.surface), selected ? color(candidate.accent) : withAlpha(candidate.accent, 95));
                g.setColor(color(candidate.accent)); g.fillRoundRect(cx + 16, cy + 17, 43, 43, 12, 12);
                font(g, Font.BOLD, 15); text(g, candidate.label, cx + 72, cy + 35, 0xFFF5F2FA);
                font(g, Font.PLAIN, 11); text(g, selected ? "ACTIVE IN VIBE + LAUNCHER" : "Apply shared colour preset", cx + 72, cy + 55, candidate.muted);
                if (selected) { font(g, Font.BOLD, 13); text(g, "✓", cx + cardW - 26, cy + 30, candidate.accent); }
                hits.add(new HitArea(new java.awt.Rectangle(cx, cy, cardW, cardH), "theme:" + candidate.name()));
            }
            round(g, x, y + 322, contentW, 61, 12, withAlpha(theme.surface, 220), withAlpha(theme.accent, 60));
            font(g, Font.BOLD, 12); text(g, "SHARED PREFERENCE", x + 16, y + 347, theme.accent);
            font(g, Font.PLAIN, 12); text(g, "Saved to Vibe's vibe/menu.properties so your in-game main menu matches this launcher.", x + 16, y + 369, theme.muted);
        }

        private void drawSettings(Graphics2D g) {
            int x = 108, y = 102, contentW = getWidth() - 150;
            heading(g, "SETTINGS & DIAGNOSTICS", "The launcher is designed to repair prerequisites and leave useful local logs when something goes wrong.", x, y);
            settingRow(g, x, y + 58, contentW, "Managed Vibe source", settings.getProjectRoot().toString(), "refreshSource", "UPDATE NOW");
            String runtime = settings.getJava8Home().isEmpty() ? "Downloaded automatically when Vibe launches" : settings.getJava8Home();
            settingRow(g, x, y + 126, contentW, "Java 8 runtime for Minecraft", runtime, "installJava", "REPAIR");
            String buildJdk = settings.getJdk21Home().isEmpty() ? "Downloaded automatically when Vibe launches" : settings.getJdk21Home();
            settingRow(g, x, y + 194, contentW, "JDK 21 for Vibe builds", buildJdk, "installJava", "REPAIR");
            settingRow(g, x, y + 262, contentW, "Launcher update repository", settings.getUpdateRepository(), "editUpdates", "EDIT");
            settingRow(g, x, y + 330, contentW, "Launcher update status", updateMessage, "checkUpdates", "CHECK NOW");
            settingRow(g, x, y + 398, contentW, "Diagnostic log", log.getFile().toString(), "openLogs", "OPEN LOGS");
            font(g, Font.PLAIN, 11); drawWrapped(g, "Vibe source updates are downloaded from SkidderClub/Vibe automatically. Launcher releases from GitHub require a .jar and matching .sha256 asset before replacement. Builds and Minecraft output are written to the launcher log.", x, y + 420, contentW, 16, theme.muted, 3);
        }

        private void settingRow(Graphics2D g, int x, int y, int width, String label, String detail, String action, String actionLabel) {
            round(g, x, y, width, 56, 10, withAlpha(theme.surface, 220), withAlpha(theme.accent, 55));
            font(g, Font.BOLD, 12); text(g, label, x + 14, y + 22, 0xFFF5F2FA);
            font(g, Font.PLAIN, 10); text(g, ellipsis(g, detail, width - 194), x + 14, y + 40, theme.muted);
            actionButton(g, x + width - 152, y + 11, 138, 33, actionLabel, "", action, false);
        }

        private void heading(Graphics2D g, String title, String detail, int x, int y) {
            font(g, Font.BOLD, 19); text(g, title, x, y, 0xFFF5F2FA);
            font(g, Font.PLAIN, 12); text(g, detail, x, y + 22, theme.muted);
        }

        private void actionButton(Graphics2D g, int x, int y, int w, int h, String title, String subtitle, String action, boolean primary) {
            round(g, x, y, w, h, 10, primary ? withAlpha(theme.accent, 72) : withAlpha(theme.surface, 235), primary ? color(theme.accent) : withAlpha(theme.accent, 120));
            font(g, Font.BOLD, h >= 48 ? 15 : 11); centered(g, title, x + w / 2, y + (subtitle.isEmpty() ? h / 2 + 4 : h / 2 - 2), 0xFFF8F5FF);
            if (!subtitle.isEmpty()) { font(g, Font.PLAIN, 10); centered(g, subtitle, x + w / 2, y + h / 2 + 16, theme.muted); }
            hits.add(new HitArea(new java.awt.Rectangle(x, y, w, h), action));
        }

        private void pill(Graphics2D g, int x, int y, int w, int h, String value, boolean primary, String action) {
            round(g, x, y, w, h, 9, primary ? withAlpha(theme.accent, 70) : withAlpha(theme.background, 130), withAlpha(theme.accent, 100));
            font(g, Font.BOLD, 10); centered(g, ellipsis(g, value, w - 16), x + w / 2, y + 20, primary ? 0xFFFFFFFF : 0xFFF5F2FA);
            hits.add(new HitArea(new java.awt.Rectangle(x, y, w, h), action));
        }

        private void mouseDown(MouseEvent event) {
            for (HitArea area : hits) if (area.bounds.contains(event.getPoint()) && "model".equals(area.action)) { dragX = event.getX(); return; }
        }

        private void mouseDrag(MouseEvent event) {
            if (dragX != 0) { modelYaw += (event.getX() - dragX) * .018F; dragX = event.getX(); repaint(); }
        }

        private void mouseUp(MouseEvent event) {
            dragX = 0;
            for (HitArea area : new ArrayList<HitArea>(hits)) if (area.bounds.contains(event.getPoint())) { handle(area.action); return; }
        }

        private void updateCursor(int x, int y) {
            for (HitArea area : hits) if (area.bounds.contains(x, y)) { setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return; }
            setCursor(Cursor.getDefaultCursor());
        }

        private void handle(String action) {
            if (action.startsWith("page:")) { page = Integer.parseInt(action.substring(5)); repaint(); return; }
            if (preparationInProgress && !("logs".equals(action) || "openLogs".equals(action) || "checkUpdates".equals(action))) {
                status = "Vibe is finishing its automatic first-run setup. Please wait for the ready message.";
                repaint();
                return;
            }
            if (action.startsWith("theme:")) { applyTheme(Theme.safeValueOf(action.substring(6))); return; }
            if (action.startsWith("selectAccount:")) {
                String[] parts = action.split(":", 3);
                settings.selectAccount(parts[1], parts[2]); status = "Selected " + parts[2] + ". Vibe will use this saved profile on launch."; repaint(); return;
            }
            if ("launch".equals(action)) { launch("vibe"); return; }
            if ("gta".equals(action)) { launch("gta7"); return; }
            if ("chooseMods".equals(action)) { chooseMods(); return; }
            if ("openMods".equals(action)) { open(settings.modsDirectory()); return; }
            if ("manageAccounts".equals(action)) { launch("accounts"); return; }
            if ("refreshAccounts".equals(action)) { reloadAccounts(); return; }
            if ("refreshSource".equals(action)) { refreshSource(); return; }
            if ("installJava".equals(action)) { installJava8(); return; }
            if ("editUpdates".equals(action)) { editUpdateRepository(); return; }
            if ("checkUpdates".equals(action)) { checkForUpdates(true); return; }
            if ("openLogs".equals(action) || "logs".equals(action)) { open(log.getFile().getParent()); return; }
            if ("accounts".equals(action)) { page = ACCOUNTS; repaint(); }
        }

        private void applyTheme(Theme newTheme) {
            theme = newTheme; settings.setTheme(newTheme); status = newTheme.label + " now matches Vibe's main menu."; repaint();
        }

        private void chooseMods() {
            JFileChooser chooser = new JFileChooser(); chooser.setMultiSelectionEnabled(true); chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) importMods(Arrays.asList(chooser.getSelectedFiles()));
        }

        private void importMods(List<File> files) {
            if (preparationInProgress) { status = "Vibe is finishing its automatic first-run setup. Add mods once it is ready."; repaint(); return; }
            int copied = 0, rejected = 0;
            try {
                Files.createDirectories(settings.modsDirectory());
                for (File source : files) {
                    if (source == null || !source.isFile() || !source.getName().toLowerCase(Locale.ROOT).endsWith(".jar") || source.length() > 300L * 1024L * 1024L) { rejected++; continue; }
                    Path target = uniqueTarget(settings.modsDirectory(), source.getName());
                    Files.copy(source.toPath(), target, StandardCopyOption.COPY_ATTRIBUTES);
                    copied++;
                }
                status = copied + " mod" + (copied == 1 ? "" : "s") + " added" + (rejected == 0 ? "." : "; " + rejected + " rejected.");
                log.info(status);
            } catch (IOException error) { report("Could not add mods", error); }
            repaint();
        }

        private void reloadAccounts() {
            try { accounts = AccountVault.read(settings.accountDirectory()); status = accounts.isEmpty() ? "No saved Vibe accounts yet." : accounts.size() + " saved Vibe account(s) found."; }
            catch (IOException error) { accounts = Collections.emptyList(); status = "Could not read the encrypted Vibe account vault. Use Vibe's Account Manager to repair it."; log.info("Account vault unavailable: " + error.getMessage()); }
            repaint();
        }

        private void launch(final String mode) {
            if (launchInProgress) { status = "Vibe is already being prepared or launched."; repaint(); return; }
            if (preparationInProgress) { status = "First-run setup is still running. Vibe will be ready as soon as the downloads finish."; repaint(); return; }
            launchInProgress = true;
            status = "gta7".equals(mode) ? "Preparing Vibe and the GTA7 route…"
                    : "accounts".equals(mode) ? "Building Vibe, then opening Vibe's Alt Manager…"
                    : "Preparing Vibe, Java, and Forge…";
            log.info(status);
            repaint();
            new Thread(new Runnable() { @Override public void run() {
                try {
                    status = "Checking the managed Vibe source for updates…";
                    Path projectRoot = SourceBootstrap.ensure(settings, log);
                    status = "Checking compatible Java runtimes…";
                    RuntimePair runtime = RuntimeInstaller.ensure(settings, log);
                    settings.setJava8Home(runtime.java8.toString()); settings.setJdk21Home(runtime.jdk21.toString());
                    settings.writeLaunchBridge(mode);
                    SwingUtilities.invokeLater(new Runnable() { @Override public void run() { reloadAccounts(); } });
                    status = "gta7".equals(mode) ? "Building Vibe, then opening the GTA7 route…"
                            : "accounts".equals(mode) ? "Building Vibe, then opening Vibe's Alt Manager…"
                            : "Building Vibe and launching Forge…";
                    ProcessBuilder process = new ProcessBuilder("cmd.exe", "/c", projectRoot.resolve("build.bat").toString(), "runClient", "-PvibeOptifine", "-PvibePersistentRun");
                    process.directory(projectRoot.toFile());
                    process.redirectErrorStream(true);
                    process.environment().put("VIBE_JAVA8", runtime.java8.toString());
                    if (runtime.jdk21 != null) {
                        process.environment().put("JAVA_HOME", runtime.jdk21.toString());
                        String currentPath = process.environment().get("PATH");
                        process.environment().put("PATH", runtime.jdk21.toString() + "\\bin;" + (currentPath == null ? "" : currentPath));
                    }
                    // This reaches Gradle even when the freshly downloaded source
                    // predates build.bat's VIBE_JAVA8 support. It lets Forge use the
                    // private Java 8 runtime while Gradle itself remains on JDK 21.
                    String existingGradleOptions = process.environment().get("GRADLE_OPTS");
                    String toolchainOption = "\"-Dorg.gradle.java.installations.paths=" + runtime.jdk21 + "," + runtime.java8 + "\"";
                    process.environment().put("GRADLE_OPTS", toolchainOption + (existingGradleOptions == null ? "" : " " + existingGradleOptions));
                    Process child = process.start();
                    pipeToLog(child.getInputStream());
                    int code = child.waitFor();
                    status = code == 0 ? "Vibe closed normally." : "Vibe build/launch ended with code " + code + ". See logs.";
                    log.info(status);
                } catch (Exception error) { report("Vibe could not start", error); }
                finally { launchInProgress = false; }
                SwingUtilities.invokeLater(() -> repaint());
            }}, "Vibe launch worker").start();
        }

        private void pipeToLog(InputStream input) throws IOException {
            byte[] buffer = new byte[4096]; int read;
            while ((read = input.read(buffer)) != -1) log.info(new String(buffer, 0, read, StandardCharsets.UTF_8).replace("\r", "").replace("\n", " "));
        }

        private void installJava8() {
            status = "Downloading compatible Java runtimes…"; repaint();
            new Thread(new Runnable() { @Override public void run() {
                try { RuntimePair runtime = RuntimeInstaller.ensure(settings, log); settings.setJava8Home(runtime.java8.toString()); settings.setJdk21Home(runtime.jdk21.toString()); status = "Java 8 and JDK 21 are installed and ready."; }
                catch (Exception error) { report("Java installation failed", error); }
                SwingUtilities.invokeLater(() -> repaint());
            }}, "Java 8 runtime installer").start();
        }

        private void refreshSource() {
            if (launchInProgress || preparationInProgress) { status = "Vibe is already being prepared or launched."; repaint(); return; }
            status = "Checking GitHub for Vibe source updates…"; repaint();
            new Thread(new Runnable() { @Override public void run() {
                try {
                    Path root = SourceBootstrap.ensure(settings, log);
                    status = "Vibe source is ready: " + root.getFileName();
                    SwingUtilities.invokeLater(new Runnable() { @Override public void run() { reloadAccounts(); } });
                } catch (Exception error) { report("Vibe source update failed", error); }
                SwingUtilities.invokeLater(() -> repaint());
            }}, "Vibe source updater").start();
        }

        /** First startup is a real installation: source and runtimes arrive without a wizard. */
        private void prepareInstallation() {
            preparationInProgress = true;
            status = "Preparing the standalone Vibe installation…";
            new Thread(new Runnable() { @Override public void run() {
                try {
                    status = "Downloading or updating Vibe from GitHub…";
                    SourceBootstrap.ensure(settings, log);
                    status = "Checking compatible Java runtimes…";
                    RuntimePair runtime = RuntimeInstaller.ensure(settings, log);
                    settings.setJava8Home(runtime.java8.toString()); settings.setJdk21Home(runtime.jdk21.toString());
                    status = "Vibe is ready. Press Launch to build and start Forge.";
                    SwingUtilities.invokeLater(new Runnable() { @Override public void run() { reloadAccounts(); } });
                } catch (Exception error) { report("Automatic Vibe setup failed", error); }
                finally { preparationInProgress = false; }
                SwingUtilities.invokeLater(() -> repaint());
            }}, "Vibe automatic setup").start();
        }

        private void editUpdateRepository() {
            String value = JOptionPane.showInputDialog(this, "GitHub repository URL (owner/repository):", settings.getUpdateRepository());
            if (value != null) { settings.setUpdateRepository(value.trim()); checkForUpdates(false); }
        }

        private void checkForUpdates(final boolean userRequested) {
            new Thread(new Runnable() { @Override public void run() {
                try {
                    UpdateInfo update = UpdateService.check(settings.getUpdateRepository());
                    if (update == null) { updateMessage = "No launcher release is published; Vibe source stays current automatically."; }
                    else if (!isNewer(update.version, VERSION)) { updateMessage = "Launcher is up to date (" + VERSION + ")."; }
                    else {
                        updateMessage = "Version " + update.version + " is available.";
                        if (update.jarUrl != null && update.shaUrl != null) SwingUtilities.invokeLater(() -> offerUpdate(update));
                    }
                } catch (Exception error) { updateMessage = "Update check unavailable. " + concise(error.getMessage()); log.info("Update check: " + error.getMessage()); }
                SwingUtilities.invokeLater(() -> repaint());
            }}, "GitHub update check").start();
        }

        private void offerUpdate(UpdateInfo update) {
            int choice = JOptionPane.showConfirmDialog(this, "Vibe Launcher " + update.version + " is available from GitHub.\nDownload it now?", PRODUCT, JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return;
            new Thread(new Runnable() { @Override public void run() {
                try { UpdateService.downloadAndReplace(update, settings, log); status = "Update downloaded. Restarting launcher…"; }
                catch (Exception error) { report("Launcher update failed", error); }
                SwingUtilities.invokeLater(() -> repaint());
            }}, "Launcher updater").start();
        }

        private void open(Path path) {
            try { Files.createDirectories(path); if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(path.toFile()); else throw new IOException("Desktop integration is unavailable."); }
            catch (Exception error) { report("Could not open folder", error); }
        }

        private void report(String title, Exception error) {
            log.error(title, error); status = title + ". See the launcher log.";
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, title + ".\n" + concise(error.getMessage()) + "\nSee Logs for details.", PRODUCT, JOptionPane.ERROR_MESSAGE));
        }

        private List<Path> customMods() {
            try {
                if (!Files.isDirectory(settings.modsDirectory())) return Collections.emptyList();
                List<Path> result = new ArrayList<Path>();
                java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(settings.modsDirectory(), "*.jar");
                try { for (Path path : stream) result.add(path); } finally { stream.close(); }
                Collections.sort(result); return result;
            } catch (IOException ignored) { return Collections.emptyList(); }
        }

        private static Path uniqueTarget(Path folder, String fileName) {
            String safe = fileName.replaceAll("[^A-Za-z0-9._ -]", "_"); Path target = folder.resolve(safe);
            for (int index = 2; Files.exists(target); index++) { int dot = safe.lastIndexOf('.'); target = folder.resolve((dot < 1 ? safe : safe.substring(0, dot)) + " (" + index + ")" + (dot < 1 ? "" : safe.substring(dot))); }
            return target;
        }
        private static String safeSize(Path path) { try { return Math.max(1, Files.size(path) / 1024) + " KB"; } catch (IOException ignored) { return "? KB"; } }
        private static String concise(String value) { return value == null || value.trim().isEmpty() ? "Unexpected error." : value.length() > 160 ? value.substring(0, 160) + "…" : value; }
    }

    static final class HitArea { final java.awt.Rectangle bounds; final String action; HitArea(java.awt.Rectangle bounds, String action) { this.bounds = bounds; this.action = action; } }

    static final class ModsTransferHandler extends TransferHandler {
        private final LauncherPanel panel;
        ModsTransferHandler(LauncherPanel panel) { this.panel = panel; }
        @Override public boolean canImport(TransferSupport support) { return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) && (support.getSourceDropActions() & DnDConstants.ACTION_COPY) != 0; }
        @Override @SuppressWarnings("unchecked") public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try { panel.importMods((List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor)); return true; }
            catch (Exception error) { panel.report("Could not import dropped mod", error); return false; }
        }
    }

    static final class LauncherSettings {
        private final AppLog log; private final Path dataDirectory; private final Path file; private final Properties values = new Properties();
        LauncherSettings(AppLog log) {
            this.log = log; this.dataDirectory = appDataDirectory(); this.file = dataDirectory.resolve("launcher.properties");
            try { Files.createDirectories(dataDirectory); if (Files.isRegularFile(file)) { InputStream input = Files.newInputStream(file); try { values.load(input); } finally { input.close(); } } }
            catch (IOException error) { log.error("Could not load launcher settings", error); }
            Path managed = managedSourceDirectory();
            Path configured;
            try { configured = Paths.get(get("projectRoot")).toAbsolutePath().normalize(); }
            catch (Exception ignored) { configured = managed; }
            if (!managed.equals(configured)) values.setProperty("projectRoot", managed.toString());
            if (get("theme").isEmpty()) values.setProperty("theme", Theme.LAVENDER.name());
            if (get("updateRepository").isEmpty() || "https://codeberg.org/SkidderClub/Vibe".equalsIgnoreCase(get("updateRepository"))) values.setProperty("updateRepository", SourceBootstrap.REPOSITORY);
            save();
        }
        boolean hasValidRoot() { return isVibeRoot(getProjectRoot()); }
        Path getProjectRoot() { try { return Paths.get(get("projectRoot")).toAbsolutePath().normalize(); } catch (Exception ignored) { return managedSourceDirectory(); } }
        void setManagedProjectRoot(Path root) { values.setProperty("projectRoot", root.toAbsolutePath().normalize().toString()); values.setProperty("installed", "true"); save(); }
        String getSourceRevision() { return get("sourceRevision"); }
        void setSourceRevision(String revision) { values.setProperty("sourceRevision", revision == null ? "" : revision); save(); }
        Theme getTheme() { return Theme.safeValueOf(get("theme")); }
        void setTheme(Theme theme) { values.setProperty("theme", theme.name()); save(); syncTheme(theme); }
        String getJava8Home() { return get("java8Home"); }
        void setJava8Home(String home) { values.setProperty("java8Home", home); save(); }
        String getJdk21Home() { return get("jdk21Home"); }
        void setJdk21Home(String home) { values.setProperty("jdk21Home", home); save(); }
        String getUpdateRepository() { return get("updateRepository"); }
        void setUpdateRepository(String repo) { values.setProperty("updateRepository", repo); save(); }
        String getSelectedUuid() { return get("selectedUuid"); }
        String getSelectedName() { return get("selectedName"); }
        void selectAccount(String uuid, String name) { values.setProperty("selectedUuid", uuid); values.setProperty("selectedName", name); save(); writeLaunchBridge("vibe"); }
        Path profileDirectory() { return getProjectRoot().resolve("run").resolve("client"); }
        Path modsDirectory() { return profileDirectory().resolve("mods"); }
        Path accountDirectory() { return profileDirectory().resolve("vibe").resolve("accounts"); }
        void writeLaunchBridge(String mode) {
            Properties bridge = new Properties(); bridge.setProperty("mode", mode); bridge.setProperty("selectedUuid", getSelectedUuid()); bridge.setProperty("selectedName", getSelectedName());
            Path output = profileDirectory().resolve("vibe").resolve("launcher.properties");
            try { Files.createDirectories(output.getParent()); OutputStream stream = Files.newOutputStream(output); try { bridge.store(stream, "Vibe Launcher bridge - no credentials"); } finally { stream.close(); } }
            catch (IOException error) { log.error("Could not write Vibe launch bridge", error); }
        }
        void syncTheme(Theme theme) {
            Path output = profileDirectory().resolve("vibe").resolve("menu.properties"); Properties menu = new Properties(); menu.setProperty("theme", theme.name());
            try { Files.createDirectories(output.getParent()); OutputStream stream = Files.newOutputStream(output); try { menu.store(stream, "Vibe menu colours"); } finally { stream.close(); } }
            catch (IOException error) { log.error("Could not share theme with Vibe", error); }
        }
        Path dataDirectory() { return dataDirectory; }
        Path managedSourceDirectory() { return dataDirectory.resolve("source").toAbsolutePath().normalize(); }
        private String get(String key) { return values.getProperty(key, "").trim(); }
        private synchronized void save() { try { Files.createDirectories(dataDirectory); OutputStream output = Files.newOutputStream(file); try { values.store(output, "Vibe Launcher settings"); } finally { output.close(); } } catch (IOException error) { log.error("Could not save launcher settings", error); } }
        static boolean isVibeRoot(Path root) { return root != null && Files.isRegularFile(root.resolve("build.bat")) && Files.isDirectory(root.resolve("src")); }
        private static Path appDataDirectory() { String appData = System.getenv("APPDATA"); return (appData == null || appData.trim().isEmpty() ? Paths.get(System.getProperty("user.home")) : Paths.get(appData)).resolve("VibeLauncher"); }
    }

    static final class AccountSummary { final String name, uuid; final boolean microsoft; AccountSummary(String name, String uuid, boolean microsoft) { this.name = name; this.uuid = uuid; this.microsoft = microsoft; } }

    /**
     * Maintains a private, disposable Vibe checkout under the launcher's app-data
     * folder. A user never needs to clone the repository or choose a source folder.
     * The profile's run directory is moved into the new source tree before a swap so
     * custom mods, account data, and settings survive source updates.
     */
    static final class SourceBootstrap {
        static final String REPOSITORY = "https://github.com/SkidderClub/Vibe";
        private static final String OWNER = "SkidderClub", PROJECT = "Vibe", BRANCH = "main";

        static synchronized Path ensure(LauncherSettings settings, AppLog log) throws Exception {
            Path active = settings.managedSourceDirectory();
            String revision;
            try { revision = remoteRevision(); }
            catch (Exception error) {
                if (LauncherSettings.isVibeRoot(active)) {
                    log.info("GitHub source check unavailable; using the installed Vibe source: " + concise(error.getMessage()));
                    return active;
                }
                throw new IOException("Vibe could not be downloaded from GitHub: " + concise(error.getMessage()), error);
            }
            if (LauncherSettings.isVibeRoot(active) && revision.equals(settings.getSourceRevision())) return active;

            Files.createDirectories(settings.dataDirectory());
            Path stage = Files.createTempDirectory(settings.dataDirectory(), "source-");
            Path archive = stage.resolve("vibe-source.zip"), extracted = stage.resolve("extracted");
            try {
                log.info("Downloading Vibe source revision " + revision + " from GitHub.");
                download("https://api.github.com/repos/" + OWNER + "/" + PROJECT + "/zipball/" + revision, archive, 500L * 1024L * 1024L);
                RuntimeInstaller.unzipSafe(archive, extracted);
                Path candidate = findVibeRoot(extracted);
                if (candidate == null) throw new IOException("The downloaded Vibe archive did not contain build.bat and src.");

                Path backup = null;
                try {
                    if (Files.exists(active)) {
                        backup = settings.dataDirectory().resolve("source-backup-" + System.currentTimeMillis());
                        move(active, backup);
                        Path oldRun = backup.resolve("run"), newRun = candidate.resolve("run");
                        if (Files.exists(oldRun) && !Files.exists(newRun)) move(oldRun, newRun);
                    }
                    move(candidate, active);
                    settings.setManagedProjectRoot(active);
                    settings.setSourceRevision(revision);
                    log.info("Vibe source is ready at " + active + "." + (backup == null ? "" : " Previous source retained at " + backup + "."));
                    return active;
                } catch (Exception error) {
                    if (!Files.exists(active) && backup != null && Files.exists(backup)) {
                        Path candidateRun = candidate.resolve("run"), backupRun = backup.resolve("run");
                        if (Files.exists(candidateRun) && !Files.exists(backupRun)) move(candidateRun, backupRun);
                        move(backup, active);
                    }
                    throw error;
                }
            } finally {
                try { Files.deleteIfExists(archive); } catch (IOException ignored) { }
                try { Files.deleteIfExists(extracted); } catch (IOException ignored) { }
                try { Files.deleteIfExists(stage); } catch (IOException ignored) { }
            }
        }

        private static String remoteRevision() throws IOException {
            String json = readText(new URL("https://api.github.com/repos/" + OWNER + "/" + PROJECT + "/commits/" + BRANCH), 1024 * 1024);
            Matcher sha = Pattern.compile("\\\"sha\\\"\\s*:\\s*\\\"([0-9a-fA-F]{40})\\\"").matcher(json);
            if (!sha.find()) throw new IOException("GitHub did not return a commit revision.");
            return sha.group(1).toLowerCase(Locale.ROOT);
        }

        private static Path findVibeRoot(Path root) throws IOException {
            if (LauncherSettings.isVibeRoot(root)) return root;
            java.nio.file.DirectoryStream<Path> children = Files.newDirectoryStream(root);
            try { for (Path child : children) if (Files.isDirectory(child) && LauncherSettings.isVibeRoot(child)) return child; }
            finally { children.close(); }
            return null;
        }

        private static void move(Path source, Path target) throws IOException {
            try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target); }
        }
    }

    /** Read-only counterpart to Vibe's AccountStore. It never exposes or retains a refresh token. */
    static final class AccountVault {
        private static final byte[] MAGIC = "VIBEAC01".getBytes(StandardCharsets.US_ASCII);
        private static final Pattern ENTRY = Pattern.compile("\\{\\s*\\\"name\\\"\\s*:\\s*\\\"([A-Za-z0-9_]{1,16})\\\"\\s*,\\s*\\\"uuid\\\"\\s*:\\s*\\\"([a-fA-F0-9-]{36})\\\"\\s*,\\s*\\\"refreshToken\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"", Pattern.DOTALL);
        static List<AccountSummary> read(Path directory) throws IOException {
            Path vault = directory.resolve("accounts.vault"), keyFile = directory.resolve("accounts.key");
            if (!Files.isRegularFile(vault)) return Collections.emptyList();
            byte[] data = bounded(vault, 2 * 1024 * 1024), key = bounded(keyFile, 16), plain = null;
            try {
                if (data.length < MAGIC.length + 28 || !Arrays.equals(MAGIC, Arrays.copyOf(data, MAGIC.length)) || key.length != 16) throw new IOException("The Vibe account vault or key is invalid.");
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, Arrays.copyOfRange(data, MAGIC.length, MAGIC.length + 12))); cipher.updateAAD(MAGIC);
                plain = cipher.doFinal(data, MAGIC.length + 12, data.length - MAGIC.length - 12);
                Matcher matcher = ENTRY.matcher(new String(plain, StandardCharsets.UTF_8)); List<AccountSummary> result = new ArrayList<AccountSummary>();
                while (matcher.find() && result.size() < 1000) result.add(new AccountSummary(matcher.group(1), matcher.group(2), !matcher.group(3).isEmpty()));
                return result;
            } catch (Exception error) { throw new IOException("The Vibe account vault could not be read."); }
            finally { Arrays.fill(data, (byte) 0); Arrays.fill(key, (byte) 0); if (plain != null) Arrays.fill(plain, (byte) 0); }
        }
        private static byte[] bounded(Path file, int limit) throws IOException { InputStream input = Files.newInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream(); try { byte[] buffer = new byte[4096]; int count; while ((count = input.read(buffer)) != -1) { if (output.size() + count > limit) throw new IOException("File is too large."); output.write(buffer, 0, count); } return output.toByteArray(); } finally { input.close(); } }
    }

    static final class RuntimePair { final Path java8, jdk21; RuntimePair(Path java8, Path jdk21) { this.java8 = java8; this.jdk21 = jdk21; } }
    static final class RuntimeInstaller {
        private static final String TEMURIN_8 = "https://api.adoptium.net/v3/binary/latest/8/ga/windows/x64/jre/hotspot/normal/eclipse";
        private static final String TEMURIN_21 = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse";
        static synchronized RuntimePair ensure(LauncherSettings settings, AppLog log) throws Exception {
            Path base = settings.dataDirectory().resolve("runtime"); Files.createDirectories(base);
            Path java8 = usableHome(settings.getJava8Home(), false);
            if (java8 == null) java8 = ensure(base, "temurin-8", TEMURIN_8, "temurin8", log);
            Path jdk21 = usableHome(settings.getJdk21Home(), true);
            if (jdk21 == null) jdk21 = ensure(base, "temurin-21", TEMURIN_21, "temurin21", log);
            if (!Files.isRegularFile(jdk21.resolve("bin").resolve("javac.exe"))) throw new IOException("The downloaded JDK 21 does not include javac.exe.");
            return new RuntimePair(java8, jdk21);
        }
        private static Path ensure(Path base, String folder, String source, String temporaryPrefix, AppLog log) throws Exception {
            Path output = base.resolve(folder); Path existing = findJava(output); if (existing != null) return existing.getParent().getParent();
            Path archive = Files.createTempFile(base, temporaryPrefix + "-", ".zip");
            try { download(source, archive, 400L * 1024L * 1024L); Files.createDirectories(output); unzipSafe(archive, output); Path java = findJava(output); if (java == null) throw new IOException("Downloaded Java archive did not contain java.exe."); Path home = java.getParent().getParent(); log.info("Installed runtime: " + home); return home; }
            finally { Files.deleteIfExists(archive); }
        }
        static void unzipSafe(Path archive, Path target) throws IOException { ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive)); try { ZipEntry entry; byte[] buffer = new byte[8192]; while ((entry = zip.getNextEntry()) != null) { Path output = target.resolve(entry.getName()).normalize(); if (!output.startsWith(target)) throw new IOException("Unsafe file in Java runtime archive."); if (entry.isDirectory()) Files.createDirectories(output); else { Files.createDirectories(output.getParent()); OutputStream stream = Files.newOutputStream(output); try { int count; while ((count = zip.read(buffer)) != -1) stream.write(buffer, 0, count); } finally { stream.close(); } } zip.closeEntry(); } } finally { zip.close(); } }
        private static Path usableHome(String configured, boolean requireJdk) {
            if (configured == null || configured.trim().isEmpty()) return null;
            try {
                Path home = Paths.get(configured).toAbsolutePath().normalize();
                if (!Files.isRegularFile(home.resolve("bin").resolve("java.exe"))) return null;
                return !requireJdk || Files.isRegularFile(home.resolve("bin").resolve("javac.exe")) ? home : null;
            } catch (Exception ignored) { return null; }
        }
        private static Path findJava(Path root) throws IOException {
            if (!Files.isDirectory(root)) return null;
            java.nio.file.DirectoryStream<Path> top = Files.newDirectoryStream(root);
            try { for (Path path : top) { Path java = path.resolve("bin").resolve("java.exe"); if (Files.isRegularFile(java)) return java; } }
            finally { top.close(); }
            return Files.isRegularFile(root.resolve("bin").resolve("java.exe")) ? root.resolve("bin").resolve("java.exe") : null;
        }
    }

    static final class UpdateInfo { final String version, jarUrl, shaUrl; UpdateInfo(String version, String jarUrl, String shaUrl) { this.version = version; this.jarUrl = jarUrl; this.shaUrl = shaUrl; } }
    static final class UpdateService {
        static UpdateInfo check(String repository) throws Exception {
            URI repo = URI.create(repository);
            if (!"https".equalsIgnoreCase(repo.getScheme()) || !"github.com".equalsIgnoreCase(repo.getHost())) throw new IOException("Configure an HTTPS GitHub repository first.");
            String[] parts = repo.getPath().replaceAll("^/+|/+$", "").split("/");
            if (parts.length != 2) throw new IOException("Repository URL must be https://github.com/owner/repository.");
            String json;
            try { json = readText(new URL("https://api.github.com/repos/" + parts[0] + "/" + parts[1] + "/releases/latest"), 1024 * 1024); }
            catch (IOException error) { if (String.valueOf(error.getMessage()).contains("HTTP 404")) return null; throw error; }
            Matcher tag = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json); if (!tag.find()) throw new IOException("Release did not include a version tag.");
            String jar = asset(json, ".jar"), sha = asset(json, ".sha256"); return new UpdateInfo(tag.group(1).replaceFirst("^[vV]", ""), jar, sha);
        }
        static void downloadAndReplace(UpdateInfo update, LauncherSettings settings, AppLog log) throws Exception {
            if (update.jarUrl == null || update.shaUrl == null) throw new IOException("The GitHub release needs both a .jar and a .sha256 asset.");
            Path current = Paths.get(VibeLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI()); if (!current.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) throw new IOException("Automatic replacement works only from the built VibeLauncher.jar.");
            Path staged = settings.dataDirectory().resolve("VibeLauncher-" + update.version + ".jar"); download(update.jarUrl, staged, 100L * 1024L * 1024L);
            String expected = readText(new URL(update.shaUrl), 4096).trim().split("\\s+")[0].toLowerCase(Locale.ROOT); String actual = sha256(staged); if (!actual.equals(expected)) { Files.deleteIfExists(staged); throw new IOException("Update checksum did not match the signed release asset."); }
            Path script = settings.dataDirectory().resolve("replace-launcher.cmd"); String command = "@echo off\r\ntimeout /t 2 /nobreak >nul\r\ncopy /y \"" + staged + "\" \"" + current + "\" >nul\r\nstart \"\" javaw -jar \"" + current + "\"\r\n"; Files.write(script, command.getBytes(StandardCharsets.UTF_8)); new ProcessBuilder("cmd.exe", "/c", script.toString()).start(); System.exit(0);
        }
        private static String asset(String json, String suffix) { Matcher matcher = Pattern.compile("\\{[^{}]*\\\"name\\\"\\s*:\\s*\\\"[^\\\"]*" + Pattern.quote(suffix) + "\\\"[^{}]*\\\"browser_download_url\\\"\\s*:\\s*\\\"(https:[^\\\"]+)\\\"", Pattern.CASE_INSENSITIVE).matcher(json); if (!matcher.find()) return null; String value = matcher.group(1).replace("\\/", "/"); try { URI uri = URI.create(value); String host = uri.getHost(); return "https".equalsIgnoreCase(uri.getScheme()) && ("github.com".equalsIgnoreCase(host) || "objects.githubusercontent.com".equalsIgnoreCase(host) || "github-releases.githubusercontent.com".equalsIgnoreCase(host) || "release-assets.githubusercontent.com".equalsIgnoreCase(host)) ? value : null; } catch (Exception ignored) { return null; } }
    }

    static final class AppLog {
        private final Path file;
        AppLog() { Path root = LauncherSettings.appDataDirectory(); file = root.resolve("logs").resolve("vibe-launcher.log"); try { Files.createDirectories(file.getParent()); if (Files.exists(file) && Files.size(file) > 2L * 1024L * 1024L) Files.move(file, file.resolveSibling("vibe-launcher.1.log"), StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) { } info("Launcher started: " + VERSION); }
        Path getFile() { return file; }
        synchronized void info(String message) { write("INFO", message, null); }
        synchronized void error(String message, Exception error) { write("ERROR", message, error); }
        private void write(String level, String message, Exception error) { try { Files.createDirectories(file.getParent()); OutputStream output = Files.newOutputStream(file, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); try { String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " [" + level + "] " + message.replace('\n', ' ') + "\r\n"; output.write(line.getBytes(StandardCharsets.UTF_8)); if (error != null) output.write((error.getClass().getName() + ": " + String.valueOf(error.getMessage()) + "\r\n").getBytes(StandardCharsets.UTF_8)); } finally { output.close(); } } catch (IOException ignored) { } }
    }

    private static void round(Graphics2D g, int x, int y, int width, int height, int radius, Color fill, int border) { round(g, x, y, width, height, radius, fill, color(border)); }
    private static void round(Graphics2D g, int x, int y, int width, int height, int radius, Color fill, Color border) { g.setColor(fill); g.fillRoundRect(x, y, width, height, radius, radius); g.setColor(border); g.drawRoundRect(x, y, width - 1, height - 1, radius, radius); }
    private static void font(Graphics2D g, int style, int size) { g.setFont(new Font(Font.SANS_SERIF, style, size)); }
    private static void text(Graphics2D g, String value, int x, int y, int rgb) { g.setColor(color(rgb)); g.drawString(value, x, y); }
    private static void centered(Graphics2D g, String value, int x, int y, int rgb) { g.setColor(color(rgb)); g.drawString(value, x - g.getFontMetrics().stringWidth(value) / 2, y); }
    private static void drawWrapped(Graphics2D g, String value, int x, int y, int width, int lineHeight, int rgb, int maxLines) { String[] words = value.split("\\s+"); String line = ""; int row = 0; for (String word : words) { String candidate = line.isEmpty() ? word : line + " " + word; if (g.getFontMetrics().stringWidth(candidate) > width && !line.isEmpty()) { text(g, line, x, y + row * lineHeight, rgb); line = word; if (++row >= maxLines - 1) break; } else line = candidate; } if (!line.isEmpty() && row < maxLines) text(g, line, x, y + row * lineHeight, rgb); }
    private static String ellipsis(Graphics2D g, String value, int width) { if (g.getFontMetrics().stringWidth(value) <= width) return value; while (!value.isEmpty() && g.getFontMetrics().stringWidth(value + "…") > width) value = value.substring(0, value.length() - 1); return value + "…"; }
    private static Color color(int argb) { return new Color(argb, true); }
    private static Color withAlpha(int argb, int alpha) { return new Color((argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24), true); }
    private static boolean isNewer(String candidate, String current) { int[] a = versionParts(candidate), b = versionParts(current); for (int i = 0; i < Math.max(a.length, b.length); i++) { int left = i < a.length ? a[i] : 0, right = i < b.length ? b[i] : 0; if (left != right) return left > right; } return false; }
    private static int[] versionParts(String value) { String[] pieces = value.replaceFirst("^[vV]", "").split("[^0-9]+"); List<Integer> result = new ArrayList<Integer>(); for (String piece : pieces) if (!piece.isEmpty()) try { result.add(Integer.parseInt(piece)); } catch (NumberFormatException ignored) { } int[] array = new int[result.size()]; for (int i = 0; i < array.length; i++) array[i] = result.get(i); return array; }
    private static String readText(URL url, int limit) throws IOException { HttpURLConnection connection = (HttpURLConnection) url.openConnection(); connection.setConnectTimeout(10000); connection.setReadTimeout(20000); connection.setInstanceFollowRedirects(true); connection.setRequestProperty("User-Agent", "VibeLauncher/" + VERSION); try { if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) throw new IOException("HTTP " + connection.getResponseCode()); InputStream input = connection.getInputStream(); try { ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count; while ((count = input.read(buffer)) != -1) { if (output.size() + count > limit) throw new IOException("Response is too large."); output.write(buffer, 0, count); } return new String(output.toByteArray(), StandardCharsets.UTF_8); } finally { input.close(); } } finally { connection.disconnect(); } }
    private static void download(String source, Path output, long limit) throws IOException { URI uri = URI.create(source); if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IOException("Refusing a non-HTTPS download."); HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection(); connection.setConnectTimeout(15000); connection.setReadTimeout(45000); connection.setInstanceFollowRedirects(true); connection.setRequestProperty("User-Agent", "VibeLauncher/" + VERSION); try { if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) throw new IOException("Download HTTP " + connection.getResponseCode()); InputStream input = connection.getInputStream(); try { OutputStream stream = Files.newOutputStream(output); try { byte[] buffer = new byte[8192]; long count = 0; int read; while ((read = input.read(buffer)) != -1) { count += read; if (count > limit) throw new IOException("Download exceeds the safety limit."); stream.write(buffer, 0, read); } } finally { stream.close(); } } finally { input.close(); } } finally { connection.disconnect(); } }
    private static String sha256(Path file) throws Exception { MessageDigest digest = MessageDigest.getInstance("SHA-256"); InputStream input = Files.newInputStream(file); try { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count); } finally { input.close(); } StringBuilder result = new StringBuilder(); for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xFF)); return result.toString(); }
    private static String concise(String value) { return value == null || value.trim().isEmpty() ? "Unexpected error." : value.length() > 160 ? value.substring(0, 160) + "…" : value; }
}
