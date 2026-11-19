package dev.vibe.core;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Pre-mutation drop guards, liquid surfaces and native jump cancellation for the Adjust ports. */
public final class AdjustTransformer implements net.minecraft.launchwrapper.IClassTransformer,Opcodes {
    private static final String DROP="dev/vibe/inventory/DropConfirmation",JESUS="dev/vibe/movement/AdjustJesus";
    public byte[] transform(String name,String mapped,byte[] bytes){
        if(bytes==null)return null;
        boolean player=named(name,mapped,"net.minecraft.client.entity.EntityPlayerSP","bew"),controller=named(name,mapped,"net.minecraft.client.multiplayer.PlayerControllerMP","bda"),world=named(name,mapped,"net.minecraft.world.World","adm"),living=named(name,mapped,"net.minecraft.entity.EntityLivingBase","pr");
        if(!player&&!controller&&!world&&!living)return bytes;ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);boolean changed=false;
        for(Object value:c.methods){MethodNode m=(MethodNode)value;if(hooked(m))continue;
            if(player&&(m.name.equals("dropOneItem")||m.name.equals("func_71040_bB")||m.name.equals("a"))&&(m.desc.equals("(Z)Lnet/minecraft/entity/item/EntityItem;")||m.desc.equals("(Z)Luz;"))){
                InsnList h=new InsnList();h.add(new VarInsnNode(ALOAD,0));h.add(new VarInsnNode(ILOAD,1));h.add(new MethodInsnNode(INVOKESTATIC,DROP,"allowDrop","(Ljava/lang/Object;Z)Z",false));allowOrNull(m,h);changed=true;
            }
            if(controller&&(m.name.equals("windowClick")||m.name.equals("func_78753_a")||m.name.equals("a"))&&(m.desc.equals("(IIIILnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;")||m.desc.equals("(IIIILwn;)Lzx;"))){
                InsnList h=new InsnList();for(int i=1;i<=4;i++)h.add(new VarInsnNode(ILOAD,i));h.add(new VarInsnNode(ALOAD,5));h.add(new MethodInsnNode(INVOKESTATIC,DROP,"allowClick","(IIIILjava/lang/Object;)Z",false));allowOrNull(m,h);changed=true;
            }
            if(world&&(m.name.equals("getCollidingBoundingBoxes")||m.name.equals("func_72945_a")||m.name.equals("a"))&&(m.desc.equals("(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;")||m.desc.equals("(Lpk;Laug;)Ljava/util/List;"))){
                for(AbstractInsnNode i:m.instructions.toArray())if(i.getOpcode()==ARETURN){InsnList h=new InsnList();h.add(new VarInsnNode(ALOAD,1));h.add(new VarInsnNode(ALOAD,2));h.add(new MethodInsnNode(INVOKESTATIC,JESUS,"collisions","(Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",false));m.instructions.insertBefore(i,h);}changed=true;
            }
            if(living&&m.desc.equals("()V")&&(m.name.equals("jump")||m.name.equals("func_70664_aZ")||m.name.equals("bF"))){
                LabelNode pass=new LabelNode();InsnList h=new InsnList();h.add(new VarInsnNode(ALOAD,0));h.add(new MethodInsnNode(INVOKESTATIC,JESUS,"cancelJump","(Ljava/lang/Object;)Z",false));h.add(new JumpInsnNode(IFEQ,pass));h.add(new InsnNode(RETURN));h.add(pass);h.add(new FrameNode(F_SAME,0,null,0,null));m.instructions.insert(h);changed=true;
            }
        }
        if(!changed)return bytes;ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(out);return out.toByteArray();
    }
    private static boolean named(String n,String m,String expected,String raw){return expected.equals(n)||expected.equals(m)||raw.equals(n);}
    private static boolean hooked(MethodNode m){for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode&&(((MethodInsnNode)i).owner.equals(DROP)||((MethodInsnNode)i).owner.equals(JESUS)))return true;return false;}
    private static void allowOrNull(MethodNode m,InsnList h){LabelNode pass=new LabelNode();h.add(new JumpInsnNode(IFNE,pass));h.add(new InsnNode(ACONST_NULL));h.add(new InsnNode(ARETURN));h.add(pass);h.add(new FrameNode(F_SAME,0,null,0,null));m.instructions.insert(h);}
}
