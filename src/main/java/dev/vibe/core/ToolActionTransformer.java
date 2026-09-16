package dev.vibe.core;

import java.util.ArrayList;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Wrap vanilla tool actions in a finally scope, keeping the displayed slot untouched between calls. */
final class ToolActionTransformer implements Opcodes {
    private static final String HOOK = "dev/vibe/module/impl/AutoToolModule";
    private static final String PICKEN = "dev/vibe/module/impl/PickenSwitchModule";
    private ToolActionTransformer() { }

    static byte[] transform(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (Object object : new ArrayList<Object>(node.methods)) {
            MethodNode original = (MethodNode) object;
            if (!action(original.name, original.desc)) continue;
            String name = original.name;
            original.name = "vibe$tool$" + name;
            MethodNode wrapper = new MethodNode(ASM5, original.access, name, original.desc, original.signature,
                    (String[]) original.exceptions.toArray(new String[0]));
            original.access = ACC_PRIVATE | ACC_SYNTHETIC;
            Type[] arguments = Type.getArgumentTypes(original.desc);
            Type result = Type.getReturnType(original.desc);
            boolean attack=result.getSort()==Type.VOID;
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
            if(attack){wrapper.visitInsn(ICONST_M1);wrapper.visitVarInsn(ISTORE,slot+1);locals.add(INTEGER);}
            Label start = new Label(), end = new Label(), handler = new Label();
            wrapper.visitTryCatchBlock(start, end, handler, null);
            wrapper.visitLabel(start);
            if(attack){wrapper.visitVarInsn(ALOAD,2);wrapper.visitMethodInsn(INVOKESTATIC,PICKEN,"beginAttackHook","(Ljava/lang/Object;)I",false);wrapper.visitVarInsn(ISTORE,slot+1);}
            wrapper.visitVarInsn(ALOAD, 0);
            int index = 1;
            for (Type argument : arguments) {
                wrapper.visitVarInsn(argument.getOpcode(ILOAD), index);
                index += argument.getSize();
            }
            wrapper.visitMethodInsn(INVOKESPECIAL, node.name, original.name, original.desc, false);
            wrapper.visitLabel(end);
            if(attack){wrapper.visitVarInsn(ILOAD,slot+1);wrapper.visitMethodInsn(INVOKESTATIC,PICKEN,"endAttackHook","(I)V",false);}
            wrapper.visitVarInsn(ILOAD, slot);
            wrapper.visitMethodInsn(INVOKESTATIC, HOOK, "endActionHook", "(I)V", false);
            wrapper.visitInsn(result.getOpcode(IRETURN));
            wrapper.visitLabel(handler);
            wrapper.visitFrame(F_FULL, locals.size(), locals.toArray(), 1, new Object[] {"java/lang/Throwable"});
            if(attack){wrapper.visitVarInsn(ILOAD,slot+1);wrapper.visitMethodInsn(INVOKESTATIC,PICKEN,"endAttackHook","(I)V",false);}
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

    private static boolean action(String name, String desc) {
        if (desc.equals("(Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z") || desc.equals("(Lcj;Lcq;)Z"))
            return name.equals("clickBlock") || name.equals("func_180511_b") || name.equals("b")
                    || name.equals("onPlayerDamageBlock") || name.equals("func_180512_c") || name.equals("c")
                    || name.equals("onPlayerDestroyBlock") || name.equals("func_178888_a") || name.equals("a");
        return (desc.equals("(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;)V") || desc.equals("(Lwn;Lpk;)V"))
                && (name.equals("attackEntity") || name.equals("func_78764_a") || name.equals("a"));
    }
}
