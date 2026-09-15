package dev.vibe.ui;

import dev.vibe.core.ChamsTransformer;
import dev.vibe.core.MoveFixTransformer;
import java.io.*;
import java.lang.reflect.*;
import java.net.JarURLConnection;
import java.nio.file.*;
import java.util.jar.JarFile;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

/** Checks the actual Forge bytecode in both development and installed clients. */
public class ChamsHooksTest implements Opcodes {
    private static final String[] CLASSES = {
        "net.minecraft.client.renderer.RenderGlobal",
        "net.minecraft.client.renderer.entity.RendererLivingEntity",
        "net.minecraft.client.renderer.entity.layers.LayerArmorBase"
    };
    private static final String[] OBFUSCATED = {"bfr", "bjl", "bkn"};

    @Test public void developmentAndReleaseHooksReplaceBodyArmorAndGlint() throws Exception {
        try (JarFile official = officialJar()) {
            for (int i = 0; i < CLASSES.length; i++) {
                verify(CLASSES[i], CLASSES[i], read(getClass().getClassLoader()
                        .getResourceAsStream(CLASSES[i].replace('.', '/') + ".class")), i);
                verify(OBFUSCATED[i], CLASSES[i], read(official.getInputStream(
                        official.getJarEntry(OBFUSCATED[i] + ".class"))), i);
            }
        }
    }

    private void verify(String raw, String mapped, byte[] bytes, int kind) throws Exception {
        bytes = new MoveFixTransformer().transform(raw, mapped, bytes);
        bytes = new ChamsTransformer().transform(raw, mapped, bytes);
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int body = 0, armor = 0, color = 0, glint = 0, begin = 0, end = 0, invisible = 0;
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            boolean hooked = false;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals("dev/vibe/ui/InvisibleModelHooks") && call.name.equals("isInvisibleToPlayer")) invisible++;
                if (!call.owner.equals("dev/vibe/ui/ChamsRenderer")) continue;
                hooked = true;
                if (call.name.equals("renderBody")) body++;
                if (call.name.equals("renderArmor")) armor++;
                if (call.name.equals("armorColor")) color++;
                if (call.name.equals("armorHasEffect")) glint++;
                if (call.name.equals("beginWorld")) begin++;
                if (call.name.equals("endWorld")) end++;
            }
            if (hooked || method.name.startsWith("vibe$chams$"))
                new Analyzer(new BasicVerifier()).analyze(node.name, method);
        }
        if (kind == 0) { assertEquals(1, begin); assertEquals(2, end); }
        if (kind == 1) { assertEquals(1, body); assertEquals(1, invisible); }
        if (kind == 2) { assertEquals(2, armor); assertEquals(1, color); assertEquals(1, glint); }
    }

    @Test public void worldScopeRestoresAfterSuccessAndExceptions() throws Exception {
        String name = "dev/vibe/ui/ChamsWorldFixture";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(RETURN); constructor.visitMaxs(0, 0); constructor.visitEnd();
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "renderEntities",
                "(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V", null, null);
        method.visitCode(); method.visitVarInsn(FLOAD, 3);
        method.visitMethodInsn(INVOKESTATIC, "dev/vibe/ui/ChamsHooksTest", "insideWorld", "(F)V", false);
        method.visitInsn(RETURN); method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
        final byte[] bytes = new ChamsTransformer().transform(name, CLASSES[0], writer.toByteArray());
        Class<?> fixture = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(name.replace('/', '.'), bytes, 0, bytes.length); }
        }.define();
        Object instance = fixture.newInstance();
        Method render = fixture.getMethod("renderEntities", Entity.class, ICamera.class, float.class);
        assertEquals(0, depth());
        render.invoke(instance, null, null, 0F);
        assertEquals(0, depth());
        try { render.invoke(instance, null, null, 1F); fail("Expected rendering failure"); }
        catch (InvocationTargetException expected) { assertTrue(expected.getCause() instanceof IllegalStateException); }
        assertEquals(0, depth());
    }

    public static void insideWorld(float failure) throws Exception {
        assertEquals(1, depth());
        if (failure != 0) throw new IllegalStateException("Simulated renderer failure");
    }

    private static int depth() throws Exception {
        Field field = ChamsRenderer.class.getDeclaredField("worldDepth"); field.setAccessible(true);
        return field.getInt(null);
    }

    @Test public void previewsDelegateUnchangedWithoutAnOpenGlContext() {
        final int[] calls = {0};
        ModelBase model = new ModelBase() {
            @Override public void render(Entity entity, float swing, float amount, float age, float yaw, float pitch, float scale) {
                assertArrayEquals(new float[] {1, 2, 3, 4, 5, 6}, new float[] {swing, amount, age, yaw, pitch, scale}, 0);
                calls[0]++;
            }
        };
        ChamsRenderer.renderBody(model, null, 1, 2, 3, 4, 5, 6);
        ChamsRenderer.renderArmor(model, null, 1, 2, 3, 4, 5, 6);
        assertEquals(2, calls[0]);
    }

    private static byte[] read(InputStream input) throws IOException {
        assertNotNull(input);
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int length;
            while ((length = stream.read(buffer)) != -1) output.write(buffer, 0, length);
            return output.toByteArray();
        }
    }

    private JarFile officialJar() throws Exception {
        JarURLConnection resource = (JarURLConnection) getClass().getClassLoader()
                .getResource("net/minecraft/client/Minecraft.class").openConnection();
        Path directory = Paths.get(resource.getJarFileURL().toURI()).getParent();
        for (int depth = 0; depth < 4 && directory != null; depth++, directory = directory.getParent()) {
            try (DirectoryStream<Path> jars = Files.newDirectoryStream(directory, "*merged+MinecraftForge-FG2+forge*-official.jar")) {
                for (Path path : jars) return new JarFile(path.toFile());
            }
        }
        throw new AssertionError("Original Forge jar unavailable");
    }
}
