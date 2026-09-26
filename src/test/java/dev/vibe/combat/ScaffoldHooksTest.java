package dev.vibe.combat;

import dev.vibe.core.MoveFixTransformer;
import dev.vibe.core.ScaffoldTransformer;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.jar.JarFile;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

/** Exercises real Forge bytecode in both naming environments, including stack verification. */
public class ScaffoldHooksTest {
    private static final String[] TYPES = {"net.minecraft.client.Minecraft", "net.minecraft.client.entity.EntityPlayerSP",
            "net.minecraft.entity.EntityLivingBase", "net.minecraft.client.renderer.EntityRenderer", "net.minecraft.client.renderer.ItemRenderer"};
    private static final String[] RAW = {"ave", "bew", "pr", "bfk", "bfn"};

    @Test public void mappedHooksUseTheOriginalEventBoundaries() throws Exception {
        for (int i = 0; i < TYPES.length; i++) verify(i, false, bytes(getClass().getClassLoader()
                .getResourceAsStream(TYPES[i].replace('.', '/') + ".class")));
    }

    @Test public void packagedHooksUseTheOriginalEventBoundaries() throws Exception {
        URL url = getClass().getClassLoader().getResource("net/minecraft/client/Minecraft.class");
        Path directory = Paths.get(((JarURLConnection) url.openConnection()).getJarFileURL().toURI()).getParent();
        for (int depth = 0; depth < 4 && directory != null; depth++, directory = directory.getParent()) {
            try (DirectoryStream<Path> jars = Files.newDirectoryStream(directory, "*merged+MinecraftForge-FG2+forge*-official.jar")) {
                for (Path file : jars) {
                    try (JarFile jar = new JarFile(file.toFile())) {
                        for (int i = 0; i < TYPES.length; i++) verify(i, true, bytes(jar.getInputStream(jar.getJarEntry(RAW[i] + ".class"))));
                    }
                    return;
                }
            }
        }
        fail("Missing cached official Forge jar");
    }

    private static void verify(int index, boolean raw, byte[] input) throws Exception {
        String name = raw ? RAW[index] : TYPES[index];
        byte[] moved = new MoveFixTransformer().transform(name, TYPES[index], input);
        ClassNode node = new ClassNode();
        new ClassReader(new ScaffoldTransformer().transform(name, TYPES[index], moved)).accept(node, 0);
        String[] expected = index == 0 ? new String[]{"clickHook"}
                : index == 1 ? new String[]{"normalUpdateHook", "sprintForwardHook", "sprintStateHook", "beginPacketRotationHook", "endPacketRotationHook"}
                : index == 2 ? new String[]{"jumpMotionHook", "movementYawHook"}
                : index == 3 ? new String[]{"mouseOverHook", "raycastHook"} : new String[]{"heldItemHook"};
        for (String hook : expected) {
            int found = 0;
            for (Object value : node.methods) {
                MethodNode method = (MethodNode) value;
                boolean hasHook = false;
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (!(insn instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (!call.owner.startsWith("dev/vibe/") || !call.name.equals(hook)) continue;
                    hasHook = true; found++;
                    if (hook.equals("clickHook")) {
                        MethodInsnNode next = nextCall(insn);
                        assertTrue(next.name.equals(raw ? "bS" : "isUsingItem"));
                    }
                    if (hook.equals("mouseOverHook")) {
                        assertEquals(raw ? "a" : "getMouseOver", nextCall(insn).name);
                        assertEquals("(FJ)V", method.desc);
                    }
                    if (hook.equals("normalUpdateHook")) assertEquals(Opcodes.INVOKESPECIAL, nextCall(insn).getOpcode());
                }
                if (hasHook) new Analyzer(new BasicVerifier()).analyze(node.name, method);
            }
            assertTrue(name + " missing " + hook, found > 0);
            if (index == 1 && hook.equals("sprintForwardHook")) {
                // One existing MoveFix previous-input hook + three Scaffold sprint conditions.
                assertEquals("Do not patch item-use slowdown as a sprint condition", 4, found);
            }
        }
    }

    private static MethodInsnNode nextCall(AbstractInsnNode insn) {
        while ((insn = insn.getNext()) != null) if (insn instanceof MethodInsnNode) return (MethodInsnNode) insn;
        throw new AssertionError("Missing next invocation");
    }
    private static byte[] bytes(InputStream in) throws Exception {
        assertNotNull(in);
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }
}
