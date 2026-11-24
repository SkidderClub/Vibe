package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Lets the JVM collect normally during world transitions. */
public final class WorldLoadTransformer implements IClassTransformer {
    private static final String MINECRAFT = "net.minecraft.client.Minecraft";

    @Override
    public byte[] transform(String name, String mapped, byte[] bytes) {
        if (bytes == null || !(MINECRAFT.equals(mapped) || MINECRAFT.equals(name) || "ave".equals(name))) return bytes;

        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, 0);
        boolean changed = false;
        for (Object entry : type.methods) {
            MethodNode method = (MethodNode) entry;
            if (!worldTransition(method)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode) || instruction.getOpcode() != Opcodes.INVOKESTATIC) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!"java/lang/System".equals(call.owner) || !"gc".equals(call.name) || !"()V".equals(call.desc)) continue;
                // Java 8 may stop all threads for a full collection here, even with plenty of free heap.
                method.instructions.remove(call);
                changed = true;
            }
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(0);
        type.accept(writer);
        return writer.toByteArray();
    }

    private static boolean worldTransition(MethodNode method) {
        switch (method.name) {
            case "loadWorld":
            case "func_71353_a":
            case "func_71403_a":
            case "launchIntegratedServer":
            case "func_71371_a":
            case "a":
                switch (method.desc) {
                    case "(Lnet/minecraft/client/multiplayer/WorldClient;)V":
                    case "(Lbdb;)V":
                    case "(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V":
                    case "(Lbdb;Ljava/lang/String;)V":
                    case "(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V":
                    case "(Ljava/lang/String;Ljava/lang/String;Ladp;)V":
                        return true;
                    default:
                        return false;
                }
            default:
                return false;
        }
    }
}
