package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Hooks mesh readiness/recycling and the shared draw translation; terrain geometry stays untouched. */
public final class ChunkAnimationTransformer implements IClassTransformer, Opcodes {
    private static final String STATE = "dev/vibe/ui/render/ChunkAnimationState";
    private static final String HOOK = "dev/vibe/ui/render/ChunkAnimationHooks";
    private static final String FIELD = "vibe$animationStart";
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        String target = (transformedName == null ? name : transformedName);
        if (target == null) return bytes;
        target = target.replace('.', '/');
        boolean chunk = target.equals("net/minecraft/client/renderer/chunk/RenderChunk") || target.equals("bht");
        boolean container = target.equals("net/minecraft/client/renderer/ChunkRenderContainer") || target.equals("bfh");
        if (!chunk && !container) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        if (chunk && node.interfaces.contains(STATE)) return bytes;
        FieldNode position = null;
        if (chunk) for (Object entry : node.fields) {
            FieldNode field = (FieldNode) entry;
            if (field.desc.equals("Lnet/minecraft/util/BlockPos;") || field.desc.equals("Lcj;")) position = field;
        }
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            if (chunk && named(method.name, "setPosition", "func_178576_a", "a") && blockPos(method.desc) && position != null) {
                InsnList reset = new InsnList(); reset.add(new VarInsnNode(ALOAD, 0)); reset.add(new VarInsnNode(ALOAD, 0));
                reset.add(new FieldInsnNode(GETFIELD, node.name, position.name, position.desc)); reset.add(new VarInsnNode(ALOAD, 1));
                reset.add(new MethodInsnNode(INVOKESTATIC, HOOK, "resetPosition", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V", false));
                method.instructions.insert(reset);
            }
            if (chunk && named(method.name, "setCompiledChunk", "func_178580_a", "a")
                    && (method.desc.equals("(Lnet/minecraft/client/renderer/chunk/CompiledChunk;)V") || method.desc.equals("(Lbhq;)V")))
                onReturns(method, "compiled", true);
            if (chunk && named(method.name, "deleteGlResources", "func_178566_a", "a") && method.desc.equals("()V")) onReturns(method, "discarded", false);
            if (container && named(method.name, "preRenderChunk", "func_178003_a", "a")
                    && (method.desc.equals("(Lnet/minecraft/client/renderer/chunk/RenderChunk;)V") || method.desc.equals("(Lbht;)V"))) {
                boolean installed = false;
                for (AbstractInsnNode insn : method.instructions.toArray())
                    if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).owner.equals(HOOK)) installed = true;
                if (!installed) for (AbstractInsnNode insn : method.instructions.toArray()) if (insn.getOpcode() == RETURN) {
                    InsnList hook = new InsnList(); hook.add(new VarInsnNode(ALOAD, 1));
                    hook.add(new MethodInsnNode(INVOKESTATIC, HOOK, "apply", "(Ljava/lang/Object;)V", false)); method.instructions.insertBefore(insn, hook);
                }
            }
        }
        if (chunk) {
            node.interfaces.add(STATE); node.fields.add(new FieldNode(ACC_PRIVATE | ACC_VOLATILE | ACC_SYNTHETIC, FIELD, "J", null, null));
            MethodNode get = new MethodNode(ACC_PUBLIC | ACC_SYNTHETIC, "vibe$getAnimationStart", "()J", null, null);
            get.visitCode(); get.visitVarInsn(ALOAD, 0); get.visitFieldInsn(GETFIELD, node.name, FIELD, "J"); get.visitInsn(LRETURN); get.visitMaxs(0, 0); get.visitEnd();
            MethodNode set = new MethodNode(ACC_PUBLIC | ACC_SYNTHETIC, "vibe$setAnimationStart", "(J)V", null, null);
            set.visitCode(); set.visitVarInsn(ALOAD, 0); set.visitVarInsn(LLOAD, 1); set.visitFieldInsn(PUTFIELD, node.name, FIELD, "J"); set.visitInsn(RETURN); set.visitMaxs(0, 0); set.visitEnd();
            node.methods.add(get); node.methods.add(set);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private static void onReturns(MethodNode method, String name, boolean argument) {
        for (AbstractInsnNode insn : method.instructions.toArray()) if (insn.getOpcode() == RETURN) {
            InsnList hook = new InsnList(); hook.add(new VarInsnNode(ALOAD, 0));
            if (argument) hook.add(new VarInsnNode(ALOAD, 1));
            hook.add(new MethodInsnNode(INVOKESTATIC, HOOK, name, argument ? "(Ljava/lang/Object;Ljava/lang/Object;)V" : "(Ljava/lang/Object;)V", false));
            method.instructions.insertBefore(insn, hook);
        }
    }
    private static boolean blockPos(String desc) { return desc.equals("(Lnet/minecraft/util/BlockPos;)V") || desc.equals("(Lcj;)V"); }
    private static boolean named(String name, String mcp, String srg, String raw) { return name.equals(mcp) || name.equals(srg) || name.equals(raw); }
}
