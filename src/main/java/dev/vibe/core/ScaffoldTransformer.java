package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Forge equivalents of Gothaj's patched Minecraft event sites; see provenance. */
public final class ScaffoldTransformer implements IClassTransformer {
    private static final String HOOK = "dev/vibe/module/impl/ScaffoldModule";

    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        String mapped = transformedName == null ? "" : transformedName;
        String raw = name == null ? "" : name;
        final int target = mapped.equals("net.minecraft.client.entity.EntityPlayerSP") || raw.equals("bew") ? 1
                : mapped.equals("net.minecraft.entity.EntityLivingBase") || raw.equals("pr") ? 2
                : mapped.equals("net.minecraft.client.renderer.EntityRenderer") || raw.equals("bfk") ? 3
                : mapped.equals("net.minecraft.client.renderer.ItemRenderer") || raw.equals("bfn") ? 4 : 0;
        if (target == 0) return bytes;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor next = super.visitMethod(access, name, desc, signature, exceptions);
                final boolean update = target == 1 && desc.equals("()V") && matches(name, "onUpdate", "func_70071_h_", "t_");
                final boolean living = target == 1 && desc.equals("()V") && matches(name, "onLivingUpdate", "func_70636_d", "m");
                final boolean jump = target == 2 && desc.equals("()V") && matches(name, "jump", "func_70664_aZ", "bF");
                final boolean render = target == 3 && desc.equals("(FJ)V") && matches(name, "renderWorld", "func_78471_a", "a");
                final boolean item = target == 4 && desc.equals("(F)V") && matches(name, "renderItemInFirstPerson", "func_78440_a", "a");
                if (!update && !living && !jump && !render && !item) return next;
                MethodVisitor sink = living ? new SprintThresholdVisitor(next, access, name, desc, signature, exceptions) : next;
                return new MethodVisitor(Opcodes.ASM5, sink) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                        if (update && opcode == Opcodes.INVOKESPECIAL && descriptor.equals("()V")
                                && matches(method, "onUpdate", "func_70071_h_", "t_")) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "normalUpdateHook", "(Ljava/lang/Object;)V", false);
                        }
                        if (render && descriptor.equals("(F)V") && matches(method, "getMouseOver", "func_78473_a", "a"))
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "mouseOverHook", "()V", false);
                        if (living && descriptor.equals("(Z)V") && matches(method, "setSprinting", "func_70031_b", "d")) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "sprintStateHook", "(ZLjava/lang/Object;)Z", false);
                        }
                        super.visitMethodInsn(opcode, owner, method, descriptor, itf);
                        if (jump && descriptor.equals("()F") && matches(method, "getJumpUpwardsMotion", "func_175134_bD", "bE")) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "jumpMotionHook", "(FLjava/lang/Object;)F", false);
                        }
                    }

                    @Override public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                        super.visitFieldInsn(opcode, owner, field, descriptor);
                        if (opcode != Opcodes.GETFIELD) return;
                        if (item && matches(field, "itemToRender", "field_78453_b", "d")
                                && (descriptor.equals("Lnet/minecraft/item/ItemStack;") || descriptor.equals("Lzx;"))) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "heldItemHook", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                            super.visitTypeInsn(Opcodes.CHECKCAST, descriptor.substring(1, descriptor.length() - 1));
                        }
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    /** Only the three post-input sprint comparisons use Gothaj's omniSprint.
     * The previous-input flag and vanilla item-use multiplication keep their values. */
    private static final class SprintThresholdVisitor extends MethodNode {
        private final MethodVisitor delegate;
        SprintThresholdVisitor(MethodVisitor delegate, int access, String name, String desc, String signature, String[] exceptions) {
            super(Opcodes.ASM5, access, name, desc, signature, exceptions);
            this.delegate = delegate;
        }
        @Override public void visitEnd() {
            for (AbstractInsnNode insn : instructions.toArray()) {
                if (!(insn instanceof FieldInsnNode) || insn.getOpcode() != Opcodes.GETFIELD) continue;
                FieldInsnNode field = (FieldInsnNode) insn;
                if (!field.desc.equals("F") || !matches(field.name, "moveForward", "field_78900_b", "b")
                        || !(field.owner.equals("net/minecraft/util/MovementInput") || field.owner.equals("beu"))) continue;
                AbstractInsnNode threshold = nextCode(insn), comparison = nextCode(threshold);
                // MoveFix already wraps the previous-input read, so that read
                // does not directly precede a comparison. Multiplication does not either.
                if (threshold == null || threshold.getOpcode() != Opcodes.FLOAD || comparison == null
                        || (comparison.getOpcode() != Opcodes.FCMPL && comparison.getOpcode() != Opcodes.FCMPG)) continue;
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "sprintForwardHook", "(FLjava/lang/Object;)F", false));
                instructions.insert(insn, hook);
            }
            accept(delegate);
        }
        private static AbstractInsnNode nextCode(AbstractInsnNode instruction) {
            if (instruction == null) return null;
            do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0);
            return instruction;
        }
    }

    private static boolean matches(String value, String mcp, String srg, String obfuscated) {
        return value.equals(mcp) || value.equals(srg) || value.equals(obfuscated);
    }
}
