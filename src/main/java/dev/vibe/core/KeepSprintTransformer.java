package dev.vibe.core;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class KeepSprintTransformer implements net.minecraft.launchwrapper.IClassTransformer,Opcodes {
    private static final String HOOK="dev/vibe/module/impl/combat/KeepSprintModule";
    @Override public byte[] transform(String name,String mapped,byte[] bytes){
        if(bytes==null||!("net.minecraft.entity.player.EntityPlayer".equals(mapped)||"net.minecraft.entity.player.EntityPlayer".equals(name)||"wn".equals(name)))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(Object value:node.methods){MethodNode method=(MethodNode)value;
            if(!(method.name.equals("attackTargetEntityWithCurrentItem")||method.name.equals("func_71059_n")||method.name.equals("f"))
                    ||!(method.desc.equals("(Lnet/minecraft/entity/Entity;)V")||method.desc.equals("(Lpk;)V")))continue;
            for(AbstractInsnNode ins:method.instructions.toArray())if(ins instanceof MethodInsnNode&&((MethodInsnNode)ins).owner.equals(HOOK))return bytes;
            int factors=0,resets=0;
            for(AbstractInsnNode ins:method.instructions.toArray()){
                if(ins instanceof LdcInsnNode&&Double.valueOf(.6).equals(((LdcInsnNode)ins).cst)){
                    InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(new MethodInsnNode(INVOKESTATIC,HOOK,"slowdown","(DLjava/lang/Object;)D",false));method.instructions.insert(ins,hook);factors++;
                }
                if(ins instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)ins;
                    if(call.desc.equals("(Z)V")&&(call.name.equals("setSprinting")||call.name.equals("func_70031_b")||call.name.equals("d"))){
                        method.instructions.set(call,new MethodInsnNode(INVOKESTATIC,HOOK,"resetSprint","(Ljava/lang/Object;Z)V",false));resets++;
                    }
                }
            }
            if(factors!=2||resets!=1)throw new IllegalStateException("KeepSprint native sites: "+factors+"/"+resets);changed=true;
        }
        if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
