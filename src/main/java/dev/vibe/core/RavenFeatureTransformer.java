package dev.vibe.core;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** The Vanilla-use, scaffold packet and saturation injection points from RavenBS. */
public final class RavenFeatureTransformer implements net.minecraft.launchwrapper.IClassTransformer, Opcodes {
    private static final String AURA = "dev/vibe/module/impl/combat/KillAuraModule";
    private static final String SCAFFOLD = "dev/vibe/module/impl/world/ScaffoldModule";
    private static final String SATURATION = "dev/vibe/ui/effect/SaturationRenderer";

    @Override public byte[] transform(String name, String mapped, byte[] bytes) {
        if (bytes == null) return null;
        String raw = name == null ? "" : name.replace('.', '/');
        String full = mapped == null ? raw : mapped.replace('.', '/');
        boolean renderer = full.equals("net/minecraft/client/renderer/EntityRenderer") || raw.equals("bfk");
        boolean handler = full.equals("net/minecraft/client/network/NetHandlerPlayClient") || raw.equals("bcy");
        boolean controller = full.equals("net/minecraft/client/multiplayer/PlayerControllerMP") || raw.equals("bda");
        boolean minecraft = full.equals("net/minecraft/client/Minecraft") || raw.equals("ave");
        if (!renderer && !handler && !controller && !minecraft) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (Object object : node.methods) {
            MethodNode method = (MethodNode) object;
            if (handler && named(method, "addToSendQueue", "func_147297_a", "a")
                    && (method.desc.equals("(Lnet/minecraft/network/Packet;)V") || method.desc.equals("(Lff;)V"))) {
                guard(method, SCAFFOLD, "hypixelPacketHook", true, true);
            }
            if (minecraft && method.desc.equals("()V") && named(method, "rightClickMouse", "func_147121_ag", "ax")) {
                guard(method, AURA, "hypixelRightClickHook", false, false);
            }
            if (controller && named(method, "sendUseItem", "func_78769_a", "a")
                    && (method.desc.equals("(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;)Z")
                    || method.desc.equals("(Lwn;Ladm;Lzx;)Z"))) {
                guard(method, AURA, "hypixelUseHook", false, false);
            }
            if (!renderer) continue;
            if (method.desc.equals("()Z") && named(method, "isShaderActive", "func_147702_a", "a")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction.getOpcode() == IRETURN)
                    method.instructions.insertBefore(instruction, new MethodInsnNode(INVOKESTATIC, SATURATION, "shaderActiveHook", "(Z)Z", false));
            }
            if ((method.desc.equals("()Lnet/minecraft/client/shader/ShaderGroup;") || method.desc.equals("()Lblr;"))
                    && named(method, "getShaderGroup", "func_147706_e", "f")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction.getOpcode() == ARETURN) {
                    InsnList hook = new InsnList();
                    hook.add(new MethodInsnNode(INVOKESTATIC, SATURATION, "shaderGroupHook", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                    hook.add(new TypeInsnNode(CHECKCAST, Type.getReturnType(method.desc).getInternalName()));
                    method.instructions.insertBefore(instruction, hook);
                }
            }
            boolean resize = method.desc.equals("(II)V") && named(method, "updateShaderGroupSize", "func_147704_a", "a");
            // updateCameraAndRender is 'a(FJ)V', renderWorld is 'b(FJ)V'.
            boolean render = method.desc.equals("(FJ)V") && named(method, "updateCameraAndRender", "func_181560_a", "a");
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!call.owner.equals("net/minecraft/client/renderer/RenderGlobal") && !call.owner.equals("bfr")) continue;
                if (resize && call.desc.equals("(II)V") && named(call.name, "createBindEntityOutlineFbs", "func_72720_a", "a")) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(ILOAD, 1)); hook.add(new VarInsnNode(ILOAD, 2));
                    hook.add(new MethodInsnNode(INVOKESTATIC, SATURATION, "resizeHook", "(II)V", false));
                    method.instructions.insertBefore(instruction, hook);
                }
                if (render && call.desc.equals("()V") && named(call.name, "renderEntityOutlineFramebuffer", "func_174975_c", "c")) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(FLOAD, 1));
                    hook.add(new MethodInsnNode(INVOKESTATIC, SATURATION, "render", "(F)V", false));
                    method.instructions.insert(instruction, hook);
                }
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void guard(MethodNode method, String owner, String hook, boolean packet, boolean allow) {
        InsnList instructions = new InsnList();
        LabelNode resume = new LabelNode();
        if (packet) instructions.add(new VarInsnNode(ALOAD, 1));
        instructions.add(new MethodInsnNode(INVOKESTATIC, owner, hook, packet ? "(Ljava/lang/Object;)Z" : "()Z", false));
        instructions.add(new JumpInsnNode(allow ? IFNE : IFEQ, resume));
        if (Type.getReturnType(method.desc).getSort() == Type.BOOLEAN) {
            instructions.add(new InsnNode(ICONST_0));
            instructions.add(new InsnNode(IRETURN));
        } else instructions.add(new InsnNode(RETURN));
        instructions.add(resume);
        instructions.add(new FrameNode(F_SAME, 0, null, 0, null));
        method.instructions.insert(instructions);
    }
    private static boolean named(MethodNode method, String... names) { return named(method.name, names); }
    private static boolean named(String value, String... names) {
        for (String name : names) if (value.equals(name)) return true;
        return false;
    }
}
