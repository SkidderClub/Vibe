package dev.vibe.path;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.combat.TargetsModule;
import dev.vibe.module.impl.visual.PathModule;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

/** Real Minecraft block shapes; unloaded reads are assertion failures, no server or window needed. */
public final class PathTestFixture implements AutoCloseable {
    private final Minecraft previousMinecraft = Minecraft.getMinecraft();
    private final Vibe previousVibe = Vibe.getInstance();
    public final Minecraft mc;
    public final FixtureWorld world;
    public final Player player;
    public final PathModule module;
    public final TargetsModule filters;
    public final ModuleManager modules;
    public final Vibe vibe;

    public PathTestFixture() throws Exception {
        dev.vibe.camera.DroneCollisionsTest.registerBlocks();
        mc = allocate(Minecraft.class); set(Minecraft.class, null, "theMinecraft", mc);
        mc.gameSettings = new GameSettings();
        world = allocate(FixtureWorld.class); world.blocks = new HashMap<>(); world.entities = new HashMap<>();
        world.loaded = true;
        set(World.class, world, "loadedEntityList", new ArrayList<Entity>());
        set(World.class, world, "playerEntities", new ArrayList<net.minecraft.entity.player.EntityPlayer>());
        mc.theWorld = world;
        player = addPlayer(1, "Self", .5, 64, .5); mc.thePlayer = player;
        set(Minecraft.class, mc, "renderViewEntity", player);
        vibe = allocate(Vibe.class); set(Vibe.class, null, "instance", vibe);
        modules = allocate(ModuleManager.class); set(ModuleManager.class, modules, "modules", new ArrayList<Module>());
        set(Vibe.class, vibe, "moduleManager", modules);
        filters = new TargetsModule(); module = new PathModule();
        modules.registerDynamic(filters); modules.registerDynamic(module);
        floor(-12, 12, -12, 12, 63);
    }

    public Player addPlayer(int id, String name, double x, double y, double z) throws Exception {
        Player entity = allocate(Player.class); entity.setEntityId(id); entity.name = name;
        entity.localMessages = new ArrayList<String>(); entity.sentMessages = new ArrayList<String>();
        entity.width = .6F; entity.height = 1.8F; entity.alive = true; entity.worldObj = world;
        entity.posX = entity.lastTickPosX = x; entity.posY = entity.lastTickPosY = y; entity.posZ = entity.lastTickPosZ = z;
        world.entities.put(id, entity); world.loadedEntityList.add(entity); world.playerEntities.add(entity);
        return entity;
    }

    public void floor(int minX, int maxX, int minZ, int maxZ, int y) {
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) block(x, y, z, Blocks.stone.getDefaultState());
    }
    public void block(int x, int y, int z, IBlockState state) { world.blocks.put(new BlockPos(x, y, z), state); }
    public void forceReplan() throws Exception { setRouteState("nextSearch", 0L); set(PathModule.class, module, "nextSelection", 0L); }
    public void setRouteState(String name, Object value) throws Exception {
        Field field = PathModule.class.getDeclaredField("mainPath"); field.setAccessible(true);
        Object state = field.get(module); set(state.getClass(), state, name, value);
    }
    public void finishRoute() { for (int i = 0; i < 200 && module.getRoute().points.isEmpty(); i++) module.tick(); }

    @Override public void close() throws Exception {
        set(Minecraft.class, null, "theMinecraft", previousMinecraft); set(Vibe.class, null, "instance", previousVibe);
    }
    public static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    public static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe"); Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }

    public static class Player extends EntityPlayerSP {
        String name; boolean alive, hidden;
        public List<String> localMessages, sentMessages;
        private Player() { super(null, null, null, null); }
        @Override public String getName() { return name; }
        @Override public boolean isEntityAlive() { return alive && !isDead; }
        @Override public boolean isInvisible() { return hidden; }
        @Override public float getEyeHeight() { return 1.62F; }
        @Override public void addChatMessage(net.minecraft.util.IChatComponent message) {
            localMessages.add(net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(message.getUnformattedText()));
        }
        @Override public void sendChatMessage(String message) { sentMessages.add(message); }
    }

    public static class FixtureWorld extends WorldClient {
        public Map<BlockPos, IBlockState> blocks;
        public Map<Integer, Entity> entities;
        public boolean loaded;
        public int reads;
        protected FixtureWorld() { super(null, null, 0, null, null); }
        @Override public boolean isBlockLoaded(BlockPos pos) { return loaded && Math.abs(pos.getX()) < 32 && Math.abs(pos.getZ()) < 32; }
        @Override public IBlockState getBlockState(BlockPos pos) {
            if (!isBlockLoaded(pos)) throw new AssertionError("Path requested unloaded terrain: " + pos);
            reads++; return blocks.getOrDefault(pos, Blocks.air.getDefaultState());
        }
        @Override public Entity getEntityByID(int id) { return entities.get(id); }
    }
}
