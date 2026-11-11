package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Post-vanilla poses and a local-only companion click, including remapped descriptors. */
public final class MemeAnimationTransformer implements IClassTransformer,Opcodes {
    @Override public byte[] transform(String name,String transformedName,byte[] bytes){
        if(bytes==null)return null;String mapped=(transformedName==null?name:transformedName).replace('.','/');
        boolean player=mapped.equals("net/minecraft/client/model/ModelPlayer"),biped=mapped.equals("net/minecraft/client/model/ModelBiped"),client=mapped.equals("net/minecraft/client/Minecraft");
        if(!player&&!biped&&!client)return bytes;ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(Object entry:node.methods){MethodNode method=(MethodNode)entry;
            boolean installed=false;for(AbstractInsnNode n:method.instructions.toArray())if(n instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)n;if(call.owner.equals("dev/vibe/features/EmotePoses")&&call.name.equals("before")||call.owner.equals("dev/vibe/module/impl/meme/GirlfriendModule")&&call.name.equals("hitHook"))installed=true;}if(installed)continue;
            if((player||biped)&&(method.name.equals("setRotationAngles")||method.name.equals("func_78087_a")||method.name.equals("a"))&&(method.desc.equals("(FFFFFFLnet/minecraft/entity/Entity;)V")||method.desc.equals("(FFFFFFLpk;)V"))){
                InsnList restore=new InsnList();restore.add(new VarInsnNode(ALOAD,0));restore.add(new MethodInsnNode(INVOKESTATIC,"dev/vibe/features/EmotePoses","before","(Ljava/lang/Object;)V",false));method.instructions.insert(restore);
                for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(new VarInsnNode(ALOAD,7));hook.add(new VarInsnNode(FLOAD,3));hook.add(new MethodInsnNode(INVOKESTATIC,"dev/vibe/module/impl/meme/DabModule",player?"poseHook":"basePoseHook","(Ljava/lang/Object;Ljava/lang/Object;F)V",false));method.instructions.insertBefore(instruction,hook);changed=true;}
            }
            if(client&&method.desc.equals("()V")&&(method.name.equals("clickMouse")||method.name.equals("func_147116_af")||method.name.equals("aw"))){
                InsnList hook=new InsnList();LabelNode vanilla=new LabelNode();hook.add(new MethodInsnNode(INVOKESTATIC,"dev/vibe/module/impl/meme/GirlfriendModule","hitHook","()Z",false));hook.add(new JumpInsnNode(IFEQ,vanilla));hook.add(new InsnNode(RETURN));hook.add(vanilla);hook.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.insert(hook);changed=true;
            }
        }
        if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
