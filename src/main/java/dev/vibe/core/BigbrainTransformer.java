package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Scale native player parts after pose calculation, retaining armour and attachments. */
public final class BigbrainTransformer implements IClassTransformer,Opcodes {
    private static final String HOOK="dev/vibe/module/impl/meme/BigbrainModule";
    @Override public byte[] transform(String name,String transformedName,byte[] bytes){
        if(bytes==null)return null;
        String mapped=(transformedName==null?name:transformedName).replace('.','/');
        boolean player=mapped.equals("net/minecraft/client/model/ModelPlayer"),biped=mapped.equals("net/minecraft/client/model/ModelBiped"),held=mapped.equals("net/minecraft/client/renderer/entity/layers/LayerHeldItem"),head=mapped.equals("net/minecraft/client/renderer/entity/layers/LayerCustomHead"),capeLayer=mapped.equals("net/minecraft/client/renderer/entity/layers/LayerCape");
        if(!player&&!biped&&!held&&!head&&!capeLayer)return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(Object entry:node.methods){MethodNode method=(MethodNode)entry;
            boolean render=(player||biped)&&named(method.name,"render","func_78088_a","a")&&(method.desc.equals("(Lnet/minecraft/entity/Entity;FFFFFF)V")||method.desc.equals("(Lpk;FFFFFF)V"));
            boolean ears=player&&named(method.name,"renderDeadmau5Head","func_178727_b","b")&&method.desc.equals("(F)V"),capeInstalled=false;
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;if(call.owner.equals(HOOK)&&call.name.equals("beginCape"))capeInstalled=true;}
            for(AbstractInsnNode instruction:method.instructions.toArray()){
                if(!(instruction instanceof MethodInsnNode))continue;MethodInsnNode call=(MethodInsnNode)instruction;
                if(capeLayer&&!capeInstalled&&call.getOpcode()==INVOKESTATIC&&call.desc.equals("()V")&&named(call.name,"pushMatrix","func_179094_E","E")){
                    InsnList begin=new InsnList();begin.add(new VarInsnNode(ALOAD,1));begin.add(new MethodInsnNode(INVOKESTATIC,HOOK,"beginCape","(Ljava/lang/Object;)V",false));method.instructions.insert(call,begin);capeInstalled=true;changed=true;
                }
                if(call.getOpcode()!=INVOKEVIRTUAL||!call.desc.equals("(F)V"))continue;
                if((render||ears)&&named(call.name,"render","func_78785_a","a")){
                    InsnList args=new InsnList();String hook,descriptor;
                    if(render){args.add(new VarInsnNode(ALOAD,0));args.add(new VarInsnNode(ALOAD,1));hook="renderPart";descriptor="(Ljava/lang/Object;FLjava/lang/Object;Ljava/lang/Object;)V";}
                    else{args.add(new InsnNode(ears?ICONST_1:ICONST_0));hook="renderAttachment";descriptor="(Ljava/lang/Object;FZ)V";}
                    method.instructions.insertBefore(call,args);method.instructions.set(call,new MethodInsnNode(INVOKESTATIC,HOOK,hook,descriptor,false));changed=true;
                }else if(held&&named(call.name,"postRenderArm","func_178718_a","a")||head&&named(call.name,"postRender","func_78794_c","c")){
                    method.instructions.insertBefore(call,new VarInsnNode(ALOAD,1));method.instructions.set(call,new MethodInsnNode(INVOKESTATIC,HOOK,held?"postRenderArm":"postRenderHead","(Ljava/lang/Object;FLjava/lang/Object;)V",false));changed=true;
                }
            }
        }
        if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static boolean named(String actual,String mcp,String srg,String obfuscated){return actual.equals(mcp)||actual.equals(srg)||actual.equals(obfuscated);}
}
