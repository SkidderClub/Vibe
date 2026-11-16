package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Replace only the two private block-pose transforms; native model/glint/lighting stay intact. */
public final class NovolineAnimationTransformer implements IClassTransformer, Opcodes {
    private static final String HOOK="dev/vibe/ui/render/NovolineAnimations";
    @Override public byte[] transform(String name,String mapped,byte[] bytes){
        if(bytes==null||!("net.minecraft.client.renderer.ItemRenderer".equals(mapped)||"net.minecraft.client.renderer.ItemRenderer".equals(name)||"bfn".equals(name)))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(Object entry:node.methods){
            MethodNode method=(MethodNode)entry;
            boolean transform=method.desc.equals("(FF)V")&&(method.name.equals("transformFirstPersonItem")||method.name.equals("func_178096_b")||method.name.equals("b"));
            boolean block=method.desc.equals("()V")&&(method.name.equals("doBlockTransformations")||method.name.equals("func_178103_d")||method.name.equals("d"));
            if(!transform&&!block)continue;
            for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals(HOOK))return bytes;
            InsnList hook=new InsnList();
            if(transform){hook.add(new VarInsnNode(FLOAD,1));hook.add(new VarInsnNode(FLOAD,2));}
            hook.add(new MethodInsnNode(INVOKESTATIC,HOOK,transform?"transform":"active",transform?"(FF)Z":"()Z",false));
            LabelNode vanilla=new LabelNode();hook.add(new JumpInsnNode(IFEQ,vanilla));hook.add(new InsnNode(RETURN));hook.add(vanilla);
            hook.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.insert(hook);changed=true;
        }
        if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
