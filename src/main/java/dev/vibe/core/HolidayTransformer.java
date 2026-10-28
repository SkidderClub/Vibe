package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Render-only precipitation overrides. Biomes, blocks and server weather are never changed. */
public final class HolidayTransformer implements IClassTransformer,Opcodes {
    private static final String HOOK="dev/vibe/easter/EasterEggs";
    @Override public byte[] transform(String name,String mapped,byte[] bytes){
        if(bytes==null)return null;
        if(!"net.minecraft.client.renderer.EntityRenderer".equals(mapped)&&!"net.minecraft.client.renderer.EntityRenderer".equals(name)&&!"bfk".equals(name))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(Object entry:node.methods){MethodNode method=(MethodNode)entry;
            if(!(method.desc.equals("(F)V")&&named(method.name,"renderRainSnow","func_78474_d","c"))
                    &&!(method.desc.equals("()V")&&named(method.name,"addRainParticles","func_78484_h","o")))continue;
            boolean installed=false;for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof MethodInsnNode&&((MethodInsnNode)insn).owner.equals(HOOK))installed=true;
            if(installed)continue;
            for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)insn;String hook=null,desc=null;
                if((call.owner.equals("net/minecraft/client/multiplayer/WorldClient")||call.owner.equals("net/minecraft/world/World")||call.owner.equals("bdb")||call.owner.equals("adm"))
                        &&call.desc.equals("(F)F")&&named(call.name,"getRainStrength","func_72867_j","j")){hook="rainStrength";desc="(Ljava/lang/Object;F)F";}
                if(call.owner.equals("net/minecraft/world/biome/BiomeGenBase")||call.owner.equals("ady")){
                    if(call.desc.equals("()Z")&&named(call.name,"canRain","func_76738_d","e")){hook="canRain";desc="(Ljava/lang/Object;)Z";}
                    if(call.desc.equals("()Z")&&named(call.name,"getEnableSnow","func_76746_c","d")){hook="snowBiome";desc="(Ljava/lang/Object;)Z";}
                    if((call.desc.equals("(Lnet/minecraft/util/BlockPos;)F")||call.desc.equals("(Lcj;)F"))&&named(call.name,"getFloatTemperature","func_180626_a","a")){hook="temperature";desc="(Ljava/lang/Object;Ljava/lang/Object;)F";}
                }
                if(hook!=null)method.instructions.set(call,new MethodInsnNode(INVOKESTATIC,HOOK,hook,desc,false));
                else if(call.name.equals("isRainOff")&&call.desc.equals("()Z"))method.instructions.insert(call,new MethodInsnNode(INVOKESTATIC,HOOK,"rainOff","(Z)Z",false));
            }
        }
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static boolean named(String name,String... options){for(String option:options)if(option.equals(name))return true;return false;}
}
