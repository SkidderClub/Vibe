package dev.vibe.ui;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Click-to-expand release notes; the main menu stays in place underneath. */
final class ChangelogDrawer {
    private final int x, y, width, height, contentTop, contentBottom;
    private final boolean overlay;
    private final AccountScreenStyle.Button close;
    private final List<Row> rows = new ArrayList<Row>();
    private boolean open, dragging;
    private int scroll, contentHeight, dragOffset;
    private float amount;
    private long lastFrame = System.nanoTime();

    ChangelogDrawer(int x, int y, int width, int height, boolean overlay) {
        this.x = x; this.y = y; this.width = width; this.height = height; this.overlay = overlay;
        contentTop = y + 54;
        contentBottom = y + height - 26;
        close = new AccountScreenStyle.Button(0, x + width - 60, y + 12, 46, 24, "Close", "", false, false);
        for (VibeChangelog.Entry entry : VibeChangelog.entries()) {
            rows.add(new Row(contentHeight, entry.version, 'V'));
            contentHeight += 26;
            for (String note : entry.lines) {
                List<String> lines = Minecraft.getMinecraft().fontRendererObj.listFormattedStringToWidth(note.substring(1).trim(), width - 48);
                for (int line = 0; line < lines.size(); line++) {
                    rows.add(new Row(contentHeight, lines.get(line), line == 0 ? note.charAt(0) : ' '));
                    contentHeight += 12;
                }
                contentHeight += 8;
            }
            contentHeight += 12;
        }
    }

    void toggle() { open = !open; dragging = false; lastFrame = System.nanoTime(); }
    boolean isVisible() { return open || amount > 0; }

    void draw(int screenWidth, int screenHeight, int mouseX, int mouseY) {
        long now = System.nanoTime();
        float elapsed = Math.min(0.05F, (now - lastFrame) / 1_000_000_000F);
        lastFrame = now;
        amount = Math.max(0, Math.min(1, amount + (open ? 1 : -1) * elapsed / 0.18F));
        if (amount <= 0) return;
        float eased = 1 - (float) Math.pow(1 - amount, 3);
        if (overlay) Gui.drawRect(0, 0, screenWidth, screenHeight, ((int) (105 * eased)) << 24);
        try (Clip outer = new Clip(x - 4, y, width + 8, Math.round((height + 6) * eased))) {
            AccountScreenStyle.window(x, y, width, height);
            AccountScreenStyle.title("Changelog", x + 14, y + 15);
            AccountScreenStyle.text("Latest & previous updates", x + 14, y + 35, AccountScreenStyle.MUTED);
            close.drawButton(Minecraft.getMinecraft(), mouseX, mouseY);
            try (Clip content = new Clip(x + 12, contentTop, width - 24, contentBottom - contentTop)) {
                for (Row row : rows) {
                    int rowY = contentTop + 7 + row.y - scroll;
                    if (rowY + 12 < contentTop || rowY > contentBottom) continue;
                    if (row.kind == 'V') {
                        AccountScreenStyle.text(row.text, x + 16, rowY, AccountScreenStyle.ACCENT);
                        Gui.drawRect(x + 16, rowY + 16, x + width - 22, rowY + 17, AccountScreenStyle.BORDER);
                    } else {
                        int color = row.kind == '+' ? AccountScreenStyle.SUCCESS : row.kind == '-' ? AccountScreenStyle.ERROR : AccountScreenStyle.ACCENT;
                        if (row.kind != ' ') AccountScreenStyle.text(String.valueOf(row.kind), x + 16, rowY, color);
                        AccountScreenStyle.text(row.text, x + 28, rowY, AccountScreenStyle.TEXT);
                    }
                }
            }
            if (maxScroll() > 0) {
                MenuRoundedRenderer.rect(x + width - 17, contentTop, 3, contentBottom - contentTop, 1, AccountScreenStyle.BORDER);
                MenuRoundedRenderer.rect(x + width - 17, thumbTop(), 3, thumbHeight(), 1, AccountScreenStyle.ACCENT);
            }
            AccountScreenStyle.text("Scroll for more", x + 16, y + height - 16, AccountScreenStyle.MUTED);
        }
    }

    boolean mouse(int mouseX, int mouseY, int wheel, int button, boolean pressed) {
        if (!open) return false;
        boolean inside = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        if (button == 0 && pressed) {
            if (!inside) {
                // Leave outside clicks to the live menu, including its
                // Changelog toggle. Close and Escape dismiss this drawer.
                dragging = false; return false;
            }
            if (close.mousePressed(Minecraft.getMinecraft(), mouseX, mouseY)) {
                open = false; dragging = false; return true;
            }
            if (maxScroll() > 0 && mouseX >= x + width - 23 && mouseY >= contentTop && mouseY <= contentBottom) {
                dragOffset = mouseY >= thumbTop() && mouseY <= thumbTop() + thumbHeight() ? mouseY - thumbTop() : thumbHeight() / 2;
                dragging = true;
            }
        }
        if (button == 0 && !pressed) dragging = false;
        if (dragging) scroll = Math.round((mouseY - contentTop - dragOffset) * maxScroll()
                / (float) Math.max(1, contentBottom - contentTop - thumbHeight()));
        else if (wheel != 0 && mouseX >= x && mouseX < x + width && mouseY >= contentTop && mouseY < contentBottom)
            scroll += wheel > 0 ? -36 : 36;
        clampScroll();
        return inside || dragging;
    }

    boolean key(int key) {
        if (!isVisible()) return false;
        switch (key) {
            case Keyboard.KEY_ESCAPE: open = false; dragging = false; break;
            case Keyboard.KEY_UP: scroll -= 24; break;
            case Keyboard.KEY_DOWN: scroll += 24; break;
            case Keyboard.KEY_PRIOR: scroll -= contentBottom - contentTop - 24; break;
            case Keyboard.KEY_NEXT: scroll += contentBottom - contentTop - 24; break;
            case Keyboard.KEY_HOME: scroll = 0; break;
            case Keyboard.KEY_END: scroll = maxScroll(); break;
            default: break;
        }
        clampScroll();
        return true;
    }

    private void clampScroll() { scroll = Math.max(0, Math.min(maxScroll(), scroll)); }
    private int maxScroll() { return Math.max(0, contentHeight + 10 - (contentBottom - contentTop)); }
    private int thumbHeight() { return Math.max(18, (contentBottom - contentTop) * (contentBottom - contentTop) / Math.max(1, contentHeight + 10)); }
    private int thumbTop() { return contentTop + Math.round((contentBottom - contentTop - thumbHeight()) * scroll / (float) Math.max(1, maxScroll())); }

    private static final class Row {
        final int y; final String text; final char kind;
        Row(int y, String text, char kind) { this.y = y; this.text = text; this.kind = kind; }
    }

    /** Intersects nested scissors and restores both the rectangle and enable state. */
    private static final class Clip implements AutoCloseable {
        private final boolean enabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final IntBuffer previous = BufferUtils.createIntBuffer(16);
        Clip(int x, int y, int width, int height) {
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, previous);
            Minecraft mc = Minecraft.getMinecraft();
            int scale = new ScaledResolution(mc).getScaleFactor();
            int left = x * scale, bottom = mc.displayHeight - (y + height) * scale;
            int right = left + width * scale, top = bottom + height * scale;
            if (enabled) {
                left = Math.max(left, previous.get(0)); bottom = Math.max(bottom, previous.get(1));
                right = Math.min(right, previous.get(0) + previous.get(2)); top = Math.min(top, previous.get(1) + previous.get(3));
            }
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
        }
        @Override public void close() {
            GL11.glScissor(previous.get(0), previous.get(1), previous.get(2), previous.get(3));
            if (!enabled) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }
}
