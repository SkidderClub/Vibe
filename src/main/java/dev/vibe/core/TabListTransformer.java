package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Filter vanilla's sorted presentation list before its 80-entry cap; leave NetHandler untouched. */
public final class TabListTransformer implements IClassTransformer,Opcodes {
    private static final String TARGET="net.minecraft.client.gui.GuiPlayerTabOverlay",HOOK="dev/vibe/ui/render/RewoundPlayers";
    @Override public byte[] transform(String name,String mapped,byte[] bytes) {
        if(bytes==null||!TARGET.equals(mapped)&&!TARGET.equals(name))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(Object value:node.methods){MethodNode method=(MethodNode)value;boolean hooked=false;
            for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals(HOOK))hooked=true;
            if(hooked)continue;
            for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;
                if(call.owner.equals("com/google/common/collect/Ordering")&&call.name.equals("sortedCopy")&&call.desc.equals("(Ljava/lang/Iterable;)Ljava/util/List;")){
                    method.instructions.insert(call,new MethodInsnNode(INVOKESTATIC,HOOK,"tabEntries","(Ljava/util/List;)Ljava/util/List;",false));changed=true;
                }
            }
        }
        if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
