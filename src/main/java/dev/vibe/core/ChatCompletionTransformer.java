package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Keeps GuiChat's common-prefix and cycling machinery; only dot-command responses are local. */
public final class ChatCompletionTransformer implements IClassTransformer, Opcodes {
    private static final String HOOK = "dev/vibe/command/VibeChatGui";

    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        if (!"net.minecraft.client.gui.GuiChat".equals(transformedName)
                && !"net.minecraft.client.gui.GuiChat".equals(name) && !"awv".equals(name)) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            boolean request = method.desc.equals("(Ljava/lang/String;Ljava/lang/String;)V");
            boolean complete = method.desc.equals("()V") && named(method.name, "autocompletePlayerNames", "func_146404_p_", "a");
            boolean response = method.desc.equals("([Ljava/lang/String;)V");
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (request && call.getOpcode() == INVOKEVIRTUAL
                        && (call.owner.equals("net/minecraft/client/network/NetHandlerPlayClient") || call.owner.equals("bcy"))
                        && (call.desc.equals("(Lnet/minecraft/network/Packet;)V") || call.desc.equals("(Lff;)V"))) {
                    method.instructions.insertBefore(call, new VarInsnNode(ALOAD, 0));
                    method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HOOK, "sendAutocomplete",
                            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V", false));
                } else if ((complete || response) && call.getOpcode() == INVOKEVIRTUAL && call.desc.equals("(IIZ)I")) {
                    method.instructions.insertBefore(call, new VarInsnNode(ALOAD, 0));
                    method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HOOK, "completionStart",
                            "(Ljava/lang/Object;IIZLjava/lang/Object;)I", false));
                } else if (response && call.owner.equals("java/lang/String") && call.name.equals("substring") && call.desc.equals("(I)Ljava/lang/String;")) {
                    method.instructions.insertBefore(call, new VarInsnNode(ALOAD, 0));
                    method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HOOK, "completionPrefix",
                            "(Ljava/lang/String;ILjava/lang/Object;)Ljava/lang/String;", false));
                }
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private static boolean named(String name, String mcp, String srg, String raw) {
        return name.equals(mcp) || name.equals(srg) || name.equals(raw);
    }
}
