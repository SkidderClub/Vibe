package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Reuses only item geometry; native transforms, lighting, effects and overlays still run. */
public final class HudItemsTransformer implements IClassTransformer,Opcodes {
    @Override public byte[] transform(String name,String transformedName,byte[] bytes) {
        if(bytes==null)return null;
        if(!"net.minecraft.client.renderer.entity.RenderItem".equals(transformedName)&&!"bjh".equals(name))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(Object value:node.methods) {
            MethodNode method=(MethodNode)value;
            if(method.desc.equals("(Lnet/minecraft/client/resources/IResourceManager;)V")||method.desc.equals("(Lbni;)V")) {
                method.instructions.insert(new MethodInsnNode(INVOKESTATIC,"dev/vibe/ui/render/HudItemGeometryRenderer","clear","()V",false));
                changed=true;continue;
            }
            if(!method.desc.equals("(Lnet/minecraft/client/resources/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V")
                    &&!method.desc.equals("(Lboq;ILzx;)V"))continue;
            LabelNode nativeDraw=new LabelNode();InsnList hook=new InsnList();
            hook.add(new VarInsnNode(ALOAD,1));hook.add(new VarInsnNode(ILOAD,2));hook.add(new VarInsnNode(ALOAD,3));
            hook.add(new MethodInsnNode(INVOKESTATIC,"dev/vibe/ui/render/HudItemGeometryRenderer","draw","(Ljava/lang/Object;ILjava/lang/Object;)Z",false));
            hook.add(new JumpInsnNode(IFEQ,nativeDraw));hook.add(new InsnNode(RETURN));hook.add(nativeDraw);
            hook.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.insert(hook);changed=true;
        }
        if(!changed)return bytes;
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
