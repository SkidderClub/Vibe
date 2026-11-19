package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Keep accelerated progress and break accounting inside vanilla's tool-action sequence. */
public final class BlockBreakingTransformer implements IClassTransformer, Opcodes {
    private static final String FAST="dev/vibe/module/impl/world/FastBreakModule",STATS="dev/vibe/statistics/BlockBreakHooks";
    @Override public byte[] transform(String name,String mapped,byte[] bytes){
        if(bytes==null||!("net.minecraft.client.multiplayer.PlayerControllerMP".equals(name)||"net.minecraft.client.multiplayer.PlayerControllerMP".equals(mapped)||"bda".equals(name)))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,ClassReader.EXPAND_FRAMES);boolean changed=false;
        for(Object entry:node.methods){MethodNode method=(MethodNode)entry;String nativeName=method.name.replace("vibe$tool$","");
            if(!method.desc.equals("(Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z")&&!method.desc.equals("(Lcj;Lcq;)Z"))continue;
            boolean skip=false;for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;
                if(call.owner.equals(FAST)||call.owner.equals(STATS)||call.name.startsWith("vibe$tool$"))skip=true;}
            if(skip)continue;
            if(named(nativeName,"onPlayerDamageBlock","func_180512_c","c")){
                boolean delay=false;
                for(AbstractInsnNode i:method.instructions.toArray()){
                    if(!delay&&i instanceof FieldInsnNode&&i.getOpcode()==GETFIELD){FieldInsnNode field=(FieldInsnNode)i;
                        if(field.desc.equals("I")&&named(field.name,"blockHitDelay","field_78781_i","g")){
                            InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(new VarInsnNode(ALOAD,1));hook.add(call(FAST,"delayHook","(ILjava/lang/Object;Ljava/lang/Object;)I"));method.instructions.insert(i,hook);delay=true;
                        }
                    }
                    if(i instanceof MethodInsnNode){MethodInsnNode invocation=(MethodInsnNode)i;
                        if(named(invocation.name,"getPlayerRelativeBlockHardness","func_180647_a","a")&&(invocation.desc.equals("(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)F")||invocation.desc.equals("(Lwn;Ladm;Lcj;)F"))){
                            InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(new VarInsnNode(ALOAD,1));hook.add(call(FAST,"stepHook","(FLjava/lang/Object;Ljava/lang/Object;)F"));method.instructions.insert(i,hook);
                        }
                    }
                }
                for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof FieldInsnNode&&i.getOpcode()==PUTFIELD){FieldInsnNode field=(FieldInsnNode)i;
                    AbstractInsnNode previous=i.getPrevious();while(previous!=null&&previous.getOpcode()<0)previous=previous.getPrevious();
                    if(field.desc.equals("F")&&named(field.name,"curBlockDamageMP","field_78770_f","e")&&previous!=null&&previous.getOpcode()==FADD){
                        InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(new VarInsnNode(ALOAD,1));hook.add(call(FAST,"progressHook","(FLjava/lang/Object;Ljava/lang/Object;)F"));method.instructions.insertBefore(i,hook);
                    }
                }
                changed=true;
            }else if(named(nativeName,"onPlayerDestroyBlock","func_178888_a","a")){
                int token=method.maxLocals++;InsnList head=new InsnList();head.add(new VarInsnNode(ALOAD,1));head.add(call(STATS,"begin","(Ljava/lang/Object;)Ljava/lang/Object;"));head.add(new VarInsnNode(ASTORE,token));method.instructions.insert(head);
                for(AbstractInsnNode i:method.instructions.toArray()){
                    if(i instanceof FrameNode){FrameNode frame=(FrameNode)i;frame.local=new java.util.ArrayList(frame.local);while(frame.local.size()<token)frame.local.add(TOP);frame.local.add("java/lang/Object");}
                    if(i.getOpcode()==IRETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,token));hook.add(call(STATS,"finish","(ZLjava/lang/Object;)Z"));method.instructions.insertBefore(i,hook);}
                }
                changed=true;
            }
        }
        if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static MethodInsnNode call(String owner,String name,String desc){return new MethodInsnNode(INVOKESTATIC,owner,name,desc,false);}
    private static boolean named(String name,String... names){for(String candidate:names)if(candidate.equals(name))return true;return false;}
}
