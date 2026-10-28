package dev.vibe.easter;

import java.util.*;
import net.minecraft.entity.item.EntityFireworkRocket;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.world.World;

/** Native rocket flight, trail and status-17 explosion, with a client-owned lifetime. */
public final class ClientFireworks {
    private final List<Flight> flights=new ArrayList<>();
    private final Random random=new Random();
    private int nextId=-1900000000;
    public void spawn(World world,double x,double y,double z){
        if(!world.isRemote||flights.size()>=12)return;
        EntityFireworkRocket rocket=create(world,x,y,z,random);
        while(world.getEntityByID(nextId)!=null)nextId--;
        rocket.setEntityId(nextId--);
        NBTTagCompound state=new NBTTagCompound();rocket.writeEntityToNBT(state);
        if(world.spawnEntityInWorld(rocket))flights.add(new Flight(rocket,state.getInteger("LifeTime")));
    }
    public static EntityFireworkRocket create(World world,double x,double y,double z,Random random){
        ItemStack item=new ItemStack(Items.fireworks);NBTTagCompound root=new NBTTagCompound(),fireworks=new NBTTagCompound();
        fireworks.setByte("Flight",(byte)1);NBTTagList bursts=new NBTTagList();
        int[] palette={0xFF4848,0xFFD15A,0x65D8FF,0xA87BFF,0x6FFFA6,0xFF88CE};
        int count=1+random.nextInt(2);
        for(int i=0;i<count;i++){
            NBTTagCompound burst=new NBTTagCompound();burst.setByte("Type",(byte)random.nextInt(5));
            burst.setIntArray("Colors",new int[]{palette[random.nextInt(palette.length)],palette[random.nextInt(palette.length)]});
            burst.setIntArray("FadeColors",new int[]{0xFFF2CB});burst.setBoolean("Trail",true);burst.setBoolean("Flicker",random.nextBoolean());bursts.appendTag(burst);
        }
        fireworks.setTag("Explosions",bursts);root.setTag("Fireworks",fireworks);item.setTagCompound(root);
        return new EntityFireworkRocket(world,x,y,z,item);
    }
    public void tick(World current){
        for(Iterator<Flight> it=flights.iterator();it.hasNext();){Flight flight=it.next();
            if(flight.rocket.worldObj!=current||flight.rocket.isDead){flight.rocket.setDead();it.remove();}
            else if(++flight.age>flight.lifetime){flight.rocket.handleStatusUpdate((byte)17);flight.rocket.setDead();it.remove();}
        }
    }
    public void clear(){for(Flight flight:flights)flight.rocket.setDead();flights.clear();}
    public int count(){return flights.size();}
    private static final class Flight{final EntityFireworkRocket rocket;final int lifetime;int age;Flight(EntityFireworkRocket rocket,int lifetime){this.rocket=rocket;this.lifetime=lifetime;}}
}
