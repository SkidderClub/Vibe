package keystrokesmod.script.model;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;

public class Block {
    public String type, name; public boolean interactable; public int variant; public double height,width,length,x,y,z;
    public Block(net.minecraft.block.Block block, BlockPos pos){this(Minecraft.getMinecraft().theWorld==null?null:Minecraft.getMinecraft().theWorld.getBlockState(pos),pos);}
    public Block(IBlockState state, BlockPos pos){net.minecraft.block.Block block=state==null?net.minecraft.init.Blocks.air:state.getBlock();type=block.getClass().getSimpleName();String registry=String.valueOf(block.getRegistryName());name=registry.startsWith("minecraft:")?registry.substring(10):registry;interactable=block instanceof BlockContainer;variant=state==null?0:block.getMetaFromState(state);height=block.getBlockBoundsMaxY()-block.getBlockBoundsMinY();width=block.getBlockBoundsMaxX()-block.getBlockBoundsMinX();length=block.getBlockBoundsMaxZ()-block.getBlockBoundsMinZ();x=pos.getX();y=pos.getY();z=pos.getZ();}
    public Block(int x,int y,int z){this(new Vec3(x,y,z));} public Block(Vec3 pos){this(Minecraft.getMinecraft().theWorld.getBlockState(Vec3.getBlockPos(pos)),Vec3.getBlockPos(pos));}
    public Block(String name){this((net.minecraft.block.Block)net.minecraft.block.Block.blockRegistry.getObject(new ResourceLocation("minecraft:"+name)),new BlockPos(-1,-1,-1));}
}
