package dev.vibe.core;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Observe applied title/subtitle text on the render thread, after vanilla's packet thread check. */
public final class ChatterTitleTransformer implements IClassTransformer,Opcodes{
    @Override public byte[] transform(String name,String mapped,byte[] bytes){
        if(bytes==null||!"net.minecraft.client.gui.GuiIngame".equals(mapped)&&!"avo".equals(name))return bytes;
        ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);
        for(Object entry:c.methods){MethodNode m=(MethodNode)entry;
            if(!m.desc.equals("(Ljava/lang/String;Ljava/lang/String;III)V")||!m.name.equals("displayTitle")&&!m.name.equals("func_175178_a")&&!m.name.equals("a"))continue;
            for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode&&((MethodInsnNode)n).owner.equals("dev/vibe/module/impl/client/ChatterModule"))return bytes;
            InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,1));hook.add(new VarInsnNode(ALOAD,2));hook.add(new MethodInsnNode(INVOKESTATIC,"dev/vibe/module/impl/client/ChatterModule","titleHook","(Ljava/lang/String;Ljava/lang/String;)V",false));m.instructions.insert(hook);
        }
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray();
    }
}
