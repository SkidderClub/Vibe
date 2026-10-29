package dev.vibe.event;

import dev.vibe.Vibe;
import dev.vibe.account.RandomUsername;
import dev.vibe.language.LanguageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Keeps a copy of the last remote connection even after Minecraft clears its world. */
public final class ReconnectEvents {
    private static final int RECONNECT = 0x564901, RANDOM = 0x564902;
    private ServerData lastServer;
    private String error = "";

    private void remember() {
        Minecraft mc = Minecraft.getMinecraft();
        ServerData current = mc.getCurrentServerData();
        if (current == null || mc.isIntegratedServerRunning()) return;
        ServerData copy = new ServerData(current.serverName, current.serverIP, current.isOnLAN());
        copy.setResourceMode(current.getResourceMode());
        lastServer = copy;
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) remember();
    }
    @SubscribeEvent public void open(GuiOpenEvent event) {
        if (event.gui instanceof GuiConnecting || event.gui instanceof GuiDisconnected) remember();
        if (!(event.gui instanceof GuiDisconnected)) error = "";
    }
    @SubscribeEvent public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        boolean pause=event.gui instanceof GuiIngameMenu;
        if (!(event.gui instanceof GuiDisconnected) && !pause) return;
        event.buttonList.removeIf(button->button.id==RECONNECT||button.id==RANDOM);
        if(pause)remember();
        int w = Math.min(140, Math.max(90, event.gui.width - 16)), x = event.gui.width - w - 8;
        int y=pause?8:event.gui.height-52;
        GuiButton reconnect = new GuiButton(RECONNECT, x, y, w, 20, LanguageManager.translate("Reconnect"));
        GuiButton random = new GuiButton(RANDOM, x, y+24, w, 20, LanguageManager.translate("Random username"));
        reconnect.enabled = random.enabled = lastServer != null && (!pause || remoteWorld());
        if(pause){reconnect=new dev.vibe.ui.menu.VibeMenuButton(reconnect);random=new dev.vibe.ui.menu.VibeMenuButton(random);}
        event.buttonList.add(reconnect); event.buttonList.add(random);
    }
    @SubscribeEvent public void click(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        boolean pause=event.gui instanceof GuiIngameMenu;
        if ((!(event.gui instanceof GuiDisconnected) && !pause) || event.button == null
                || (event.button.id != RECONNECT && event.button.id != RANDOM)) return;
        event.setCanceled(true);
        if(pause){if(!remoteWorld())return;remember();}
        if (lastServer == null || !event.button.enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        ServerData destination=lastServer;
        // AccountManager deliberately rejects live-world session changes. Finish
        // the normal disconnect before switching identity or starting a new connection.
        if(mc.theWorld!=null){mc.theWorld.sendQuittingDisconnectingPacket();mc.loadWorld(null);}
        if (event.button.id == RANDOM) {
            String name;
            do { name = RandomUsername.generate(); } while (name.equals(mc.getSession().getUsername()));
            if (!Vibe.getInstance().getAccountManager().useTemporaryOffline(name)) {
                error = Vibe.getInstance().getAccountManager().getStatus();
                mc.displayGuiScreen(new GuiDisconnected(new GuiMultiplayer(new GuiMainMenu()),"disconnect.genericReason",new net.minecraft.util.ChatComponentText(error)));return;
            }
            if (Vibe.getInstance().getStatistics() != null) Vibe.getInstance().getStatistics().recordRandomCracked(name);
        }
        dev.vibe.ui.server.MinibloxConnectGui.connect(new GuiMultiplayer(new GuiMainMenu()), mc, destination,
                Vibe.getInstance().getServerBrowser());
    }
    private static boolean remoteWorld(){Minecraft mc=Minecraft.getMinecraft();return mc.theWorld!=null&&!mc.isIntegratedServerRunning()&&mc.getCurrentServerData()!=null;}
    @SubscribeEvent public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiDisconnected) || error.isEmpty()) return;
        Minecraft.getMinecraft().fontRendererObj.drawSplitString(error, 8, event.gui.height - 80,
                Math.max(40, event.gui.width - 164), 0xFFFF7777);
    }
}
