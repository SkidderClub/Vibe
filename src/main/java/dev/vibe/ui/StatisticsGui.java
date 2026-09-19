package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.module.impl.StatisticsModule;
import dev.vibe.statistics.StatisticsService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/** Local activity dashboard using the same restrained Skeet surface as Vibe's editors. */
public final class StatisticsGui extends GuiScreen {
    private final StatisticsModule module;
    private StatisticsService.Scope scope = StatisticsService.Scope.GLOBAL;
    private int left, top, right, bottom, accountIndex;

    public StatisticsGui(StatisticsModule module) { this.module = module; }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        left = Math.max(8, (width - 690) / 2);
        right = Math.min(width - 8, left + 690);
        top = Math.max(18, (height - 424) / 2);
        bottom = Math.min(height - 12, top + 424);
        SkeetEditorStyle.backdrop(this, BlurModule.CLICK_GUI, partialTicks);
        SkeetEditorStyle.window(left, top, right, bottom, "Statistics", "Encrypted local activity");
        drawScopeButtons(mouseX, mouseY);

        StatisticsService service = Vibe.getInstance().getStatistics();
        String account = viewedAccount(service);
        StatisticsService.Snapshot stats = service == null ? new StatisticsService.Snapshot() : service.snapshot(scope, account);

        int overviewRight = left + 428;
        SkeetEditorStyle.panel(left + 10, top + 31, overviewRight, bottom - 10,
                scope == StatisticsService.Scope.GLOBAL ? "Overview" : "Overview · " + trim(stats.account, 23));
        metric("Playtime", duration(stats.playtime), left + 22, top + 58);
        metric("Kills", String.valueOf(stats.kills), left + 155, top + 58);
        metric("Joins", String.valueOf(stats.joins), left + 288, top + 58);
        metric("Beds broken", format(stats.bedsBroken), left + 22, top + 112);
        metric("Jumps", format(stats.jumps), left + 155, top + 112);
        metric("Blocks walked", format(stats.blocksWalked), left + 288, top + 112);
        metric("Blocks placed", format(stats.blocksPlaced), left + 22, top + 166);
        metric("Blocks broken", format(stats.blocksBroken), left + 155, top + 166);
        metric("Last server", trim(stats.lastServer, 15), left + 288, top + 166);

        fontRendererObj.drawStringWithShadow("Recent playtime", left + 22, top + 232, SkeetEditorStyle.TEXT);
        graph(stats.activity, left + 22, top + 248, overviewRight - left - 44, 80);
        fontRendererObj.drawString("Last seven days", left + 22, top + 337, SkeetEditorStyle.MUTED);

        int sideLeft = overviewRight + 10;
        SkeetEditorStyle.panel(sideLeft, top + 31, right - 10, top + 226,
                scope == StatisticsService.Scope.GLOBAL ? "Client totals" : "Server playtime");
        drawSidePanel(stats, sideLeft, right - 10, top + 56);
        SkeetEditorStyle.panel(sideLeft, top + 236, right - 10, bottom - 10,
                scope == StatisticsService.Scope.GLOBAL ? "Favourite accounts" : "Tracking");
        drawLowerPanel(stats, sideLeft, right - 10, top + 261);

        if (scope == StatisticsService.Scope.ACCOUNT) drawAccountPicker(account, mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawScopeButtons(int mouseX, int mouseY) {
        String[] names = { "Global", "Accounts", "Random accounts" };
        int x = right - 303;
        for (int i = 0; i < names.length; i++) {
            int x2 = x + (i == 2 ? 111 : 88);
            SkeetEditorStyle.button(x, top + 2, x2, top + 17, names[i], scope.ordinal() == i);
            x = x2 + 4;
        }
    }

    private void metric(String title, String value, int x, int y) {
        SkeetEditorStyle.row(x, y, x + 120, y + 42, false, false);
        fontRendererObj.drawString(title, x + 7, y + 7, SkeetEditorStyle.MUTED);
        fontRendererObj.drawStringWithShadow(value, x + 7, y + 22, SkeetEditorStyle.TEXT);
    }

    private void drawSidePanel(StatisticsService.Snapshot stats, int x, int x2, int y) {
        if (scope == StatisticsService.Scope.GLOBAL) {
            y = line("Client launches", stats.launches, x, x2, y);
            y = line("Configs loaded", stats.configsLoaded, x, x2, y);
            y = line("Configs created", stats.configsCreated, x, x2, y);
            y = line("Configs deleted", stats.configsDeleted, x, x2, y);
            y = line("Modules toggled", stats.modulesToggled, x, x2, y);
            y = line("GTA7 kills", stats.gtaKills, x, x2, y);
            y = line("Battlefront kills", stats.battlefrontKills, x, x2, y);
            line("Game playtime", stats.gtaPlaytime + stats.battlefrontPlaytime, x, x2, y);
            return;
        }
        List<Map.Entry<String, Long>> servers = new ArrayList<Map.Entry<String, Long>>(stats.servers.entrySet());
        Collections.sort(servers, new Comparator<Map.Entry<String, Long>>() {
            @Override public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) { return Long.compare(b.getValue(), a.getValue()); }
        });
        if (servers.isEmpty()) {
            fontRendererObj.drawString("No server time recorded yet", x + 8, y, SkeetEditorStyle.MUTED);
            return;
        }
        for (Map.Entry<String, Long> entry : servers.subList(0, Math.min(8, servers.size()))) {
            fontRendererObj.drawString(trim(entry.getKey(), 19), x + 8, y, SkeetEditorStyle.TEXT);
            rightText(duration(entry.getValue()), x2 - 8, y, SkeetEditorStyle.accent(.22F));
            y += 19;
        }
    }

    private void drawLowerPanel(StatisticsService.Snapshot stats, int x, int x2, int y) {
        if (scope != StatisticsService.Scope.GLOBAL) {
            fontRendererObj.drawSplitString("Each account and every generated cracked profile remain separate in the encrypted local vault.", x + 8, y, x2 - x - 16, SkeetEditorStyle.MUTED);
            return;
        }
        if (stats.favourites.isEmpty()) {
            fontRendererObj.drawString("Play to build your ranking", x + 8, y, SkeetEditorStyle.MUTED);
            return;
        }
        int rank = 1;
        for (StatisticsService.RankedAccount favourite : stats.favourites.subList(0, Math.min(6, stats.favourites.size()))) {
            fontRendererObj.drawString(rank + ".", x + 8, y, SkeetEditorStyle.MUTED);
            fontRendererObj.drawString(trim(favourite.name, 16), x + 23, y, SkeetEditorStyle.TEXT);
            rightText(duration(favourite.playtime), x2 - 8, y, SkeetEditorStyle.accent(.22F));
            rank++;
            y += 19;
        }
    }

    private int line(String label, long value, int x, int x2, int y) {
        fontRendererObj.drawString(label, x + 8, y, SkeetEditorStyle.MUTED);
        rightText(format(value), x2 - 8, y, SkeetEditorStyle.TEXT);
        return y + 19;
    }

    private void graph(List<Long> values, int x, int y, int w, int h) {
        SkeetEditorStyle.input(x, y, x + w, y + h);
        long max = 1;
        for (Long value : values) max = Math.max(max, value == null ? 0L : value.longValue());
        int count = Math.max(1, values.size());
        for (int i = 0; i < count; i++) {
            long value = values.get(i) == null ? 0L : values.get(i).longValue();
            int barHeight = (int) Math.round((h - 12) * value / (double) max);
            int barLeft = x + 7 + i * (w - 14) / count;
            int barWidth = Math.max(2, (w - 18) / count);
            Gui.drawRect(barLeft, y + h - 6 - barHeight, barLeft + barWidth, y + h - 6, SkeetEditorStyle.accent(i / (float) count));
        }
    }

    private void drawAccountPicker(String account, int mouseX, int mouseY) {
        int x = left + 188, y = top + 2;
        SkeetEditorStyle.row(x, y, x + 190, y + 15, false, hit(x, y, x + 190, y + 15, mouseX, mouseY));
        fontRendererObj.drawString("‹", x + 7, y + 4, SkeetEditorStyle.accent(.1F));
        fontRendererObj.drawString(trim(account, 18), x + 22, y + 4, SkeetEditorStyle.TEXT);
        fontRendererObj.drawString("›", x + 177, y + 4, SkeetEditorStyle.accent(.1F));
    }

    private String viewedAccount(StatisticsService service) {
        if (service == null || scope != StatisticsService.Scope.ACCOUNT) return "";
        List<String> accounts = service.getAccounts();
        if (accounts.isEmpty()) return "Offline";
        accountIndex = Math.max(0, Math.min(accountIndex, accounts.size() - 1));
        return accounts.get(accountIndex);
    }

    private void rightText(String text, int x, int y, int colour) { fontRendererObj.drawString(text, x - fontRendererObj.getStringWidth(text), y, colour); }
    private static boolean hit(int x, int y, int x2, int y2, int mouseX, int mouseY) { return mouseX >= x && mouseX < x2 && mouseY >= y && mouseY < y2; }
    private String trim(String value, int characters) { return value == null ? "" : fontRendererObj.trimStringToWidth(value, characters * 6); }
    private static String format(long value) { return value >= 1000L ? String.format(java.util.Locale.ROOT, "%.1fk", value / 1000.0D) : String.valueOf(value); }
    private static String duration(long seconds) { return seconds < 60L ? seconds + "s" : seconds < 3600L ? seconds / 60L + "m" : seconds / 3600L + "h " + (seconds / 60L) % 60L + "m"; }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button == 0) {
            int scopeStart = right - 303;
            if (hit(scopeStart, top + 2, scopeStart + 88, top + 17, mouseX, mouseY)) { scope = StatisticsService.Scope.GLOBAL; return; }
            if (hit(scopeStart + 92, top + 2, scopeStart + 180, top + 17, mouseX, mouseY)) { scope = StatisticsService.Scope.ACCOUNT; return; }
            if (hit(scopeStart + 184, top + 2, right - 4, top + 17, mouseX, mouseY)) { scope = StatisticsService.Scope.RANDOM; return; }
            if (scope == StatisticsService.Scope.ACCOUNT && hit(left + 188, top + 2, left + 378, top + 17, mouseX, mouseY)) {
                List<String> accounts = Vibe.getInstance().getStatistics().getAccounts();
                if (!accounts.isEmpty()) accountIndex = (accountIndex + (mouseX < left + 213 ? -1 : 1) + accounts.size()) % accounts.size();
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void keyTyped(char character, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RSHIFT) { mc.displayGuiScreen(null); return; }
        super.keyTyped(character, key);
    }
    @Override public void onGuiClosed() { if (module.isEnabled()) module.setEnabled(false); super.onGuiClosed(); }
    @Override public boolean doesGuiPauseGame() { return false; }
}
