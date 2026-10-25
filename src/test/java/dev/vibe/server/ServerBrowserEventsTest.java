package dev.vibe.server;

import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.server.ServerBrowserGui;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.util.*;
import net.minecraftforge.client.event.GuiOpenEvent;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static dev.vibe.server.ServerJoinHistory.Status.*;

public class ServerBrowserEventsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Minecraft old, mc;
    private ServerBrowserEvents events;
    private final ServerData server = new ServerData("Example", "example.net", false);
    private Path file;
    @Before public void setup() throws Exception {
        old = Minecraft.getMinecraft(); mc = PathTestFixture.allocate(Minecraft.class);
        PathTestFixture.set(Minecraft.class, null, "theMinecraft", mc);
        session("Alex", "11111111111111111111111111111111");
        file = temp.getRoot().toPath().resolve("history.json"); events = new ServerBrowserEvents(mc,file);
    }
    @After public void cleanup() throws Exception { PathTestFixture.set(Minecraft.class,null,"theMinecraft",old); }
    private void session(String name,String id)throws Exception { PathTestFixture.set(Minecraft.class,mc,"session",new Session(name,id,"","legacy")); }
    @Test public void nativeDisconnectedMessageIsRecordedForTheCapturedAccount() throws Exception {
        String original = ServerBrowserEvents.accountKey(mc.getSession()); events.prepare(server);
        events.onGui(new GuiOpenEvent(PathTestFixture.allocate(GuiConnecting.class)));
        session("Steve","22222222222222222222222222222222");
        events.onGui(new GuiOpenEvent(disconnected("You are banned for 30 days")));
        assertEquals(UNKNOWN,events.result(server).status);
        ServerJoinHistory.Result stored = new ServerJoinHistory(file).get(original,server.serverIP);
        assertEquals(FAILED,stored.status); assertEquals("You are banned for 30 days",stored.reason);
    }
    @Test public void returningToBrowserCancelsWithoutMarkingAFailure() {
        events.prepare(server);
        events.onGui(new GuiOpenEvent(new ServerBrowserGui(new GuiScreen() {},null,events,false)));
        assertEquals(UNKNOWN,events.result(server).status);
        assertFalse(java.nio.file.Files.exists(file));
    }
    @Test public void vanillaReconnectAlsoStartsAnAttempt() throws Exception {
        PathTestFixture.set(Minecraft.class,mc,"currentServerData",server);
        events.onGui(new GuiOpenEvent(PathTestFixture.allocate(GuiConnecting.class)));
        assertEquals(CONNECTING,events.result(server).status);
        events.onGui(new GuiOpenEvent(disconnected("Connection refused")));
        assertEquals(FAILED,events.result(server).status);
    }
    private static GuiDisconnected disconnected(String text) throws Exception {
        GuiDisconnected screen = PathTestFixture.allocate(GuiDisconnected.class);
        for (java.lang.reflect.Field field : GuiDisconnected.class.getDeclaredFields()) if (field.getType() == IChatComponent.class) {
            field.setAccessible(true); field.set(screen,new ChatComponentText(text));
        }
        return screen;
    }
}

