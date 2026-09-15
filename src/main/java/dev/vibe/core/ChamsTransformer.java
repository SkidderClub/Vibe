package dev.vibe.core;

import java.util.ArrayList;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Reuses vanilla's posed models and bound textures, including Forge armor models. */
public final class ChamsTransformer implements IClassTransformer, Opcodes {
    private static final String HOOK = "dev/vibe/ui/ChamsRenderer";

    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        String mapped = transformedName == null ? name : transformedName;
        if (mapped == null) return bytes;
        mapped = mapped.replace('.', '/');
        boolean world = mapped.equals("net/minecraft/client/renderer/RenderGlobal");
        boolean body = mapped.equals("net/minecraft/client/renderer/entity/RendererLivingEntity");
        boolean armor = mapped.equals("net/minecraft/client/renderer/entity/layers/LayerArmorBase");
        if (!world && !body && !armor) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (Object entry : new ArrayList<Object>(node.methods)) {
            MethodNode method = (MethodNode) entry;
            if (world && named(method.name, "renderEntities", "func_180446_a", "a")
                    && (method.desc.equals("(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V")
                        || method.desc.equals("(Lpk;Lbia;F)V"))) {
                wrapWorld(node, method);
            }
            if (body && named(method.name, "renderModel", "func_77036_a", "a")
                    && livingArgs(method.desc, "FFFFFF")) replaceModels(method, false);
            if (armor && named(method.name, "renderLayer", "func_177182_a", "a")
                    && livingArgs(method.desc, "FFFFFFFI")) replaceModels(method, true);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean named(String value, String mcp, String srg, String obfuscated) {
        return value.equals(mcp) || value.equals(srg) || value.equals(obfuscated);
    }

    private static boolean livingArgs(String desc, String tail) {
        return desc.equals("(Lnet/minecraft/entity/EntityLivingBase;" + tail + ")V")
                || desc.equals("(Lpr;" + tail + ")V");
    }

    private static void replaceModels(MethodNode method, boolean armor) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (named(call.name, "render", "func_78088_a", "a")
                    && (call.owner.equals("net/minecraft/client/model/ModelBase") || call.owner.equals("bbo"))
                    && (call.desc.equals("(Lnet/minecraft/entity/Entity;FFFFFF)V") || call.desc.equals("(Lpk;FFFFFF)V"))) {
                method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HOOK,
                        armor ? "renderArmor" : "renderBody", "(Ljava/lang/Object;Ljava/lang/Object;FFFFFF)V", false));
            } else if (armor && named(call.name, "getColor", "func_82814_b", "b")
                    && (call.desc.equals("(Lnet/minecraft/item/ItemStack;)I") || call.desc.equals("(Lzx;)I"))) {
                // A single material pass also avoids double alpha on dyed leather's overlay.
                method.instructions.insertBefore(call, new VarInsnNode(ALOAD, 1));
                method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HOOK, "armorColor",
                        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)I", false));
            } else if (armor && named(call.name, "hasEffect", "func_77962_s", "t") && call.desc.equals("()Z")) {
                method.instructions.insertBefore(call, new VarInsnNode(ALOAD, 1));
                method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HOOK, "armorHasEffect",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false));
            }
        }
    }

    /** A finally scope keeps inventory previews and first-person hands on vanilla rendering. */
    private static void wrapWorld(ClassNode node, MethodNode original) {
        String name = original.name;
        original.name = "vibe$chams$" + name;
        MethodNode wrapper = new MethodNode(ASM5, original.access, name, original.desc, original.signature,
                (String[]) original.exceptions.toArray(new String[0]));
        original.access = ACC_PRIVATE | ACC_SYNTHETIC;
        Type[] args = Type.getArgumentTypes(original.desc);
        wrapper.visitCode();
        wrapper.visitMethodInsn(INVOKESTATIC, HOOK, "beginWorld", "()V", false);
        Label start = new Label(), end = new Label(), handler = new Label();
        wrapper.visitTryCatchBlock(start, end, handler, null);
        wrapper.visitLabel(start);
        wrapper.visitVarInsn(ALOAD, 0);
        wrapper.visitVarInsn(ALOAD, 1);
        wrapper.visitVarInsn(ALOAD, 2);
        wrapper.visitVarInsn(FLOAD, 3);
        wrapper.visitMethodInsn(INVOKESPECIAL, node.name, original.name, original.desc, false);
        wrapper.visitLabel(end);
        wrapper.visitMethodInsn(INVOKESTATIC, HOOK, "endWorld", "()V", false);
        wrapper.visitInsn(RETURN);
        wrapper.visitLabel(handler);
        wrapper.visitFrame(F_FULL, 4, new Object[] {node.name, args[0].getInternalName(), args[1].getInternalName(), FLOAT},
                1, new Object[] {"java/lang/Throwable"});
        wrapper.visitMethodInsn(INVOKESTATIC, HOOK, "endWorld", "()V", false);
        wrapper.visitInsn(ATHROW);
        wrapper.visitMaxs(0, 0);
        wrapper.visitEnd();
        node.methods.add(wrapper);
    }
}
