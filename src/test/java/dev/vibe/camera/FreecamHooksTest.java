package dev.vibe.camera;

import dev.vibe.core.ChamsTransformer;
import dev.vibe.core.FreecamTransformer;
import dev.vibe.core.MoveFixTransformer;
import dev.vibe.core.RavenFeatureTransformer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.nio.file.*;
import java.util.jar.JarFile;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

/** Checks installed and development names against the real Forge bytecode and existing hook chain. */
public class FreecamHooksTest implements Opcodes {
    private static final String HOOK = "dev/vibe/module/impl/movement/FreecamModule";
    private static final String[] CLASSES = {
        "net.minecraft.client.renderer.EntityRenderer", "net.minecraft.client.renderer.RenderGlobal",
        "net.minecraft.client.entity.EntityPlayerSP", "net.minecraft.client.Minecraft",
        "net.minecraft.client.renderer.ActiveRenderInfo", "net.minecraft.client.renderer.entity.RenderManager"
    };
    private static final String[] OFFICIAL = {"bfk", "bfr", "bew", "ave", "auz", "biu"};

    @Test public void realForgeHooksWorkInBothNamespacesAndPreserveWalkingPackets() throws Exception {
        try (JarFile jar = officialJar()) {
            for (int i = 0; i < CLASSES.length; i++) {
                check(CLASSES[i], CLASSES[i], resource(CLASSES[i]), i, false);
                check(OFFICIAL[i], CLASSES[i], read(jar.getInputStream(jar.getJarEntry(OFFICIAL[i] + ".class"))), i, true);
            }
        }
    }

    private void check(String raw, String mapped, byte[] bytes, int kind, boolean obfuscated) throws Exception {
        bytes = new MoveFixTransformer().transform(raw, mapped, bytes);
        bytes = new ChamsTransformer().transform(raw, mapped, bytes);
        bytes = new RavenFeatureTransformer().transform(raw, mapped, bytes);
        ClassNode before = node(bytes);
        ClassNode after = node(new FreecamTransformer().transform(raw, mapped, bytes));
        for (Object entry : after.methods) {
            MethodNode method = (MethodNode) entry;
            if (calls(method, null) > 0) new Analyzer(new BasicVerifier()).analyze(after.name, method);
        }
        if (kind == 0) {
            assertEquals(1, calls(method(after, obfuscated ? "f" : "orientCamera", "(F)V"), "orientCameraHook"));
            assertEquals(2, calls(method(after, obfuscated ? "a" : "updateCameraAndRender", "(FJ)V"), "mouseLookHook"));
            assertEquals(1, calls(method(after, obfuscated ? "b" : "renderHand", "(FI)V"), "cameraActiveHook"));
            assertEquals(1, calls(method(after, obfuscated ? "b" : "renderStreamIndicator", "(F)V"), "hideHudHook"));
            assertTrue(calls(method(after, obfuscated ? "a" : "renderWorldPass", "(IFJ)V"), "thirdPersonHook") >= 1);
            assertEquals(1, calls(method(after, obfuscated ? "a" : "renderWorldPass", "(IFJ)V"), "renderSkyHook"));
        }
        if (kind == 1) {
            MethodNode setup = method(after, obfuscated ? "a" : "setupTerrain",
                    obfuscated ? "(Lpk;DLbia;IZ)V" : "(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V");
            assertTrue(calls(setup, "terrainXHook") >= 3);
            assertTrue(calls(setup, "terrainYHook") >= 3);
            assertTrue(calls(setup, "terrainZHook") >= 3);
            assertEquals(1, calls(setup, "terrainOriginHook"));
            assertTrue(calls(setup, "terrainChunkXHook") >= 2);
            MethodNode render = method(after, "vibe$chams$" + (obfuscated ? "a" : "renderEntities"),
                    obfuscated ? "(Lpk;Lbia;F)V" : "(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V");
            assertTrue("The actual player is visible in both world rendering passes", calls(render, "thirdPersonHook") >= 2);
        }
        if (kind == 2) {
            String walking = obfuscated ? "p" : "onUpdateWalkingPlayer";
            assertArrayEquals("Camera controls must leave movement/rotation packet generation byte-for-byte intact",
                    methodBytes(before, method(before, walking, "()V")), methodBytes(after, method(after, walking, "()V")));
            MethodNode living = method(after, obfuscated ? "m" : "onLivingUpdate", "()V");
            assertEquals(1, calls(living, "movementInputHook"));
            for (AbstractInsnNode instruction : living.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode) || !((MethodInsnNode) instruction).name.equals("movementInputHook")) continue;
                assertEquals(ALOAD, instruction.getPrevious().getOpcode());
                MethodInsnNode sample = (MethodInsnNode) instruction.getPrevious().getPrevious();
                assertEquals(obfuscated ? "a" : "updatePlayerMoveState", sample.name);
            }
        }
        if (kind == 3) {
            assertEquals(1, calls(method(after, obfuscated ? "a" : "displayDebugInfo", "(J)V"), "hideHudHook"));
            assertEquals(1, calls(method(after, obfuscated ? "av" : "runGameLoop", "()V"), "achievementHudHook"));
            assertEquals(1, calls(method(after, obfuscated ? "aw" : "clickMouse", "()V"), "cameraActiveHook"));
            assertEquals(1, calls(method(after, obfuscated ? "ax" : "rightClickMouse", "()V"), "cameraActiveHook"));
            assertEquals(1, calls(method(after, obfuscated ? "b" : "sendClickBlockToController", "(Z)V"), "cameraActiveHook"));
        }
        if (kind == 4 || kind == 5) {
            int yaw = 0, pitch = 0;
            for (Object entry : after.methods) { yaw += calls((MethodNode) entry, "yawHook"); pitch += calls((MethodNode) entry, "pitchHook"); }
            assertTrue("Render-facing angles follow the lens", yaw > 0 && pitch > 0);
        }
    }

    @Test public void moduleCannotWriteEntityStateOrSendCancelOrSpoofPackets() throws Exception {
        for (String type : new String[] {"dev.vibe.module.impl.movement.FreecamModule", "dev.vibe.camera.DroneCollisions"}) {
            ClassNode module = node(resource(type));
            for (Object entry : module.methods) for (AbstractInsnNode instruction : ((MethodNode) entry).instructions.toArray()) {
                if (instruction instanceof FieldInsnNode && instruction.getOpcode() == PUTFIELD) {
                    String owner = ((FieldInsnNode) instruction).owner;
                    assertFalse("No entity mutation: " + owner, owner.startsWith("net/minecraft/entity/") || owner.startsWith("net/minecraft/client/entity/"));
                    assertFalse("No camera-mode setting mutation", owner.equals("net/minecraft/client/settings/GameSettings"));
                }
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    assertFalse(call.owner.startsWith("net/minecraft/network/"));
                    assertFalse(call.name.equals("setRenderViewEntity") || call.name.equals("addEntityToWorld")
                            || call.name.equals("setPosition") || call.name.equals("setPositionAndRotation") || call.name.equals("setVelocity"));
                    assertFalse("Block queries must not touch an entity", call.name.equals("getCollidingBoundingBoxes"));
                }
            }
        }
        String handler = "net.minecraft.client.network.NetHandlerPlayClient";
        byte[] bytes = resource(handler);
        assertSame(bytes, new FreecamTransformer().transform(handler, handler, bytes));
    }

    private static int calls(MethodNode method, String name) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.owner.equals(HOOK) && (name == null || name.equals(call.name))) count++;
        }
        return count;
    }
    private static ClassNode node(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    private static MethodNode method(ClassNode node, String name, String desc) {
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            if (name.equals(method.name) && desc.equals(method.desc)) return method;
        }
        throw new AssertionError("Missing " + node.name + "." + name + desc);
    }
    private static byte[] methodBytes(ClassNode owner, MethodNode method) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(owner.version, owner.access, owner.name, null, owner.superName, null);
        method.accept(writer); writer.visitEnd(); return writer.toByteArray();
    }
    private byte[] resource(String type) throws Exception { return read(getClass().getClassLoader().getResourceAsStream(type.replace('.', '/') + ".class")); }
    private static byte[] read(InputStream input) throws Exception {
        assertNotNull(input);
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = stream.read(buffer)) != -1) output.write(buffer, 0, n);
            return output.toByteArray();
        }
    }
    private JarFile officialJar() throws Exception {
        JarURLConnection resource = (JarURLConnection) getClass().getClassLoader().getResource("net/minecraft/client/Minecraft.class").openConnection();
        Path directory = Paths.get(resource.getJarFileURL().toURI()).getParent();
        for (int depth = 0; depth < 4 && directory != null; depth++, directory = directory.getParent()) {
            try (DirectoryStream<Path> jars = Files.newDirectoryStream(directory, "*merged+MinecraftForge-FG2+forge*-official.jar")) {
                for (Path jar : jars) return new JarFile(jar.toFile());
            }
        }
        throw new AssertionError("Official Forge jar not found");
    }
}
