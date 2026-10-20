package dev.vibe.language;

import dev.vibe.core.FontLocalizationTransformer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import net.minecraft.client.gui.FontRenderer;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.*;

/** Ensures legacy FontRenderer calls cannot bypass the packaged language catalog. */
public class FontLocalizationTransformerTest implements Opcodes {
    @Test
    public void routesStringFirstFontRendererMethodsThroughLanguageManager() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V1_6, ACC_PUBLIC, "net/minecraft/client/gui/FontRenderer", null, "java/lang/Object", null);
        MethodNode method = new MethodNode(ACC_PUBLIC, "drawString", "(Ljava/lang/String;FFIZ)I", null, null);
        method.visitCode();
        method.visitInsn(ICONST_0);
        method.visitInsn(IRETURN);
        method.visitMaxs(1, 6);
        method.visitEnd();
        method.accept(writer);
        writer.visitEnd();

        byte[] transformed = new FontLocalizationTransformer().transform(
                "net.minecraft.client.gui.FontRenderer", "net.minecraft.client.gui.FontRenderer", writer.toByteArray());
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        MethodNode draw = (MethodNode) node.methods.get(0);
        boolean localized = false, scripted = false;
        for (AbstractInsnNode instruction : draw.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                localized |= call.owner.equals("dev/vibe/language/LanguageManager")
                        && call.name.equals("translate")
                        && call.desc.equals("(Ljava/lang/String;)Ljava/lang/String;");
                scripted |= call.owner.equals("dev/vibe/ui/ScriptTextRenderer")
                        && call.name.equals("draw")
                        && call.desc.equals("(Ljava/lang/Object;Ljava/lang/String;FFIZ)I");
            }
        }
        assertTrue("FontRenderer labels must be localized before rendering", localized);
        assertTrue("Custom-language labels must be routed through their bundled script font", scripted);
    }

    @Test
    public void leavesUnrelatedClassesUntouched() {
        byte[] raw = {1, 2, 3};
        assertSame(raw, new FontLocalizationTransformer().transform("example.Widget", "example.Widget", raw));
    }

    @Test
    public void patchesTheActualMinecraftFontRendererFinalDrawOverload() throws Exception {
        byte[] raw;
        try (InputStream input = FontRenderer.class.getResourceAsStream("FontRenderer.class")) {
            assertNotNull("Minecraft FontRenderer bytecode must be available to the core hook", input);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            for (int read; (read = input.read(chunk)) >= 0;) output.write(chunk, 0, read);
            raw = output.toByteArray();
        }
        byte[] transformed = new FontLocalizationTransformer().transform("bip",
                "net.minecraft.client.gui.FontRenderer", raw);
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        boolean found = false;
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            if (!"(Ljava/lang/String;FFIZ)I".equals(method.desc)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    found |= call.owner.equals("dev/vibe/ui/ScriptTextRenderer") && call.name.equals("draw");
                }
            }
        }
        assertTrue("The actual FontRenderer must receive the dedicated script-font draw hook", found);
    }
}
