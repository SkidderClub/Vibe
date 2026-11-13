package dev.vibe.core;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Preserve FOV, near/far planes and pass ordering; replace only the aspect argument. */
public final class AspectRatioTransformer implements IClassTransformer,Opcodes {
    @Override public byte[] transform(String name,String mapped,byte[] bytes){if(bytes==null)return null;String type=mapped==null?name:mapped;if(!"net.minecraft.client.renderer.EntityRenderer".equals(type)&&!"bfk".equals(type))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(Object entry:node.methods){MethodNode method=(MethodNode)entry;boolean hand=method.desc.startsWith("(FI")&&(method.name.equals("renderHand")||method.name.equals("func_78476_b")||method.name.equals("b"));
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;if(call.getOpcode()==INVOKESTATIC&&call.owner.startsWith("org/lwjgl/util/glu/")&&call.name.equals("gluPerspective")&&call.desc.equals("(FFFF)V")){method.instructions.insertBefore(call,new InsnNode(hand?ICONST_1:ICONST_0));method.instructions.set(call,new MethodInsnNode(INVOKESTATIC,"dev/vibe/module/impl/visual/AspectRatioModule","perspective","(FFFFZ)V",false));changed=true;}}
        }if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();}
}
