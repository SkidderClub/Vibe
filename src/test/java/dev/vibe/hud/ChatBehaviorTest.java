package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.core.HudChatTransformer;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.path.PathTestFixture;
import java.io.*;
import java.lang.reflect.*;
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

public class ChatBehaviorTest {
    @Rule public org.junit.rules.TemporaryFolder temporary=new org.junit.rules.TemporaryFolder();
    private static IChatComponent clicked;
    public static boolean recordClick(IChatComponent value){clicked=value;return value!=null;}

    @Test public void nativeMouseClickUsesThePressPositionAndStyledSpansWithAndWithoutCustomChat()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            HudModule hud=setup(f);ClickHistory history=new ClickHistory(f.mc);RecordingHud overlay=PathTestFixture.allocate(RecordingHud.class);overlay.chat=history;f.mc.ingameGUI=overlay;
            IChatComponent message=new ChatComponentText("").appendSibling(new ChatComponentText("Very bold prefix ").setChatStyle(new ChatStyle().setBold(true)))
                    .appendSibling(new ChatComponentText("[Play again]").setChatStyle(new ChatStyle().setBold(true).setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/play again"))));
            List<ChatLine> nativeRows=rows(history,"drawnChatLines");nativeRows.add(new ChatLine(0,message,0));
            Class<?> nativeChat=clickClass();Object screen=PathTestFixture.allocate(nativeChat);f.mc.currentScreen=(GuiScreen)screen;
            PathTestFixture.set(GuiScreen.class,screen,"mc",f.mc);((GuiScreen)screen).width=960;((GuiScreen)screen).height=600;
            PathTestFixture.set(GuiChat.class,screen,"inputField",new GuiTextField(0,f.mc.fontRendererObj,0,580,950,20));
            Method press=nativeChat.getDeclaredMethod("mouseClicked",int.class,int.class,int.class);press.setAccessible(true);
            int prefix=f.mc.fontRendererObj.getStringWidth("§lVery bold prefix ");int link=f.mc.fontRendererObj.getStringWidth("§l[Play again]");
            int x=3+prefix+link-2,y=600-1-30;
            // Vanilla measures only unstyled text, missing the end of this bold button.
            assertNull(history.nativeComponent(x,30));clicked=null;press.invoke(screen,x,y,0);
            assertNotNull(clicked);assertEquals("/play again",clicked.getChatStyle().getChatClickEvent().getValue());
            assertEquals(Collections.singletonList("/play again"),f.player.sentMessages);
            hud.getHudElements().toggle(HudManager.CHAT);HudManager manager=Vibe.getInstance().getHudManager();HudManager.HudElement e=manager.getElement(HudManager.CHAT);
            Field shown=HudChat.class.getDeclaredField("shown");shown.setAccessible(true);List<IChatComponent> visible=(List<IChatComponent>)shown.get(null);visible.clear();visible.add(message);
            PathTestFixture.set(HudChat.class,null,"shownHeight",23);
            for(int guiScale:new int[]{1,2})for(float scale:new float[]{.75F,1F,1.5F}){
                f.mc.gameSettings.guiScale=guiScale;e.setScale(scale);e.setBounds(20,100,Math.round(320*scale),Math.round(23*scale));
                int gx=Math.round(20+(6+prefix+link-4)*scale),gy=Math.round(100+11*scale);clicked=null;
                press.invoke(screen,gx,gy,0);assertNotNull("scale "+guiScale+" HUD "+scale,clicked);
                assertEquals("/play again",clicked.getChatStyle().getChatClickEvent().getValue());
            }
            assertEquals(7,f.player.sentMessages.size());
            visible.clear();
        }
    }

    @Test public void bothNativeHistoryListsRetainMessagesAcrossRewrapScrollAndExplicitClear()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            HudModule hud=setup(f);f.mc.currentScreen=new GuiChat();String name="net.minecraft.client.gui.GuiNewChat";
            HudChatTransformer transformer=new HudChatTransformer();byte[] patched=transformer.transform(name,name,resource(name));verify(patched);
            assertArrayEquals(patched,transformer.transform(name,name,patched));
            Class<?> nativeChat=new ClassLoader(getClass().getClassLoader()){Class<?> define(){return defineClass(null,patched,0,patched.length);}}.define();
            Object chat=nativeChat.getConstructor(Minecraft.class).newInstance(f.mc);
            Method insert=nativeChat.getDeclaredMethod("setChatLine",IChatComponent.class,int.class,int.class,boolean.class);insert.setAccessible(true);
            assertTrue(hud.chat.infiniteScrolling.isEnabled());
            for(int i=0;i<350;i++)insert.invoke(chat,new ChatComponentText("Message "+i),0,i,false);
            assertEquals(350,rows(chat,"chatLines").size());assertEquals(350,rows(chat,"drawnChatLines").size());
            nativeChat.getMethod("scroll",int.class).invoke(chat,300);Field scroll=nativeChat.getDeclaredField("scrollPos");scroll.setAccessible(true);assertEquals(300,scroll.getInt(chat));
            nativeChat.getMethod("refreshChat").invoke(chat);assertEquals(350,rows(chat,"drawnChatLines").size());
            assertEquals("Message 0",rows(chat,"chatLines").get(349).getChatComponent().getUnformattedText());
            hud.chat.infiniteScrolling.setValue(false);insert.invoke(chat,new ChatComponentText("Limited"),0,351,false);
            assertEquals(100,rows(chat,"chatLines").size());assertEquals(100,rows(chat,"drawnChatLines").size());
            hud.chat.infiniteScrolling.setValue(true);nativeChat.getMethod("clearChatMessages").invoke(chat);
            assertTrue(rows(chat,"chatLines").isEmpty());assertTrue(rows(chat,"drawnChatLines").isEmpty());
        }
    }

    @Test public void rawNamesReceiveAllHooksBeforeForgeRemapsClasses()throws Exception{
        HudChatTransformer transformer=new HudChatTransformer();
        for(String name:new String[]{"net.minecraft.client.gui.GuiNewChat","net.minecraft.client.gui.GuiChat"}){
            ClassNode raw=new ClassNode();new ClassReader(resource(name)).accept(raw,0);
            for(Object value:raw.methods){MethodNode m=(MethodNode)value;
                if(m.name.equals("getChatWidth"))m.name="f";else if(m.name.equals("getChatScale"))m.name="h";
                else if(m.name.equals("getLineCount"))m.name="i";
                else if(m.name.equals("getChatComponent")||m.name.equals("setChatLine")||m.name.equals("mouseClicked"))m.name="a";
            }
            ClassWriter writer=new ClassWriter(0);raw.accept(writer);String alias=name.endsWith("GuiNewChat")?"avt":"awv";
            byte[] patched=transformer.transform(alias,name,writer.toByteArray());assertArrayEquals(patched,transformer.transform(alias,name,patched));verify(patched);
            ClassNode node=new ClassNode();new ClassReader(patched).accept(node,0);int hooks=0;
            for(Object value:node.methods)for(AbstractInsnNode i:((MethodNode)value).instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals("dev/vibe/hud/HudChat"))hooks++;
            assertEquals(name,name.endsWith("GuiNewChat")?6:2,hooks);
        }
    }

    private HudModule setup(PathTestFixture f)throws Exception{
        f.mc.displayWidth=960;f.mc.displayHeight=600;f.mc.gameSettings.guiScale=1;f.mc.gameSettings.chatScale=1;f.mc.gameSettings.chatColours=true;
        PathTestFixture.set(Minecraft.class,f.mc,"mcLanguageManager",new net.minecraft.client.resources.LanguageManager(new net.minecraft.client.resources.data.IMetadataSerializer(),"en_US"));
        FontRenderer font=PathTestFixture.allocate(FontRenderer.class);int[] widths=new int[256];Arrays.fill(widths,6);PathTestFixture.set(FontRenderer.class,font,"charWidth",widths);font.FONT_HEIGHT=9;f.mc.fontRendererObj=font;
        HudModule hud=new HudModule();f.modules.registerDynamic(hud);PathTestFixture.set(Vibe.class,f.vibe,"hudManager",new HudManager(temporary.newFolder()));return hud;
    }
    private static List<ChatLine> rows(Object chat,String name)throws Exception{Class<?> type=chat instanceof GuiNewChat?GuiNewChat.class:chat.getClass();Field field=type.getDeclaredField(name);field.setAccessible(true);return (List<ChatLine>)field.get(chat);}
    private Class<?> clickClass()throws Exception{
        String name="net.minecraft.client.gui.GuiChat";HudChatTransformer transformer=new HudChatTransformer();byte[] patched=transformer.transform(name,name,resource(name));verify(patched);assertArrayEquals(patched,transformer.transform(name,name,patched));
        ClassNode source=new ClassNode();new ClassReader(patched).accept(source,0);
        ClassNode type=new ClassNode();type.version=52;type.access=Opcodes.ACC_PUBLIC;type.name="dev/vibe/hud/NativeChatPress";type.superName="net/minecraft/client/gui/GuiChat";
        for(Object value:source.methods)if(((MethodNode)value).name.equals("mouseClicked"))type.methods.add(value);
        // Execute vanilla's actual RUN_COMMAND action, stubbing only hardware Shift polling.
        ClassNode nativeScreen=new ClassNode();new ClassReader(resource("net.minecraft.client.gui.GuiScreen")).accept(nativeScreen,0);
        for(Object value:nativeScreen.methods){MethodNode handle=(MethodNode)value;if(!handle.name.equals("handleComponentClick"))continue;
            for(AbstractInsnNode i:handle.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).name.equals("isShiftKeyDown"))handle.instructions.set(i,new InsnNode(Opcodes.ICONST_0));
            InsnList record=new InsnList();record.add(new VarInsnNode(Opcodes.ALOAD,1));record.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/vibe/hud/ChatBehaviorTest","recordClick",handle.desc,false));record.add(new InsnNode(Opcodes.POP));handle.instructions.insert(record);type.methods.add(handle);
        }
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(writer);byte[] bytes=writer.toByteArray();
        return new ClassLoader(getClass().getClassLoader()){Class<?> define(){return defineClass(null,bytes,0,bytes.length);}}.define();
    }
    private static void verify(byte[] bytes)throws Exception{ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);for(Object value:node.methods)new Analyzer(new BasicVerifier()).analyze(node.name,(MethodNode)value);}
    private static byte[] resource(String name)throws Exception{try(InputStream in=ChatBehaviorTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);return out.toByteArray();}}
    private static class ClickHistory extends GuiNewChat {ClickHistory(Minecraft mc){super(mc);}@Override public IChatComponent getChatComponent(int x,int y){return (IChatComponent)HudChat.component(this,x,y);}IChatComponent nativeComponent(int x,int y){return super.getChatComponent(x,y);}}
    private static class RecordingHud extends GuiIngame {GuiNewChat chat;private RecordingHud(){super(null);}@Override public GuiNewChat getChatGUI(){return chat;}}
}
