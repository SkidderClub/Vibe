package dev.vibe.combat;
import dev.vibe.path.PathTestFixture;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.profiler.Profiler;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.*;
import net.minecraft.world.*;

public final class ProjectileFixture implements AutoCloseable {
    public final PathTestFixture base=new PathTestFixture();public final TestWorld world;public final Player self;
    public ProjectileFixture()throws Exception{
        world=PathTestFixture.allocate(TestWorld.class);world.blocks=new HashMap<>();world.loaded=true;world.scoreboard=new Scoreboard();
        PathTestFixture.set(World.class,world,"provider",new WorldProviderSurface());PathTestFixture.set(World.class,world,"theProfiler",new Profiler());PathTestFixture.set(World.class,world,"isRemote",true);
        PathTestFixture.set(World.class,world,"loadedEntityList",new ArrayList<Entity>());PathTestFixture.set(World.class,world,"playerEntities",new ArrayList<EntityPlayer>());
        base.mc.theWorld=world;self=player("Self",.5,64,.5);base.mc.thePlayer=self;base.mc.inGameHasFocus=true;
        base.mc.playerController=PathTestFixture.allocate(PlayerControllerMP.class);PathTestFixture.set(net.minecraft.client.Minecraft.class,base.mc,"renderViewEntity",self);
        for(int x=-20;x<=20;x++)for(int z=-20;z<=80;z++)block(x,63,z,Blocks.stone.getDefaultState());
    }
    public Player player(String name,double x,double y,double z)throws Exception{Player p=PathTestFixture.allocate(Player.class);p.name=name;p.duration=20;p.setEntityId(world.loadedEntityList.size()+1);p.worldObj=world;p.width=.6F;p.height=1.8F;p.inventory=new InventoryPlayer(p);p.setPosition(x,y,z);p.prevPosX=p.lastTickPosX=x;p.prevPosY=p.lastTickPosY=y;p.prevPosZ=p.lastTickPosZ=z;p.onGround=true;world.loadedEntityList.add(p);world.playerEntities.add(p);return p;}
    public void block(int x,int y,int z,IBlockState s){world.blocks.put(new BlockPos(x,y,z),s);}
    @Override public void close()throws Exception{base.close();}
    public static class Player extends EntityPlayerSP{
        String name;public boolean drawing;public int duration=20;
        private Player(){super(null,null,null,null);}
        @Override public String getName(){return name;}@Override public boolean isEntityAlive(){return !isDead;}@Override public boolean isInvisible(){return false;}@Override public boolean isSpectator(){return false;}
        @Override public float getEyeHeight(){return 1.62F;}
        @Override public boolean isUsingItem(){return drawing;}@Override public int getItemInUseDuration(){return duration;}@Override public ItemStack getHeldItem(){return inventory.getCurrentItem();}
        @Override public void addStat(net.minecraft.stats.StatBase stat,int amount){}@Override public void swingItem(){}
        @Override public boolean isSprinting(){return false;}@Override public boolean isSneaking(){return false;}
    }
    public static class TestWorld extends WorldClient{
        public Map<BlockPos,IBlockState> blocks;public boolean loaded;public int reads;Scoreboard scoreboard;
        private TestWorld(){super(null,null,0,null,null);}
        @Override public boolean isBlockLoaded(BlockPos p){return loaded;}@Override public boolean isBlockLoaded(BlockPos p,boolean empty){return loaded;}
        @Override protected boolean isChunkLoaded(int x,int z,boolean empty){return loaded;}@Override public boolean isAreaLoaded(BlockPos a,BlockPos b){return loaded;}@Override public boolean isAreaLoaded(BlockPos a,BlockPos b,boolean empty){return loaded;}
        @Override public IBlockState getBlockState(BlockPos p){if(!loaded)throw new AssertionError("Unloaded terrain read");reads++;return blocks.getOrDefault(p,Blocks.air.getDefaultState());}
        @Override public List<AxisAlignedBB> getCollidingBoundingBoxes(Entity entity,AxisAlignedBB box){List<AxisAlignedBB> result=new ArrayList<>();for(int x=MathHelper.floor_double(box.minX);x<=MathHelper.floor_double(box.maxX);x++)for(int y=MathHelper.floor_double(box.minY);y<=MathHelper.floor_double(box.maxY);y++)for(int z=MathHelper.floor_double(box.minZ);z<=MathHelper.floor_double(box.maxZ);z++){BlockPos p=new BlockPos(x,y,z);IBlockState s=getBlockState(p);s.getBlock().addCollisionBoxesToList(this,p,s,box,result,entity);}return result;}
        @Override public List<Entity> getEntitiesWithinAABBExcludingEntity(Entity entity,AxisAlignedBB box){return Collections.emptyList();}
        @Override public void spawnParticle(EnumParticleTypes type,double x,double y,double z,double a,double b,double c,int... params){}
        @Override public void playSoundAtEntity(Entity entity,String sound,float volume,float pitch){}
        @Override public Scoreboard getScoreboard(){return scoreboard;}@Override public long getTotalWorldTime(){return 0;}
    }
}
