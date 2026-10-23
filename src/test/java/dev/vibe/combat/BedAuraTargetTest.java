package dev.vibe.combat;

import dev.vibe.module.impl.world.BedAuraModule;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Uses vanilla block collision boxes to check defence selection without a server. */
public class BedAuraTargetTest {
    @Test public void modesSelectRayObstructionsDirectBedOrAdjacentDefence() throws Exception {
        Minecraft previous = Minecraft.getMinecraft();
        try {
            // Forge's statistics bootstrap needs its launch classloader; only blocks/items are needed here.
            Field registered = net.minecraft.init.Bootstrap.class.getDeclaredField("alreadyRegistered");
            registered.setAccessible(true);
            if (!registered.getBoolean(null)) {
                registered.setBoolean(null,true);
                net.minecraft.block.Block.registerBlocks(); net.minecraft.item.Item.registerItems();
            }
            Minecraft mc = allocate(Minecraft.class); set(Minecraft.class,null,"theMinecraft",mc);
            mc.thePlayer = allocate(Player.class); mc.playerController = allocate(Controller.class);
            World world = allocate(World.class); world.blocks = new HashMap<BlockPos,IBlockState>(); mc.theWorld = world;
            BlockPos bed = new BlockPos(0,0,3), head = new BlockPos(1,0,3);
            world.blocks.put(bed,Blocks.bed.getDefaultState().withProperty(BlockBed.FACING,EnumFacing.EAST).withProperty(BlockBed.PART,BlockBed.EnumPartType.FOOT));
            world.blocks.put(head,Blocks.bed.getDefaultState().withProperty(BlockBed.FACING,EnumFacing.EAST).withProperty(BlockBed.PART,BlockBed.EnumPartType.HEAD));
            BlockPos outer = new BlockPos(0,1,1), adjacent = new BlockPos(0,0,2);
            world.blocks.put(outer,Blocks.stone.getDefaultState()); world.blocks.put(adjacent,Blocks.wool.getDefaultState());
            BedAuraModule module = new BedAuraModule(); set(BedAuraModule.class,module,"owner",mc.thePlayer);
            Method select = BedAuraModule.class.getDeclaredMethod("selectHit",BlockPos.class); select.setAccessible(true);
            module.getMode().setValue("Raycast");
            assertEquals(outer,hit(select,module,bed).getBlockPos());
            world.blocks.remove(outer);
            assertEquals(adjacent,hit(select,module,bed).getBlockPos());
            world.blocks.remove(adjacent);
            assertEquals(bed,hit(select,module,bed).getBlockPos());
            for (BlockPos half : new BlockPos[]{bed,head}) for (EnumFacing face : EnumFacing.values()) {
                BlockPos neighbour=half.offset(face);
                if (!neighbour.equals(bed)&&!neighbour.equals(head)) world.blocks.put(neighbour,Blocks.wool.getDefaultState());
            }
            world.blocks.put(outer,Blocks.stone.getDefaultState());
            module.getMode().setValue("Vanilla");
            assertEquals(bed,hit(select,module,bed).getBlockPos());
            module.getMode().setValue("Hypixel");
            assertEquals("Outer layers must not replace adjacent defence selection",adjacent,hit(select,module,bed).getBlockPos());
            world.blocks.remove(head.up());
            assertEquals("Either half's exposed side allows the bed",bed,hit(select,module,bed).getBlockPos());
            module.getMode().setValue("Raycast");
            world.blocks.put(outer,Blocks.bedrock.getDefaultState());
            assertNull("Unbreakable obstructions must not start mining",hit(select,module,bed));
        } finally { set(Minecraft.class,null,"theMinecraft",previous); }
    }
    private static MovingObjectPosition hit(Method method,BedAuraModule module,BlockPos bed)throws Exception{return (MovingObjectPosition)method.invoke(module,bed);}
    @Test public void movingPlayerRefreshesAimBeforeWalkingAndSwingsBeforeControllerDigging() throws Exception {
        Minecraft previous=Minecraft.getMinecraft(); dev.vibe.Vibe previousVibe=dev.vibe.Vibe.getInstance();
        try {
            Field registered=net.minecraft.init.Bootstrap.class.getDeclaredField("alreadyRegistered");registered.setAccessible(true);
            if(!registered.getBoolean(null)){registered.setBoolean(null,true);net.minecraft.block.Block.registerBlocks();net.minecraft.item.Item.registerItems();}
            Minecraft mc=allocate(Minecraft.class);set(Minecraft.class,null,"theMinecraft",mc);
            Player player=allocate(Player.class);mc.thePlayer=player;player.actions=new java.util.ArrayList<String>();
            Controller controller=allocate(Controller.class);controller.actions=player.actions;mc.playerController=controller;
            World world=allocate(World.class);world.blocks=new HashMap<BlockPos,IBlockState>();mc.theWorld=world;
            BlockPos bed=new BlockPos(0,0,3);world.blocks.put(bed,Blocks.bed.getDefaultState());
            dev.vibe.Vibe vibe=new dev.vibe.Vibe();set(dev.vibe.Vibe.class,null,"instance",vibe);
            dev.vibe.module.ModuleManager modules=allocate(dev.vibe.module.ModuleManager.class);set(dev.vibe.Vibe.class,vibe,"moduleManager",modules);
            dev.vibe.module.impl.movement.MoveFixModule fix=new dev.vibe.module.impl.movement.MoveFixModule();
            BedAuraModule aura=new BedAuraModule();set(dev.vibe.module.ModuleManager.class,modules,"modules",new java.util.ArrayList<dev.vibe.module.Module>(java.util.Arrays.asList(fix,aura)));
            set(dev.vibe.module.Module.class,fix,"enabled",true);set(dev.vibe.module.Module.class,aura,"enabled",true);
            set(BedAuraModule.class,aura,"owner",player);set(BedAuraModule.class,aura,"bed",bed);set(BedAuraModule.class,aura,"ready",true);
            aura.beforeWalkingUpdate(player);float before=fix.getRotationYaw();
            player.posX=.4; aura.beforeWalkingUpdate(player);
            assertTrue(Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(fix.getRotationYaw()-before))>1);
            aura.afterWalkingUpdate(player);assertEquals(java.util.Arrays.asList("swing","dig"),player.actions);
            assertTrue(aura.isActivelyMining());
        } finally {set(Minecraft.class,null,"theMinecraft",previous);set(dev.vibe.Vibe.class,null,"instance",previousVibe);}
    }
    private static void set(Class<?> type,Object object,String name,Object value)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private static <T>T allocate(Class<T> type)throws Exception{Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);return type.cast(u.getMethod("allocateInstance",Class.class).invoke(f.get(null),type));}
    public static final class Player extends EntityPlayerSP {
        java.util.List<String> actions;
        private Player(){super(null,null,null,null);}
        @Override public Vec3 getPositionEyes(float partialTicks){return new Vec3(posX+.5,1.62,.5);}
        @Override public void swingItem(){if(actions!=null)actions.add("swing");}
    }
    public static final class Controller extends PlayerControllerMP {
        java.util.List<String> actions;
        private Controller(){super(null,null);}
        @Override public float getBlockReachDistance(){return 4.5F;}
        @Override public boolean onPlayerDamageBlock(BlockPos block,EnumFacing face){if(actions!=null)actions.add("dig");return true;}
    }
    public static final class World extends WorldClient {
        Map<BlockPos,IBlockState> blocks;
        private World(){super(null,null,0,null,null);}
        @Override public IBlockState getBlockState(BlockPos pos){IBlockState state=blocks.get(pos);return state==null?Blocks.air.getDefaultState():state;}
        @Override public boolean isAirBlock(BlockPos pos){return getBlockState(pos).getBlock()==Blocks.air;}
    }
}
