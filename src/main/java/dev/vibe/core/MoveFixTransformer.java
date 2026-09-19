package dev.vibe.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Ports the three injection points used by Tarasande to Forge 1.8.9:
 * velocity yaw, jump yaw, and the walking packet's fake-rotation scope.
 */
public final class MoveFixTransformer implements net.minecraft.launchwrapper.IClassTransformer {
    private static final String ENTITY = "net/minecraft/entity/Entity";
    private static final String LIVING = "net/minecraft/entity/EntityLivingBase";
    private static final String PLAYER = "net/minecraft/client/entity/EntityPlayerSP";
    private static final String ENTITY_RENDERER = "net/minecraft/client/renderer/EntityRenderer";
    private static final String MINECRAFT = "net/minecraft/client/Minecraft";
    private static final String PLAYER_RENDERER = "net/minecraft/client/renderer/entity/RenderPlayer";
    private static final String AURA_HOOK = "dev/vibe/module/impl/KillAuraModule";
    private static final String INPUT = "net/minecraft/util/MovementInput";
    private static final String HOOK = "dev/vibe/module/impl/MoveFixModule";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        String raw = name == null ? "" : name.replace('.', '/');
        String mapped = transformedName == null ? "" : transformedName.replace('.', '/');
        int target = ENTITY.equals(raw) || ENTITY.equals(mapped) || "pk".equals(raw) ? 1
                : LIVING.equals(raw) || LIVING.equals(mapped) || "pr".equals(raw) ? 2
                : PLAYER.equals(raw) || PLAYER.equals(mapped) || "bew".equals(raw) ? 3
                : ENTITY_RENDERER.equals(raw) || ENTITY_RENDERER.equals(mapped) || "bfk".equals(raw) ? 4
                : MINECRAFT.equals(raw) || MINECRAFT.equals(mapped) || "ave".equals(raw) ? 5
                : PLAYER_RENDERER.equals(raw) || PLAYER_RENDERER.equals(mapped) || "bln".equals(raw) ? 6
                : "net/minecraft/client/renderer/entity/RendererLivingEntity".equals(mapped) || "bjl".equals(raw) ? 7
                : "net/minecraft/client/multiplayer/PlayerControllerMP".equals(mapped) || "bda".equals(raw) ? 8 : 0;
        if (target == 0) {
            return bytes;
        }
        try {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String method, String descriptor,
                        String signature, String[] exceptions) {
                    MethodVisitor delegate = super.visitMethod(access, method, descriptor, signature, exceptions);
                    if (target == 7 && ("renderModel".equals(method) || "func_77036_a".equals(method) || "a".equals(method))
                            && ("(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V".equals(descriptor) || "(Lpr;FFFFFF)V".equals(descriptor))) {
                        return new InvisibleModelVisitor(delegate);
                    }
                    if (target == 8 && "()V".equals(descriptor)
                            && ("syncCurrentPlayItem".equals(method) || "func_78750_j".equals(method) || "n".equals(method)))
                        return new ToolSyncVisitor(delegate);
                    if (target == 5 && "()V".equals(descriptor)
                            && ("clickMouse".equals(method) || "func_147116_af".equals(method) || "aw".equals(method))) {
                        return new ClickCooldownVisitor(delegate);
                    }
                    if (target == 6 && ("setModelVisibilities".equals(method) || "func_177137_d".equals(method) || "d".equals(method))
                            && ("(Lnet/minecraft/client/entity/AbstractClientPlayer;)V".equals(descriptor) || "(Lbet;)V".equals(descriptor))) {
                        return new BlockModelVisitor(delegate);
                    }
                    if (target == 5 && "()V".equals(descriptor)
                            && ("runTick".equals(method) || "func_71407_l".equals(method) || "s".equals(method))) {
                        return new InputVisitor(delegate);
                    }
                    if (target == 1 && isLook(method, descriptor)) {
                        return new LookVisitor(delegate);
                    }
                    if (target == 1 && isMoveFlying(method, descriptor)) {
                        return new YawVisitor(delegate);
                    }
                    if (target == 2 && isJump(method, descriptor)) {
                        return new YawVisitor(delegate);
                    }
                    if (target == 3 && isWalkingPacket(method, descriptor)) {
                        return new PacketVisitor(delegate);
                    }
                    if (target == 3 && isLivingUpdate(method, descriptor)) {
                        return new SprintVisitor(delegate);
                    }
                    if (target == 4 && isMouseOver(method, descriptor)) {
                        return new RaycastVisitor(delegate);
                    }
                    return delegate;
                }
            }, 0);
            byte[] result = writer.toByteArray();
            return target == 8 ? dev.vibe.core.ToolActionTransformer.transform(result) : result;
        } catch (Throwable ignored) {
            // A failed optional hook must never stop Forge from loading Vibe.
            return bytes;
        }
    }

    private static boolean isMoveFlying(String method, String descriptor) {
        return "(FFF)V".equals(descriptor)
                && ("moveFlying".equals(method) || "func_70060_a".equals(method) || "a".equals(method));
    }

    private static boolean isLook(String method, String descriptor) {
        return ("(F)Lnet/minecraft/util/Vec3;".equals(descriptor) || "(F)Laui;".equals(descriptor))
                && ("getLook".equals(method) || "func_70676_i".equals(method) || "d".equals(method));
    }

    private static boolean isJump(String method, String descriptor) {
        return "()V".equals(descriptor)
                && ("jump".equals(method) || "func_70664_aZ".equals(method) || "bF".equals(method));
    }

    private static boolean isWalkingPacket(String method, String descriptor) {
        return "()V".equals(descriptor) && ("onUpdateWalkingPlayer".equals(method)
                || "func_175161_p".equals(method) || "p".equals(method));
    }

    private static boolean isLivingUpdate(String method, String descriptor) {
        return "()V".equals(descriptor) && ("onLivingUpdate".equals(method)
                || "func_70636_d".equals(method) || "m".equals(method));
    }

    private static boolean isMouseOver(String method, String descriptor) {
        return "(F)V".equals(descriptor) && ("getMouseOver".equals(method)
                || "func_78473_a".equals(method) || "a".equals(method));
    }

    private static boolean isYaw(String owner, String field, String descriptor) {
        return "F".equals(descriptor) && ("rotationYaw".equals(field) || "field_70177_z".equals(field)
                || ((ENTITY.equals(owner) || LIVING.equals(owner) || "pk".equals(owner) || "pr".equals(owner))
                && "y".equals(field)));
    }

    private static boolean isForward(String owner, String field, String descriptor) {
        return (INPUT.equals(owner) || "beu".equals(owner)) && "F".equals(descriptor)
                && ("moveForward".equals(field) || "field_78900_b".equals(field) || "b".equals(field));
    }

    private static boolean isLookYaw(String owner, String field, String descriptor) {
        return "F".equals(descriptor) && ("rotationYaw".equals(field) || "prevRotationYaw".equals(field)
                || "field_70177_z".equals(field) || "field_70126_B".equals(field)
                || ((ENTITY.equals(owner) || "pk".equals(owner)) && ("y".equals(field) || "B".equals(field))));
    }

    private static boolean isLookPitch(String owner, String field, String descriptor) {
        return "F".equals(descriptor) && ("rotationPitch".equals(field) || "prevRotationPitch".equals(field)
                || "field_70125_A".equals(field) || "field_70127_C".equals(field)
                || ((ENTITY.equals(owner) || "pk".equals(owner)) && ("z".equals(field) || "C".equals(field))));
    }

    /** Runs after getMouseOver and physical input, before attack/use dispatch. */
    private static final class InputVisitor extends MethodVisitor {
        private boolean prepared;

        private InputVisitor(MethodVisitor delegate) { super(Opcodes.ASM5, delegate); }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
            if (!prepared && opcode == Opcodes.INVOKEVIRTUAL && "()Z".equals(descriptor)
                    && (PLAYER.equals(owner) || "bew".equals(owner))
                    && ("isUsingItem".equals(name) || "func_71039_bw".equals(name) || "bS".equals(name))) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, AURA_HOOK, "prepareInputHook", "()V", false);
                prepared = true;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, itf);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (opcode == Opcodes.PUTFIELD && "I".equals(descriptor) && (MINECRAFT.equals(owner) || "ave".equals(owner))
                    && ("leftClickCounter".equals(name) || "field_71429_W".equals(name) || "ag".equals(name))) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, AURA_HOOK, "guiClickCounterHook", "(I)I", false);
            }
            super.visitFieldInsn(opcode, owner, name, descriptor);
            if (opcode == Opcodes.GETFIELD && "Z".equals(descriptor)
                    && ("net/minecraft/client/gui/GuiScreen".equals(owner) || "axu".equals(owner))
                    && ("allowUserInput".equals(name) || "field_146291_p".equals(name) || "p".equals(name))) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, AURA_HOOK, "allowInputHook", "(Z)Z", false);
            }
        }
    }

    private static final class InvisibleModelVisitor extends MethodVisitor {
        InvisibleModelVisitor(MethodVisitor delegate) { super(Opcodes.ASM5, delegate); }
        @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if (("isInvisibleToPlayer".equals(name) || "func_98034_c".equals(name) || "f".equals(name))
                    && ("(Lnet/minecraft/entity/player/EntityPlayer;)Z".equals(desc) || "(Lwn;)Z".equals(desc))) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/vibe/ui/InvisibleModelHooks", "isInvisibleToPlayer",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
            } else super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
        @Override public void visitLdcInsn(Object value) {
            super.visitLdcInsn(value);
            if (Float.valueOf(.15F).equals(value)) {
                super.visitVarInsn(Opcodes.ALOAD, 1);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/vibe/ui/InvisibleModelHooks", "alpha", "(FLjava/lang/Object;)F", false);
            }
        }
    }

    private static final class ToolSyncVisitor extends MethodVisitor {
        ToolSyncVisitor(MethodVisitor delegate) { super(Opcodes.ASM5, delegate); }
        @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            super.visitFieldInsn(opcode, owner, name, desc);
            if (opcode == Opcodes.GETFIELD && "I".equals(desc)
                    && ("currentItem".equals(name) || "field_70461_c".equals(name) || ("wm".equals(owner) && "c".equals(name)))) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/vibe/module/impl/AutoToolModule", "serverSlotHook", "(I)I", false);
            }
        }
    }

    private static final class ClickCooldownVisitor extends MethodVisitor {
        ClickCooldownVisitor(MethodVisitor delegate) { super(Opcodes.ASM5, delegate); }
        @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (opcode == Opcodes.PUTFIELD && "I".equals(desc)
                    && ("leftClickCounter".equals(name) || "field_71429_W".equals(name) || "ag".equals(name))) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/vibe/module/impl/AutoClickerModule", "missCooldownHook", "(I)I", false);
            }
            super.visitFieldInsn(opcode, owner, name, desc);
            if (opcode == Opcodes.GETFIELD && "I".equals(desc)
                    && ("leftClickCounter".equals(name) || "field_71429_W".equals(name) || "ag".equals(name))) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/vibe/module/impl/AutoClickerModule", "missCooldownHook", "(I)I", false);
            }
        }
    }

    private static final class BlockModelVisitor extends MethodVisitor {
        private BlockModelVisitor(MethodVisitor delegate) { super(Opcodes.ASM5, delegate); }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.RETURN) {
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitVarInsn(Opcodes.ALOAD, 1);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/vibe/ui/AuraBlockVisual", "applyThirdPerson",
                        "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
            }
            super.visitInsn(opcode);
        }
    }

    private static final class YawVisitor extends MethodVisitor {
        private YawVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM5, delegate);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
            if (opcode == Opcodes.GETFIELD && isYaw(owner, field, descriptor)) {
                // Preserve one receiver for the hook: [Entity] becomes
                // [Entity, float] after GETFIELD, matching (Object, float).
                visitInsn(Opcodes.DUP);
                super.visitFieldInsn(opcode, owner, field, descriptor);
                visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "movementYawHook", "(Ljava/lang/Object;F)F", false);
                return;
            }
            super.visitFieldInsn(opcode, owner, field, descriptor);
        }
    }

    private static final class PacketVisitor extends MethodVisitor {
        private PacketVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM5, delegate);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            visitVarInsn(Opcodes.ALOAD, 0);
            visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "beginPacketRotationHook", "(Ljava/lang/Object;)V", false);
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.RETURN) {
                visitVarInsn(Opcodes.ALOAD, 0);
                visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "endPacketRotationHook", "(Ljava/lang/Object;)V", false);
            }
            super.visitInsn(opcode);
        }
    }

    private static final class LookVisitor extends MethodVisitor {
        private LookVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM5, delegate);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
            if (opcode == Opcodes.GETFIELD && isLookYaw(owner, field, descriptor)) {
                visitInsn(Opcodes.DUP);
                super.visitFieldInsn(opcode, owner, field, descriptor);
                visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "lookYawHook", "(Ljava/lang/Object;F)F", false);
                return;
            }
            if (opcode == Opcodes.GETFIELD && isLookPitch(owner, field, descriptor)) {
                visitInsn(Opcodes.DUP);
                super.visitFieldInsn(opcode, owner, field, descriptor);
                visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "lookPitchHook", "(Ljava/lang/Object;F)F", false);
                return;
            }
            super.visitFieldInsn(opcode, owner, field, descriptor);
        }
    }

    private static final class SprintVisitor extends MethodVisitor {
        private boolean firstForward = true;

        private SprintVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM5, delegate);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
            super.visitFieldInsn(opcode, owner, field, descriptor);
            if (firstForward && opcode == Opcodes.GETFIELD && isForward(owner, field, descriptor)) {
                firstForward = false;
                visitVarInsn(Opcodes.ALOAD, 0);
                visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "sprintForwardHook", "(FLjava/lang/Object;)F", false);
            }
        }
    }

    private static final class RaycastVisitor extends MethodVisitor {
        private RaycastVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM5, delegate);
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.RETURN) {
                visitVarInsn(Opcodes.FLOAD, 1);
                visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "raycastHook", "(F)V", false);
            }
            super.visitInsn(opcode);
        }
    }
}
