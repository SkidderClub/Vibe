package dev.vibe.miniblox;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

/** A reserved saved-server address; the actual loopback endpoint is never saved. */
public final class MinibloxServer {
    public static final String ADDRESS = "miniblox.io";
    private MinibloxServer() { }

    public static boolean is(ServerData data) {
        return data != null && data.serverIP != null && ADDRESS.equalsIgnoreCase(data.serverIP.trim());
    }

    /** Append once, retaining the user's existing order and subsequent moves. */
    public static ServerData ensure(ServerList list) {
        for (int i = 0; i < list.countServers(); i++) {
            ServerData data = list.getServerData(i);
            if (is(data)) return data;
        }
        ServerData data = new ServerData("Miniblox", ADDRESS, false);
        list.addServerData(data);
        list.saveServerList();
        return data;
    }
}
