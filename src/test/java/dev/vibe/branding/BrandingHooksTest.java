package dev.vibe.branding;

import dev.vibe.core.BrandingTransformer;
import java.io.*;
import java.net.JarURLConnection;
import java.nio.file.*;
import java.util.jar.JarFile;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class BrandingHooksTest implements Opcodes {
    private static final String HOOK = "dev/vibe/core/startup/GhostStartup";

    @Test public void actualForgeSplashPreservesItsSemaphoreAndDisplayOwnership() throws Exception {
        String type = "net.minecraftforge.fml.client.SplashProgress$3";
        byte[] raw = resource(type), transformed = patch(type, type, raw);
        ClassNode node = node(transformed); MethodNode run = method(node, "run");
        verify(node);
        assertEquals(1, count(run, HOOK, "renderForge"));
        assertEquals(1, count(run, "java/util/concurrent/Semaphore", "acquireUninterruptibly"));
        assertEquals(1, count(run, "java/util/concurrent/Semaphore", "release"));
        assertEquals(1, count(run, "org/lwjgl/opengl/Display", "update"));
        assertEquals(1, count(method(node, "clearGL"), HOOK, "release"));
        for (AbstractInsnNode i : run.instructions.toArray()) if (i instanceof MethodInsnNode && ((MethodInsnNode)i).name.equals("renderForge")) {
            assertTrue(i.getNext() instanceof JumpInsnNode);
            AbstractInsnNode target = ((JumpInsnNode)i.getNext()).label.getNext();
            while (target.getOpcode() < 0) target = target.getNext();
            assertTrue("Custom drawing must rejoin before acquiring Forge's semaphore", target instanceof FieldInsnNode);
            assertEquals("Ljava/util/concurrent/Semaphore;", ((FieldInsnNode)target).desc);
        }
        assertArrayEquals("Hooks must be idempotent", transformed, patch(type, type, transformed));
        String progress = "net.minecraftforge.fml.common.ProgressManager$ProgressBar";
        ClassNode bar = node(patch(progress, progress, resource(progress))); verify(bar);
        int changed = 0; for (Object entry : bar.methods) changed += count((MethodNode)entry, HOOK, "progressChanged");
        assertEquals(1, changed);
    }

    @Test public void minecraftHooksLinkInBothDevelopmentAndInstalledNamespaces() throws Exception {
        checkMinecraft(resource("net.minecraft.client.Minecraft"), "net.minecraft.client.Minecraft");
        JarURLConnection source = (JarURLConnection)getClass().getClassLoader().getResource("net/minecraft/client/Minecraft.class").openConnection();
        Path directory = Paths.get(source.getJarFileURL().toURI()).getParent();
        for (int depth = 0; depth < 4 && directory != null; depth++, directory = directory.getParent()) {
            try (DirectoryStream<Path> jars = Files.newDirectoryStream(directory, "*merged+MinecraftForge-FG2+forge*-official.jar")) {
                for (Path path : jars) try (JarFile jar = new JarFile(path.toFile())) { checkMinecraft(read(jar.getInputStream(jar.getJarEntry("ave.class"))), "ave"); return; }
            }
        }
        fail("Missing installed Forge bytecode");
    }

    private void checkMinecraft(byte[] raw, String name) throws Exception {
        ClassNode node = node(patch(name, "net.minecraft.client.Minecraft", raw)); verify(node);
        int begin = 0, finish = 0, icon = 0, fallback = 0;
        for (Object entry : node.methods) {
            MethodNode m = (MethodNode)entry;
            begin += count(m, HOOK, "begin"); finish += count(m, HOOK, "finish");
            icon += count(m, "dev/vibe/core/startup/GhostWindowIcon", "apply"); fallback += count(m, HOOK, "drawVanilla");
        }
        assertEquals(1, begin); assertTrue(finish > 0); assertEquals(1, icon);
        assertEquals(1, fallback);
    }
    private static void verify(ClassNode node) throws Exception {
        for (Object entry : node.methods) {
            MethodNode m = (MethodNode)entry;
            boolean hooked = false; for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof MethodInsnNode && ((MethodInsnNode)i).owner.startsWith("dev/vibe/core/startup/")) hooked = true;
            if (hooked) new Analyzer(new BasicVerifier()).analyze(node.name, m);
        }
    }
    private static int count(MethodNode m, String owner, String name) { int n = 0; for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof MethodInsnNode && ((MethodInsnNode)i).owner.equals(owner) && ((MethodInsnNode)i).name.equals(name)) n++; return n; }
    private static MethodNode method(ClassNode node, String name) { for (Object entry : node.methods) if (((MethodNode)entry).name.equals(name)) return (MethodNode)entry; throw new AssertionError(name); }
    private static ClassNode node(byte[] bytes) { ClassNode n = new ClassNode(); new ClassReader(bytes).accept(n, 0); return n; }
    private static byte[] patch(String name, String mapped, byte[] bytes) { return new BrandingTransformer().transform(name, mapped, bytes); }
    private byte[] resource(String name) throws Exception { return read(getClass().getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")); }
    private static byte[] read(InputStream input) throws Exception { assertNotNull(input); try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) { byte[] b = new byte[8192]; for (int n; (n = in.read(b)) != -1;) out.write(b, 0, n); return out.toByteArray(); } }
}
