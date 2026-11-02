package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** The standalone arena replaces the world image, while Minecraft still renders and drives its GuiScreen. */
public final class RecorderRenderTransformer implements IClassTransformer,Opcodes {
    private static final String HOOK="dev/vibe/ui/render/AuraArenaRenderer";
    @Override public byte[] transform(String name,String mapped,byte[] bytes){
        if(bytes==null||!"net.minecraft.client.renderer.EntityRenderer".equals(mapped)&&!"net.minecraft.client.renderer.EntityRenderer".equals(name)&&!"bfk".equals(name))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(Object value:node.methods){MethodNode method=(MethodNode)value;
            if(!method.desc.equals("(FJ)V")||!(method.name.equals("renderWorld")||method.name.equals("func_78471_a")||method.name.equals("b")))continue;
            boolean hooked=false;for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals(HOOK)&&((MethodInsnNode)i).name.equals("recorderOwnsFrame"))hooked=true;if(hooked)continue;
            LabelNode vanilla=new LabelNode();InsnList head=new InsnList();head.add(new MethodInsnNode(INVOKESTATIC,HOOK,"recorderOwnsFrame","()Z",false));head.add(new JumpInsnNode(IFEQ,vanilla));head.add(new InsnNode(RETURN));head.add(vanilla);head.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.insert(head);changed=true;
        }
        if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
