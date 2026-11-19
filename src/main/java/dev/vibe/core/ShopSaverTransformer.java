package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Only intercept a local dismissal; server close packets retain their native close path. */
public final class ShopSaverTransformer implements IClassTransformer, Opcodes {
    private static final String HOOK = "dev/vibe/module/impl/client/ShopSaverModule";
    @Override public byte[] transform(String name, String mapped, byte[] bytes) {
        if (bytes == null) return null;
        boolean player = named(name, mapped, "net.minecraft.client.entity.EntityPlayerSP", "bew");
        boolean container = named(name, mapped, "net.minecraft.client.gui.inventory.GuiContainer", "ayl");
        if (!player && !container) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); boolean changed = false;
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            if (!method.desc.equals("()V") || !(player
                    ? method.name.equals("closeScreen") || method.name.equals("func_71053_j") || method.name.equals("n")
                    : method.name.equals("onGuiClosed") || method.name.equals("func_146281_b") || method.name.equals("m"))) continue;
            boolean installed = false;
            for (AbstractInsnNode i : method.instructions.toArray())
                if (i instanceof MethodInsnNode && ((MethodInsnNode)i).owner.equals(HOOK)) installed = true;
            if (installed) continue;
            LabelNode vanilla = new LabelNode(); InsnList head = new InsnList();
            head.add(new VarInsnNode(ALOAD, 0));
            head.add(new MethodInsnNode(INVOKESTATIC, HOOK, player ? "hideHook" : "keepContainerHook", "(Ljava/lang/Object;)Z", false));
            head.add(new JumpInsnNode(IFEQ, vanilla)); head.add(new InsnNode(RETURN)); head.add(vanilla);
            head.add(new FrameNode(F_SAME, 0, null, 0, null)); method.instructions.insert(head); changed = true;
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private static boolean named(String name, String mapped, String full, String raw) {
        return full.equals(name) || full.equals(mapped) || raw.equals(name);
    }
}
