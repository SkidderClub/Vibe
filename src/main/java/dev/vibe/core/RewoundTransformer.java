package dev.vibe.core;

import java.util.ArrayList;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Ports Exire's move/pre-network/post-network/correction events without replacing vanilla physics. */
public final class RewoundTransformer implements net.minecraft.launchwrapper.IClassTransformer, Opcodes {
    private static final String HOOK = "dev/vibe/movement/RewoundHooks";
    @Override public byte[] transform(String name, String mapped, byte[] bytes) {
        if (bytes == null) return null;
        boolean player = "net.minecraft.client.entity.EntityPlayerSP".equals(mapped) || "net.minecraft.client.entity.EntityPlayerSP".equals(name) || "bew".equals(name);
        boolean handler = "net.minecraft.client.network.NetHandlerPlayClient".equals(mapped) || "net.minecraft.client.network.NetHandlerPlayClient".equals(name) || "bcy".equals(name);
        boolean entity = "net.minecraft.entity.Entity".equals(mapped) || "net.minecraft.entity.Entity".equals(name) || "pk".equals(name);
        if (!player && !handler && !entity) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (method.name.startsWith("vibe$rewound$")) return bytes;
            if (handler || entity) for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).owner.equals(HOOK)) return bytes;
            }
        }
        if (entity) {
            for (Object value : node.methods) {
                MethodNode method = (MethodNode) value;
                if ("(DDD)V".equals(method.desc) && (method.name.equals("moveEntity") || method.name.equals("func_70091_d") || method.name.equals("d"))) stepSites(method);
            }
        } else if (player) {
            String movementName = "moveEntity";
            boolean hasMovement = false;
            for (Object value : new ArrayList<Object>(node.methods)) {
                MethodNode method = (MethodNode) value;
                String updateName = method.name.replace("vibe$hotbar$", "");
                if ("()V".equals(method.desc) && (updateName.equals("onUpdate") || updateName.equals("func_70071_h_") || updateName.equals("t_"))) preUpdate(method);
                if ("(DDD)V".equals(method.desc) && ("moveEntity".equals(method.name) || "func_70091_d".equals(method.name) || "d".equals(method.name))) hasMovement = true;
                if (!"()V".equals(method.desc) || !("onUpdateWalkingPlayer".equals(method.name) || "func_175161_p".equals(method.name) || "p".equals(method.name))) continue;
                movementName = "p".equals(method.name) ? "d" : "func_175161_p".equals(method.name) ? "func_70091_d" : "moveEntity";
                groundReads(method);
                wrapWalking(node, method);
            }
            // Forge's vanilla local player inherits moveEntity. Keeping the override
            // local to SP avoids adding hooks to every entity's collision processing.
            if (!hasMovement) node.methods.add(movement(node, movementName));
        } else {
            for (Object value : node.methods) {
                MethodNode method = (MethodNode) value;
                if (!("handlePlayerPosLook".equals(method.name) || "func_147258_a".equals(method.name) || "a".equals(method.name))
                        || !(method.desc.equals("(Lnet/minecraft/network/play/server/S08PacketPlayerPosLook;)V") || method.desc.equals("(Lfi;)V"))) continue;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (!call.owner.equals("net/minecraft/network/PacketThreadUtil") && !call.owner.equals("fh")) continue;
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(ALOAD, 1));
                    hook.add(new MethodInsnNode(INVOKESTATIC, HOOK, "correction", "(Ljava/lang/Object;)V", false));
                    method.instructions.insert(call, hook);
                    break;
                }
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void groundReads(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof FieldInsnNode)) continue;
            FieldInsnNode field = (FieldInsnNode) instruction;
            if (field.getOpcode() == GETFIELD && "D".equals(field.desc)
                    && (field.owner.equals("net/minecraft/util/AxisAlignedBB") || field.owner.equals("aug"))
                    && (field.name.equals("minY") || field.name.equals("field_72338_b") || field.name.equals("b"))) {
                method.instructions.insert(field, new MethodInsnNode(INVOKESTATIC, HOOK, "walkingY", "(D)D", false));
            }
            AbstractInsnNode next = field.getNext();
            while (next != null && next.getOpcode() < 0) next = next.getNext();
            if (field.getOpcode() == GETFIELD && "I".equals(field.desc) && next instanceof IntInsnNode && ((IntInsnNode) next).operand == 20) {
                method.instructions.insert(field, new MethodInsnNode(INVOKESTATIC, HOOK, "positionTicks", "(I)I", false));
            }
            if (field.getOpcode() != GETFIELD || !"Z".equals(field.desc)
                    || !(field.name.equals("onGround") || field.name.equals("field_70122_E") || field.name.equals("C"))) continue;
            method.instructions.insertBefore(field, new InsnNode(DUP));
            method.instructions.insert(field, new MethodInsnNode(INVOKESTATIC, HOOK, "ground", "(Ljava/lang/Object;Z)Z", false));
        }
    }

    private static void preUpdate(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode) || instruction.getOpcode() != INVOKESPECIAL) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (!call.desc.equals("()V") || !(call.name.equals("onUpdate") || call.name.equals("func_70071_h_") || call.name.equals("t_"))) continue;
            InsnList hook = new InsnList(); hook.add(new VarInsnNode(ALOAD, 0));
            hook.add(new MethodInsnNode(INVOKESTATIC, HOOK, "preUpdate", "(Ljava/lang/Object;)V", false));
            method.instructions.insertBefore(call, hook);
            return;
        }
    }

    private static void stepSites(MethodNode method) {
        ArrayList<FieldInsnNode> heights = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof FieldInsnNode)) continue;
            FieldInsnNode field = (FieldInsnNode) instruction;
            if (field.getOpcode() == GETFIELD && field.desc.equals("F")
                    && (field.name.equals("stepHeight") || field.name.equals("field_70138_W") || field.name.equals("S"))) heights.add(field);
        }
        if (heights.size() != 2) throw new IllegalStateException("Native step-height sites: " + heights.size());
        ArrayList<MethodInsnNode> boxes = new ArrayList<>();
        for (AbstractInsnNode instruction = heights.get(1).getNext(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if ((call.desc.equals("(Lnet/minecraft/util/AxisAlignedBB;)V") || call.desc.equals("(Laug;)V"))
                    && (call.name.equals("setEntityBoundingBox") || call.name.equals("func_174826_a") || call.name.equals("a"))) boxes.add(call);
        }
        if (boxes.size() != 4) throw new IllegalStateException("Native step-box sites: " + boxes.size());
        for (int i = 0; i < heights.size(); i++) {
            FieldInsnNode field = heights.get(i);
            method.instructions.insertBefore(field, new InsnNode(DUP));
            method.instructions.insert(field, new MethodInsnNode(INVOKESTATIC, HOOK, i == 0 ? "preStepHeight" : "stepHeight", "(Ljava/lang/Object;F)F", false));
        }
        InsnList post = new InsnList(); post.add(new VarInsnNode(ALOAD, 0));
        post.add(new MethodInsnNode(INVOKESTATIC, HOOK, "postStep", "(Ljava/lang/Object;)V", false));
        method.instructions.insert(boxes.get(2), post);
    }

    private static void wrapWalking(ClassNode node, MethodNode original) {
        String name = original.name;
        MethodNode wrapper = new MethodNode(ASM5, original.access, name, original.desc, original.signature, null);
        original.name = "vibe$rewound$" + name;
        original.access = ACC_PRIVATE | ACC_SYNTHETIC;
        wrapper.visitCode();
        Label start = new Label(), end = new Label(), failed = new Label();
        wrapper.visitTryCatchBlock(start, end, failed, null);
        wrapper.visitLabel(start);
        wrapper.visitVarInsn(ALOAD, 0);
        wrapper.visitMethodInsn(INVOKESTATIC, HOOK, "beforeWalking", "(Ljava/lang/Object;)V", false);
        wrapper.visitVarInsn(ALOAD, 0);
        wrapper.visitMethodInsn(INVOKESPECIAL, node.name, original.name, "()V", false);
        wrapper.visitLabel(end);
        wrapper.visitVarInsn(ALOAD, 0);
        wrapper.visitMethodInsn(INVOKESTATIC, HOOK, "afterWalking", "(Ljava/lang/Object;)V", false);
        wrapper.visitInsn(RETURN);
        wrapper.visitLabel(failed);
        wrapper.visitFrame(F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
        wrapper.visitMethodInsn(INVOKESTATIC, HOOK, "endWalking", "()V", false);
        wrapper.visitInsn(ATHROW);
        wrapper.visitMaxs(0, 0);
        wrapper.visitEnd();
        node.methods.add(wrapper);
    }

    private static MethodNode movement(ClassNode node, String name) {
        MethodNode method = new MethodNode(ASM5, ACC_PUBLIC, name, "(DDD)V", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(DLOAD, 1); method.visitVarInsn(DLOAD, 3); method.visitVarInsn(DLOAD, 5);
        method.visitMethodInsn(INVOKESTATIC, HOOK, "move", "(Ljava/lang/Object;DDD)[D", false);
        method.visitVarInsn(ASTORE, 7);
        Label vanilla = new Label();
        method.visitVarInsn(ALOAD, 7);
        method.visitJumpInsn(IFNULL, vanilla);
        for (int i = 0; i < 3; i++) {
            method.visitVarInsn(ALOAD, 7); method.visitInsn(ICONST_0 + i); method.visitInsn(DALOAD); method.visitVarInsn(DSTORE, 1 + i * 2);
        }
        method.visitLabel(vanilla);
        method.visitFrame(F_APPEND, 1, new Object[] {"[D"}, 0, null);
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(DLOAD, 1); method.visitVarInsn(DLOAD, 3); method.visitVarInsn(DLOAD, 5);
        method.visitMethodInsn(INVOKESPECIAL, node.superName, name, "(DDD)V", false);
        method.visitVarInsn(ALOAD, 0); method.visitVarInsn(ALOAD, 7);
        method.visitMethodInsn(INVOKESTATIC, HOOK, "afterMove", "(Ljava/lang/Object;[D)V", false);
        method.visitInsn(RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        return method;
    }
}
