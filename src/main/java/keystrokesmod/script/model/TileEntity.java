package keystrokesmod.script.model;

import net.minecraft.tileentity.TileEntitySkull;

public class TileEntity {
    private final net.minecraft.tileentity.TileEntity tileEntity; private final Vec3 position; public String type,name;
    public TileEntity(net.minecraft.tileentity.TileEntity value){tileEntity=value;position=new Vec3(value.getPos());type=value.getBlockType().getClass().getSimpleName();String key=String.valueOf(value.getBlockType().getRegistryName());name=key.startsWith("minecraft:")?key.substring(10):key;}
    public Vec3 getPosition(){return position;} public Object[] getSkullData(){if(!(tileEntity instanceof TileEntitySkull))return null;TileEntitySkull skull=(TileEntitySkull)tileEntity;return new Object[]{skull.getSkullType(),skull.getSkullRotation(),skull.getPlayerProfile()==null?null:skull.getPlayerProfile().getName(),skull.getPlayerProfile()==null?null:skull.getPlayerProfile().getId(),null};}
}
