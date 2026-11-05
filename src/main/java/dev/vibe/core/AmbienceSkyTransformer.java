package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Wrap only vanilla's two celestial draws; retain Forge sky handlers and the entire sky pipeline. */
public final class AmbienceSkyTransformer implements IClassTransformer,Opcodes {
    private static final String HOOK="dev/vibe/ambience/AmbienceRenderer";
    @Override public byte[] transform(String name,String mapped,byte[] bytes){
        if(bytes==null)return null;
        String target=(mapped==null?name:mapped);if(target==null)return bytes;target=target.replace('.','/');
        if(!target.equals("net/minecraft/client/renderer/RenderGlobal")&&!target.equals("bfr"))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(Object value:node.methods){
            MethodNode method=(MethodNode)value;
            if(!method.desc.equals("(FI)V")||!named(method.name,"renderSky","func_174976_a","a"))continue;
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode&&((MethodInsnNode)instruction).owner.equals(HOOK))return bytes;
            String body=null;
            for(AbstractInsnNode instruction:method.instructions.toArray()){
                if(instruction instanceof FieldInsnNode){
                    FieldInsnNode field=(FieldInsnNode)instruction;
                    if(field.getOpcode()==GETSTATIC&&field.owner.equals(node.name)){
                        if(named(field.name,"locationSunPng","field_110928_i","d"))body="drawSun";
                        if(named(field.name,"locationMoonPhasesPng","field_110927_h","c"))body="drawMoon";
                    }
                }
                if(body==null||!(instruction instanceof MethodInsnNode))continue;
                MethodInsnNode call=(MethodInsnNode)instruction;
                if(call.desc.equals("()V")&&named(call.name,"draw","func_78381_a","b")
                        &&(call.owner.equals("net/minecraft/client/renderer/Tessellator")||call.owner.equals("bfx"))){
                    method.instructions.insertBefore(call,new VarInsnNode(FLOAD,1));
                    method.instructions.set(call,new MethodInsnNode(INVOKESTATIC,HOOK,body,"(Ljava/lang/Object;F)V",false));
                    changed=true;body=null;
                }
            }
        }
        if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static boolean named(String value,String a,String b,String c){return value.equals(a)||value.equals(b)||value.equals(c);}
}
