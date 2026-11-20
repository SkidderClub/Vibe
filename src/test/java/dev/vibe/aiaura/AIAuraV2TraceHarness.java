package dev.vibe.aiaura;

import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.*;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.*;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

/**
 * Not a test: a recorder. With VIBE_TRACE_DIR set it lets AIAura V2 fight and writes what a server would
 * see of it, tick by tick, to trace.csv there - the rotation, the swings and attacks sent.
 *
 * <p>The fight is one of yours (VIBE_TRACE_REPLAY, a Record Fights csv, and VIBE_TRACE_FIGHT, its fight
 * number): both of you go exactly where you went, and only the aim and the clicking are the aura's - so
 * the two compare like for like. Without it, a made-up opponent strafes and jumps round it for 40 s.
 * VIBE_TRACE_MODEL is a COPY of an aim.bin to fight with (it is saved to). Ticks are 50 ms of real time,
 * and vanilla 1.8.9's miss delay is as in the game: it only holds while the button stays down.
 */
public class AIAuraV2TraceHarness {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final double[] JUMP = {0.42, 0.75, 1.0, 1.17, 1.25, 1.25, 1.17, 1.02, 0.79, 0.48, 0.1};

    @Before @After public void forget() {
        AimModel.detach();
        AimModel.clear();
    }

    @Test public void trace() throws Exception {
        String dir = System.getenv("VIBE_TRACE_DIR"), model = System.getenv("VIBE_TRACE_MODEL"), replay = System.getenv("VIBE_TRACE_REPLAY");
        Assume.assumeTrue(dir != null);
        if (model != null) AimModel.load(new File(model));
        String learn = System.getenv("VIBE_TRACE_LEARN");      // recordings to learn your aim from first, ; between them
        if (learn != null) for (String csv : learn.split(";")) AimModel.learnFrom(new File(csv));
        Files.write(new File(dir, "learned.txt").toPath(), String.valueOf(AimModel.steps()).getBytes());
        String aimMode = System.getenv("VIBE_TRACE_AIM");
        List<double[]> fight = new ArrayList<double[]>();
        if (replay != null) {
            String wanted = System.getenv("VIBE_TRACE_FIGHT");
            for (String line : Files.readAllLines(new File(replay).toPath())) {
                if (line.startsWith("tick")) continue;
                String[] cells = line.split(",");
                if (wanted != null && !cells[18].equals(wanted)) continue;
                double[] row = new double[cells.length];
                for (int i = 0; i < cells.length; i++) row[i] = Double.parseDouble(cells[i]);
                fight.add(row);
            }
            Assume.assumeTrue(!fight.isEmpty());
        }
        try (PathTestFixture f = new PathTestFixture(); PrintWriter out = new PrintWriter(new File(dir, "trace.csv"))) {
            PathTestFixture.set(Minecraft.class, f.mc, "mcDataDir", temp.newFolder());
            AIAuraIntegrationTest.TestWorld world = PathTestFixture.allocate(AIAuraIntegrationTest.TestWorld.class);
            PathTestFixture.set(net.minecraft.world.World.class, world, "loadedEntityList", new ArrayList<Entity>());
            f.mc.theWorld = world;
            AIAuraIntegrationTest.Queue queue = PathTestFixture.allocate(AIAuraIntegrationTest.Queue.class);
            queue.packets = new ArrayList<Packet<?>>();
            AIAuraIntegrationTest.Player self = player(world, 1), enemy = player(world, 2);
            self.inventory = new InventoryPlayer(self);
            self.movementInput = new MovementInput();
            self.onGround = true;
            PathTestFixture.set(EntityPlayerSP.class, self, "sendQueue", queue);
            PathTestFixture.set(EntityPlayerSP.class, self, "mc", f.mc);
            f.mc.thePlayer = self;
            f.mc.inGameHasFocus = true;
            f.mc.displayWidth = 960;
            f.mc.displayHeight = 600;
            f.mc.gameSettings.mouseSensitivity = .5F;
            f.mc.gameSettings.fovSetting = 70;
            PathTestFixture.set(Minecraft.class, f.mc, "renderViewEntity", self);
            f.mc.playerController = new PlayerControllerMP(f.mc, queue);
            f.filters.getIgnoreTeammates().setEnabled(false);

            AIAuraV2Module v2 = new AIAuraV2Module();
            MoveFixModule fix = new MoveFixModule();
            f.modules.registerDynamic(v2);
            f.modules.registerDynamic(fix);
            number(v2, "reaction", env("VIBE_REACTION", 80));
            number(v2, "trackingDelay", env("VIBE_TRACKING", 65));
            number(v2, "missRate", env("VIBE_MISS", 10));
            number(v2, "fov", 360);
            RangeSetting cps = (RangeSetting) field(AIAuraV2Module.class, "cps").get(v2);
            cps.setMin(env("VIBE_CPS_MIN", 8));
            cps.setMax(env("VIBE_CPS_MAX", 13.5));
            ((ModeSetting) field(AIAuraV2Module.class, "clicking").get(v2)).setValue("Learned");
            if (aimMode != null) ((ModeSetting) field(AIAuraV2Module.class, "aim").get(v2)).setValue(aimMode);
            if (System.getenv("VIBE_PULL") != null) field(AIAuraV2Module.class, "PULL").setFloat(null, Float.parseFloat(System.getenv("VIBE_PULL")));
            PathTestFixture.set(Module.class, v2, "enabled", true);
            Method click = Minecraft.class.getDeclaredMethod("clickMouse");
            click.setAccessible(true);
            Field missDelay = field(Minecraft.class, "leftClickCounter"), resting = field(AIAuraV2Module.class, "resting"),
                    polled = field(AIAuraV2Module.class, "polled");
            missDelay.setInt(f.mc, 0);

            Random r = new Random(11);
            double sx = 0, sz = 0, ex = 0, ez = 3, sy = 64, ey = 64;
            double eDir = 1, sDir = -1, want = 2.8, push = 0;
            int eSwitch = 10, sSwitch = 14, wantSwitch = 30, eJump = -1, sJump = -1, resist = 0, ownHit = 20, ownHurtWas = 0;
            if (!fight.isEmpty()) {
                double[] first = fight.get(0);
                sx = first[4]; sy = first[5]; sz = first[6]; ex = first[7]; ey = first[8]; ez = first[9];
                self.rotationYaw = (float) first[2];
                self.rotationPitch = (float) first[3];
            }
            place(self, sx, sy, sz, sx, sy, sz);
            place(enemy, ex, ey, ez, ex, ey, ez);
            out.println("tick,ms,yaw,pitch,sx,sy,sz,ex,ey,ez,dist,swings,attacks,hurt,resting,clicks");
            long start = System.nanoTime();
            int ticks = fight.isEmpty() ? 800 : fight.size();
            for (int tick = 0; tick < ticks; tick++) {
                double nex, ney, nez, nsx, nsy, nsz;
                if (!fight.isEmpty()) {                  // where the two of you were
                    double[] row = fight.get(tick);
                    nsx = row[4]; nsy = row[5]; nsz = row[6]; nex = row[7]; ney = row[8]; nez = row[9];
                    self.onGround = row[16] == 1;
                    if (row[15] > ownHurtWas) self.hurtTime = (int) row[15];
                    ownHurtWas = (int) row[15];
                } else {                                 // strafing round one another, changing sides, closing and backing off, jumping now and then
                    double dx = ex - sx, dz = ez - sz, d = Math.sqrt(dx * dx + dz * dz), ux = dx / d, uz = dz / d;
                    if (--eSwitch <= 0) { eDir = -eDir; eSwitch = 6 + r.nextInt(25); }
                    if (--sSwitch <= 0) { sDir = -sDir; sSwitch = 8 + r.nextInt(30); }
                    if (--wantSwitch <= 0) { want = 1.8 + r.nextDouble() * 2.4; wantSwitch = 20 + r.nextInt(40); }
                    double radial = Math.max(-0.12, Math.min(0.12, (want - d) * 0.3)) + push;
                    push *= 0.6;
                    nex = ex + ux * radial - uz * eDir * 0.2;
                    nez = ez + uz * radial + ux * eDir * 0.2;
                    nsx = sx + uz * sDir * 0.13;
                    nsz = sz - ux * sDir * 0.13;
                    if (eJump < 0 && r.nextInt(30) == 0) eJump = 0;
                    if (sJump < 0 && r.nextInt(45) == 0) sJump = 0;
                    ney = 64 + (eJump >= 0 ? JUMP[eJump] : 0);
                    nsy = 64 + (sJump >= 0 ? JUMP[sJump] : 0);
                    if (eJump >= 0 && ++eJump >= JUMP.length) eJump = -1;
                    self.onGround = sJump < 0;
                    if (sJump >= 0 && ++sJump >= JUMP.length) sJump = -1;
                    if (--ownHit <= 0 && d < 3.2) { self.hurtTime = 10; ownHit = 9 + r.nextInt(14); }
                }
                place(enemy, nex, ney, nez, ex, ey, ez);
                place(self, nsx, nsy, nsz, sx, sy, sz);
                ex = nex; ey = ney; ez = nez; sx = nsx; sy = nsy; sz = nsz;
                if (enemy.hurtTime > 0) enemy.hurtTime--;
                if (self.hurtTime > 0) self.hurtTime--;
                if (resist > 0) resist--;

                fix.beginRotationTick();
                v2.tickStart();
                fix.tick();
                queue.packets.clear();
                KillAuraModule.prepareInputHook();
                while (f.mc.gameSettings.keyBindAttack.isPressed()) click.invoke(f.mc);
                // vanilla, after the clicks: the miss delay counts down a tick at a time, and is 0 again after every tick the button is not held
                int counter = missDelay.getInt(f.mc);
                if (!f.mc.gameSettings.keyBindAttack.isKeyDown()) missDelay.setInt(f.mc, 0);
                else if (counter > 0) missDelay.setInt(f.mc, counter - 1);

                int swings = 0, attacks = 0;
                for (Packet<?> packet : queue.packets) {
                    if (packet instanceof C0APacketAnimation) swings++;
                    if (packet instanceof C02PacketUseEntity) attacks++;
                }
                v2.tickEnd();
                int hurt = enemy.hurtTime;               // as it was when the attack went: the hurt shows a tick later
                if (attacks > 0 && resist == 0) { enemy.hurtTime = 10; resist = 10; push = fight.isEmpty() ? 0.35 : 0; }
                AxisAlignedBB box = enemy.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
                Vec3 eye = new Vec3(sx, sy + 1.62, sz);
                double reach = eye.distanceTo(new Vec3(clamp(eye.xCoord, box.minX, box.maxX), clamp(eye.yCoord, box.minY, box.maxY), clamp(eye.zCoord, box.minZ, box.maxZ)));
                out.println(tick + "," + (System.nanoTime() - start) / 1000000L + "," + fix.getRotationYaw() + "," + fix.getRotationPitch() + "," + sx + "," + sy + "," + sz
                        + "," + ex + "," + ey + "," + ez + "," + reach + "," + swings + "," + attacks + "," + hurt + "," + (resting.getBoolean(v2) ? 1 : 0)
                        + "," + polled.getInt(null));     // the aura's own clicks this tick, sent or not
                polled.setInt(null, 0);
                long next = start + (tick + 1) * 50000000L;
                while (System.nanoTime() < next) Thread.sleep(1L);
            }
        }
    }

    private static double env(String name, double fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : Double.parseDouble(value);
    }

    private static void number(AIAuraV2Module v2, String name, double value) throws Exception {
        ((NumberSetting) field(AIAuraV2Module.class, name).get(v2)).setValue(value);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    private static void place(Entity p, double x, double y, double z, double px, double py, double pz) {
        p.prevPosX = p.lastTickPosX = px;
        p.prevPosY = p.lastTickPosY = py;
        p.prevPosZ = p.lastTickPosZ = pz;
        p.posX = x;
        p.posY = y;
        p.posZ = z;
        p.setEntityBoundingBox(new AxisAlignedBB(x - .3, y, z - .3, x + .3, y + 1.8, z + .3));
    }

    private static AIAuraIntegrationTest.Player player(AIAuraIntegrationTest.TestWorld world, int id) throws Exception {
        AIAuraIntegrationTest.Player p = PathTestFixture.allocate(AIAuraIntegrationTest.Player.class);
        p.setEntityId(id);
        p.worldObj = world;
        p.width = .6F;
        p.height = 1.8F;
        DataWatcher watcher = new DataWatcher(p);
        watcher.addObject(6, 20F);
        PathTestFixture.set(Entity.class, p, "dataWatcher", watcher);
        net.minecraft.entity.ai.attributes.ServersideAttributeMap attributes = new net.minecraft.entity.ai.attributes.ServersideAttributeMap();
        attributes.registerAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(20);
        PathTestFixture.set(EntityLivingBase.class, p, "attributeMap", attributes);
        world.loadedEntityList.add(p);
        return p;
    }
}
