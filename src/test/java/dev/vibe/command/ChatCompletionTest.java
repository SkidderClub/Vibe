package dev.vibe.command;

import dev.vibe.core.ChatCompletionTransformer;
import dev.vibe.module.impl.movement.AntiAFKModule;
import dev.vibe.module.impl.world.AutoToolModule;
import dev.vibe.path.PathTestFixture;
import java.io.*;
import java.lang.reflect.*;
import java.net.JarURLConnection;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.util.*;
import org.junit.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

/** Executes the transformed vanilla GuiChat rather than a separate completion implementation. */
public class ChatCompletionTest {
    private PathTestFixture f; private Object chat; private GuiTextField input; private Class<?> vanilla;
    private RecordingNetwork network; private RecordingChat history;
    @Before public void setup() throws Exception {
        f = new PathTestFixture(); f.modules.registerDynamic(new AutoToolModule()); f.modules.registerDynamic(new AntiAFKModule());
        PathTestFixture.set(dev.vibe.Vibe.class, f.vibe, "commandManager", new CommandManager());
        network = PathTestFixture.allocate(RecordingNetwork.class); network.sent = new ArrayList<>();
        PathTestFixture.set(net.minecraft.client.entity.EntityPlayerSP.class, f.player, "sendQueue", network);
        history = new RecordingChat(f.mc); RecordingHud hud = PathTestFixture.allocate(RecordingHud.class); hud.chat = history; f.mc.ingameGUI = hud;
        FontRenderer font = PathTestFixture.allocate(PlainFont.class); f.mc.fontRendererObj = font;
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals("net.minecraft.client.gui.GuiChat") && !name.equals("dev.vibe.command.VibeChatGui")) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) try {
                    byte[] bytes = resource(name);
                    if (name.endsWith("GuiChat")) bytes = new ChatCompletionTransformer().transform(name, name, bytes);
                    type = defineClass(name, bytes, 0, bytes.length);
                } catch (IOException failure) { throw new ClassNotFoundException(name, failure); }
                if (resolve) resolveClass(type); return type;
            }
        };
        vanilla = loader.loadClass("net.minecraft.client.gui.GuiChat");
        chat = loader.loadClass("dev.vibe.command.VibeChatGui").newInstance();
        PathTestFixture.set(GuiScreen.class, chat, "mc", f.mc);
        input = new GuiTextField(0, font, 0, 0, 300, 20); input.setMaxStringLength(256);
        PathTestFixture.set(vanilla, chat, "inputField", input);
    }
    @After public void cleanup() throws Exception { f.close(); }
    private void tab() throws Exception { Method key = vanilla.getDeclaredMethod("keyTyped", char.class, int.class); key.setAccessible(true); key.invoke(chat, '\t', 15); }
    @Test public void repeatedTabCyclesTheVanillaCandidateListAndWrapsWithoutSendingDotCommands() throws Exception {
        input.setText(".t a"); tab(); assertEquals(".t autotool", input.getText());
        tab(); assertEquals(".t antiafk", input.getText()); tab(); assertEquals(".t autotool", input.getText());
        assertTrue(network.sent.isEmpty());
    }
    @Test public void commonPrefixAndWholeCommandReplacementAlsoWorkForSettings() throws Exception {
        input.setText(".antiafk modes "); List<String> candidates = new CommandManager().getSuggestions(input.getText()); assertTrue(candidates.size() > 1);
        tab(); String first = input.getText(); assertTrue(first.toLowerCase(Locale.ROOT).startsWith(".antiafk modes ")); tab(); String second = input.getText(); assertNotEquals(first, second);
        assertFalse(second.contains(".antiafk modes .antiafk")); assertTrue(network.sent.isEmpty());
    }
    @Test public void vanillaHistoryResetAndCursorSuffixArePreserved() throws Exception {
        input.setText(".t a suffix"); input.setCursorPosition(4); tab(); assertEquals(".t autotool suffix", input.getText());
        tab(); assertEquals(".t antiafk suffix", input.getText());
        // Up/Down are real vanilla key handling, which resets cycling and restores the current draft.
        Method key = vanilla.getDeclaredMethod("keyTyped", char.class, int.class); key.setAccessible(true);
        history.addToSentMessages(".t autotool"); PathTestFixture.set(vanilla, chat, "sentHistoryCursor", 1);
        key.invoke(chat, '\0', 200); assertEquals(".t autotool", input.getText());
        key.invoke(chat, '\0', 208); assertEquals(".t antiafk suffix", input.getText());
        input.setText(".t a"); tab(); assertEquals(".t autotool", input.getText());
    }
    @Test public void ordinaryChatStillUsesTheServerAndVanillaLastWordReplacement() throws Exception {
        input.setText("hello Al"); tab(); assertEquals(1, network.sent.size());
        vanilla.getMethod("onAutocompleteResponse", String[].class).invoke(chat, (Object) new String[] {"Alice", "Alfred"});
        assertEquals("hello Alice", input.getText()); tab(); assertEquals("hello Alfred", input.getText());
    }
    @Test public void mappedAndRealReleaseBytecodeHaveAllHooksAndValidStacks() throws Exception {
        verify("net.minecraft.client.gui.GuiChat", resource("net.minecraft.client.gui.GuiChat"));
        Path path = Paths.get(((JarURLConnection) getClass().getClassLoader().getResource("net/minecraft/client/Minecraft.class").openConnection()).getJarFileURL().toURI());
        JarFile original = null;
        for (Path dir = path.getParent(); dir != null && original == null; dir = dir.getParent())
            try (DirectoryStream<Path> jars = Files.newDirectoryStream(dir, "*merged+MinecraftForge-FG2+forge*-official.jar")) {
                for (Path jar : jars) { original = new JarFile(jar.toFile()); break; }
            }
        assertNotNull(original);
        try (JarFile jar = original) { verify("awv", read(jar.getInputStream(jar.getJarEntry("awv.class")))); }
    }
    private static void verify(String raw, byte[] bytes) throws Exception {
        ChatCompletionTransformer transformer = new ChatCompletionTransformer(); byte[] result = transformer.transform(raw, "net.minecraft.client.gui.GuiChat", bytes);
        ClassNode node = new ClassNode(); new ClassReader(result).accept(node, 0); int requests = 0, boundaries = 0;
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry; new Analyzer(new BasicVerifier()).analyze(node.name, method);
            for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.owner.equals("dev/vibe/command/VibeChatGui")) { if (call.name.equals("sendAutocomplete")) requests++; if (call.name.equals("completionStart")) boundaries++; }
            }
        }
        assertEquals(1, requests); assertEquals(4, boundaries); assertArrayEquals(result, transformer.transform(raw, "net.minecraft.client.gui.GuiChat", result));
    }
    private static byte[] resource(String name) throws IOException { return read(ChatCompletionTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")); }
    private static byte[] read(InputStream source) throws IOException {
        try (InputStream input = source; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); return output.toByteArray();
        }
    }
    private static class RecordingNetwork extends NetHandlerPlayClient {
        List<Packet<?>> sent;
        private RecordingNetwork() { super(null, null, null, null); }
        @Override public void addToSendQueue(Packet packet) { sent.add(packet); }
    }
    private static class RecordingChat extends GuiNewChat {
        RecordingChat(Minecraft mc) { super(mc); }
        @Override public void printChatMessageWithOptionalDeletion(IChatComponent component, int id) { }
    }
    private static class RecordingHud extends GuiIngame {
        RecordingChat chat; private RecordingHud() { super(null); }
        @Override public GuiNewChat getChatGUI() { return chat; }
    }
    private static class PlainFont extends FontRenderer {
        private PlainFont() { super(null, null, null, false); }
        @Override public int getStringWidth(String text) { return text.length() * 6; }
        @Override public String trimStringToWidth(String text, int width) { return text.substring(0, Math.min(text.length(), Math.max(0, width / 6))); }
        @Override public String trimStringToWidth(String text, int width, boolean reverse) { return trimStringToWidth(text, width); }
    }
}
