package dev.vibe.model;

import dev.vibe.core.CustomModelTransformer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.Assert.*;

/** The held-item hook must reach RenderItem in both the development (MCP) and the release (obfuscated) client. */
public class CustomModelHooksTest {

    private static final String HOOK = "dev/vibe/ui/render/CustomModelRenderer";

    @Test
    public void renderItemHooksAreInstalledInDevelopmentAndReleaseNames() throws Exception {
        String mapped = "net.minecraft.client.renderer.entity.RenderItem";
        verify(transform(mapped, mapped, read(getClass().getClassLoader().getResourceAsStream(mapped.replace('.', '/') + ".class"))));
        try (JarFile official = officialJar()) {
            verify(transform("bjh", mapped, read(official.getInputStream(official.getJarEntry("bjh.class")))));
        }
    }

    @Test
    public void otherClassesAreLeftUntouched() {
        byte[] bytes = {1, 2, 3};
        assertSame(bytes, new CustomModelTransformer().transform("bjg", "net.minecraft.client.renderer.ItemModelMesher", bytes));
    }

    private static byte[] transform(String raw, String mapped, byte[] bytes) {
        return new CustomModelTransformer().transform(raw, mapped, bytes);
    }

    private static void verify(byte[] bytes) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int renderItem = 0, holder = 0;
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            boolean hooked = false;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!call.owner.equals(HOOK)) continue;
                hooked = true;
                if (call.name.equals("renderItem")) renderItem++;
                if (call.name.equals("holder")) holder++;
            }
            if (hooked) new Analyzer(new BasicVerifier()).analyze(node.name, method);
        }
        assertEquals("renderItemModelTransform routes its quad drawing through the hook", 1, renderItem);
        assertEquals("renderItemModelForEntity reports the holder", 1, holder);
    }

    private static byte[] read(InputStream input) throws Exception {
        assertNotNull(input);
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
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
        throw new AssertionError("Official Forge jar not found");
    }
}
