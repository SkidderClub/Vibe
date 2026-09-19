package dev.vibe.combat;

import dev.vibe.core.MoveFixTransformer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import static org.junit.Assert.*;

/** Inspects real mapped AND obfuscated Forge classes, without a game/window. */
public class CombatHooksTest {
    private static final String MC = "net.minecraft.client.Minecraft";
    private static final String PLAYER = "net.minecraft.client.entity.EntityPlayerSP";
    private static final String RENDERER = "net.minecraft.client.renderer.entity.RenderPlayer";
    private static final String AUTO_TOOL = "dev/vibe/module/impl/AutoToolModule";

    @Test public void developmentInputAndRenderHooksHaveValidStacksAndCorrectOrder() throws Exception {
        verifyInput(transform(MC, MC, resource(MC)), false);
        verifyPlayer(transform(PLAYER, PLAYER, resource(PLAYER)), false);
        verifyRenderer(transform(RENDERER, RENDERER, resource(RENDERER)), false);
        String living = "net.minecraft.client.renderer.entity.RendererLivingEntity";
        verifyInvisible(transform(living, living, resource(living)), false);
        String controller = "net.minecraft.client.multiplayer.PlayerControllerMP";
        verifyTool(transform(controller, controller, resource(controller)), false);
    }

    @Test public void releaseInputAndRenderHooksHaveValidStacksAndCorrectOrder() throws Exception {
        try (JarFile jar = officialJar()) {
            verifyInput(transform("ave", MC, read(jar.getInputStream(jar.getJarEntry("ave.class")))), true);
            verifyPlayer(transform("bew", PLAYER, read(jar.getInputStream(jar.getJarEntry("bew.class")))), true);
            verifyRenderer(transform("bln", RENDERER, read(jar.getInputStream(jar.getJarEntry("bln.class")))), true);
            verifyInvisible(transform("bjl", "net.minecraft.client.renderer.entity.RendererLivingEntity", read(jar.getInputStream(jar.getJarEntry("bjl.class")))), true);
            verifyTool(transform("bda", "net.minecraft.client.multiplayer.PlayerControllerMP", read(jar.getInputStream(jar.getJarEntry("bda.class")))), true);
        }
    }

    private void verifyInvisible(ClassNode node, boolean obfuscated) throws Exception {
        MethodNode render = method(node, obfuscated ? "a" : "renderModel", obfuscated ? "(Lpr;FFFFFF)V" : "(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V");
        verify(node, render);
        assertEquals(1, calls(render, "isInvisibleToPlayer"));
        assertEquals(1, calls(render, "alpha"));
        assertTrue(callIndex(render, "isInvisibleToPlayer", "dev/vibe/ui/InvisibleModelHooks") < callIndex(render, "alpha"));
    }

    private void verifyTool(ClassNode node, boolean obfuscated) throws Exception {
        String desc = obfuscated ? "(Lcj;Lcq;)Z" : "(Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z";
        String[] names = obfuscated ? new String[] {"a", "b", "c"} : new String[] {"onPlayerDestroyBlock", "clickBlock", "onPlayerDamageBlock"};
        for (String name : names) verifyToolScope(node, method(node, name, desc));
        verifyToolScope(node, method(node, obfuscated ? "a" : "attackEntity", obfuscated ? "(Lwn;Lpk;)V" : "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;)V"));
        MethodNode sync = method(node, obfuscated ? "n" : "syncCurrentPlayItem", "()V");
        verify(node, sync);
        assertEquals(1, calls(sync, "serverSlotHook"));
        assertEquals(1, calls(sync, "serverSlotHook", AUTO_TOOL));
    }

    private void verifyToolScope(ClassNode node, MethodNode wrapper) throws Exception {
        verify(node, wrapper);
        assertEquals(1, calls(wrapper, "beginActionHook"));
        assertEquals(2, calls(wrapper, "endActionHook"));
        assertEquals(1, wrapper.tryCatchBlocks.size());
        MethodNode original = method(node, "vibe$tool$" + wrapper.name, wrapper.desc);
        verify(node, original);
        assertTrue(original.instructions.size() > wrapper.instructions.size());
    }

    private void verifyInput(ClassNode node, boolean obfuscated) throws Exception {
        MethodNode method = method(node, obfuscated ? "s" : "runTick", "()V");
        verify(node, method);
        assertEquals(1, calls(method, "prepareInputHook"));
        assertEquals(1, calls(method, "allowInputHook"));
        assertTrue(calls(method, "guiClickCounterHook") >= 1);
        int hook = callIndex(method, "prepareInputHook");
        assertTrue(hook > callIndex(method, obfuscated ? "a" : "getMouseOver", obfuscated ? "bfk" : "net/minecraft/client/renderer/EntityRenderer"));
        assertTrue(hook < callIndex(method, obfuscated ? "bS" : "isUsingItem"));
        assertTrue(hook < callIndex(method, obfuscated ? "aw" : "clickMouse", node.name));
        AbstractInsnNode next = method.instructions.get(hook).getNext();
        assertTrue(next instanceof MethodInsnNode);
        assertEquals(obfuscated ? "bS" : "isUsingItem", ((MethodInsnNode) next).name);
        MethodNode click = method(node, obfuscated ? "aw" : "clickMouse", "()V");
        verify(node, click);
        assertTrue(calls(click, "missCooldownHook") > 0);
    }

    private void verifyPlayer(ClassNode node, boolean obfuscated) throws Exception {
        MethodNode method = method(node, obfuscated ? "p" : "onUpdateWalkingPlayer", "()V");
        verify(node, method);
        assertEquals(1, calls(method, "beginPacketRotationHook"));
        assertTrue(calls(method, "endPacketRotationHook") >= 1);
    }

    private void verifyRenderer(ClassNode node, boolean obfuscated) throws Exception {
        MethodNode method = method(node, obfuscated ? "d" : "setModelVisibilities",
                obfuscated ? "(Lbet;)V" : "(Lnet/minecraft/client/entity/AbstractClientPlayer;)V");
        verify(node, method);
        int returns = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) if (insn.getOpcode() == Opcodes.RETURN) returns++;
        assertEquals(returns, calls(method, "applyThirdPerson"));
        assertTrue(returns > 0);
    }

    private void verify(ClassNode node, MethodNode method) throws Exception {
        new Analyzer(new BasicVerifier()).analyze(node.name, method);
    }

    private ClassNode transform(String raw, String mapped, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(new MoveFixTransformer().transform(raw, mapped, bytes)).accept(node, 0);
        return node;
    }

    private MethodNode method(ClassNode node, String name, String descriptor) {
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        }
        throw new AssertionError("Missing method " + node.name + "." + name + descriptor);
    }

    private int calls(MethodNode method, String name) {
        return calls(method, name, null);
    }

    private int calls(MethodNode method, String name, String owner) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (name.equals(call.name) && (owner == null || owner.equals(call.owner))) count++;
            }
        }
        return count;
    }

    private int callIndex(MethodNode method, String name) { return callIndex(method, name, null); }
    private int callIndex(MethodNode method, String name, String owner) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (name.equals(call.name) && (owner == null || owner.equals(call.owner))) return method.instructions.indexOf(insn);
            }
        }
        throw new AssertionError("Missing call " + name + " in " + method.name);
    }

    private byte[] resource(String name) throws Exception {
        return read(getClass().getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class"));
    }

    private byte[] read(InputStream source) throws Exception {
        assertNotNull(source);
        try (InputStream input = source; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            return output.toByteArray();
        }
    }

    private JarFile officialJar() throws Exception {
        URL mapped = getClass().getClassLoader().getResource("net/minecraft/client/Minecraft.class");
        Path mappedJar = Paths.get(((JarURLConnection) mapped.openConnection()).getJarFileURL().toURI());
        // Unimined stores the original patched Forge jar one level above its
        // mapping-specific output. Walk a bounded number of cache directories.
        Path directory = mappedJar.getParent();
        for (int depth = 0; depth < 4 && directory != null; depth++, directory = directory.getParent()) {
            try (DirectoryStream<Path> jars = Files.newDirectoryStream(directory, "*merged+MinecraftForge-FG2+forge*-official.jar")) {
                for (Path path : jars) return new JarFile(path.toFile());
            }
        }
        throw new AssertionError("Original Forge jar not found beside " + mappedJar);
    }
}
