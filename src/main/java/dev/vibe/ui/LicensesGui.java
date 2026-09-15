package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.language.LanguageManager;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Offline credits and complete license texts, reachable before entering a world. */
public final class LicensesGui extends GuiScreen {
    private static final int TAB_BASE = 10;
    private static final int LINE_HEIGHT = 12;
    private final GuiScreen parent;
    private final MainMenuShaderManager shaders;
    private final List<String> lines = new ArrayList<String>();
    private LicenseDocuments.Page selected = LicenseDocuments.Page.CREDITS;
    private int left, top, panelWidth, panelHeight, contentTop, contentBottom, scroll;
    private boolean dragging;
    private int dragOffset;
    private String status = "";
    private boolean statusError;

    public LicensesGui(GuiScreen parent, MainMenuShaderManager shaders) {
        this.parent = parent;
        this.shaders = shaders;
    }

    @Override public void initGui() {
        panelWidth = Math.min(720, width - 24);
        panelHeight = Math.min(480, height - 24);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        contentTop = top + 108;
        contentBottom = top + panelHeight - 48;
        buttonList.clear();
        buttonList.add(button(0, left + panelWidth - 70, top + 12, 56, "Back"));
        int tabWidth = (panelWidth - 40) / 4;
        for (LicenseDocuments.Page page : LicenseDocuments.Page.values()) {
            int index = page.ordinal();
            buttonList.add(button(TAB_BASE + index, left + 14 + (index % 4) * (tabWidth + 4),
                    top + 54 + (index / 4) * 25, tabWidth, page.label));
        }
        int half = (panelWidth - 32) / 2;
        buttonList.add(button(1, left + 14, top + panelHeight - 30, half, "Project source"));
        buttonList.add(button(2, left + 18 + half, top + panelHeight - 30,
                panelWidth - half - 32, "Copy source link"));
        reload();
    }

    private GuiButton button(int id, int x, int y, int width, String label) {
        return new AccountScreenStyle.Button(id, x, y, width, 21, label, "", false, false);
    }

    private void reload() {
        lines.clear();
        dragging = false;
        String text;
        try {
            text = LicenseDocuments.read(selected);
            status = "";
            statusError = false;
        } catch (IOException failure) {
            text = LanguageManager.translate("This document is missing. Obtain the complete build from its distributor.");
            status = LanguageManager.translate("License document unavailable");
            statusError = true;
        }
        // License and copyright text is never machine-translated or replaced by a summary.
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) lines.add("");
            else lines.addAll(fontRendererObj.listFormattedStringToWidth(line.replace("\t", "    "), panelWidth - 48));
        }
        for (int index = 0; index < buttonList.size(); index++) {
            GuiButton button = buttonList.get(index);
            if (button.id >= TAB_BASE) buttonList.set(index, new AccountScreenStyle.Button(button.id,
                    button.xPosition, button.yPosition, button.width, button.height, button.displayString, "",
                    button.id == TAB_BASE + selected.ordinal(), false));
        }
        clampScroll();
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        shaders.draw(width, height);
        GuiRenderState.prepare(false);
        drawRect(0, 0, width, height, 0x85000000);
        AccountScreenStyle.window(left, top, panelWidth, panelHeight);
        // Fit the localized heading at the normal font size on small GUI scales.
        AccountScreenStyle.text(AccountScreenStyle.fit("Licenses & credits", panelWidth - 98),
                left + 14, top + 16, AccountScreenStyle.TEXT);
        AccountScreenStyle.rawText("Vibe " + Vibe.VERSION + "  /  GPLv3 + AGPLv3", left + 14, top + 35, AccountScreenStyle.MUTED);
        drawRect(left + 14, contentTop - 5, left + panelWidth - 14, contentTop - 4, AccountScreenStyle.BORDER);
        try (GuiClip clip = new GuiClip(left + 14, contentTop, panelWidth - 28, contentBottom - contentTop)) {
            for (int index = Math.max(0, scroll / LINE_HEIGHT); index < lines.size(); index++) {
                int y = contentTop + index * LINE_HEIGHT - scroll;
                if (y >= contentBottom) break;
                AccountScreenStyle.rawText(lines.get(index), left + 17, y, AccountScreenStyle.TEXT);
            }
        }
        if (maxScroll() > 0) {
            drawRect(left + panelWidth - 19, contentTop, left + panelWidth - 16, contentBottom, AccountScreenStyle.BORDER);
            drawRect(left + panelWidth - 19, thumbTop(), left + panelWidth - 16, thumbTop() + thumbHeight(), AccountScreenStyle.ACCENT);
        }
        String footer = status.isEmpty() ? LanguageManager.translate(selected.label) + "  "
                + Math.min(lines.size(), scroll / LINE_HEIGHT + 1) + " / " + lines.size() : status;
        AccountScreenStyle.rawText(AccountScreenStyle.fitRaw(footer, panelWidth - 32), left + 16,
                contentBottom + 6, statusError ? AccountScreenStyle.ERROR : AccountScreenStyle.MUTED);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(parent);
        else if (button.id == 1) {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                    throw new IOException("Browser unavailable");
                Desktop.getDesktop().browse(URI.create(LicenseDocuments.REPOSITORY));
            } catch (Exception failure) {
                status = LanguageManager.translate("Could not open the browser. Use Copy source link.");
                statusError = true;
            }
        } else if (button.id == 2) {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(LicenseDocuments.REPOSITORY), null);
                status = LanguageManager.translate("Source link copied");
                statusError = false;
            } catch (Exception failure) {
                status = LanguageManager.translate("Could not copy source link.");
                statusError = true;
            }
        } else if (button.id >= TAB_BASE && button.id < TAB_BASE + LicenseDocuments.Page.values().length) {
            selected = LicenseDocuments.Page.values()[button.id - TAB_BASE];
            scroll = 0;
            reload();
        }
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int x = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
        int y = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
        if (x >= left + 14 && x < left + panelWidth - 14 && y >= contentTop && y < contentBottom) {
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0) scroll += wheel > 0 ? -36 : 36;
            clampScroll();
        }
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (button == 0 && maxScroll() > 0 && x >= left + panelWidth - 24 && x < left + panelWidth - 12
                && y >= contentTop && y < contentBottom) {
            dragOffset = y >= thumbTop() && y < thumbTop() + thumbHeight() ? y - thumbTop() : thumbHeight() / 2;
            dragging = true;
            dragTo(y);
        } else super.mouseClicked(x, y, button);
    }

    @Override protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (dragging && button == 0) dragTo(y);
        else super.mouseClickMove(x, y, button, elapsed);
    }

    @Override protected void mouseReleased(int x, int y, int state) {
        dragging = false;
        super.mouseReleased(x, y, state);
    }

    private void dragTo(int y) {
        scroll = Math.round((y - contentTop - dragOffset) * maxScroll()
                / (float) Math.max(1, contentBottom - contentTop - thumbHeight()));
        clampScroll();
    }

    @Override protected void keyTyped(char character, int key) throws IOException {
        switch (key) {
            case Keyboard.KEY_ESCAPE: mc.displayGuiScreen(parent); return;
            case Keyboard.KEY_UP: scroll -= LINE_HEIGHT; break;
            case Keyboard.KEY_DOWN: scroll += LINE_HEIGHT; break;
            case Keyboard.KEY_PRIOR: scroll -= contentBottom - contentTop; break;
            case Keyboard.KEY_NEXT: scroll += contentBottom - contentTop; break;
            case Keyboard.KEY_HOME: scroll = 0; break;
            case Keyboard.KEY_END: scroll = maxScroll(); break;
            default: super.keyTyped(character, key); return;
        }
        clampScroll();
    }

    private int maxScroll() { return Math.max(0, lines.size() * LINE_HEIGHT - (contentBottom - contentTop)); }
    private void clampScroll() { scroll = Math.max(0, Math.min(maxScroll(), scroll)); }
    private int thumbHeight() { return Math.max(12, (contentBottom - contentTop) * (contentBottom - contentTop) / Math.max(1, lines.size() * LINE_HEIGHT)); }
    private int thumbTop() { return contentTop + Math.round((contentBottom - contentTop - thumbHeight()) * scroll / (float) Math.max(1, maxScroll())); }
}
