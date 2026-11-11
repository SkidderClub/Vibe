package dev.vibe.core;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class BlockDebrisTransformer implements IClassTransformer,Opcodes {
    @Override public byte[] transform(String name,String mapped,byte[] bytes){if(bytes==null||!"net.minecraft.client.particle.EffectRenderer".equals(mapped))return bytes;ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);boolean changed=false;
        for(Object value:c.methods){MethodNode m=(MethodNode)value;if(!(m.name.equals("addBlockDestroyEffects")||m.name.equals("func_180533_a")||m.name.equals("a"))||!(m.desc.equals("(Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;)V")||m.desc.equals("(Lcj;Lalz;)V")))continue;
            boolean installed=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode&&((MethodInsnNode)n).owner.equals("dev/vibe/module/impl/visual/BlockDebrisModule"))installed=true;if(installed)continue;
            InsnList hook=new InsnList();LabelNode nativeCode=new LabelNode();hook.add(new VarInsnNode(ALOAD,1));hook.add(new VarInsnNode(ALOAD,2));hook.add(new MethodInsnNode(INVOKESTATIC,"dev/vibe/module/impl/visual/BlockDebrisModule","destroyHook","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));hook.add(new JumpInsnNode(IFEQ,nativeCode));hook.add(new InsnNode(RETURN));hook.add(nativeCode);hook.add(new FrameNode(F_SAME,0,null,0,null));m.instructions.insert(hook);changed=true;
        }if(!changed)return bytes;ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray();
    }
}
