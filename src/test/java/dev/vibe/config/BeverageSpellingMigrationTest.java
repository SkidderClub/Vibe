package dev.vibe.config;
import dev.vibe.path.PathTestFixture;
import dev.vibe.module.impl.visual.BeverageNotificationModule;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
public class BeverageSpellingMigrationTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void legacySpellingKeepsKeybindAndModuleSettings()throws Exception{try(PathTestFixture f=new PathTestFixture()){
        PathTestFixture.set(dev.vibe.Vibe.class,f.vibe,"minecraft",f.mc);PathTestFixture.set(net.minecraft.client.Minecraft.class,f.mc,"session",new net.minecraft.util.Session("Alex","11111111111111111111111111111111","","legacy"));
        BeverageNotificationModule module=new BeverageNotificationModule();f.modules.registerDynamic(module);VibeConfig config=new VibeConfig(temp.newFolder());Files.createDirectories(config.getDirectory().toPath());
        Files.write(config.getDirectory().toPath().resolve("old.json"),Collections.singleton("{\"modules\":[{\"id\":\"baveragenotification\",\"key\":24,\"settings\":{\"Chat Message\":false}}]}"),StandardCharsets.UTF_8);
        assertTrue(config.load("old",f.modules));assertEquals(24,module.getKey());assertFalse(module.chat.isEnabled());assertEquals("Beverage Notification",module.getRawName());assertEquals("beveragenotification",module.getId());
        assertTrue(config.save("old",f.modules));String saved=new String(Files.readAllBytes(config.getDirectory().toPath().resolve("old.json")),StandardCharsets.UTF_8);assertFalse(saved.contains("baveragenotification"));
    }}
}
