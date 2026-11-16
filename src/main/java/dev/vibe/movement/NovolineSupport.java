package dev.vibe.movement;

import java.util.Locale;
import net.minecraft.client.Minecraft;

public final class NovolineSupport {
    private NovolineSupport() { }
    public static boolean hypixel() {
        net.minecraft.client.multiplayer.ServerData server = Minecraft.getMinecraft().getCurrentServerData();
        if (server == null || server.serverIP == null) return false;
        String host = server.serverIP.toLowerCase(Locale.ROOT).split(":", 2)[0];
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }
}
