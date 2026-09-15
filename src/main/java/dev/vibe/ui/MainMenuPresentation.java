package dev.vibe.ui;

import dev.vibe.Vibe;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiButtonLanguage;

/** Responsive Vibe home screen drawn over Forge's live menu buttons. */
public final class MainMenuPresentation {
    public static final int SHADER_BUTTON_ID = 924201;
    public static final int DISCORD_BUTTON_ID = 924202;
    public static final int ACCOUNT_BUTTON_ID = 924203;
    public static final int CHANGELOG_BUTTON_ID = 924204;
    public static final int THEMES_BUTTON_ID = 924205;
    public static final int LICENSES_BUTTON_ID = 924206;

    private final int left, top, panelWidth, panelHeight, innerLeft, menuWidth;
    private final int bodyTop, accountTop, accountHeight, footerTop, mainHeight, smallHeight, optionTop, optionHeight, gap;
    private final boolean details;
    private final ChangelogDrawer changelog;

    public MainMenuPresentation(int width, int height) {
        panelWidth = Math.min(360, width - 24);
        // Keep the compact card tight around its controls.  The previous
        // 350px height left a conspicuous empty lower third at normal scale.
        panelHeight = Math.min(height >= 300 ? 300 : 252, height - 20);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        innerLeft = left + 16;
        menuWidth = panelWidth - 32;
        details = panelHeight >= 280;
        gap = details ? 8 : 5;
        mainHeight = details ? 42 : 24;
        smallHeight = details ? 30 : 22;
        optionHeight = smallHeight;
        bodyTop = top + (details ? 64 : 42);
        accountTop = bodyTop + 2 * (mainHeight + gap);
        accountHeight = details ? 40 : 30;
        footerTop = accountTop + accountHeight + gap;
        optionTop = footerTop + smallHeight + gap;
        int room = width - (left + panelWidth) - 24;
        boolean beside = room >= 260;
        int drawerWidth = beside ? Math.min(300, room) : Math.min(360, width - 24);
        changelog = new ChangelogDrawer(beside ? left + panelWidth + 12 : (width - drawerWidth) / 2,
                top, drawerWidth, panelHeight, !beside);
    }

    public void prepare(List<GuiButton> buttons) {
        buttons.removeIf(button -> button.id == 14 || button instanceof GuiButtonLanguage
                || button.id == ACCOUNT_BUTTON_ID || button.id == SHADER_BUTTON_ID || button.id == DISCORD_BUTTON_ID
                || button.id == CHANGELOG_BUTTON_ID || button.id == THEMES_BUTTON_ID || button.id == LICENSES_BUTTON_ID);
        buttons.add(new GuiButton(SHADER_BUTTON_ID, 0, 0, "Shaders"));
        buttons.add(new GuiButton(DISCORD_BUTTON_ID, 0, 0, "Discord"));
        buttons.add(new GuiButton(CHANGELOG_BUTTON_ID, 0, 0, "Changelog"));
        buttons.add(new GuiButton(THEMES_BUTTON_ID, 0, 0, "Themes"));
        buttons.add(new GuiButton(LICENSES_BUTTON_ID, 0, 0, "Licenses"));

        int half = (menuWidth - gap) / 2;
        int slotWidth = (menuWidth - gap * 3) / 4;
        for (int index = 0; index < buttons.size(); index++) {
            GuiButton button = buttons.get(index);
            if (button.getClass() != GuiButton.class && !(button instanceof VibeMenuButton)) continue;
            String subtitle = "";
            switch (button.id) {
                case 1: case 11:
                    place(button, innerLeft, bodyTop, menuWidth, mainHeight);
                    if (details) subtitle = "Your worlds, ready to explore";
                    break;
                case 2: case 12:
                    place(button, innerLeft, bodyTop + mainHeight + gap, menuWidth, mainHeight);
                    if (details) subtitle = button.id == 2 ? "Find your next server" : "Start your demo world again";
                    break;
                case DISCORD_BUTTON_ID: place(button, innerLeft, footerTop, slotWidth, smallHeight); break;
                case SHADER_BUTTON_ID: place(button, innerLeft + (slotWidth + gap), footerTop, slotWidth, smallHeight); break;
                case THEMES_BUTTON_ID: place(button, innerLeft + 2 * (slotWidth + gap), footerTop, slotWidth, smallHeight); break;
                case 6: place(button, innerLeft + 3 * (slotWidth + gap), footerTop, menuWidth - 3 * (slotWidth + gap), smallHeight); break;
                case 0: place(button, innerLeft, optionTop, half, optionHeight); break;
                case 4: place(button, innerLeft + half + gap, optionTop, menuWidth - half - gap, optionHeight); break;
                case CHANGELOG_BUTTON_ID: place(button, left + panelWidth - 88, top + 13, 72, 22); break;
                case LICENSES_BUTTON_ID: place(button, left + panelWidth - 180, top + 13, 84, 22); break;
                default: break;
            }
            buttons.set(index, new VibeMenuButton(button, subtitle, false));
        }
    }

    private static void place(GuiButton button, int x, int y, int width, int height) {
        button.xPosition = x; button.yPosition = y; button.width = width; button.height = height;
    }

    public void draw(int width, int height, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        Gui.drawRect(0, 0, width, height, 0x85000000);
        AccountScreenStyle.window(left, top, panelWidth, panelHeight);
        AccountScreenStyle.title(Vibe.NAME, innerLeft, top + 16);
        AccountScreenStyle.text("v" + Vibe.VERSION, innerLeft + 52, top + 21, AccountScreenStyle.MUTED);
        if (details) AccountScreenStyle.text("Minecraft 1.8.9", innerLeft, top + 38, AccountScreenStyle.MUTED);

        boolean accountHovered = isAccountHit(mouseX, mouseY);
        AccountScreenStyle.panel(innerLeft, accountTop, menuWidth, accountHeight,
                accountHovered ? AccountScreenStyle.HOVER : AccountScreenStyle.SURFACE,
                accountHovered ? AccountScreenStyle.ACCENT : AccountScreenStyle.BORDER);
        String username = mc.getSession().getUsername();
        int headSize = details ? 30 : 24;
        SkinHeads.draw(mc.getSession().getProfile().getId(), username, innerLeft + 7, accountTop + (accountHeight - headSize) / 2, headSize);
        int textLeft = innerLeft + headSize + 17;
        AccountScreenStyle.text("PLAYING AS", textLeft, accountTop + (details ? 8 : 4), AccountScreenStyle.MUTED);
        AccountScreenStyle.rawText(AccountScreenStyle.fitRaw(username, menuWidth - headSize - 28), textLeft,
                accountTop + (details ? 23 : 17), AccountScreenStyle.TEXT);
    }

    public boolean isAccountHit(int mouseX, int mouseY) {
        return mouseX >= innerLeft && mouseX < innerLeft + menuWidth && mouseY >= accountTop && mouseY < accountTop + accountHeight;
    }

    public void drawOverlay(int width, int height, int mouseX, int mouseY) { changelog.draw(width, height, mouseX, mouseY); }
    public void toggleChangelog() { changelog.toggle(); }
    public boolean mouseInput(int x, int y, int wheel, int button, boolean pressed) { return changelog.mouse(x, y, wheel, button, pressed); }
    public boolean keyInput(int key) { return changelog.key(key); }
}
