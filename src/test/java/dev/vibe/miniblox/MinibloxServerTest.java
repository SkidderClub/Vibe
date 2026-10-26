package dev.vibe.miniblox;

import dev.vibe.path.PathTestFixture;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class MinibloxServerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Minecraft mc;
    @Before public void setup() throws Exception {
        mc = PathTestFixture.allocate(Minecraft.class);
        PathTestFixture.set(Minecraft.class, mc, "mcDataDir", temporary.getRoot());
    }
    @Test public void defaultIsSavedOnceAndMovingSurvivesReload() {
        ServerList list = new ServerList(mc);
        ServerData ordinary = new ServerData("Ordinary", "example.net", false); list.addServerData(ordinary);
        ServerData miniblox = MinibloxServer.ensure(list);
        assertEquals(2, list.countServers()); assertSame(ordinary, list.getServerData(0));
        assertSame(miniblox, MinibloxServer.ensure(list)); assertEquals(2, list.countServers());
        list.func_147413_a(0, miniblox); list.func_147413_a(1, ordinary); list.saveServerList();
        ServerList reloaded = new ServerList(mc); MinibloxServer.ensure(reloaded);
        assertEquals(2, reloaded.countServers()); assertTrue(MinibloxServer.is(reloaded.getServerData(0)));
        assertEquals("example.net", reloaded.getServerData(1).serverIP);
        assertTrue(new File(temporary.getRoot(), "servers.dat").isFile());
    }
    @Test public void matchesReservedAddressWithoutInterceptingUnrelatedHosts() {
        assertTrue(MinibloxServer.is(new ServerData("Renamed", " Miniblox.IO ", false)));
        for (String address : new String[]{"miniblox.io.example.net", "127.0.0.1:25565", "example.net"})
            assertFalse(MinibloxServer.is(new ServerData("Miniblox", address, false)));
        assertFalse(MinibloxServer.is(null));
    }
}
