package dev.vibe.features;

import dev.vibe.core.MoveFixTransformer;
import dev.vibe.core.NoEscMenuTransformer;
import dev.vibe.inventory.AutomationFixture;
import dev.vibe.module.impl.world.NoEscMenuModule;
import dev.vibe.path.PathTestFixture;
import java.io.*;
import java.net.JarURLConnection;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

/** Real native pause/use/input bytecode, including MCP and production obfuscation. */
public class GameplayHooksTest implements Opcodes {
    @Test public void nativePauseDistinguishesAutomaticAndManualRequestsWithoutChangingFocusSettings() throws Exception {
        try (AutomationFixture f=new AutomationFixture()) {
            Minecraft client=pauseClient(false);client.theWorld=f.mc.theWorld;client.thePlayer=f.player;client.gameSettings=f.mc.gameSettings;
            PathTestFixture.set(Minecraft.class,null,"theMinecraft",client);
            NoEscMenuModule m=new NoEscMenuModule();f.base.modules.registerDynamic(m);
            client.displayInGameMenu();assertTrue(client.currentScreen instanceof GuiIngameMenu);client.currentScreen=null;
            boolean pauseSetting=client.gameSettings.pauseOnLostFocus;m.setEnabled(true);assertTrue(m.allowManual.isEnabled());
            client.displayInGameMenu();assertNull(client.currentScreen);
            NoEscMenuModule.openManualHook(client);assertTrue(client.currentScreen instanceof GuiIngameMenu);client.currentScreen=null;
            assertTrue("The explicit Escape scope must end after opening",NoEscMenuModule.blocksMenuHook());
            m.allowManual.setEnabled(false);NoEscMenuModule.openManualHook(client);assertNull(client.currentScreen);
            assertEquals(pauseSetting,client.gameSettings.pauseOnLostFocus);
            m.setEnabled(false);client.displayInGameMenu();assertTrue(client.currentScreen instanceof GuiIngameMenu);
        }
    }
    @Test public void manualPauseScopeIsClearedEvenWhenOpeningTheScreenThrows() throws Exception {
        try (AutomationFixture f=new AutomationFixture()) {
            Minecraft client=pauseClient(true);client.theWorld=f.mc.theWorld;client.thePlayer=f.player;
            PathTestFixture.set(Minecraft.class,null,"theMinecraft",client);
            NoEscMenuModule m=new NoEscMenuModule();f.base.modules.registerDynamic(m);m.setEnabled(true);
            try { NoEscMenuModule.openManualHook(client);fail("Expected the display fixture failure"); }
            catch(IllegalStateException expected){assertEquals("display fixture",expected.getMessage());}
            assertTrue(NoEscMenuModule.blocksMenuHook());m.setEnabled(false);
        }
    }
    @Test public void nativeMappedAndObfuscatedHooksVerifyAndPreserveAutomaticCallSites() throws Exception {
        check("net.minecraft.client.Minecraft","net.minecraft.client.Minecraft",resource("net.minecraft.client.Minecraft"),false);
        checkUse("net.minecraft.client.multiplayer.PlayerControllerMP",resource("net.minecraft.client.multiplayer.PlayerControllerMP"));
        checkAutomatic(resource("net.minecraft.client.renderer.EntityRenderer"),false);
        try(JarFile jar=officialJar()) {
            check("ave","net.minecraft.client.Minecraft",read(jar.getInputStream(jar.getJarEntry("ave.class"))),true);
            checkUse("bda",read(jar.getInputStream(jar.getJarEntry("bda.class"))));
            checkAutomatic(read(jar.getInputStream(jar.getJarEntry("bfk.class"))),true);
        }
    }
    private void check(String raw,String mapped,byte[] original,boolean obfuscated)throws Exception{
        byte[] moved=new MoveFixTransformer().transform(raw,mapped,original);NoEscMenuTransformer t=new NoEscMenuTransformer();byte[] result=t.transform(raw,mapped,moved);
        assertArrayEquals(result,t.transform(raw,mapped,result));ClassNode node=new ClassNode();new ClassReader(result).accept(node,0);
        int guard=0,manual=0,automatic=0,finish=0;
        for(Object value:node.methods){MethodNode method=(MethodNode)value;new Analyzer(new BasicVerifier()).analyze(node.name,method);
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;
                if(call.owner.equals("dev/vibe/module/impl/world/NoEscMenuModule")){if(call.name.equals("blocksMenuHook"))guard++;if(call.name.equals("openManualHook"))manual++;}
                if(call.name.equals(obfuscated?"p":"displayInGameMenu")&&call.owner.equals(obfuscated?"ave":"net/minecraft/client/Minecraft"))automatic++;
                if(call.name.equals("finishInputHook")&&call.owner.equals("dev/vibe/module/impl/combat/AutoBuffModule"))finish++;
            }
        }
        assertEquals(1,guard);assertEquals(1,manual);assertTrue(finish>=2);
    }
    private void checkAutomatic(byte[] bytes,boolean obfuscated){
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int calls=0;
        for(Object value:node.methods)for(AbstractInsnNode instruction:((MethodNode)value).instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;
            if(call.owner.equals(obfuscated?"ave":"net/minecraft/client/Minecraft")&&call.name.equals(obfuscated?"p":"displayInGameMenu"))calls++;
        }
        assertTrue("The renderer's focus-loss pause must stay outside the manual Escape scope",calls>0);
    }
    private void checkUse(String raw,byte[] original)throws Exception{
        byte[] result=new MoveFixTransformer().transform(raw,"net.minecraft.client.multiplayer.PlayerControllerMP",original);ClassNode node=new ClassNode();new ClassReader(result).accept(node,0);int callbacks=0;
        for(Object value:node.methods){MethodNode method=(MethodNode)value;new Analyzer(new BasicVerifier()).analyze(node.name,method);
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode&&((MethodInsnNode)instruction).name.equals("headUseSentHook"))callbacks++;
        }
        assertEquals("Only the native air-use and block-use C08 dispatches mark a head interaction",2,callbacks);
    }
    private Minecraft pauseClient(boolean failing)throws Exception{
        String parent="net/minecraft/client/Minecraft",name="dev/vibe/features/NativePauseClient";
        byte[] result=new NoEscMenuTransformer().transform(parent.replace('/','.'),parent.replace('/','.'),resource(parent.replace('/','.')));
        ClassNode node=new ClassNode();new ClassReader(result).accept(node,0);node.name=name;node.superName=Type.getInternalName(AutomationFixture.Client.class);
        node.fields.clear();node.interfaces.clear();node.innerClasses.clear();
        for(Iterator<?> it=node.methods.iterator();it.hasNext();)if(!((MethodNode)it.next()).name.equals("displayInGameMenu"))it.remove();
        for(Object value:node.methods)for(AbstractInsnNode instruction:((MethodNode)value).instructions.toArray())if(instruction instanceof FrameNode){FrameNode frame=(FrameNode)instruction;if(frame.local!=null)Collections.replaceAll(frame.local,parent,name);if(frame.stack!=null)Collections.replaceAll(frame.stack,parent,name);}
        if(failing){MethodNode display=new MethodNode(ASM5,ACC_PUBLIC,"displayGuiScreen","(Lnet/minecraft/client/gui/GuiScreen;)V",null,null);display.visitCode();display.visitMethodInsn(INVOKESTATIC,Type.getInternalName(GameplayHooksTest.class),"displayFailure","()V",false);display.visitInsn(RETURN);display.visitMaxs(0,0);display.visitEnd();node.methods.add(display);}
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);final byte[] bytes=writer.toByteArray();
        Class<?> type=new ClassLoader(getClass().getClassLoader()){Class<?> define(){return defineClass(name.replace('/','.'),bytes,0,bytes.length);}}.define();return(Minecraft)PathTestFixture.allocate(type);
    }
    public static void displayFailure(){throw new IllegalStateException("display fixture");}
    private byte[] resource(String type)throws Exception{return read(getClass().getClassLoader().getResourceAsStream(type.replace('.','/')+".class"));}
    private static byte[] read(InputStream source)throws Exception{assertNotNull(source);try(InputStream in=source;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);return out.toByteArray();}}
    private JarFile officialJar()throws Exception{
        Path path=Paths.get(((JarURLConnection)getClass().getClassLoader().getResource("net/minecraft/client/Minecraft.class").openConnection()).getJarFileURL().toURI());
        for(Path directory=path.getParent();directory!=null;directory=directory.getParent()){
            try(DirectoryStream<Path> jars=Files.newDirectoryStream(directory,"*merged+MinecraftForge-FG2+forge*-official.jar")){for(Path jar:jars)return new JarFile(jar.toFile());}
        }throw new AssertionError("Original Forge jar missing");
    }
}
