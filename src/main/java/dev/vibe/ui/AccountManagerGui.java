package dev.vibe.ui;

import dev.vibe.account.Account;
import dev.vibe.account.AccountManager;
import dev.vibe.account.CookieFolderImporter;
import dev.vibe.account.MicrosoftLogin;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Vibe account management, available only outside a running world. */
public final class AccountManagerGui extends GuiScreen {
    private enum View { ACCOUNTS, TOOLS, REMOVE, COOKIES }
    private final GuiScreen parent;
    private final MainMenuShaderManager shaders;
    private final AccountManager accounts;
    private GuiTextField search;
    private Account selected;
    private Account removing;
    private View view = View.ACCOUNTS;
    private View builtView;
    private boolean builtBusy;
    private boolean builtCode;
    private int scroll;
    private int left, top, panelWidth, panelHeight, innerLeft, innerWidth;
    private int bodyTop, contentBottom, statusTop, listWidth, searchY, listTop, visibleRows, rowHeight;
    private int railLeft;
    private boolean wide;
    private long lastClick;
    private volatile boolean choosingFile;
    private String chooserMessage;
    private long copiedUntil;

    public AccountManagerGui(GuiScreen parent, MainMenuShaderManager shaders, AccountManager accounts) {
        this.parent = parent;
        this.shaders = shaders;
        this.accounts = accounts;
    }

    @Override public void initGui() {
        String query = search == null ? "" : search.getText();
        boolean focused = search != null && search.isFocused();
        Keyboard.enableRepeatEvents(true);
        panelWidth = Math.min(640, width - 24);
        panelHeight = Math.min(420, height - 20);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        innerLeft = left + 14;
        innerWidth = panelWidth - 28;
        wide = panelWidth >= 500 && panelHeight >= 330;
        bodyTop = top + (wide ? 64 : 48);
        statusTop = top + panelHeight - 37;
        contentBottom = statusTop - 10;
        railLeft = innerLeft + innerWidth - 190;
        listWidth = wide ? innerWidth - 208 : innerWidth;
        searchY = wide ? bodyTop : bodyTop + 38;
        listTop = searchY + 28;
        int listBottom = wide ? contentBottom : contentBottom - 30;
        rowHeight = wide ? 42 : Math.min(34, Math.max(26, listBottom - listTop));
        visibleRows = Math.max(1, (listBottom - listTop) / rowHeight);
        search = new GuiTextField(0, fontRendererObj, innerLeft + 11, searchY + 7, listWidth - 22, 14);
        search.setEnableBackgroundDrawing(false);
        search.setTextColor(AccountScreenStyle.TEXT);
        search.setMaxStringLength(32);
        search.setText(query);
        search.setFocused(focused);
        rebuildButtons();
    }

    private void addButton(int id, int x, int y, int w, int h, String label, String subtitle, boolean primary) {
        buttonList.add(new AccountScreenStyle.Button(id, x, y, w, h, label, subtitle, primary, id == 4 || id == 14));
    }

    private void rebuildButtons() {
        builtView = view;
        builtBusy = accounts.isBusy();
        builtCode = accounts.getDeviceCode() != null;
        buttonList.clear();
        addButton(6, left + panelWidth - 62, top + 15, 48, 24, "Back", "", false);
        if (builtBusy) {
            addButton(5, left + panelWidth - 123, top + 15, 55, 24, "Cancel", "", false);
            if (builtCode) {
                int w = Math.min(138, (innerWidth - 8) / 2);
                addButton(1, width / 2 - w - 4, contentBottom - 27, w, 27, "Open browser", "", true);
                addButton(3, width / 2 + 4, contentBottom - 27, w, 27, "Copy code", "", false);
            }
        } else if (view == View.TOOLS) {
            int w = (innerWidth - 10) / 2;
            int h = Math.min(46, (contentBottom - bodyTop - 16) / 3);
            boolean details = h >= 36 && w >= 190;
            addButton(2, innerLeft, bodyTop, w, h, "Offline profile", details ? "Singleplayer & local servers" : "", false);
            addButton(5, innerLeft + w + 10, bodyTop, w, h, "Launcher account", details ? "Restore original session" : "", false);
            addButton(7, innerLeft, bodyTop + h + 8, w, h, "Import backup", details ? "Choose a .vibeaccounts file" : "", false);
            addButton(9, innerLeft + w + 10, bodyTop + h + 8, w, h, "Export backup", details ? "Save an encrypted copy" : "", false);
            addButton(8, innerLeft, bodyTop + 2 * (h + 8), w, h, "Backup folder", details ? "Import Vibe backups" : "", false);
            addButton(11, innerLeft + w + 10, bodyTop + 2 * (h + 8), w, h, "Cookie folder", details ? "Automatic account import" : "", true);
        } else if (view == View.COOKIES) {
            int w = (innerWidth - 16) / 3;
            addButton(16, innerLeft, contentBottom - 26, w, 26, "Open folder", "", true);
            addButton(17, innerLeft + w + 8, contentBottom - 26, w, 26, "Pause", "", false);
            addButton(18, innerLeft + 2 * (w + 8), contentBottom - 26, innerWidth - 2 * (w + 8), 26, "Copy path", "", false);
        } else if (view == View.REMOVE) {
            int w = Math.min(120, (innerWidth - 8) / 2);
            addButton(14, width / 2 - w - 4, contentBottom - 28, w, 28, "Remove", "", false);
            addButton(15, width / 2 + 4, contentBottom - 28, w, 28, "Keep account", "", true);
        } else {
            addButton(12, left + panelWidth - 123, top + 15, 55, 24, "More", "", false);
            if (wide) {
                addButton(10, railLeft, bodyTop + 18, 190, 40, "Cookie login", "Choose a .txt file", true);
                addButton(1, railLeft, bodyTop + 66, 190, 40, "Microsoft login", "Continue in your browser", false);
                addButton(3, railLeft, contentBottom - 28, 119, 28, "Use account", "", true);
                addButton(4, railLeft + 127, contentBottom - 28, 63, 28, "Remove", "", false);
                addButton(19, railLeft, contentBottom - 62, 190, 28, "Auto Login", "", false);
            } else {
                int w = (innerWidth - 8) / 2;
                addButton(10, innerLeft, bodyTop, w, 30, "Cookie login", "", true);
                addButton(1, innerLeft + w + 8, bodyTop, innerWidth - w - 8, 30, "Microsoft login", "", false);
                int useWidth = Math.max(60, (innerWidth - 16) / 3);
                addButton(3, innerLeft, contentBottom - 24, useWidth, 24, "Use account", "", true);
                addButton(4, innerLeft + innerWidth - 68, contentBottom - 24, 68, 24, "Remove", "", false);
                addButton(19, innerLeft + useWidth + 8, contentBottom - 24, innerWidth - useWidth - 84, 24, "Auto Login", "", false);
            }
        }
        updateButtons();
    }

    private void ensureButtons() {
        if (builtView != view || builtBusy != accounts.isBusy() || builtCode != (accounts.getDeviceCode() != null)) rebuildButtons();
        else updateButtons();
    }

    private List<Account> filtered() {
        String query = search == null ? "" : search.getText().toLowerCase(Locale.ROOT);
        return accounts.getAccounts().stream().filter(account -> account.getName().toLowerCase(Locale.ROOT).contains(query))
                .collect(Collectors.toList());
    }

    private Account selectedAccount() {
        return filtered().stream().filter(account -> account.sameIdentity(selected)).findFirst().orElse(null);
    }

    private boolean isActive(Account account) {
        return account != null && account.getUuid().toString().replace("-", "").equals(mc.getSession().getPlayerID().replace("-", ""));
    }

    private boolean canActivate(Account account) {
        return account != null && (!isActive(account) || account.isMicrosoft());
    }

    private void updateButtons() {
        boolean busy = accounts.isBusy();
        boolean code = accounts.getDeviceCode() != null;
        boolean available = accounts.isStorageAvailable();
        for (GuiButton button : buttonList) {
            button.enabled = !busy && !choosingFile && available;
            if (button.id == 1) {
                button.enabled = !choosingFile && available && (!busy || code);
            } else if (button.id == 3) {
                Account account = selectedAccount();
                button.displayString = code ? "Copy code" : isActive(account) ? account.isMicrosoft() ? "Sign in again" : "Active account" : "Use account";
                button.enabled = !choosingFile && available && (code || (!busy && canActivate(account)));
            } else if (button.id == 4) button.enabled &= selectedAccount() != null;
            else if (button.id == 5 || button.id == 6 || button.id == 12 || button.id == 15) button.enabled = !choosingFile;
            else if (button.id == 9) button.enabled &= !accounts.getAccounts().isEmpty();
            else if (button.id == 17) button.displayString = accounts.getCookieFolder().isPaused() ? "Resume" : "Pause";
            else if (button.id == 19) {
                button.enabled &= selectedAccount() != null;
                button.displayString = accounts.isAutoLogin(selectedAccount()) ? "Disable Auto Login" : "Auto Login";
            }
        }
    }

    @Override protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) return;
        MicrosoftLogin.DeviceCode code = accounts.getDeviceCode();
        switch (button.id) {
            case 1:
                if (code != null) {
                    if (!AccountManager.openBrowser(code.verificationUri)) accounts.report("Open " + code.verificationUri + " in your browser.");
                } else accounts.addMicrosoft();
                break;
            case 2: mc.displayGuiScreen(new OfflineAccountGui(this, shaders, accounts)); break;
            case 3:
                if (code != null) { setClipboardString(code.userCode); copiedUntil = System.currentTimeMillis() + 4000; }
                else accounts.login(selectedAccount());
                break;
            case 4:
                removing = selectedAccount();
                if (removing != null) view = View.REMOVE;
                break;
            case 5: if (accounts.isBusy()) accounts.cancel(); else { accounts.restoreLauncher(); view = View.ACCOUNTS; } break;
            case 6: back(); break;
            case 7: chooseFile(FileAction.IMPORT_FILE); break;
            case 8: chooseFile(FileAction.IMPORT_FOLDER); break;
            case 9: chooseFile(FileAction.EXPORT_BACKUP); break;
            case 10: chooseFile(FileAction.COOKIE_LOGIN); break;
            case 11: view = View.COOKIES; break;
            case 12: view = View.TOOLS; break;
            case 14: if (removing != null) accounts.remove(removing); selected = null; view = View.ACCOUNTS; break;
            case 15: view = View.ACCOUNTS; break;
            case 16: openCookieFolder(); break;
            case 17: accounts.getCookieFolder().setPaused(!accounts.getCookieFolder().isPaused()); break;
            case 18: setClipboardString(accounts.getCookieFolder().getDirectory().toString()); break;
            case 19: accounts.toggleAutoLogin(selectedAccount()); break;
            default: break;
        }
        ensureButtons();
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        shaders.draw(width, height);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        drawRect(0, 0, width, height, 0x85000000);
        AccountScreenStyle.window(left, top, panelWidth, panelHeight);
        String title = accounts.isBusy() ? accounts.getDeviceCode() != null ? "Sign in" : "Please wait"
                : view == View.TOOLS ? "More" : view == View.REMOVE ? "Remove" : view == View.COOKIES ? "Cookie folder" : "Accounts";
        AccountScreenStyle.title(title, innerLeft, top + 14);
        String subtitle = accounts.isBusy() ? accounts.getDeviceCode() != null ? "Microsoft account" : "Account operation"
                : view == View.TOOLS ? "Profiles, imports & backups" : view == View.REMOVE ? "Saved sign-in"
                : view == View.COOKIES ? accounts.getCookieFolder().isPaused() ? "Paused after the current file" : "Automatic account import"
                : "Playing as " + mc.getSession().getUsername();
        AccountScreenStyle.text(AccountScreenStyle.fit(subtitle, panelWidth - 150), innerLeft, top + 33, AccountScreenStyle.MUTED);
        if (accounts.isBusy()) drawLogin();
        else if (view == View.REMOVE) drawRemove();
        else if (view == View.ACCOUNTS) drawAccounts(mouseX, mouseY);
        else if (view == View.COOKIES) drawCookieFolder();
        ensureButtons();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawStatus(mouseX, mouseY);
    }

    private void drawStatus(int mouseX, int mouseY) {
        boolean folderView = view == View.COOKIES && !accounts.isBusy();
        boolean error = accounts.hasError() && !choosingFile && !folderView;
        String message = choosingFile ? chooserMessage : folderView ? accounts.getCookieFolder().getStatus()
                : !error && System.currentTimeMillis() < copiedUntil ? "Code copied to clipboard." : accounts.getStatus();
        AccountScreenStyle.panel(innerLeft, statusTop, innerWidth, 27,
                error ? 0xFF2B1C25 : AccountScreenStyle.SURFACE, error ? 0xFF59313F : AccountScreenStyle.BORDER);
        int color = error ? AccountScreenStyle.ERROR : AccountScreenStyle.MUTED;
        RenderUtils.roundedRect(innerLeft + 9, statusTop + 11, innerLeft + 13, statusTop + 15, 2,
                error ? color : accounts.isBusy() || choosingFile ? AccountScreenStyle.ACCENT : AccountScreenStyle.SUCCESS);
        List<String> lines = fontRendererObj.listFormattedStringToWidth(message, innerWidth - 32);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            String line = i == 1 && lines.size() > 2 ? AccountScreenStyle.fit(lines.get(i), innerWidth - 48) + "..." : lines.get(i);
            AccountScreenStyle.text(line, innerLeft + 20, statusTop + (lines.size() > 1 ? 5 : 10) + i * 9, color);
        }
        if (mouseX >= innerLeft && mouseX < innerLeft + innerWidth && mouseY >= statusTop && mouseY < statusTop + 27 && lines.size() > 2)
            drawHoveringText(fontRendererObj.listFormattedStringToWidth(message, Math.min(300, innerWidth)), mouseX, mouseY);
    }

    private void drawAccounts(int mouseX, int mouseY) {
        List<Account> entries = filtered();
        if (selectedAccount() == null && !entries.isEmpty()) selected = entries.stream().filter(this::isActive).findFirst().orElse(entries.get(0));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - visibleRows)));
        AccountScreenStyle.panel(innerLeft, searchY, listWidth, 24, AccountScreenStyle.SURFACE,
                search.isFocused() ? AccountScreenStyle.ACCENT : AccountScreenStyle.BORDER);
        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused())
            AccountScreenStyle.text("Search accounts...", innerLeft + 11, searchY + 8, AccountScreenStyle.MUTED);
        if (entries.isEmpty()) {
            int y = listTop + Math.max(0, (visibleRows * rowHeight - 26) / 2);
            String label = search.getText().isEmpty() ? "Your accounts appear here" : "No matching accounts";
            centered(label, innerLeft + listWidth / 2, y, AccountScreenStyle.TEXT);
            if (visibleRows * rowHeight >= 32)
                centered(search.getText().isEmpty() ? "Choose a login method to get started." : "Try a different name.", innerLeft + listWidth / 2, y + 16, AccountScreenStyle.MUTED);
        }
        for (int i = 0; i < visibleRows && scroll + i < entries.size(); i++) {
            Account account = entries.get(scroll + i);
            int y = listTop + i * rowHeight;
            boolean active = isActive(account);
            boolean hover = mouseX >= innerLeft && mouseX < innerLeft + listWidth - 6 && mouseY >= y && mouseY < y + rowHeight - 3;
            AccountScreenStyle.panel(innerLeft, y, listWidth - 6, rowHeight - 3,
                    account.sameIdentity(selected) ? AccountScreenStyle.TINT : hover ? AccountScreenStyle.HOVER : AccountScreenStyle.SURFACE,
                    account.sameIdentity(selected) ? AccountScreenStyle.ACCENT : AccountScreenStyle.BORDER);
            int avatarSize = wide ? 26 : 22;
            SkinHeads.draw(account.getUuid(), account.getName(), innerLeft + 8, y + (rowHeight - 3 - avatarSize) / 2, avatarSize);
            int textLeft = innerLeft + avatarSize + 17;
            boolean auto = accounts.isAutoLogin(account);
            String autoLabel = dev.vibe.language.LanguageManager.translate("Auto Login");
            String activeLabel = dev.vibe.language.LanguageManager.translate("Active");
            int tagWidth = Math.max(auto ? fontRendererObj.getStringWidth(autoLabel) + 8 : 0, active ? fontRendererObj.getStringWidth(activeLabel) + 8 : 0);
            int tagLeft = innerLeft + listWidth - tagWidth - 14;
            AccountScreenStyle.rawText(AccountScreenStyle.fitRaw(account.getName(), listWidth - avatarSize - 34 - tagWidth),
                    textLeft, y + (wide ? 8 : 4), AccountScreenStyle.TEXT);
            AccountScreenStyle.text(AccountScreenStyle.fit(account.isMicrosoft() ? "Microsoft" : "Offline profile", listWidth - avatarSize - 34 - tagWidth), textLeft, y + (wide ? 23 : 16), AccountScreenStyle.MUTED);
            if (active) {
                accountTag(activeLabel, tagLeft, y + 3, tagWidth, rowHeight >= 32 ? 12 : 9, AccountScreenStyle.SUCCESS);
            }
            if (auto) accountTag(autoLabel, tagLeft, y + (active ? rowHeight >= 32 ? 17 : 13 : 8), tagWidth, rowHeight >= 32 ? 12 : 9, AccountScreenStyle.ACCENT);
        }
        if (entries.size() > visibleRows) {
            int track = visibleRows * rowHeight - 3;
            int thumb = Math.max(8, track * visibleRows / entries.size());
            int y = listTop + (track - thumb) * scroll / (entries.size() - visibleRows);
            drawRect(innerLeft + listWidth - 3, listTop, innerLeft + listWidth, listTop + track, AccountScreenStyle.BORDER);
            RenderUtils.roundedRect(innerLeft + listWidth - 3, y, innerLeft + listWidth, y + thumb, 1, AccountScreenStyle.ACCENT);
        }
        if (wide) {
            AccountScreenStyle.text("ADD ACCOUNT", railLeft, bodyTop + 3, AccountScreenStyle.MUTED);
            int y = bodyTop + 127;
            AccountScreenStyle.text("SELECTED ACCOUNT", railLeft, y, AccountScreenStyle.MUTED);
            Account account = selectedAccount();
            if (account != null) {
                SkinHeads.draw(account.getUuid(), account.getName(), railLeft, y + 18, 32);
                AccountScreenStyle.rawText(AccountScreenStyle.fitRaw(account.getName(), 147), railLeft + 42, y + 21, AccountScreenStyle.TEXT);
                AccountScreenStyle.text(isActive(account) ? "Currently playing" : account.isMicrosoft() ? "Minecraft Java" : "Offline profile",
                        railLeft + 42, y + 37, isActive(account) ? AccountScreenStyle.SUCCESS : AccountScreenStyle.MUTED);
            } else AccountScreenStyle.rawText(account == null ? dev.vibe.language.LanguageManager.translate("Select a saved account") : account.getName(), railLeft, y + 17, AccountScreenStyle.MUTED);
        }
    }

    private void centered(String value, int centerX, int y, int color) {
        value = dev.vibe.language.LanguageManager.translate(value);
        AccountScreenStyle.text(value, centerX - fontRendererObj.getStringWidth(value) / 2, y, color);
    }

    private void accountTag(String label, int x, int y, int width, int height, int color) {
        MenuRoundedRenderer.rect(x, y, width, height, 3, RenderUtils.blend(AccountScreenStyle.SURFACE, color, .16F));
        AccountScreenStyle.text(label, x + 4, y + Math.max(0, (height - 8) / 2), color);
    }

    private void drawLogin() {
        MicrosoftLogin.DeviceCode code = accounts.getDeviceCode();
        int w = Math.min(innerWidth, 360);
        int h = contentBottom - bodyTop - (code != null ? 37 : 0);
        AccountScreenStyle.panel(width / 2 - w / 2, bodyTop, w, h, AccountScreenStyle.SURFACE, AccountScreenStyle.BORDER);
        if (code != null) {
            int y = bodyTop + Math.max(9, (h - 110) / 2);
            centered("Enter this code in your browser", width / 2, y, AccountScreenStyle.TEXT);
            AccountScreenStyle.panel(width / 2 - 76, y + 16, 152, 25, AccountScreenStyle.TINT, AccountScreenStyle.BORDER);
            centered(code.userCode, width / 2, y + 25, AccountScreenStyle.ACCENT);
            centered(AccountScreenStyle.fit(code.verificationUri.toString(), w - 20), width / 2, y + 47, AccountScreenStyle.MUTED);
            centered(AccountScreenStyle.fit("App: In-Game Account Switcher", w - 20), width / 2, y + 62, AccountScreenStyle.MUTED);
            if (h >= 115) {
                long seconds = Math.max(0, (code.expiresAt - System.currentTimeMillis()) / 1000);
                centered("Expires in " + seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60),
                        width / 2, y + 84, AccountScreenStyle.MUTED);
            }
        } else {
            int y = bodyTop + h / 2 - 18;
            centered("Working on your account", width / 2, y, AccountScreenStyle.TEXT);
            for (int i = 0; i < 4; i++) {
                int x = width / 2 - 20 + i * 12;
                RenderUtils.roundedRect(x, y + 23, x + 5, y + 28, 2,
                        (System.currentTimeMillis() / 240) % 4 == i ? AccountScreenStyle.ACCENT : AccountScreenStyle.TINT);
            }
        }
    }

    private void drawRemove() {
        int y = bodyTop + Math.max(0, (contentBottom - bodyTop - 112) / 2);
        if (removing == null) return;
        SkinHeads.draw(removing.getUuid(), removing.getName(), width / 2 - 17, y, 34);
        centered("Remove " + removing.getName() + "?", width / 2, y + 45, AccountScreenStyle.TEXT);
        centered("Deletes the saved sign-in from Vibe.", width / 2, y + 62, AccountScreenStyle.MUTED);
    }

    private void drawCookieFolder() {
        CookieFolderImporter folder = accounts.getCookieFolder();
        int y = bodyTop;
        String[] lines = {
                "This run: " + folder.getSummary(),
                "Drop one Netscape .txt export per account into vibe/cookies.",
                "valid: verified and saved. invalid: rejected or expired export.",
                "error: check failed; move the export back to retry.",
                "Each processed file has a .result note. Existing accounts are updated.",
                "The current session stays active. Closing this screen keeps the import running."
        };
        for (String text : lines) {
            for (String line : fontRendererObj.listFormattedStringToWidth(text, innerWidth)) {
                if (y + 9 > contentBottom - 34) return;
                AccountScreenStyle.text(line, innerLeft, y, AccountScreenStyle.MUTED);
                y += 11;
            }
            y += wide ? 8 : 2;
        }
    }

    private void openCookieFolder() {
        choosingFile = true;
        chooserMessage = "Opening vibe/cookies...";
        SwingUtilities.invokeLater(() -> {
            boolean failed = false;
            try {
                java.nio.file.Path directory = accounts.getCookieFolder().getDirectory();
                java.nio.file.Files.createDirectories(directory);
                java.awt.Desktop.getDesktop().open(directory.toFile());
            } catch (Exception e) { failed = true; }
            final boolean unavailable = failed;
            mc.addScheduledTask(() -> {
                choosingFile = false;
                if (unavailable) {
                    view = View.ACCOUNTS;
                    accounts.report("Open this folder manually: " + accounts.getCookieFolder().getDirectory());
                }
            });
        });
    }

    @Override protected void mouseClicked(int x, int y, int mouseButton) throws IOException {
        if (view == View.ACCOUNTS && !accounts.isBusy() && !choosingFile) search.mouseClicked(x, y, mouseButton);
        if (mouseButton == 0 && view == View.ACCOUNTS && !accounts.isBusy() && !choosingFile
                && x >= innerLeft && x < innerLeft + listWidth - 6 && y >= listTop && y < listTop + visibleRows * rowHeight
                && (y - listTop) % rowHeight < rowHeight - 3) {
            List<Account> entries = filtered();
            int index = scroll + (y - listTop) / rowHeight;
            if (index < entries.size()) {
                Account account = entries.get(index);
                if (account.sameIdentity(selected) && System.currentTimeMillis() - lastClick < 300 && canActivate(account)) accounts.login(account);
                selected = account;
                lastClick = System.currentTimeMillis();
            }
        }
        ensureButtons();
        super.mouseClicked(x, y, mouseButton);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (wheel != 0 && view == View.ACCOUNTS && !accounts.isBusy() && !choosingFile
                && x >= innerLeft && x < innerLeft + listWidth && y >= listTop && y < listTop + visibleRows * rowHeight)
            scroll += wheel > 0 ? -1 : 1;
    }

    @Override protected void keyTyped(char character, int key) throws IOException {
        if (choosingFile) return;
        if (key == Keyboard.KEY_ESCAPE) { back(); return; }
        if (view == View.ACCOUNTS && !accounts.isBusy()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                if (canActivate(selectedAccount())) accounts.login(selectedAccount());
                return;
            }
            if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
                List<Account> entries = filtered();
                if (entries.isEmpty()) return;
                int index = -1;
                for (int i = 0; i < entries.size(); i++) if (entries.get(i).sameIdentity(selected)) index = i;
                index = Math.max(0, Math.min(entries.size() - 1, index + (key == Keyboard.KEY_UP ? -1 : 1)));
                selected = entries.get(index);
                if (index < scroll) scroll = index;
                if (index >= scroll + visibleRows) scroll = index - visibleRows + 1;
            } else if (search.textboxKeyTyped(character, key)) { scroll = 0; selected = null; }
        }
    }

    private void chooseFile(FileAction action) {
        if (choosingFile || accounts.isBusy()) return;
        boolean directory = action == FileAction.IMPORT_FOLDER;
        boolean export = action == FileAction.EXPORT_BACKUP;
        boolean cookies = action == FileAction.COOKIE_LOGIN;
        chooserMessage = cookies ? "Choose your Microsoft cookie export (Netscape .txt)."
                : "Choose an encrypted Vibe backup (.vibeaccounts).";
        choosingFile = true;
        SwingUtilities.invokeLater(() -> {
            File selectedFile = null;
            boolean failed = false;
            try {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle(cookies ? "Sign in with Microsoft cookies (Netscape format)"
                        : export ? "Export encrypted Vibe accounts" : "Import Vibe account backup");
                chooser.setFileSelectionMode(directory ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);
                if (cookies) chooser.setFileFilter(new FileNameExtensionFilter("Netscape cookie export (*.txt)", "txt"));
                else if (!directory) chooser.setFileFilter(new FileNameExtensionFilter("Vibe account backup (*.vibeaccounts)", "vibeaccounts"));
                if (export) chooser.setSelectedFile(new File("Vibe-accounts.vibeaccounts"));
                int answer = export ? chooser.showSaveDialog(null) : chooser.showOpenDialog(null);
                if (answer == JFileChooser.APPROVE_OPTION) {
                    selectedFile = chooser.getSelectedFile();
                    if (export && !selectedFile.getName().toLowerCase(Locale.ROOT).endsWith(".vibeaccounts"))
                        selectedFile = new File(selectedFile.getParentFile(), selectedFile.getName() + ".vibeaccounts");
                    if (export && selectedFile.exists() && JOptionPane.showConfirmDialog(null, "Replace this backup?",
                            "Vibe accounts", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) selectedFile = null;
                }
            } catch (RuntimeException e) { failed = true; }
            final File result = selectedFile;
            final boolean unavailable = failed;
            mc.addScheduledTask(() -> {
                choosingFile = false;
                if (mc.currentScreen != this) return;
                if (unavailable) accounts.report("File picker unavailable on this desktop.");
                else if (result != null) {
                    view = View.ACCOUNTS;
                    if (cookies) accounts.addMicrosoftCookies(result.toPath());
                    else if (export) accounts.exportBackup(result.toPath());
                    else accounts.importBackup(result.toPath());
                }
            });
        });
    }

    private enum FileAction { IMPORT_FILE, IMPORT_FOLDER, EXPORT_BACKUP, COOKIE_LOGIN }

    void showAccounts() { view = View.ACCOUNTS; mc.displayGuiScreen(this); }

    private void back() {
        if (!accounts.isBusy() && view != View.ACCOUNTS) { view = View.ACCOUNTS; ensureButtons(); }
        else { accounts.cancel(); mc.displayGuiScreen(parent); }
    }
    @Override public void updateScreen() { search.updateCursorCounter(); ensureButtons(); }
    @Override public void onGuiClosed() { accounts.cancel(); Keyboard.enableRepeatEvents(false); }
    @Override public boolean doesGuiPauseGame() { return false; }
}
