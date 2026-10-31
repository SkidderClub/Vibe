package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Replaces artwork only. Forge retains its splash thread, context locks and display semaphore. */
public final class BrandingTransformer implements IClassTransformer, Opcodes {
    private static final String START = "dev/vibe/core/startup/GhostStartup";
    @Override public byte[] transform(String name, String mapped, byte[] bytes) {
        if (bytes == null) return null;
        boolean minecraft = "net.minecraft.client.Minecraft".equals(mapped) || "net.minecraft.client.Minecraft".equals(name) || "ave".equals(name);
        boolean splash = name.startsWith("net.minecraftforge.fml.client.SplashProgress$");
        boolean progress = name.equals("net.minecraftforge.fml.common.ProgressManager$ProgressBar");
        if (!minecraft && !splash && !progress) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        boolean changed = false;
        for (Object entry : node.methods) {
            MethodNode m = (MethodNode)entry;
            if (hooked(m)) continue;
            if (minecraft) {
                if (m.desc.equals("()V") && (named(m.name, "startGame", "func_71384_a") || calls(m, "net/minecraftforge/fml/client/FMLClientHandler", "beginMinecraftLoading"))) {
                    m.instructions.insert(call(START, "begin", "()V"));
                    beforeReturns(m, "finish"); changed = true;
                } else if (m.desc.equals("()V") && (named(m.name, "setWindowIcon", "func_175594_ao") || calls(m, "org/lwjgl/opengl/Display", "setIcon"))) {
                    guard(m, "dev/vibe/core/startup/GhostWindowIcon", "apply"); changed = true;
                } else if (m.desc.endsWith(")V") && (named(m.name, "drawSplashScreen", "func_180510_a")
                        || (m.desc.startsWith("(L") && Type.getArgumentTypes(m.desc).length == 1
                        && constant(m, "logo") && calls(m, "javax/imageio/ImageIO", "read")))) {
                    guard(m, START, "drawVanilla"); changed = true;
                }
            } else if (progress && m.name.equals("step") && m.desc.equals("(Ljava/lang/String;)V")) {
                beforeReturns(m, "progressChanged"); changed = true;
            } else if (splash && m.name.equals("run") && m.desc.equals("()V")) {
                MethodInsnNode clear = null; FieldInsnNode semaphore = null;
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode c = (MethodInsnNode)insn;
                        if (c.owner.equals("org/lwjgl/opengl/GL11") && c.name.equals("glClear") && c.desc.equals("(I)V")) clear = c;
                    }
                    if (clear != null && insn instanceof FieldInsnNode) {
                        FieldInsnNode f = (FieldInsnNode)insn;
                        if (f.getOpcode() == GETSTATIC && f.desc.equals("Ljava/util/concurrent/Semaphore;")) { semaphore = f; break; }
                    }
                }
                if (clear != null && semaphore != null) {
                    LabelNode present = new LabelNode(); m.instructions.insertBefore(semaphore, present);
                    m.instructions.set(clear, call(START, "renderForge", "(I)Z"));
                    // The int previously passed to glClear is consumed by renderForge.
                    for (AbstractInsnNode insn : m.instructions.toArray()) if (insn instanceof MethodInsnNode && ((MethodInsnNode)insn).owner.equals(START)) {
                        m.instructions.insert(insn, new JumpInsnNode(IFNE, present)); break;
                    }
                    changed = true;
                }
            } else if (splash && m.name.equals("clearGL") && m.desc.equals("()V")) {
                m.instructions.insert(call(START, "release", "()V")); changed = true;
            }
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private static boolean named(String name, String... names) { for (String s : names) if (s.equals(name)) return true; return false; }
    private static boolean calls(MethodNode m, String owner, String name) {
        for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof MethodInsnNode && ((MethodInsnNode)i).owner.equals(owner) && ((MethodInsnNode)i).name.equals(name)) return true;
        return false;
    }
    private static boolean constant(MethodNode m, String value) {
        for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof LdcInsnNode && value.equals(((LdcInsnNode)i).cst)) return true;
        return false;
    }
    private static boolean hooked(MethodNode m) {
        for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof MethodInsnNode && ((MethodInsnNode)i).owner.startsWith("dev/vibe/core/startup/")) return true;
        return false;
    }
    private static MethodInsnNode call(String owner, String name, String desc) { return new MethodInsnNode(INVOKESTATIC, owner, name, desc, false); }
    private static void guard(MethodNode m, String owner, String name) {
        LabelNode vanilla = new LabelNode(); InsnList list = new InsnList();
        list.add(call(owner, name, "()Z")); list.add(new JumpInsnNode(IFEQ, vanilla)); list.add(new InsnNode(RETURN)); list.add(vanilla);
        m.instructions.insert(list);
    }
    private static void beforeReturns(MethodNode m, String hook) {
        for (AbstractInsnNode insn : m.instructions.toArray()) if (insn.getOpcode() == RETURN) m.instructions.insertBefore(insn, call(START, hook, "()V"));
    }
}
