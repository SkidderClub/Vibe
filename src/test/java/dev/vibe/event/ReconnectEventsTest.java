package dev.vibe.event;

import dev.vibe.path.PathTestFixture;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReconnectEventsTest {
    @Test public void deathExploreLivesInTheTopRightAndKeepsVanillaActions()throws Exception{try(PathTestFixture f=new PathTestFixture()){
        ReconnectEvents events=new ReconnectEvents();GuiGameOver screen=new GuiGameOver();screen.width=640;screen.height=360;List<GuiButton> buttons=new ArrayList<>();GuiButton respawn=new GuiButton(0,200,180,"Respawn");buttons.add(respawn);
        events.init(new GuiScreenEvent.InitGuiEvent.Post(screen,buttons));assertSame(respawn,buttons.get(0));assertEquals(2,buttons.size());GuiButton explore=buttons.get(1);assertEquals("Explore",explore.displayString);assertEquals(8,explore.yPosition);assertEquals(632,explore.xPosition+explore.width);assertTrue(explore instanceof dev.vibe.ui.menu.AccountScreenStyle.Button);
        events.init(new GuiScreenEvent.InitGuiEvent.Post(screen,buttons));assertEquals(2,buttons.size());
    }}
    @Test public void pauseMenuReusesReconnectAndRandomUsernameAndKeepsVanillaButtons()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            ReconnectEvents events=new ReconnectEvents();f.mc.setServerData(new ServerData("Test","example.invalid:25565",false));GuiIngameMenu menu=new GuiIngameMenu();menu.width=640;menu.height=360;
            List<GuiButton> buttons=new ArrayList<>();buttons.add(new GuiButton(1,200,100,"Disconnect"));events.init(new GuiScreenEvent.InitGuiEvent.Post(menu,buttons));assertEquals(3,buttons.size());assertEquals(1,buttons.get(0).id);
            assertEquals("Reconnect",buttons.get(1).displayString);assertEquals("Random username",buttons.get(2).displayString);
            for(int i=1;i<3;i++){GuiButton b=buttons.get(i);assertTrue(b.enabled);assertTrue(b.xPosition>=0&&b.xPosition+b.width<=menu.width&&b.yPosition+b.height<100);}
            f.mc.setServerData(null);List<GuiButton> singleplayer=new ArrayList<>();events.init(new GuiScreenEvent.InitGuiEvent.Post(menu,singleplayer));assertEquals(2,singleplayer.size());for(GuiButton b:singleplayer)assertFalse("Do not reconnect a singleplayer world to an old remote server",b.enabled);
            GuiDisconnected disconnected=PathTestFixture.allocate(GuiDisconnected.class);disconnected.width=640;disconnected.height=360;List<GuiButton> retry=new ArrayList<>();events.init(new GuiScreenEvent.InitGuiEvent.Post(disconnected,retry));for(GuiButton b:retry)assertTrue(b.enabled);
        }
    }
}
