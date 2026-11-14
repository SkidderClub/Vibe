package dev.vibe.movement;

import dev.vibe.core.MoveFixTransformer;
import dev.vibe.core.RewoundTransformer;
import java.io.*;
import java.net.JarURLConnection;
import java.nio.file.*;
import java.util.jar.JarFile;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

/** Analyze real MCP and obfuscated Forge classes, including the existing movement transformer. */
public class RewoundHooksTest {
    @Test public void mappedAndObfuscatedWalkingMoveAndCorrectionHooksVerifyAndAreIdempotent() throws Exception {
        String player = "net.minecraft.client.entity.EntityPlayerSP", handler = "net.minecraft.client.network.NetHandlerPlayClient", entity = "net.minecraft.entity.Entity";
        check(player, player, resource(player), false);
        check(handler, handler, resource(handler), false);
        check(entity, entity, resource(entity), false);
        try (JarFile jar = officialJar()) {
            check("bew", player, read(jar.getInputStream(jar.getJarEntry("bew.class"))), true);
            check("bcy", handler, read(jar.getInputStream(jar.getJarEntry("bcy.class"))), true);
            check("pk", entity, read(jar.getInputStream(jar.getJarEntry("pk.class"))), true);
        }
    }

    private void check(String raw, String mapped, byte[] original, boolean obfuscated) throws Exception {
        original = new MoveFixTransformer().transform(raw, mapped, original);
        RewoundTransformer transformer = new RewoundTransformer();
        byte[] result = transformer.transform(raw, mapped, original);
        assertArrayEquals(result, transformer.transform(raw, mapped, result));
        ClassNode node = new ClassNode(); new ClassReader(result).accept(node, 0);
        int before = 0, after = 0, ground = 0, move = 0, correction = 0, preStep = 0, step = 0, postStep = 0, preUpdate = 0, y = 0, force = 0;
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            new Analyzer(new BasicVerifier()).analyze(node.name, method);
            int threadIndex = -1;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals(obfuscated ? "fh" : "net/minecraft/network/PacketThreadUtil")) threadIndex = method.instructions.indexOf(instruction);
                if (!call.owner.equals("dev/vibe/movement/RewoundHooks")) continue;
                if (call.name.equals("beforeWalking")) before++;
                if (call.name.equals("afterWalking")) after++;
                if (call.name.equals("ground")) ground++;
                if (call.name.equals("move")) move++;
                if (call.name.equals("preStepHeight")) preStep++;
                if (call.name.equals("stepHeight")) step++;
                if (call.name.equals("postStep")) postStep++;
                if (call.name.equals("preUpdate")) preUpdate++;
                if (call.name.equals("walkingY")) y++;
                if (call.name.equals("positionTicks")) force++;
                if (call.name.equals("correction")) {
                    correction++;
                    assertTrue("The native thread gate must run before the correction hook", threadIndex >= 0 && threadIndex < method.instructions.indexOf(instruction));
                }
            }
        }
        if (mapped.endsWith("EntityPlayerSP")) {
            assertEquals(1, before); assertEquals(1, after); assertEquals(1, move);
            assertEquals(1, preUpdate); assertEquals(1, force); assertTrue(y >= 4);
            assertTrue("Every C03 variant needs the ground override", ground >= 4);
        } else if (mapped.endsWith(".Entity")) {
            assertEquals(1, preStep); assertEquals(1, step); assertEquals(1, postStep);
        } else assertEquals(1, correction);
    }
    private byte[] resource(String type) throws Exception { return read(getClass().getClassLoader().getResourceAsStream(type.replace('.', '/') + ".class")); }
    private static byte[] read(InputStream source) throws Exception {
        assertNotNull(source);
        try (InputStream in = source; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; for (int n; (n = in.read(buffer)) != -1;) out.write(buffer, 0, n); return out.toByteArray();
        }
    }
    private JarFile officialJar() throws Exception {
        Path path = Paths.get(((JarURLConnection) getClass().getClassLoader().getResource("net/minecraft/client/Minecraft.class").openConnection()).getJarFileURL().toURI());
        Path directory = path.getParent();
        for (int i = 0; i < 4 && directory != null; i++, directory = directory.getParent()) {
            try (DirectoryStream<Path> jars = Files.newDirectoryStream(directory, "*merged+MinecraftForge-FG2+forge*-official.jar")) {
                for (Path jar : jars) return new JarFile(jar.toFile());
            }
        }
        throw new AssertionError("Original Forge jar not found");
    }
}
