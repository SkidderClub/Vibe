package dev.vibe.ui.menu;

import dev.vibe.path.PathTestFixture;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class MenuThemesTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void customThemesAndShaderColoursRoundTripWithoutLosingTheirModes()throws Exception{
        Path dir=temp.newFolder().toPath();MenuThemeStore store=new MenuThemeStore(dir);MenuTheme theme=new MenuTheme();theme.name="Northern lights";theme.shader="polarlights.frag";theme.colorMode="Fade";theme.first=0xFF00BB88;theme.second=0xFF6688FF;theme.speed=.7;theme.validate();store.custom.add(theme);store.shaderColors.put(theme.shader,0xFF77DD99);store.save();
        MenuThemeStore restored=new MenuThemeStore(dir);assertEquals(1,restored.custom.size());MenuTheme value=restored.custom.get(0);assertEquals(theme.id,value.id);assertEquals(theme.shader,value.shader);assertEquals("Fade",value.colorMode);assertEquals(.7,value.speed,0);assertEquals(theme.first,value.first);assertEquals((Integer)0xFF77DD99,restored.shaderColors.get(theme.shader));
    }
    @Test public void selectionOwnsShaderAndColoursAndSurvivesRestart()throws Exception{
        try(PathTestFixture fixture=new PathTestFixture()){
            Path dir=temp.newFolder().toPath();PathTestFixture.set(Minecraft.class,fixture.mc,"mcDataDir",dir.toFile());MainMenuShaderManager shaders=new MainMenuShaderManager();shaders.getPresets();
            MenuTheme theme=new MenuTheme();theme.name="My fade";theme.shader="purplefade.frag";theme.colorMode="Fade";theme.first=0xFFFF88AA;theme.second=0xFF88AAFF;theme.background=false;
            assertTrue(MenuThemes.saveCustom(theme,shaders));assertEquals(theme.shader,shaders.getSelected());assertFalse(shaders.isEnabled());
            MenuPalette startup=MenuPalette.read(dir);assertTrue(startup.animated());assertNotEquals(startup.sample(0).accent,startup.sample(6000).accent);
            MainMenuShaderManager restart=new MainMenuShaderManager();restart.getPresets();assertEquals(theme.shader,restart.getSelected());assertFalse(restart.isEnabled());assertEquals(theme.id,MenuThemes.active(restart).id);
            restart.select("matrix.frag");assertTrue(MenuThemes.active(restart).automatic());assertEquals("matrix.frag",MenuThemes.active(restart).shader);
        }
    }
    @Test public void thumbnailColourIgnoresBlackBackgroundAndSupportsMonochrome(){
        ByteBuffer data=ByteBuffer.allocate(400);for(int i=0;i<100;i++)data.putInt(0x000000FF);for(int i=0;i<10;i++){data.put(i*4,(byte)20);data.put(i*4+1,(byte)180);data.put(i*4+2,(byte)230);}
        int color=ShaderThemeColors.sample(data);assertTrue((color&255)>(color>>16&255));assertTrue((color>>8&255)>(color>>16&255));
        data.clear();for(int i=0;i<100;i++)data.putInt(0x222222FF);assertEquals(MenuPalette.GRAPHITE.accent,ShaderThemeColors.sample(data));
    }
    @Test public void legacyAndCustomStartupColoursRemainReadableAndRainbowMoves()throws Exception{
        Path dir=temp.newFolder().toPath();Files.createDirectories(dir.resolve("vibe"));Files.write(dir.resolve("vibe/menu.properties"),"theme=ROSE\n".getBytes(StandardCharsets.UTF_8));assertSame(MenuPalette.ROSE,MenuPalette.read(dir));
        Files.write(dir.resolve("vibe/menu.properties"),"color1=FF99AAFF\ncolor2=FF88FFAA\ncolorMode=Rainbow\ncolorSpeed=1\nname=Rainbow\n".getBytes(StandardCharsets.UTF_8));MenuPalette rainbow=MenuPalette.read(dir);assertNotEquals(rainbow.sample(0).accent,rainbow.sample(4000).accent);
        assertEquals(0xFF123456,MenuPalette.custom("Solid",0xFF123456,0xFFFFFFFF,"Solid",1).sample(2000).accent);
    }
    @Test public void invalidShaderPathIsNotATheme(){MenuTheme t=new MenuTheme();t.shader="../escape.frag";try{t.validate();fail("Accepted a shader outside its folder");}catch(IllegalArgumentException expected){}}
    @Test public void menuStylesPersistAndIngameLavaUsesRainbow()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            Path dir=temp.newFolder().toPath();PathTestFixture.set(Minecraft.class,f.mc,"mcDataDir",dir.toFile());MainMenuShaderManager shaders=new MainMenuShaderManager();shaders.getPresets();
            for(String style:new String[]{"Minimalistic","Skeet"}){MenuTheme t=MenuTheme.builtin(style);assertTrue(MenuThemes.activate(t,shaders));assertEquals(style,MenuThemes.active(shaders).menuStyle);
                assertFalse(MenuThemes.simpleMenus());assertNotEquals(MenuThemes.lavaPalette().accent,t.first);f.mc.theWorld=null;assertTrue(MenuThemes.simpleMenus());f.mc.theWorld=f.world;
                java.util.Properties p=new java.util.Properties();try(java.io.InputStream in=Files.newInputStream(dir.resolve("vibe/menu.properties"))){p.load(in);}assertEquals(style,p.getProperty("menuStyle"));}
            MenuTheme original=MenuThemes.active(shaders);MenuTheme draft=new MenuTheme();draft.first=0xFF123456;MenuThemes.preview(draft);MenuThemes.frame();assertEquals(draft.first,MenuThemes.current().accent);assertEquals(original.id,MenuThemes.active(shaders).id);MenuThemes.preview(null);
        }
    }
    @Test public void thumbnailQueueIncludesVisibleAndAdjacentRowsInOrder()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            MenuThemesGui gui=new MenuThemesGui(null,new MainMenuShaderManager());
            PathTestFixture.set(MenuThemesGui.class,gui,"columns",2);PathTestFixture.set(MenuThemesGui.class,gui,"visibleRows",2);PathTestFixture.set(MenuThemesGui.class,gui,"scrollRow",2);
            java.lang.reflect.Field field=MenuThemesGui.class.getDeclaredField("entries");field.setAccessible(true);java.util.List<MenuTheme> entries=(java.util.List<MenuTheme>)field.get(gui);for(int i=0;i<20;i++)entries.add(MenuTheme.automatic("test"+i+".frag",0xFFFFFFFF));
            assertEquals(java.util.Arrays.asList("test4.frag","test5.frag","test6.frag","test7.frag","test2.frag","test3.frag","test8.frag","test9.frag"),gui.previewQueue());
        }
    }
}
