package dev.vibe.core;

import java.util.ArrayList;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Wrap vanilla tool actions in a finally scope, keeping the displayed slot untouched between calls. */
final class ToolActionTransformer implements Opcodes {
    private static final String HOOK = "dev/vibe/module/impl/AutoToolModule";
    private static final String PICKEN = "dev/vibe/module/impl/PickenSwitchModule";
    private static final String HYPIXEL = "dev/vibe/module/impl/HypixelModule";
    private ToolActionTransformer() { }

    static byte[] transform(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (Object object : new ArrayList<Object>(node.methods)) {
            MethodNode original = (MethodNode) object;
            if (original.desc.equals("()V") && (original.name.equals("resetBlockRemoving")
                    || original.name.equals("func_78767_c") || original.name.equals("c"))) {
                InsnList guard = new InsnList();
                LabelNode resume = new LabelNode();
                guard.add(new MethodInsnNode(INVOKESTATIC, "dev/vibe/module/impl/BedAuraModule", "keepBreakingHook", "()Z", false));
                guard.add(new JumpInsnNode(IFEQ, resume));
                guard.add(new InsnNode(RETURN));
                guard.add(resume);
                guard.add(new FrameNode(F_SAME, 0, null, 0, null));
                original.instructions.insert(guard);
            }
            if (!action(original.name, original.desc)) continue;
            boolean attack = Type.getReturnType(original.desc).getSort() == Type.VOID;
            if (attack) injectPickenImmediatelyBeforeSlotSync(original);
            String name = original.name;
            original.name = "vibe$tool$" + name;
            MethodNode wrapper = new MethodNode(ASM5, original.access, name, original.desc, original.signature,
                    (String[]) original.exceptions.toArray(new String[0]));
            original.access = ACC_PRIVATE | ACC_SYNTHETIC;
            Type[] arguments = Type.getArgumentTypes(original.desc);
            Type result = Type.getReturnType(original.desc);
            int slot = 1;
            ArrayList<Object> locals = new ArrayList<Object>();
            locals.add(node.name);
            for (Type argument : arguments) {
                locals.add(argument.getSort() == Type.OBJECT ? argument.getInternalName() : INTEGER);
                slot += argument.getSize();
            }
            wrapper.visitCode();
            wrapper.visitMethodInsn(INVOKESTATIC, HOOK, "beginActionHook", "()I", false);
            wrapper.visitVarInsn(ISTORE, slot);
            locals.add(INTEGER);
            Label start = new Label(), end = new Label(), handler = new Label();
            wrapper.visitTryCatchBlock(start, end, handler, null);
            wrapper.visitLabel(start);
            wrapper.visitVarInsn(ALOAD, 0);
            int index = 1;
            for (Type argument : arguments) {
                wrapper.visitVarInsn(argument.getOpcode(ILOAD), index);
                index += argument.getSize();
            }
            wrapper.visitMethodInsn(INVOKESPECIAL, node.name, original.name, original.desc, false);
            wrapper.visitLabel(end);
            if (attack) {
                wrapper.visitInsn(ICONST_M1); wrapper.visitMethodInsn(INVOKESTATIC, PICKEN, "endAttackHook", "(I)V", false);
                wrapper.visitInsn(ICONST_M1); wrapper.visitMethodInsn(INVOKESTATIC, HYPIXEL, "endAttackHook", "(I)V", false);
            }
            wrapper.visitVarInsn(ILOAD, slot);
            wrapper.visitMethodInsn(INVOKESTATIC, HOOK, "endActionHook", "(I)V", false);
            wrapper.visitInsn(result.getOpcode(IRETURN));
            wrapper.visitLabel(handler);
            wrapper.visitFrame(F_FULL, locals.size(), locals.toArray(), 1, new Object[] {"java/lang/Throwable"});
            if (attack) {
                wrapper.visitMethodInsn(INVOKESTATIC, PICKEN, "abortAttackHook", "()V", false);
                wrapper.visitMethodInsn(INVOKESTATIC, HYPIXEL, "abortAttackHook", "()V", false);
            }
            wrapper.visitVarInsn(ILOAD, slot);
            wrapper.visitMethodInsn(INVOKESTATIC, HOOK, "endActionHook", "(I)V", false);
            wrapper.visitInsn(ATHROW);
            wrapper.visitMaxs(0, 0);
            wrapper.visitEnd();
            node.methods.add(wrapper);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    /** Select the utility item at the last possible instruction before vanilla's C09/C02 attack pair. */
    private static void injectPickenImmediatelyBeforeSlotSync(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (!"()V".equals(call.desc) || !("syncCurrentPlayItem".equals(call.name) || "func_78750_j".equals(call.name) || "n".equals(call.name))) continue;
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(ALOAD, 2));
            hook.add(new MethodInsnNode(INVOKESTATIC, HYPIXEL, "beginAttackHook", "(Ljava/lang/Object;)I", false));
            hook.add(new InsnNode(POP));
            hook.add(new VarInsnNode(ALOAD, 2));
            hook.add(new MethodInsnNode(INVOKESTATIC, PICKEN, "beginAttackHook", "(Ljava/lang/Object;)I", false));
            hook.add(new InsnNode(POP));
            method.instructions.insertBefore(call, hook);
            return;
        }
    }

    private static boolean action(String name, String desc) {
        if (desc.equals("(Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z") || desc.equals("(Lcj;Lcq;)Z"))
            return name.equals("clickBlock") || name.equals("func_180511_b") || name.equals("b")
                    || name.equals("onPlayerDamageBlock") || name.equals("func_180512_c") || name.equals("c")
                    || name.equals("onPlayerDestroyBlock") || name.equals("func_178888_a") || name.equals("a");
        return (desc.equals("(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;)V") || desc.equals("(Lwn;Lpk;)V"))
                && (name.equals("attackEntity") || name.equals("func_78764_a") || name.equals("a"));
    }
}
