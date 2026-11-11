package dev.vibe.chatter;

import dev.vibe.Vibe;
import dev.vibe.config.VibeConfig;
import dev.vibe.feedback.ModuleFeedback;
import dev.vibe.feedback.ModuleNotifications;
import dev.vibe.module.Category;
import dev.vibe.module.impl.client.ChatterModule;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.path.PathTestFixture;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.util.Session;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ChatterModuleTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void messageMenuIsAClientActionAndProfileLoadingCannotOpenIt()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            ChatterModule module=new ChatterModule();assertEquals(Category.CLIENT,module.getCategory());assertFalse(module.isEnabled());
            VibeConfig config=new VibeConfig(temporary.getRoot());PathTestFixture.set(Vibe.class,f.vibe,"config",config);
            PathTestFixture.set(VibeConfig.class,config,"loading",true);module.openMessageMenu.setEnabled(true);
            assertFalse(module.openMessageMenu.isEnabled());assertFalse((Boolean)field("menuRequested").get(module));
            PathTestFixture.set(VibeConfig.class,config,"loading",false);GuiChat chat=new GuiChat();f.mc.currentScreen=chat;
            module.openMessageMenu.setEnabled(true);assertFalse(module.openMessageMenu.isEnabled());assertTrue((Boolean)field("menuRequested").get(module));
            assertSame("Open after vanilla finishes Enter handling",chat,f.mc.currentScreen);assertNull(field("menuParent").get(module));
        }
    }
    @Test public void disablingTheModuleCancelsQueuedActions()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            ChatterModule module=new ChatterModule();ChatterEngine engine=new ChatterEngine(new ChatterStore(temporary.getRoot().toPath()));field("engine").set(module,engine);
            module.setEnabled(true);ChatterEngine.Connection connection=new ChatterEngine.Connection(){public Object identity(){return this;}public String name(){return "Steve";}public String server(){return "example.org";}public void message(String text){fail("Pending message survived disable");}public void leave(){fail();}};
            engine.receive("/login",connection,0);assertEquals(1,engine.pending());module.setEnabled(false);assertEquals(0,engine.pending());engine.tick(connection,1000);
        }
    }
    @Test public void chatAndTitleMatchesUseConfiguredNotificationsAndRespectTheHudSelection()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            PathTestFixture.set(Minecraft.class,f.mc,"session",new Session("Self","","","legacy"));
            PathTestFixture.set(Minecraft.class,f.mc,"currentServerData",new ServerData("Test","example.org",false));
            PathTestFixture.set(EntityPlayerSP.class,f.player,"sendQueue",PathTestFixture.allocate(NetHandlerPlayClient.class));
            ModuleFeedback feedback=new ModuleFeedback();PathTestFixture.set(Vibe.class,f.vibe,"feedback",feedback);
            HudModule hud=new HudModule();f.modules.registerDynamic(hud);hud.notifications.duration.setValue(5.0);
            ChatterModule module=new ChatterModule();f.modules.registerDynamic(module);
            ChatterEngine engine=new ChatterEngine(new ChatterStore(temporary.getRoot().toPath()));field("engine").set(module,engine);
            module.setEnabled(true);feedback.start();
            module.receive("Please /login with your password");module.receive("Please /login with your password");
            List<ModuleNotifications.Entry> entries=feedback.notifications().active(System.nanoTime()/1000000L);
            assertEquals(1,entries.size());ModuleNotifications.Entry entry=entries.get(0);
            assertTrue(entry.message);assertEquals("Chatter",entry.name);assertEquals("Chat matched: message queued",entry.detail);assertEquals(5000,entry.duration);
            assertTrue("Detection must not bypass the action delay",f.player.sentMessages.isEmpty());assertTrue(engine.data().passwords.isEmpty());
            ChatterData.Rule title=new ChatterData.Rule("Victory",ChatterData.Action.INTERACT,"",300,600,false);title.source=ChatterData.Source.TITLE;engine.data().rules.add(title);
            ChatterModule.titleHook("Victory",null);entries=feedback.notifications().active(System.nanoTime()/1000000L);
            assertEquals(2,entries.size());assertEquals("Title matched: item interaction queued",entries.get(1).detail);
            feedback.notifications().clear();hud.getHudElements().toggle("notifications");
            module.receive("Please /register");assertEquals(3,engine.pending());assertTrue(feedback.notifications().active(System.nanoTime()/1000000L).isEmpty());
            hud.getHudElements().toggle("notifications");module.setEnabled(false);feedback.notifications().clear();
            module.receive("Please /login");ChatterModule.titleHook("Victory",null);assertTrue(feedback.notifications().active(System.nanoTime()/1000000L).isEmpty());
        }
    }
    private static Field field(String name)throws Exception{Field field=ChatterModule.class.getDeclaredField(name);field.setAccessible(true);return field;}
}
