package dev.vibe.ui.server;

import dev.vibe.miniblox.*;
import dev.vibe.server.ServerBrowserEvents;
import dev.vibe.ui.NeverLoseFont;
import java.awt.Font;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.*;
import org.lwjgl.input.Keyboard;
import static dev.vibe.ui.menu.AccountScreenStyle.*;

/** Visible, cancellable first-run installation followed by the normal Minecraft connection screen. */
public final class MinibloxConnectGui extends GuiScreen {
    private static final NeverLoseFont TITLE = new NeverLoseFont(Font.BOLD, 24);
    private static final NeverLoseFont BODY = new NeverLoseFont(Font.PLAIN, 20);
    private final GuiScreen parent;
    private final ServerData server;
    private final ServerBrowserEvents events;
    private MinibloxRuntime.Attempt attempt;
    private boolean connecting;
    private int cardX, cardY, cardW;

    public MinibloxConnectGui(GuiScreen parent, ServerData server, ServerBrowserEvents events) {
        this.parent = parent; this.server = server; this.events = events;
    }
    public static void connect(GuiScreen parent, Minecraft mc, ServerData data, ServerBrowserEvents events) {
        if (data == null) return;
        if (MinibloxServer.is(data)) { mc.displayGuiScreen(new MinibloxConnectGui(parent, data, events)); return; }
        if (events != null) events.prepare(data);
        mc.displayGuiScreen(new GuiConnecting(parent, mc, data));
    }
    @Override public void initGui() {
        cardW = Math.min(480, width - 24); cardX = (width - cardW) / 2; cardY = Math.max(12, (height - 220) / 2);
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 102, cardY + 178, 98, 20, "Cancel"));
        buttonList.add(new GuiButton(1, width / 2 + 4, cardY + 178, 98, 20, "Retry"));
        if (attempt == null) start();
    }
    private void start() {
        connecting = false;
        if (events != null) attempt = events.miniblox().start();
        else { attempt = new MinibloxRuntime.Attempt(); attempt.error = "The Miniblox service is unavailable. Return to the server browser and retry."; }
    }
    @Override public void updateScreen() {
        buttonList.get(1).visible = attempt.error != null;
        buttonList.get(0).xPosition = width / 2 - (attempt.error == null ? 49 : 102);
        if (attempt.port <= 0 || attempt.cancelled() || connecting) return;
        if (!events.miniblox().owns(attempt.port)) { attempt.error = "The translation layer stopped. Press Retry to start it again."; attempt.port = 0; return; }
        connecting = true;
        ServerData local = new ServerData(server.serverName, "127.0.0.1:" + attempt.port, false);
        local.setResourceMode(server.getResourceMode());
        events.prepare(server);
        GuiConnecting connection = new GuiConnecting(parent, mc, local);
        // The address used on the wire differs from the saved identity used by history/reconnect.
        mc.setServerData(server);
        mc.displayGuiScreen(connection);
    }
    @Override public void drawScreen(int mx, int my, float partial) {
        drawGradientRect(0, 0, width, height, 0xFF11121D, 0xFF1C2134);
        window(cardX, cardY, cardW, 216);
        TITLE.draw("Miniblox", cardX + 20, cardY + 18, TEXT);
        BODY.draw(BODY.fit(attempt.error == null ? attempt.message : "Unable to start Miniblox", cardW - 40), cardX + 20, cardY + 50, attempt.error == null ? TEXT : ERROR);
        String description = attempt.error != null ? attempt.error
                : "Vibe installs the translation layer on the first connection. Later connections reuse the installation.";
        fontRendererObj.drawSplitString(description, cardX + 20, cardY + 78, cardW - 40, MUTED);
        if (attempt.error == null) {
            dev.vibe.ui.MenuRoundedRenderer.rect(cardX + 20, cardY + 119, cardW - 40, 3, 1, TINT);
            float travel = (float) ((Math.sin(System.nanoTime() / 650000000.0) + 1) * .5);
            dev.vibe.ui.MenuRoundedRenderer.rect(cardX + 20 + Math.round((cardW - 100) * travel), cardY + 119, 60, 3, 1, ACCENT);
        }
        fontRendererObj.drawString("Translation layer by 7GrandDad", cardX + 20, cardY + 137, MUTED);
        fontRendererObj.drawString("Logs: vibe/miniblox", cardX + 20, cardY + 151, MUTED);
        super.drawScreen(mx, my, partial);
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(parent);
        else if (button.id == 1 && attempt.error != null) start();
    }
    @Override protected void keyTyped(char typed, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
    }
    @Override public void onGuiClosed() { if (!connecting && attempt != null) attempt.cancel(); }
}
