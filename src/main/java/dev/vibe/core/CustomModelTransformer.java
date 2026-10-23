package dev.vibe.core;

import java.util.ArrayList;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.FrameNode;

/**
 * Lets CustomModelRenderer draw held swords: inside RenderItem.renderItemModelTransform the
 * call that draws the item's quads, renderItem(stack, model), is routed through a hook that
 * also receives the camera transform type. Vanilla has already applied that transform, so a
 * custom model drawn there is positioned exactly like the sword sprite in first and third person.
 */
public final class CustomModelTransformer implements IClassTransformer, Opcodes {
    private static final String HOOK = "dev/vibe/ui/render/CustomModelRenderer";
    private static final String RENDER_ITEM = "net/minecraft/client/renderer/entity/RenderItem";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        String mapped = transformedName == null ? "" : transformedName.replace('.', '/');
        String raw = name == null ? "" : name.replace('.', '/');
        boolean cape = mapped.equals("net/minecraft/client/renderer/entity/layers/LayerCape") || raw.equals("bkp");
        if (!cape && !RENDER_ITEM.equals(mapped) && !"bjh".equals(raw)) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean changed = false;
        for (Object entry : new ArrayList<Object>(node.methods)) {
            MethodNode method = (MethodNode) entry;
            if (cape && (named(method.name, "doRenderLayer", "func_177141_a") || method.name.equals("func_177166_a")) && method.desc.endsWith(";FFFFFFF)V")) {
                LabelNode vanilla = new LabelNode(); InsnList guard = new InsnList();
                guard.add(new VarInsnNode(ALOAD, 1));
                guard.add(new MethodInsnNode(INVOKESTATIC, HOOK, "hidesCape", "(Ljava/lang/Object;)Z", false));
                guard.add(new JumpInsnNode(IFEQ, vanilla)); guard.add(new InsnNode(RETURN)); guard.add(vanilla);
                guard.add(new FrameNode(F_SAME, 0, null, 0, null)); method.instructions.insert(guard); changed = true;
            } else if (!cape && named(method.name, "renderItemIntoGUI", "func_180450_b")
                    && (method.desc.equals("(Lnet/minecraft/item/ItemStack;II)V") || method.desc.equals("(Lzx;II)V"))) {
                changed |= routeGuiItem(method);
            } else if (named(method.name, "renderItemModelTransform", "func_175040_a")
                    && (method.desc.equals("(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resources/model/IBakedModel;"
                            + "Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V")
                        || method.desc.equals("(Lzx;Lboq;Lbgr$b;)V"))) {
                changed |= routeRenderItem(method);
            } else if (named(method.name, "renderItemModelForEntity", "func_175049_a")
                    && (method.desc.equals("(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;"
                            + "Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V")
                        || method.desc.equals("(Lzx;Lpr;Lbgr$b;)V"))) {
                InsnList holder = new InsnList();
                holder.add(new VarInsnNode(ALOAD, 2));
                holder.add(new MethodInsnNode(INVOKESTATIC, HOOK, "holder", "(Ljava/lang/Object;)V", false));
                method.instructions.insert(holder);
                changed = true;
            }
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean named(String value, String mcp, String srg) {
        return value.equals(mcp) || value.equals(srg) || value.equals("a");
    }

    private static boolean routeGuiItem(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if ((call.name.equals("renderItem") || call.name.equals("func_180454_a") || call.name.equals("a"))
                    && (call.desc.equals("(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resources/model/IBakedModel;)V") || call.desc.equals("(Lzx;Lboq;)V"))) {
                method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HOOK, "renderGuiItem", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V", false));
                return true;
            }
        }
        return false;
    }

    private static boolean routeRenderItem(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode) || instruction.getOpcode() != INVOKEVIRTUAL) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            boolean owner = call.owner.equals(RENDER_ITEM) || call.owner.equals("bjh");
            boolean target = (call.name.equals("renderItem") || call.name.equals("func_180454_a") || call.name.equals("a"))
                    && (call.desc.equals("(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resources/model/IBakedModel;)V")
                        || call.desc.equals("(Lzx;Lboq;)V"));
            if (!owner || !target) continue;
            // Stack before: renderer, stack, model. Add the transform type (argument 3) and call the hook.
            method.instructions.insertBefore(call, new VarInsnNode(ALOAD, 3));
            method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HOOK, "renderItem",
                    "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V", false));
            return true;
        }
        return false;
    }
}
