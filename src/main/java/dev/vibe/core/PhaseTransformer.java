package dev.vibe.core;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class PhaseTransformer implements net.minecraft.launchwrapper.IClassTransformer,Opcodes {
    private static final String HOOK="dev/vibe/module/impl/movement/PhaseModule";
    public byte[] transform(String name,String mapped,byte[] bytes){
        if(bytes==null)return null;boolean world="net.minecraft.world.World".equals(mapped)||"adm".equals(name),player="net.minecraft.client.entity.EntityPlayerSP".equals(mapped)||"bew".equals(name);if(!world&&!player)return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);for(Object object:node.methods)for(AbstractInsnNode ins:((MethodNode)object).instructions.toArray())if(ins instanceof MethodInsnNode&&((MethodInsnNode)ins).owner.equals(HOOK))return bytes;
        for(Object object:node.methods){MethodNode method=(MethodNode)object;
            if(world&&(method.name.equals("getCollidingBoundingBoxes")||method.name.equals("func_72945_a")||method.name.equals("a"))&&(method.desc.equals("(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;")||method.desc.equals("(Lpk;Laug;)Ljava/util/List;")))
                for(AbstractInsnNode ins:method.instructions.toArray())if(ins.getOpcode()==ARETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,1));hook.add(new MethodInsnNode(INVOKESTATIC,HOOK,"collisions","(Ljava/util/List;Ljava/lang/Object;)Ljava/util/List;",false));method.instructions.insertBefore(ins,hook);}
            if(player&&method.desc.equals("(DDD)Z")&&(method.name.equals("pushOutOfBlocks")||method.name.equals("func_145771_j")||method.name.equals("j"))){
                LabelNode pass=new LabelNode();InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(new MethodInsnNode(INVOKESTATIC,HOOK,"cancelPush","(Ljava/lang/Object;)Z",false));hook.add(new JumpInsnNode(IFEQ,pass));hook.add(new InsnNode(ICONST_0));hook.add(new InsnNode(IRETURN));hook.add(pass);hook.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.insert(hook);
            }
        }ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
