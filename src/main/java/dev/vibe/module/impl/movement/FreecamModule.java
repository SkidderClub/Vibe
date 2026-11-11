package dev.vibe.module.impl.movement;

import dev.vibe.Vibe;
import dev.vibe.camera.DroneCollisions;
import dev.vibe.camera.FpvFlight;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.achievement.GuiAchievement;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovementInput;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;

/** FPV flight of a render camera, with no fake entity or player-state transport. */
public final class FreecamModule extends Module {
    private final ModeSetting flightMode = addSetting(new ModeSetting("Flight Mode", "Acro", "Acro", "Angle"));
    private final NumberSetting rates = addSetting(new NumberSetting("Turn Rate", 120, 30, 360, 5));
    private final NumberSetting maxTilt = addSetting(new NumberSetting("Max Tilt", 45, 10, 75, 1,
            () -> flightMode.is("Angle")));
    private final NumberSetting thrust = addSetting(new NumberSetting("Thrust", 20, 12, 40, 1));
    private final NumberSetting drag = addSetting(new NumberSetting("Air Drag", 0.35, 0.05, 2, 0.05));
    private final BooleanSetting collisions = addSetting(new BooleanSetting("Collisions", false));
    private final NumberSetting cameraTilt = addSetting(new NumberSetting("Camera Tilt", 15, 0, 50, 1));
    private final NumberSetting fov = addSetting(new NumberSetting("Drone FOV", 105, 60, 130, 1));
    private final BooleanSetting droneHud = addSetting(new BooleanSetting("Drone HUD", true));
    private final BooleanSetting hideHud = addSetting(new BooleanSetting("Hide HUD", true));
    public final BooleanSetting cameraRig=addSetting(new BooleanSetting("Camera Rig",false));
    public final BooleanSetting openRig=addSetting(new BooleanSetting("Open Camera Rig",false,cameraRig::isEnabled).onEnable(()->{openRigRequested=true;}));
    public final ModeSetting rigMode=addSetting(new ModeSetting("Rig Timing","Per Pose",cameraRig::isEnabled,"Per Pose","Fixed Speed"));
    public final NumberSetting rigSpeed=addSetting(new NumberSetting("Rig Speed",2,.1,15,.1,()->cameraRig.isEnabled()&&rigMode.is("Fixed Speed")));
    public final BooleanSetting rigPreview=addSetting(new BooleanSetting("Rig Preview",false,cameraRig::isEnabled)),rigCollision=addSetting(new BooleanSetting("Rig Collision Check",true,cameraRig::isEnabled));
    private final dev.vibe.setting.StringSetting rigData=addSetting(new dev.vibe.setting.StringSetting("Rig Poses","[]",64000,()->false));
    public final dev.vibe.camera.CameraRig rig=new dev.vibe.camera.CameraRig(rigData);
    public final BooleanSetting droneTrail=addSetting(new BooleanSetting("Drone Trail",false));
    public final NumberSetting trailLifetime=addSetting(new NumberSetting("Trail Lifetime (s)",3,.5,10,.25,droneTrail::isEnabled));
    public final dev.vibe.features.WorldStyle trailStyle=new dev.vibe.features.WorldStyle(this::addSetting,droneTrail::isEnabled,true);
    public static final class TrailPoint{public final net.minecraft.util.Vec3 position;public final long born;TrailPoint(net.minecraft.util.Vec3 p,long b){position=p;born=b;}}
    public final java.util.Deque<TrailPoint> trail=new java.util.ArrayDeque<>();
    private boolean droneView,openRigRequested,deathReview;private double viewX,viewY,viewZ;
    private final FpvFlight flight = new FpvFlight();
    private final FloatBuffer viewMatrix = BufferUtils.createFloatBuffer(16);
    private WorldClient cameraWorld;
    private EntityPlayerSP cameraPlayer;
    private DroneCollisions cameraCollisions;
    private long lastFrame;
    private double framePartialTicks;

    public FreecamModule() {
        super("Freecam", "Fly the third-person camera as an FPV drone; WASD pitch/roll, mouse steer, Space/Shift thrust",
                Category.VISUAL, Keyboard.KEY_NONE);
    }

    @Override protected void onEnable() { droneView=false;tick(); }
    @Override protected void onDisable() { clearCamera(); }

    /** Only establishes/resets private camera state when a world/player changes. */
    public void tick() {
        if(openRig.isEnabled())openRig.setEnabled(false);if(openRigRequested){openRigRequested=false;if(!isConfigLoading())Minecraft.getMinecraft().displayGuiScreen(new dev.vibe.ui.screen.CameraRigGui(this,Minecraft.getMinecraft().currentScreen));}
        if (!isEnabled()&&!deathReview) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.isDead&&!deathReview) {
            clearCamera();
            return;
        }
        if (cameraWorld == mc.theWorld && cameraPlayer == mc.thePlayer) return;
        cameraWorld = mc.theWorld;
        cameraPlayer = mc.thePlayer;
        cameraCollisions = new DroneCollisions(cameraWorld);
        double yaw = Math.toRadians(cameraPlayer.rotationYaw);
        // Start just behind the body, like a detached third-person camera.
        flight.reset(cameraPlayer.posX + Math.sin(yaw) * 2.5,
                cameraPlayer.posY + cameraPlayer.getEyeHeight() + 0.5,
                cameraPlayer.posZ - Math.cos(yaw) * 2.5, cameraPlayer.rotationYaw);
        lastFrame = System.nanoTime();
    }

    public void frameTick(float partialTicks) {
        tick();
        framePartialTicks = partialTicks;
        if (!hasCamera()) return;
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        double dt = Math.min(0.05, Math.max(0, (now - lastFrame) * 1.0e-9));
        lastFrame = now;
        boolean review=deathReview&&mc.currentScreen instanceof dev.vibe.ui.screen.DeathReviewGui;
        if (mc.currentScreen != null&&!(mc.currentScreen instanceof dev.vibe.ui.screen.CameraRigGui)&&!review||mc.currentScreen==null&&!mc.inGameHasFocus||mc.isGamePaused()) return;
        if(review&&org.lwjgl.input.Mouse.isCreated()&&org.lwjgl.input.Mouse.isButtonDown(1))flight.look(org.lwjgl.input.Mouse.getDX()*.15,-org.lwjgl.input.Mouse.getDY()*.15,flightMode.is("Angle"));
        if(cameraRig.isEnabled()&&rig.playing())rig.step(dt,flight,rigMode.is("Fixed Speed"),rigSpeed.getDouble(),rigCollision.isEnabled());
        else if(mc.currentScreen==null||review)flight.step(dt, control(mc.gameSettings.keyBindForward,review) - control(mc.gameSettings.keyBindBack,review),
                control(mc.gameSettings.keyBindRight,review) - control(mc.gameSettings.keyBindLeft,review),
                control(mc.gameSettings.keyBindJump,review) - control(mc.gameSettings.keyBindSneak,review),
                flightMode.is("Angle"), rates.getDouble(), maxTilt.getDouble(), thrust.getDouble(), drag.getDouble(),
                collisions.isEnabled() ? cameraCollisions : null);
        updateView();long stamp=System.nanoTime()/1000000;
        if(droneTrail.isEnabled()){net.minecraft.util.Vec3 point=new net.minecraft.util.Vec3(flight.x,flight.y,flight.z);if(trail.isEmpty()||trail.peekLast().position.squareDistanceTo(point)>.0025)trail.addLast(new TrailPoint(point,stamp));while(!trail.isEmpty()&&(trail.size()>256||stamp-trail.peekFirst().born>trailLifetime.getDouble()*1000))trail.removeFirst();}else trail.clear();
    }

    private void clearCamera() {
        rig.stop();trail.clear();
        cameraWorld = null;
        cameraPlayer = null;
        cameraCollisions = null;
        lastFrame = 0;
    }

    public boolean hasCamera() {
        Minecraft mc = Minecraft.getMinecraft();
        return (isEnabled()||deathReview) && cameraPlayer != null && cameraPlayer == mc.thePlayer
                && cameraWorld == mc.theWorld && (!cameraPlayer.isDead||deathReview)
                && mc.getRenderViewEntity() == cameraPlayer;
    }

    private static FreecamModule active() {
        Vibe vibe = Vibe.getInstance();
        FreecamModule module = vibe == null || vibe.getModuleManager() == null ? null
                : vibe.getModuleManager().getModule(FreecamModule.class);
        return module != null && module.hasCamera() ? module : null;
    }

    /** Replaces orientCamera only. Everything else keeps the real player as its view entity. */
    public static boolean orientCameraHook(float partialTicks) {
        FreecamModule module = active();
        if (module == null) return dev.vibe.module.impl.visual.SmoothF5Module.orientCameraHook(partialTicks);
        module.framePartialTicks = partialTicks;
        module.updateView();
        if(module.droneView){GlStateManager.rotate(20,1,0,0);GlStateManager.rotate((float)module.flight.yaw()+180,0,1,0);}
        else{module.viewMatrix.clear();module.viewMatrix.put(module.flight.viewMatrix(module.cameraTilt.getDouble())).flip();GlStateManager.multMatrix(module.viewMatrix);}
        Entity player = module.cameraPlayer;
        // World meshes/entities remain relative to vanilla's player origin.
        // The offset lives exclusively in the model-view matrix.
        GlStateManager.translate(interpolate(player.lastTickPosX, player.posX, partialTicks) - module.cameraX(),
                interpolate(player.lastTickPosY, player.posY, partialTicks) - module.cameraY(),
                interpolate(player.lastTickPosZ, player.posZ, partialTicks) - module.cameraZ());
        return true;
    }

    /** Called in the renderer's mouse path before vanilla can write player yaw/pitch. */
    public static void mouseLookHook(Object entity, float yaw, float pitch) {
        if(yaw!=0||pitch!=0)dev.vibe.features.UserActivity.mark();
        AntiAFKModule.lookInputHook(yaw, pitch);
        FreecamModule module = active();
        if (module != null && entity == module.cameraPlayer) {
            module.flight.look(yaw * 0.15, -pitch * 0.15, module.flightMode.is("Angle"));
        } else if (!dev.vibe.module.impl.visual.FreeLookModule.mouse(entity,yaw,pitch)&&!dev.vibe.module.impl.combat.AIAuraModule.lockMouseLookHook(entity)) {
            ((Entity) entity).setAngles(yaw, pitch);
        }
    }

    /** Divert control input only; normal player ticks, gravity and knockback remain active. */
    public static void movementInputHook(Object entity) {
        FreecamModule module = active();
        if (module == null || entity != module.cameraPlayer) return;
        MovementInput input = module.cameraPlayer.movementInput;
        input.moveForward = input.moveStrafe = 0;
        input.jump = input.sneak = false;
        AntiAFKModule.freecamBodyInputHook(input);
    }

    public static boolean cameraActiveHook() { return active() != null; }

    public static boolean hideHudHook() {
        FreecamModule module = active();
        return module != null && module.hideHud.isEnabled();
    }

    /** Achievement toasts are rendered after GuiIngame, outside overlay events. */
    public static void achievementHudHook(Object overlay) {
        if (!hideHudHook()) ((GuiAchievement) overlay).updateAchievementWindow();
    }

    /** The sky is camera-centred, unlike world meshes rendered relative to the player. */
    public static void renderSkyHook(Object renderer, float partialTicks, int pass) {
        FreecamModule module = active();
        if (module == null) {
            ((RenderGlobal) renderer).renderSky(partialTicks, pass);
            return;
        }
        GlStateManager.pushMatrix();
        try {
            Entity player = module.cameraPlayer;
            GlStateManager.translate(module.cameraX() - interpolate(player.lastTickPosX, player.posX, partialTicks),
                    module.cameraY() - interpolate(player.lastTickPosY, player.posY, partialTicks),
                    module.cameraZ() - interpolate(player.lastTickPosZ, player.posZ, partialTicks));
            ((RenderGlobal) renderer).renderSky(partialTicks, pass);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    /** Render-only equivalent of third-person mode, so the real body is drawn. */
    public static int thirdPersonHook(int vanilla) { return active()!=null||dev.vibe.module.impl.visual.FreeLookModule.cameraActive()?1:dev.vibe.module.impl.visual.SmoothF5Module.thirdPersonHook(vanilla); }
    public static boolean frontViewHook(boolean vanilla) { return active()==null&&!dev.vibe.module.impl.visual.FreeLookModule.cameraActive()&&dev.vibe.module.impl.visual.SmoothF5Module.frontViewHook(vanilla); }

    public static float fovHook(float vanilla) {
        FreecamModule module = active();
        return module == null ? vanilla : module.fov.getFloat();
    }

    public static float yawHook(Object entity, float vanilla) {
        FreecamModule module = active();
        return module == null || entity != module.cameraPlayer ? dev.vibe.module.impl.visual.FreeLookModule.yaw(entity,dev.vibe.module.impl.visual.SmoothF5Module.yawHook(entity,dev.vibe.module.impl.combat.AIAuraModule.cameraYawHook(entity,vanilla)))
                : (float) (module.droneView?module.flight.yaw():module.flight.cameraYaw(module.cameraTilt.getDouble()));
    }

    public static float pitchHook(Object entity, float vanilla) {
        FreecamModule module = active();
        return module == null || entity != module.cameraPlayer ? dev.vibe.module.impl.visual.FreeLookModule.pitch(entity,dev.vibe.module.impl.visual.SmoothF5Module.pitchHook(entity,dev.vibe.module.impl.combat.AIAuraModule.cameraPitchHook(entity,vanilla)))
                : (float) (module.droneView?20:module.flight.cameraPitch(module.cameraTilt.getDouble()));
    }

    /** Values used only by terrain visibility/chunk selection, never by entity updates. */
    public static double terrainXHook(Object entity, double vanilla) {
        FreecamModule module = active();
        return module == null || entity != module.cameraPlayer ? vanilla : module.cameraX();
    }

    public static double terrainYHook(Object entity, double vanilla) {
        FreecamModule module = active();
        return module == null || entity != module.cameraPlayer ? vanilla
                : module.cameraY() - module.cameraPlayer.getEyeHeight();
    }

    public static double terrainZHook(Object entity, double vanilla) {
        FreecamModule module = active();
        return module == null || entity != module.cameraPlayer ? vanilla : module.cameraZ();
    }

    public static int terrainChunkXHook(Object entity, int vanilla) {
        return (int) Math.floor(terrainXHook(entity, vanilla * 16.0) / 16.0);
    }

    public static int terrainChunkYHook(Object entity, int vanilla) {
        return (int) Math.floor(terrainYHook(entity, vanilla * 16.0) / 16.0);
    }

    public static int terrainChunkZHook(Object entity, int vanilla) {
        return (int) Math.floor(terrainZHook(entity, vanilla * 16.0) / 16.0);
    }

    /** Terrain selection follows the drone, but mesh translation retains vanilla's render origin. */
    public static void terrainOriginHook(Object container, double x, double y, double z) {
        FreecamModule module = active();
        if (module != null) {
            Entity player = module.cameraPlayer;
            double partial = module.framePartialTicks;
            x = interpolate(player.lastTickPosX, player.posX, partial);
            y = interpolate(player.lastTickPosY, player.posY, partial);
            z = interpolate(player.lastTickPosZ, player.posZ, partial);
        }
        ((ChunkRenderContainer) container).initialize(x, y, z);
    }

    private static double interpolate(double previous, double current, double partial) {
        return previous + (current - previous) * partial;
    }

    private static int key(net.minecraft.client.settings.KeyBinding binding) { return binding.isKeyDown() ? 1 : 0; }
    private static int control(net.minecraft.client.settings.KeyBinding binding,boolean review){return review?(dev.vibe.input.VanillaClicks.physicallyDown(binding)?1:0):key(binding);}
    public boolean shouldDrawDroneHud() { return hasCamera() && droneHud.isEnabled(); }
    public FpvFlight getFlight() { return flight; }
    public ModeSetting getFlightMode() { return flightMode; }
    public double getCameraTilt() { return cameraTilt.getDouble(); }
    public double getDroneFov() { return fov.getDouble(); }
    public boolean hasCollisions() { return collisions.isEnabled(); }
    public boolean droneView(){return droneView;}
    public static int perspectiveChangeHook(int proposed){FreecamModule m=dev.vibe.features.FeatureModule.module(FreecamModule.class);if(m!=null&&m.hasCamera()){m.cyclePerspective();return Minecraft.getMinecraft().gameSettings.thirdPersonView;}return proposed;}
    public void cyclePerspective(){if(!isEnabled()&&!deathReview)return;droneView=!droneView;updateView();}
    public boolean beginDeathReview(){Minecraft mc=Minecraft.getMinecraft();if(mc.theWorld==null||mc.thePlayer==null||!mc.thePlayer.isDead)return false;clearCamera();deathReview=true;droneView=false;tick();return hasCamera();}
    public void endDeathReview(){deathReview=false;clearCamera();}
    private void updateView(){if(!droneView)return;double yaw=Math.toRadians(flight.yaw());net.minecraft.util.Vec3 center=new net.minecraft.util.Vec3(flight.x,flight.y,flight.z),desired=center.addVector(Math.sin(yaw)*3.5,1.3,-Math.cos(yaw)*3.5);net.minecraft.util.MovingObjectPosition hit=cameraWorld==null?null:cameraWorld.rayTraceBlocks(center,desired,false,true,false);if(hit!=null){net.minecraft.util.Vec3 d=hit.hitVec.subtract(center).normalize();desired=hit.hitVec.addVector(-d.xCoord*.2,-d.yCoord*.2,-d.zCoord*.2);}viewX=desired.xCoord;viewY=desired.yCoord;viewZ=desired.zCoord;}
    private double cameraX(){return droneView?viewX:flight.x;}private double cameraY(){return droneView?viewY:flight.y;}private double cameraZ(){return droneView?viewZ:flight.z;}
}
