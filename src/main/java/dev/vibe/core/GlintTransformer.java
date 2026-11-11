package dev.vibe.core;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Changes only native glint material/time, leaving models and item render state native. */
public final class GlintTransformer implements IClassTransformer,Opcodes {
    private static final String HOOK="dev/vibe/module/impl/visual/EnchantmentGlintModule";
    @Override public byte[] transform(String name,String mapped,byte[] bytes){if(bytes==null||!("net.minecraft.client.renderer.entity.RenderItem".equals(mapped)||"net.minecraft.client.renderer.entity.layers.LayerArmorBase".equals(mapped)))return bytes;
        ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);boolean changed=false;
        for(Object value:c.methods){MethodNode m=(MethodNode)value;boolean glint=m.name.equals("renderEffect")||m.name.equals("func_180451_a")||m.name.equals("renderGlint")||m.name.equals("func_177183_a");for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof IntInsnNode&&((IntInsnNode)n).operand==5890||n instanceof LdcInsnNode&&Integer.valueOf(-8372020).equals(((LdcInsnNode)n).cst))glint=true;if(!glint)continue;
            boolean installed=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode&&((MethodInsnNode)n).owner.equals(HOOK))installed=true;if(installed)continue;
            for(AbstractInsnNode n:m.instructions.toArray()){
                if(n instanceof LdcInsnNode&&Integer.valueOf(-8372020).equals(((LdcInsnNode)n).cst)){m.instructions.insert(n,new MethodInsnNode(INVOKESTATIC,HOOK,"tint","(I)I",false));changed=true;}
                if(n instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)n;
                    if(call.desc.equals("(FFFF)V")&&(call.owner.equals("net/minecraft/client/renderer/GlStateManager")||call.owner.equals("bfl"))&&(call.name.equals("color")||call.name.equals("func_179131_c")||call.name.equals("c"))){m.instructions.set(call,new MethodInsnNode(INVOKESTATIC,HOOK,"color","(FFFF)V",false));changed=true;}
                    else if(call.desc.equals("()J")&&call.getOpcode()==INVOKESTATIC){m.instructions.insert(call,new MethodInsnNode(INVOKESTATIC,HOOK,"time","(J)J",false));changed=true;}
                    else if(mapped.endsWith("LayerArmorBase")&&call.desc.equals("(FFF)V")&&(call.name.equals("translate")||call.name.equals("func_179109_b")||call.name.equals("b"))){m.instructions.set(call,new MethodInsnNode(INVOKESTATIC,HOOK,"translate","(FFF)V",false));changed=true;}
                }
            }
        }if(!changed)return bytes;ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray();
    }
}
