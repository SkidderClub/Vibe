package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.event.MainMenuEvents;
import dev.vibe.path.PathTestFixture;
import dev.vibe.server.*;
import dev.vibe.ui.menu.MainMenuShaderManager;
import dev.vibe.ui.server.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.util.*;
import net.minecraftforge.client.event.GuiOpenEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Real server rows, input bounds and servers.dat persistence, without opening a socket. */
public final class ServerBrowserRenderCheck {
    public static void verify(Minecraft mc, Path output) throws Exception {
        Path root = Files.createTempDirectory(output, "server-browser-fixture-");
        java.io.File oldDir = mc.mcDataDir;
        PathTestFixture.set(Minecraft.class, mc, "mcDataDir", root.toFile());
        PathTestFixture.set(Minecraft.class, mc, "session", new Session("Alex", "11111111111111111111111111111111", "", "legacy"));
        ServerBrowserEvents joins = new ServerBrowserEvents(mc, root.resolve("vibe/server-results.json"));
        PathTestFixture.set(Vibe.class, Vibe.getInstance(), "serverBrowser", joins);
        ServerJoinHistory history = (ServerJoinHistory) get(ServerBrowserEvents.class, joins, "history");
        history.record(ServerBrowserEvents.accountKey(mc.getSession()), "play.example.net", ServerJoinHistory.Status.JOINED, 1792915200000L, "Joined successfully");
        history.record(ServerBrowserEvents.accountKey(mc.getSession()), "pvp.example.net", ServerJoinHistory.Status.FAILED, 1792915200000L, "You are banned from this server.");
        ServerList list = new ServerList(mc);
        String[][] examples = {{"Example Network", "play.example.net", "\u00a7bA new season starts today", "248 / 500", "42"},
                {"PvP Practice", "pvp.example.net", "\u00a77Duels, ranked matches and practice", "96 / 200", "28"},
                {"Our survival world", "survival.example.net", "\u00a7aBuild something worth coming back to.", "7 / 20", "63"},
                {"Weekend creative", "creative.example.net", "\u00a7dAn open canvas for your next idea", "12 / 50", "86"},
                {"Minigames", "games.example.net", "\u00a7eOne more round?", "150 / 300", "51"},
                {"Old worlds", "archive.example.net", "Cannot reach server", "", "-1"}};
        for (String[] item : examples) {
            ServerData data = new ServerData(item[0], item[1], false); data.serverMOTD = item[2]; data.populationInfo = item[3]; data.pingToServer = Long.parseLong(item[4]); list.addServerData(data);
        }
        list.saveServerList();
        MainMenuShaderManager shaders = new MainMenuShaderManager();
        GuiOpenEvent open = new GuiOpenEvent(new GuiMultiplayer(new GuiMainMenu())); new MainMenuEvents().onGuiOpen(open);
        if (!(open.gui instanceof ServerBrowserGui)) throw new AssertionError("Native multiplayer entry point was not replaced");
        try {
            for (int[] size : new int[][] {{960,540},{480,300},{320,240}}) {
                ServerBrowserGui screen = new ServerBrowserGui(new GuiMainMenu(), shaders, joins, false);
                set(screen, "saved", list); setup(mc, screen, size[0], size[1]);
                List<ServerData> servers = servers(screen); set(screen, "selected", servers.get(0));
                capture(mc, screen, output.resolve("server-browser-" + size[0] + ".png"), -1, -1);
                for (GuiButton button : buttons(screen)) if (button.visible && (button.xPosition < 0 || button.yPosition < 0 || button.xPosition + button.width > size[0] || button.yPosition + button.height > size[1]))
                    throw new AssertionError("Server button outside viewport: " + size[0] + "/" + button.id);
                List<GuiButton> footer=new ArrayList<>();for(GuiButton b:buttons(screen))if(b.id<=6)footer.add(b);
                footer.sort(Comparator.comparingInt((GuiButton b)->b.yPosition).thenComparingInt(b->b.xPosition));
                int[] order={4,2,1,5,6,3,0};for(int i=0;i<order.length;i++)if(footer.get(i).id!=order[i]||footer.get(i).height!=20)throw new AssertionError("Vanilla footer arrangement changed");
                if (size[0] == 960) {
                    // Begin drag at row 0, drop after row 2. Native NBT must change only on release.
                    ServerBrowserLayout l = new ServerBrowserLayout(960,540); int x = l.listX + 70, y = l.listY + 25;
                    invoke(screen, "mouseClicked", new Class<?>[] {int.class,int.class,int.class}, x,y,0);
                    invoke(screen, "mouseClickMove", new Class<?>[] {int.class,int.class,int.class,long.class}, x,y+l.step*2,0,100L);
                    if (!new ServerList(mc).getServerData(0).serverName.equals("Example Network")) throw new AssertionError("Drag persisted before release");
                    capture(mc, screen, output.resolve("server-browser-drag.png"), x,y+l.step*2);
                    invoke(screen, "mouseReleased", new Class<?>[] {int.class,int.class,int.class}, x,y+l.step*2,0);
                    ServerList reloaded = new ServerList(mc);
                    if (!reloaded.getServerData(2).serverName.equals("Example Network")) throw new AssertionError("Drag was not persisted to native servers.dat");
                    // Release outside must cancel, including the preview.
                    capture(mc, screen, output.resolve("server-browser-reordered.png"), -1,-1);
                    invoke(screen, "mouseClicked", new Class<?>[] {int.class,int.class,int.class}, x,y,0);
                    invoke(screen, "mouseClickMove", new Class<?>[] {int.class,int.class,int.class,long.class}, x,y+l.step*2,0,100L);
                    invoke(screen, "mouseReleased", new Class<?>[] {int.class,int.class,int.class}, 0,0,0);
                    if (!new ServerList(mc).getServerData(0).serverName.equals(reloaded.getServerData(0).serverName)) throw new AssertionError("Cancelled drag persisted");
                    set(screen, "deleting", true); capture(mc, screen, output.resolve("server-browser-delete.png"), -1,-1); set(screen,"deleting",false);
                    set(screen, "selected", servers.get(0)); capture(mc, screen, output.resolve("server-browser-failed.png"), -1,-1);
                    // The same server changes badges immediately when the account changes.
                    PathTestFixture.set(Minecraft.class, mc, "session", new Session("Steve", "22222222222222222222222222222222", "", "legacy"));
                    if (joins.result(servers.get(2)).status != ServerJoinHistory.Status.UNKNOWN) throw new AssertionError("Account history leaked");
                    capture(mc, screen, output.resolve("server-browser-other-account.png"), -1,-1);
                    PathTestFixture.set(Minecraft.class, mc, "session", new Session("Alex", "11111111111111111111111111111111", "", "legacy"));
                    set(screen, "query", "No matching server"); ((GuiTextField)get(ServerBrowserGui.class,screen,"search")).setText("No matching server"); invoke(screen, "updateRows", new Class<?>[0]);
                    capture(mc, screen, output.resolve("server-browser-filter-empty.png"), -1,-1);
                }
                screen.onGuiClosed();
            }
            // Real scaled GUI projection must use the same logical bounds for rendering and input.
            ServerBrowserGui scaled = new ServerBrowserGui(new GuiMainMenu(),shaders,joins,false);set(scaled,"saved",list);
            mc.displayWidth=960;mc.displayHeight=540;mc.gameSettings.guiScale=2;
            scaled.setWorldAndResolution(mc,480,270);mc.currentScreen=scaled;set(scaled,"selected",servers(scaled).get(0));
            capture(mc,scaled,output.resolve("server-browser-scale2.png"),-1,-1);scaled.onGuiClosed();
            ServerList large=new ServerList(mc);
            for(int i=0;i<1000;i++){ServerData data=new ServerData("Server "+i,"server"+i+".example.net",false);data.serverMOTD="A large saved server list";data.pingToServer=35;large.addServerData(data);}
            ServerBrowserGui virtualized=new ServerBrowserGui(new GuiMainMenu(),shaders,joins,false);set(virtualized,"saved",large);setup(mc,virtualized,960,540);
            capture(mc,virtualized,output.resolve("server-browser-large-list.png"),-1,-1);
            ServerBrowserLayout bounds=new ServerBrowserLayout(960,540);int limit=(bounds.listH+bounds.step-1)/bounds.step+3;
            if((Integer)get(ServerBrowserGui.class,virtualized,"lastRowsDrawn")>limit||((Map<?,?>)get(ServerBrowserGui.class,virtualized,"rowVisuals")).size()>limit)
                throw new AssertionError("The browser rendered or formatted offscreen rows");
            set(virtualized,"scroll",700F*bounds.step);set(virtualized,"targetScroll",700F*bounds.step);
            capture(mc,virtualized,output.resolve("server-browser-large-scrolled.png"),-1,-1);
            if(((Map<?,?>)get(ServerBrowserGui.class,virtualized,"positions")).size()>limit*2)throw new AssertionError("Offscreen rows were animated");
            long[] samples=new long[80];
            for(int i=0;i<samples.length;i++){ClientChangesRenderCheck.frame(960,540);long start=System.nanoTime();virtualized.drawScreen(-1,-1,0);GL11.glFinish();samples[i]=System.nanoTime()-start;}
            Arrays.sort(samples);String timing=String.format(Locale.ROOT,"1006 saved servers; %s rows drawn; p50 %.3f ms, p95 %.3f ms (native GL, local fixture).",get(ServerBrowserGui.class,virtualized,"lastRowsDrawn"),samples[40]/1e6,samples[76]/1e6);
            Files.write(output.resolve("server-browser-performance.txt"),Collections.singleton(timing));System.out.println(timing);virtualized.onGuiClosed();
        } finally { PathTestFixture.set(Minecraft.class,mc,"mcDataDir",oldDir); mc.currentScreen=null; }
        System.out.println("Custom server browser: desktop/compact/minimum layout, native drag persistence/cancel, modal, account isolation and real GL frames passed.");
    }
    private static void setup(Minecraft mc, GuiScreen screen, int w, int h) throws Exception {
        mc.displayWidth=w; mc.displayHeight=h; mc.gameSettings.guiScale=1;
        ClientChangesRenderCheck.frame(w,h); screen.setWorldAndResolution(mc,w,h); mc.currentScreen=screen;
    }
    private static void capture(Minecraft mc, GuiScreen screen, Path path, int mx, int my) throws Exception {
        screen.updateScreen();
        for (int i=0;i<7;i++) { ClientChangesRenderCheck.frame(mc.displayWidth,mc.displayHeight); screen.drawScreen(mx,my,0); Thread.sleep(30); }
        int w=mc.displayWidth,h=mc.displayHeight; ByteBuffer pixels=BufferUtils.createByteBuffer(w*h*4);
        GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int p=((h-y-1)*w+x)*4; image.setRGB(x,y,0xFF000000|(pixels.get(p)&255)<<16|(pixels.get(p+1)&255)<<8|pixels.get(p+2)&255);}
        ImageIO.write(image,"png",path.toFile());
        if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("Browser GL error: "+path);
    }
    @SuppressWarnings("unchecked") private static List<ServerData> servers(ServerBrowserGui gui) throws Exception { return (List<ServerData>) get(ServerBrowserGui.class,gui,"servers"); }
    @SuppressWarnings("unchecked") private static List<GuiButton> buttons(GuiScreen gui) throws Exception { return (List<GuiButton>) get(GuiScreen.class,gui,"buttonList"); }
    private static Object get(Class<?> type,Object object,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void set(Object object,String name,Object value)throws Exception{PathTestFixture.set(ServerBrowserGui.class,object,name,value);}
    private static void invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(target,args);}
}
