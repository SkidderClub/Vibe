package dev.vibe.server;

import dev.vibe.ui.server.ServerBrowserGui;
import java.lang.reflect.Field;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.Session;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ServerBrowserEvents {
    private final Minecraft mc;
    private final ServerJoinHistory history;
    private final ServerJoinTracker tracker;
    private volatile boolean prepared;
    private Session cachedSession;
    private String cachedAccount;
    public ServerBrowserEvents(Minecraft mc, Path file) {
        this.mc = mc; history = new ServerJoinHistory(file); tracker = new ServerJoinTracker(history);
    }
    public static String accountKey(Session session) {
        return session == null ? ServerJoinHistory.accountKey(null, "Player")
                : ServerJoinHistory.accountKey(session.getPlayerID(), session.getUsername());
    }
    private synchronized String currentAccount() {
        Session session = mc.getSession();
        if (cachedAccount == null || cachedSession != session) { cachedSession = session; cachedAccount = accountKey(session); }
        return cachedAccount;
    }
    public ServerJoinHistory.Result result(ServerData data) { return tracker.get(currentAccount(), data.serverIP); }
    public String error() { return history.error(); }
    public void prepare(ServerData data) {
        tracker.begin(currentAccount(), data.serverIP); prepared = true;
    }
    @SubscribeEvent public void onGui(GuiOpenEvent event) {
        if (event.gui instanceof GuiConnecting) {
            if (!prepared && mc.getCurrentServerData() != null) prepare(mc.getCurrentServerData());
            prepared = false;
        } else if (event.gui instanceof GuiDisconnected) {
            tracker.disconnected(tracker.active(), disconnectReason((GuiDisconnected) event.gui), System.currentTimeMillis());
            prepared = false;
        } else if ((event.gui instanceof ServerBrowserGui || event.gui instanceof GuiMultiplayer || event.gui instanceof GuiMainMenu)
                && mc.theWorld == null) {
            tracker.cancel(); prepared = false;
        }
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && mc.theWorld != null && mc.thePlayer != null && !mc.isSingleplayer())
            tracker.ready(tracker.active(), System.currentTimeMillis());
    }
    private static String disconnectReason(GuiDisconnected gui) {
        for (Field field : GuiDisconnected.class.getDeclaredFields()) {
            if (!IChatComponent.class.isAssignableFrom(field.getType())) continue;
            try { field.setAccessible(true); IChatComponent text = (IChatComponent) field.get(gui);
                if (text != null) return text.getUnformattedText();
            } catch (ReflectiveOperationException ignored) { }
        }
        return "Connection failed";
    }
}
