package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.core.HudChatTransformer;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.path.PathTestFixture;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.*;
import org.junit.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class HudOverlayTest {
    @Rule public org.junit.rules.TemporaryFolder temporary=new org.junit.rules.TemporaryFolder();
    @Test public void tabToggleUsesPressEdgesAndResetsAcrossFocusWorldAndModeChanges(){
        TabKeyState state=new TabKeyState();Object world=new Object();
        assertFalse(state.update(false,true,true,world));assertTrue(state.update(true,true,true,world));
        assertTrue(state.update(true,true,true,world));assertTrue(state.update(false,true,true,world));assertFalse(state.update(true,true,true,world));
        assertFalse(state.update(false,true,false,world));assertFalse(state.update(false,true,true,new Object()));
        assertTrue(state.update(true,false,true,world));assertFalse(state.update(false,false,true,world));
    }
    @Test public void newWidgetsAreOptionalAndTheirDistinctThemesScalesAndPositionsSurviveLayouts()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            language(f.mc);
            f.mc.displayWidth=960;f.mc.displayHeight=540;f.mc.gameSettings.guiScale=1;HudModule hud=new HudModule();f.modules.registerDynamic(hud);
            HudManager source=new HudManager(temporary.newFolder());HudManager restored=new HudManager(temporary.newFolder());
            for(String id:new String[]{HudManager.CHAT,HudManager.HOTBAR,HudManager.CUSTOM_HEALTH,HudManager.TAB}){
                assertTrue(hud.getHudElements().hasOption(id));assertFalse(hud.getHudElements().isSelected(id));
                HudManager.HudElement e=source.getElement(id);e.setBounds(0,0,220,48);e.moveTo(123,100,new ScaledResolution(f.mc));e.setScale(1.4F);e.setTheme(id.equals(HudManager.HOTBAR)?"Fortnite":"LiquidGlass");
            }
            restored.fromJson(source.toJson());
            for(String id:new String[]{HudManager.CHAT,HudManager.HOTBAR,HudManager.CUSTOM_HEALTH,HudManager.TAB}){assertEquals(source.getElement(id).getTheme(),restored.getElement(id).getTheme());assertEquals(1.4F,restored.getElement(id).getScale(),0);assertEquals(123,restored.getElement(id).left(new ScaledResolution(f.mc),220));}
            assertTrue(hud.chat.all.contains(hud.chat.width));assertTrue(hud.chat.all.contains(hud.chat.height));assertNotSame(hud.chat.width,hud.chat.height);
        }
    }
    @Test public void transformedNativeChatUsesCustomWrappingAndPreservesStyledClickTargets()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            language(f.mc);
            f.mc.displayWidth=960;f.mc.displayHeight=600;f.mc.gameSettings.guiScale=1;HudModule hud=new HudModule();f.modules.registerDynamic(hud);hud.getHudElements().toggle(HudManager.CHAT);
            HudManager manager=new HudManager(temporary.newFolder());PathTestFixture.set(Vibe.class,f.vibe,"hudManager",manager);
            HudManager.HudElement e=manager.getElement(HudManager.CHAT);e.setBounds(20,200,320,150);hud.chat.width.setValue(320D);hud.chat.height.setValue(150D);
            FontRenderer font=PathTestFixture.allocate(FontRenderer.class);int[] widths=new int[256];Arrays.fill(widths,6);PathTestFixture.set(FontRenderer.class,font,"charWidth",widths);f.mc.fontRendererObj=font;f.mc.currentScreen=new GuiChat();
            IChatComponent link=new ChatComponentText("Open").setChatStyle(new ChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/help")));
            IChatComponent message=new ChatComponentText("").appendText("See ").appendSibling(link).appendText(" now");
            Field shown=HudChat.class.getDeclaredField("shown");shown.setAccessible(true);@SuppressWarnings("unchecked")List<IChatComponent> rows=(List<IChatComponent>)shown.get(null);rows.clear();rows.add(message);
            e.setBounds(20,200,320,23);PathTestFixture.set(HudChat.class,null,"shownHeight",23);PathTestFixture.set(HudChat.class,null,"textOffset",0F);
            int mouseY=600-1-(200+23-6-5);
            IChatComponent hovered=(IChatComponent)HudChat.component(20+6+font.getStringWidth("See ")+2,mouseY);assertNotNull(hovered);assertEquals("/help",hovered.getChatStyle().getChatClickEvent().getValue());
            IChatComponent plain=(IChatComponent)HudChat.component(28,mouseY);assertNotNull(plain);assertNull(plain.getChatStyle().getChatClickEvent());
            byte[] raw;try(InputStream in=getClass().getClassLoader().getResourceAsStream("net/minecraft/client/gui/GuiNewChat.class");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] block=new byte[4096];int n;while((n=in.read(block))!=-1)out.write(block,0,n);raw=out.toByteArray();}
            HudChatTransformer transformer=new HudChatTransformer();byte[] patched=transformer.transform("net.minecraft.client.gui.GuiNewChat","net.minecraft.client.gui.GuiNewChat",raw);assertArrayEquals(patched,transformer.transform("net.minecraft.client.gui.GuiNewChat","net.minecraft.client.gui.GuiNewChat",patched));
            ClassNode node=new ClassNode();new ClassReader(patched).accept(node,0);for(Object value:node.methods)new Analyzer(new BasicVerifier()).analyze(node.name,(MethodNode)value);
            Class<?> nativeChat=new ClassLoader(getClass().getClassLoader()){Class<?> define(byte[] bytes){return defineClass("net.minecraft.client.gui.GuiNewChat",bytes,0,bytes.length);}}.define(patched);
            Object chat=nativeChat.getConstructor(Minecraft.class).newInstance(f.mc);
            assertEquals(308,nativeChat.getMethod("getChatWidth").invoke(chat));assertEquals(12,nativeChat.getMethod("getLineCount").invoke(chat));
            hud.chat.width.setValue(420D);e.setScale(1.5F);assertEquals(612,nativeChat.getMethod("getChatWidth").invoke(chat));assertEquals(1.5F,(Float)nativeChat.getMethod("getChatScale").invoke(chat),0);
            hud.getHudElements().toggle(HudManager.CHAT);assertFalse(HudChat.active());assertEquals(77,HudChat.width(77));rows.clear();
        }
    }
    private static void language(Minecraft mc)throws Exception{PathTestFixture.set(Minecraft.class,mc,"mcLanguageManager",new net.minecraft.client.resources.LanguageManager(new net.minecraft.client.resources.data.IMetadataSerializer(),"en_US"));}
}
