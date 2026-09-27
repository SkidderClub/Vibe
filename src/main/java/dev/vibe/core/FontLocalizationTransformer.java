package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Routes text sent through Minecraft's standard font renderer through Vibe's
 * catalog. Older screens can construct labels in arrays or GuiButtons, which
 * otherwise bypass a bespoke Vibe renderer.
 */
public final class FontLocalizationTransformer implements IClassTransformer, Opcodes {
    private static final String FONT_RENDERER = "net/minecraft/client/gui/FontRenderer";
    private static final String LANGUAGE_MANAGER = "dev/vibe/language/LanguageManager";
    private static final String SCRIPT_RENDERER = "dev/vibe/ui/ScriptTextRenderer";
    private static final int UNHANDLED = Integer.MIN_VALUE;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        String raw = name == null ? "" : name.replace('.', '/');
        String mapped = transformedName == null ? "" : transformedName.replace('.', '/');
        if (basicClass == null || (!FONT_RENDERER.equals(raw) && !FONT_RENDERER.equals(mapped)
                && !"bip".equals(raw))) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            for (MethodNode method : node.methods) {
                if ((method.access & ACC_STATIC) != 0 || !method.desc.startsWith("(Ljava/lang/String;")) {
                    continue;
                }

                InsnList localize = new InsnList();
                localize.add(new VarInsnNode(ALOAD, 1));
                localize.add(new MethodInsnNode(INVOKESTATIC, LANGUAGE_MANAGER, "translate",
                        "(Ljava/lang/String;)Ljava/lang/String;", false));
                localize.add(new VarInsnNode(ASTORE, 1));
                method.instructions.insert(localize);

                // drawString's final overload contains every vanilla GUI draw.
                // Give the two custom Vibe scripts their real bundled fonts,
                // but only when the string came from Vibe's own catalog.
                if ("(Ljava/lang/String;FFIZ)I".equals(method.desc)) {
                    insertScriptDrawHook(method);
                } else if ("(Ljava/lang/String;)I".equals(method.desc)) {
                    insertScriptWidthHook(method);
                }
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static void insertScriptDrawHook(MethodNode method) {
        LabelNode vanilla = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ALOAD, 0));
        hook.add(new VarInsnNode(ALOAD, 1));
        hook.add(new VarInsnNode(FLOAD, 2));
        hook.add(new VarInsnNode(FLOAD, 3));
        hook.add(new VarInsnNode(ILOAD, 4));
        hook.add(new VarInsnNode(ILOAD, 5));
        hook.add(new MethodInsnNode(INVOKESTATIC, SCRIPT_RENDERER, "draw",
                "(Ljava/lang/Object;Ljava/lang/String;FFIZ)I", false));
        hook.add(new VarInsnNode(ISTORE, 6));
        hook.add(new VarInsnNode(ILOAD, 6));
        hook.add(new LdcInsnNode(UNHANDLED));
        hook.add(new JumpInsnNode(IF_ICMPEQ, vanilla));
        hook.add(new VarInsnNode(ILOAD, 6));
        hook.add(new InsnNode(IRETURN));
        hook.add(vanilla);
        method.instructions.insert(hook);
    }

    private static void insertScriptWidthHook(MethodNode method) {
        LabelNode vanilla = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ALOAD, 1));
        hook.add(new MethodInsnNode(INVOKESTATIC, SCRIPT_RENDERER, "width", "(Ljava/lang/String;)I", false));
        hook.add(new VarInsnNode(ISTORE, 2));
        hook.add(new VarInsnNode(ILOAD, 2));
        hook.add(new LdcInsnNode(UNHANDLED));
        hook.add(new JumpInsnNode(IF_ICMPEQ, vanilla));
        hook.add(new VarInsnNode(ILOAD, 2));
        hook.add(new InsnNode(IRETURN));
        hook.add(vanilla);
        method.instructions.insert(hook);
    }
}
