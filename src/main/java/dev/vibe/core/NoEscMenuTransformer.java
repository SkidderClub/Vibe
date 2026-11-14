package dev.vibe.core;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Distinguish native keyboard Escape from focus-loss pause requests. */
public final class NoEscMenuTransformer implements net.minecraft.launchwrapper.IClassTransformer, Opcodes {
    private static final String HOOK = "dev/vibe/module/impl/world/NoEscMenuModule";
    @Override public byte[] transform(String name, String mapped, byte[] bytes) {
        if (bytes == null || !("net.minecraft.client.Minecraft".equals(mapped) || "net.minecraft.client.Minecraft".equals(name) || "ave".equals(name))) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        for (Object value : node.methods) for (AbstractInsnNode instruction : ((MethodNode) value).instructions.toArray())
            if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).owner.equals(HOOK)) return bytes;
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (method.desc.equals("()V") && pause(method.name)) {
                LabelNode resume = new LabelNode(); InsnList guard = new InsnList();
                guard.add(new MethodInsnNode(INVOKESTATIC, HOOK, "blocksMenuHook", "()Z", false));
                guard.add(new JumpInsnNode(IFEQ, resume)); guard.add(new InsnNode(RETURN)); guard.add(resume);
                guard.add(new FrameNode(F_SAME, 0, null, 0, null)); method.instructions.insert(guard);
            }
            String original = method.name.replace("vibe$hotbar$", "");
            if (!method.desc.equals("()V") || !(original.equals("runTick") || original.equals("func_71407_l") || original.equals("s"))) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == INVOKEVIRTUAL && call.desc.equals("()V") && pause(call.name)
                        && (call.owner.equals("net/minecraft/client/Minecraft") || call.owner.equals("ave")))
                    method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HOOK, "openManualHook", "(Ljava/lang/Object;)V", false));
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private static boolean pause(String name) { return name.equals("displayInGameMenu") || name.equals("func_71385_j") || name.equals("p"); }
}
