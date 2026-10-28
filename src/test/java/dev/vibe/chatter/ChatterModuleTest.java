package dev.vibe.chatter;

import dev.vibe.Vibe;
import dev.vibe.config.VibeConfig;
import dev.vibe.module.Category;
import dev.vibe.module.impl.client.ChatterModule;
import dev.vibe.path.PathTestFixture;
import java.lang.reflect.Field;
import net.minecraft.client.gui.GuiChat;
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
    private static Field field(String name)throws Exception{Field field=ChatterModule.class.getDeclaredField(name);field.setAccessible(true);return field;}
}
