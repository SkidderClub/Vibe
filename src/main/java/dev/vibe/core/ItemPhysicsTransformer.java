package dev.vibe.core;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Replaces only the native entity-item transform/count helper; native models, glint and stack copies remain. */
public final class ItemPhysicsTransformer implements IClassTransformer,Opcodes{
    @Override public byte[] transform(String name,String mapped,byte[] bytes){
        if(bytes==null||!"net.minecraft.client.renderer.entity.RenderEntityItem".equals(mapped))return bytes;
        ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);
        for(Object entry:c.methods){MethodNode m=(MethodNode)entry;
            if(!m.desc.contains(";DDDFL")||!m.desc.endsWith(";)I"))continue;
            for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode&&((MethodInsnNode)n).owner.equals("dev/vibe/module/impl/visual/ItemPhysicsModule"))return bytes;
            InsnList hook=new InsnList();LabelNode nativeCode=new LabelNode();hook.add(new VarInsnNode(ALOAD,1));hook.add(new VarInsnNode(DLOAD,2));hook.add(new VarInsnNode(DLOAD,4));hook.add(new VarInsnNode(DLOAD,6));hook.add(new VarInsnNode(FLOAD,8));hook.add(new VarInsnNode(ALOAD,9));
            hook.add(new MethodInsnNode(INVOKESTATIC,"dev/vibe/module/impl/visual/ItemPhysicsModule","transform","(Ljava/lang/Object;DDDFLjava/lang/Object;)I",false));hook.add(new InsnNode(DUP));hook.add(new LdcInsnNode(Integer.MIN_VALUE));hook.add(new JumpInsnNode(IF_ICMPEQ,nativeCode));hook.add(new InsnNode(IRETURN));hook.add(nativeCode);hook.add(new FrameNode(F_SAME1,0,null,1,new Object[]{INTEGER}));hook.add(new InsnNode(POP));m.instructions.insert(hook);
        }
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray();
    }
}
