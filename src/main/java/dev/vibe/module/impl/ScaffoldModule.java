/*
 * Ported from Gothaj (CFR 0.152), revision 8aa9e1c056bc5b6393b29dc03bcb06b529b40a07.
 * Source mapping and Forge adaptations: LICENSES/GOTHAJ-SCAFFOLD.md.
 */
package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.module.Category;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.module.impl.scaffold.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSkull;
import net.minecraft.block.BlockSnow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

/** Gothaj's Scaffold algorithm; platform-only adapters are at the end of this class. */
public final class ScaffoldModule extends Module {
    private int posY;
    private int spoofSlot;
    private int itemBefore;
    private int placed;
    private int offGroundTicks;
    private float scaffoldYaw;
    private float scaffoldPitch;
    private BlockPos blockPos;
    private EnumFacing facing;
    private boolean polarSneak;
    private boolean polarState;
    private final TimerUtil timer = new TimerUtil();
    private static Minecraft mc = Minecraft.getMinecraft();
    private final RotationComponent rotationComponent = new RotationComponent();
    private final ModeSetting mode = new ModeSetting("Mode", "Normal", "Normal", "Telly");
    private final ModeSetting rotations = new ModeSetting("Rotations", "Intave", () -> mode.is("Normal"), "Intave", "Polar", "God Bridge");
    private final BooleanSetting sideways = new BooleanSetting("Sideways", false);
    private final ModeSetting sprint = new ModeSetting("Sprint", "Always", "Always", "Off", "Legit");
    private final ModeSetting tower = new ModeSetting("Tower", "None", "None", "NCP", "Timer", "Intave");
    private final BooleanSetting sneak = new BooleanSetting("Sneak", false, () -> mode.is("Normal"));
    private final NumberSetting sneakDelay = new NumberSetting("Sneak Delay", 4, 1, 20, 1, () -> mode.is("Normal"));
    private final BooleanSetting safeWalk = new BooleanSetting("Safe Walk", true, () -> mode.is("Normal"));
    private final BooleanSetting moveFix = new BooleanSetting("Move Fix", true);
    private final BooleanSetting keepY = new BooleanSetting("Keep Y", true);
    private final BooleanSetting slotSpoof = new BooleanSetting("Spoof Slot", true);
    private final BooleanSetting swing = new BooleanSetting("Swing", false);
    private final BooleanSetting renderCount = new BooleanSetting("Render Count", false);
    private final BooleanSetting jump = new BooleanSetting("Jump", false, () -> mode.is("Normal"));
    private final BooleanSetting dragClick = new BooleanSetting("Drag Click", false);
    private final NumberSetting smoothSpeed = new NumberSetting("Smooth Speed", 30, 10, 180, 10);
    private net.minecraft.client.entity.EntityPlayerSP sessionPlayer;
    private boolean omniSprint;
    private double towerX, towerY, towerZ;
    private boolean towerPositionScoped;
    private final dev.vibe.ui.ScaffoldBlockCountRenderer blockCounter = new dev.vibe.ui.ScaffoldBlockCountRenderer();

    public ScaffoldModule() {
        super("Scaffold", "Gothaj Scaffold", Category.WORLD, Keyboard.KEY_NONE);
        registerSettings(this.mode, this.rotations, this.sideways, this.sprint, this.tower, this.keepY, this.sneak, this.sneakDelay, this.safeWalk, this.moveFix, this.slotSpoof, this.swing, this.jump, this.dragClick, this.renderCount, this.smoothSpeed);
    }

    public String getSuffix() {
        return this.mode.getValue();
    }

    @Override
    public void onDisable() {
        if (ScaffoldModule.mc.thePlayer == null || mc.thePlayer != sessionPlayer) {
            CombatTimerAccess.setSpeed(1.0F);
            rotationComponent.reset();
            omniSprint = polarSneak = polarState = false;
            return;
        }
        ScaffoldModule.mc.thePlayer.inventory.currentItem = this.itemBefore;
        rotationComponent.setRotations(ScaffoldModule.mc.thePlayer.rotationYaw, ScaffoldModule.mc.thePlayer.rotationPitch, this.smoothSpeed.getFloat(), this.smoothSpeed.getFloat() / 2.0f, this.shouldMoveFix());
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()));
        CombatTimerAccess.setSpeed(1.0f);
        this.omniSprint = false;
        this.polarSneak = false;
        this.polarState = false;
        super.onDisable();
    }

    @Override
    public void onEnable() {
        this.placed = 0;
        this.sessionPlayer = mc.thePlayer;
        rotationComponent.reset();
        if (ScaffoldModule.mc.thePlayer == null) {
            return;
        }
        rotationComponent.initialize();
        this.itemBefore = ScaffoldModule.mc.thePlayer.inventory.currentItem;
        this.posY = (int)(ScaffoldModule.mc.thePlayer.posY - 1.0);
        this.spoofSlot = ScaffoldModule.mc.thePlayer.inventory.currentItem;
        if (this.mode.is("Normal")) {
            this.scaffoldYaw = ScaffoldModule.mc.thePlayer.rotationYaw + 180.0f;
        }
        this.polarSneak = false;
        this.polarState = false;
        this.sprint();
        super.onEnable();
    }


    public void onMouseOver() {
        if (!this.hasBlocks()) {
            return;
        }
        this.processBlockData(ScaffoldModule.mc.thePlayer.posX, ScaffoldModule.mc.thePlayer.posZ);
        this.rotations();
    }

    public void onClick() {
        this.spoofSlot = ScaffoldModule.mc.thePlayer.inventory.currentItem;
        if (!this.hasBlocks()) {
            rotationComponent.setRotations(ScaffoldModule.mc.thePlayer.rotationYaw, ScaffoldModule.mc.thePlayer.rotationPitch, this.smoothSpeed.getFloat(), this.smoothSpeed.getFloat() / 2.0f, this.shouldMoveFix());
            return;
        }
        ScaffoldAccess.clearRightClickDelay();
        boolean wasPlaced = this.place();
        if (this.dragClick.isEnabled() && !wasPlaced && this.blockPos != null && ScaffoldModule.mc.objectMouseOver != null && ScaffoldModule.mc.objectMouseOver.getBlockPos() != null && ScaffoldModule.mc.objectMouseOver.getBlockPos().equals(this.blockPos) && ScaffoldModule.mc.thePlayer.onGround && !ScaffoldModule.mc.gameSettings.keyBindSneak.isKeyDown() && Math.random() > 0.5) {
            ScaffoldAccess.rightClickMouse();
        }
    }

    public void onMotion() {
        this.tower();
    }



    public void onPostMotion() {
        if (this.slotSpoof.isEnabled()) {
            ScaffoldModule.mc.thePlayer.inventory.currentItem = this.spoofSlot;
        }
    }


    public void onMoveInput(MoveInputEvent event) {
        if (!MovementUtil.isMoving() || event.isKeyJump() || event.isKeySneak() || this.rotations.is("Polar") && MovementUtil.isGoingDiagonally()) {
            this.placed = 0;
        }
        if (ScaffoldModule.mc.thePlayer.isSneaking() && !event.isKeySneak() && this.rotations.is("Polar")) {
            ScaffoldModule.mc.thePlayer.motionX = 0.0;
            ScaffoldModule.mc.thePlayer.motionZ = 0.0;
        }
        this.sprint();
        if (MovementUtil.isMoving() && ScaffoldModule.mc.thePlayer.onGround && !ScaffoldModule.mc.gameSettings.keyBindJump.isKeyDown()) {
            if (this.jump.isEnabled() || this.mode.is("Telly")) {
                event.setKeyJump(true);
            } else if (this.rotations.is("Polar") && this.placed >= MathUtil.getRandomNumber(7, 8)) {
                event.setKeyJump(true);
                this.placed = 0;
            }
        }
        if (this.sneak.isEnabled() && this.timer.hasTimeReached((long)this.sneakDelay.getDouble() * 100L)) {
            event.setSneak(true);
            event.setKeySneak(true);
            this.timer.reset();
        }
        if (this.safeWalk.isEnabled() && ScaffoldModule.mc.thePlayer.onGround && ScaffoldModule.mc.theWorld.getCollidingBoundingBoxes(ScaffoldModule.mc.thePlayer, ScaffoldModule.mc.thePlayer.getEntityBoundingBox().addCoord(ScaffoldModule.mc.thePlayer.motionX, ScaffoldModule.mc.thePlayer.motionY, ScaffoldModule.mc.thePlayer.motionZ).expand(-0.175, 0.0, -0.175)).isEmpty()) {
            event.setSneak(true);
            event.setKeySneak(true);
        }
        if (this.mode.is("Normal") && (this.rotations.is("Polar") || this.rotations.is("God Bridge"))) {
            boolean isOnRightSide;
            double movingYaw = (float)Math.round((ScaffoldModule.mc.thePlayer.rotationYaw - (float)(MovementUtil.isGoingDiagonally() ? 180 : 135)) / 45.0f) * 45.0f;
            boolean bl = isOnRightSide = Math.floor(ScaffoldModule.mc.thePlayer.posX + Math.cos(Math.toRadians(movingYaw)) * 0.22) != Math.floor(ScaffoldModule.mc.thePlayer.posX) || Math.floor(ScaffoldModule.mc.thePlayer.posZ + Math.sin(Math.toRadians(movingYaw)) * 0.22) != Math.floor(ScaffoldModule.mc.thePlayer.posZ);
            if (MovementUtil.isGoingDiagonally() || ScaffoldModule.mc.gameSettings.keyBindJump.isKeyDown()) {
                KeyBinding.setKeyBindState(ScaffoldModule.mc.gameSettings.keyBindSneak.getKeyCode(), false);
                return;
            }
            if (!isOnRightSide && event.isKeyBack()) {
                event.setKeyLeft(true);
            } else {
                event.setKeyLeft(false);
            }
            if (!this.polarSneak && !isOnRightSide) {
                KeyBinding.setKeyBindState(ScaffoldModule.mc.gameSettings.keyBindSneak.getKeyCode(), true);
                this.polarState = true;
            }
            if (this.polarState && isOnRightSide) {
                if (!this.timer.hasTimeReached(2000L)) {
                    KeyBinding.setKeyBindState(ScaffoldModule.mc.gameSettings.keyBindSneak.getKeyCode(), true);
                } else {
                    KeyBinding.setKeyBindState(ScaffoldModule.mc.gameSettings.keyBindSneak.getKeyCode(), false);
                    this.polarSneak = true;
                    this.polarState = false;
                    this.timer.reset();
                }
            } else if (!isOnRightSide) {
                this.timer.reset();
                this.polarSneak = false;
            }
        }
    }

    private boolean shouldMoveFix() {
        return this.moveFix.isEnabled();
    }

    public void tower() {
        this.offGroundTicks = !ScaffoldModule.mc.thePlayer.onGround ? ++this.offGroundTicks : 0;
        switch ((this.tower.getValue()).toLowerCase()) {
            case "ncp": {
                if (ScaffoldModule.mc.thePlayer.posY % 1.0 <= 0.00153598 && ScaffoldModule.mc.gameSettings.keyBindJump.isKeyDown()) {
                    ScaffoldModule.mc.thePlayer.setPosition(ScaffoldModule.mc.thePlayer.posX, Math.floor(ScaffoldModule.mc.thePlayer.posY), ScaffoldModule.mc.thePlayer.posZ);
                    ScaffoldModule.mc.thePlayer.motionY = 0.41998;
                    break;
                }
                if (!(ScaffoldModule.mc.thePlayer.posY % 1.0 < 0.1) || !ScaffoldModule.mc.thePlayer.onGround || !ScaffoldModule.mc.gameSettings.keyBindJump.isKeyDown()) break;
                ScaffoldModule.mc.thePlayer.setPosition(ScaffoldModule.mc.thePlayer.posX, Math.floor(ScaffoldModule.mc.thePlayer.posY), ScaffoldModule.mc.thePlayer.posZ);
                break;
            }
            case "timer": {
                if (!ScaffoldModule.mc.gameSettings.keyBindJump.isKeyDown()) break;
                CombatTimerAccess.setSpeed(1.25f);
            }
        }
    }

    public void sprint() {
        switch (this.sprint.getValue()) {
            case "Legit": {
                if (MovementUtil.isMoving() && ScaffoldModule.mc.gameSettings.keyBindForward.isKeyDown() && (double)Math.abs(MathHelper.wrapAngleTo180_float(ScaffoldModule.mc.thePlayer.rotationYaw) - MathHelper.wrapAngleTo180_float(rotationComponent.rotations[0])) < 66.5) {
                    KeyBinding.setKeyBindState(ScaffoldModule.mc.gameSettings.keyBindSprint.getKeyCode(), true);
                    this.omniSprint = true;
                    break;
                }
                KeyBinding.setKeyBindState(ScaffoldModule.mc.gameSettings.keyBindSprint.getKeyCode(), false);
                this.omniSprint = false;
                break;
            }
            case "Off": {
                KeyBinding.setKeyBindState(ScaffoldModule.mc.gameSettings.keyBindSprint.getKeyCode(), false);
                this.omniSprint = false;
                break;
            }
            case "Always": {
                if (MovementUtil.isMoving()) {
                    KeyBinding.setKeyBindState(ScaffoldModule.mc.gameSettings.keyBindSprint.getKeyCode(), true);
                    break;
                }
                this.omniSprint = false;
            }
        }
    }

    public void rotations() {
        boolean instantTurn = false;
        if (this.mode.is("Normal")) {
            if (MovementUtil.isMoving() && ScaffoldModule.mc.thePlayer.hurtTime == 0) {
                this.scaffoldYaw = (float)Math.toDegrees(MovementUtil.getDirectionKeybinds(ScaffoldModule.mc.thePlayer.rotationYaw - (float)(this.sideways.isEnabled() != false && !MovementUtil.isGoingDiagonally() ? 135 : 180)));
                this.scaffoldPitch = 76.0f;
            } else {
                this.scaffoldYaw = rotationComponent.rotations[0];
            }
            switch (this.rotations.getValue()) {
                case "Intave": {
                    if (this.blockPos != null && this.facing != null) {
                        this.scaffoldPitch = RotationUtil.getYawBasedPitch(this.blockPos, this.facing, this.scaffoldYaw, this.scaffoldPitch, 84);
                        if (RotationUtil.lookingAtBlock(this.blockPos, this.scaffoldYaw, this.scaffoldPitch, this.facing, true)) {
                            instantTurn = true;
                        } else if (!RotationUtil.lookingAtBlock(this.blockPos, this.scaffoldYaw, this.scaffoldPitch, this.facing, false)) {
                            float targetYaw = RotationUtil.getDirectionToBlock(this.blockPos.getX(), this.blockPos.getY(), this.blockPos.getZ(), this.facing)[0];
                            for (int ticks = 0; ticks <= 100 && !instantTurn; ++ticks) {
                                this.scaffoldYaw = RotationUtil.updateRotation(this.scaffoldYaw, targetYaw, (float)((double)1.8f + Math.random() / 10.0));
                                this.scaffoldPitch = RotationUtil.getYawBasedPitch(this.blockPos, this.facing, this.scaffoldYaw, this.scaffoldPitch, 84);
                                if (!RotationUtil.lookingAtBlock(this.blockPos, this.scaffoldYaw, this.scaffoldPitch, this.facing, true)) continue;
                                instantTurn = true;
                            }
                        }
                    }
                    rotationComponent.setRotations(this.scaffoldYaw, this.scaffoldPitch, !instantTurn ? (double)this.smoothSpeed.getFloat() : 180.0, !instantTurn ? (double)(this.smoothSpeed.getFloat() / 2.0f) : 180.0, this.shouldMoveFix());
                    break;
                }
                case "God Bridge": 
                case "Polar": {
                    if (this.blockPos != null && this.facing != null && !RotationUtil.lookingAtBlock(this.blockPos, this.scaffoldYaw, this.scaffoldPitch, this.facing, false)) {
                        Logger.debugChat(this, "DBG: not looking at block (updating pitch)");
                        this.scaffoldPitch = RotationUtil.getYawBasedPitch(this.blockPos, this.facing, this.scaffoldYaw, this.scaffoldPitch, 80);
                    }
                    float yaw = (float)Math.round((ScaffoldModule.mc.thePlayer.rotationYaw - (float)(MovementUtil.isGoingDiagonally() ? 180 : 135)) / 45.0f) * 45.0f;
                    rotationComponent.setRotations(yaw, this.scaffoldPitch, this.smoothSpeed.getFloat(), this.smoothSpeed.getFloat() / 2.0f, this.shouldMoveFix());
                }
            }
        } else if (this.mode.is("Telly")) {
            if (ScaffoldModule.mc.thePlayer.hurtTime == 0 && ScaffoldModule.mc.thePlayer.onGround) {
                this.scaffoldYaw = (float)Math.toDegrees(MovementUtil.getDirectionKeybinds(ScaffoldModule.mc.thePlayer.rotationYaw));
            }
            if (ScaffoldModule.mc.thePlayer.onGround && MovementUtil.isMoving()) {
                this.scaffoldYaw = ScaffoldModule.mc.thePlayer.rotationYaw;
                rotationComponent.setRotations(this.scaffoldYaw, this.scaffoldPitch, 180.0, 180.0, true);
            } else {
                if (this.blockPos != null && this.facing != null) {
                    this.scaffoldYaw = RotationUtil.getDirectionToBlock(this.blockPos.getX(), this.blockPos.getY(), this.blockPos.getZ(), this.facing)[0];
                    this.scaffoldPitch = RotationUtil.getDirectionToBlock(this.blockPos.getX(), this.blockPos.getY(), this.blockPos.getZ(), this.facing)[1];
                    this.scaffoldPitch = RotationUtil.getYawBasedPitch(this.blockPos, this.facing, this.scaffoldYaw, this.scaffoldPitch, 82);
                }
                rotationComponent.setRotations(this.scaffoldYaw, this.scaffoldPitch, this.mode.is("Telly") ? (double)(ScaffoldModule.mc.thePlayer.onGround ? 180 : (this.offGroundTicks < 2 ? 120 : 40)) : 180.0, 90.0, true);
            }
        }
    }

    public boolean place() {
        if (this.blockPos == null || this.facing == null) {
            Logger.debugChat(this, "FAIL: blockPos or facing is null (" + String.valueOf(this.blockPos) + ", " + String.valueOf(this.facing) + ")");
            return false;
        }
        if ((this.rotations.is("Polar") || this.rotations.is("God Bridge")) && this.mode.is("Normal") && ScaffoldModule.mc.gameSettings.keyBindSneak.isKeyDown()) {
            return false;
        }
        int blockSlot = InventoryUtil.getBlockSlot();
        // Inventory can change between the render event and the input event.
        if (blockSlot < 0) return false;
        ScaffoldModule.mc.thePlayer.inventory.currentItem = blockSlot;
        if (ScaffoldModule.mc.objectMouseOver == null) {
            Logger.debugChat(this, "FAIL: objectMouseOver is null");
            return false;
        }
        if (ScaffoldModule.mc.objectMouseOver.getBlockPos() == null || ScaffoldModule.mc.objectMouseOver.getBlockPos() != null && !ScaffoldModule.mc.objectMouseOver.getBlockPos().equals(this.blockPos)) {
            Logger.debugChat(this, "FAIL: objectMouseOver is invalid");
            return false;
        }
        if (ScaffoldModule.mc.objectMouseOver.sideHit == EnumFacing.UP && this.facing != EnumFacing.UP) {
            Logger.clientChat("FAIL: sideHit UP but facing " + String.valueOf(this.facing));
            return false;
        }
        boolean clicked = ScaffoldModule.mc.playerController.onPlayerRightClick(ScaffoldModule.mc.thePlayer, ScaffoldModule.mc.theWorld, ScaffoldModule.mc.thePlayer.getHeldItem(), ScaffoldModule.mc.objectMouseOver.getBlockPos(), ScaffoldModule.mc.objectMouseOver.sideHit, ScaffoldModule.mc.objectMouseOver.hitVec);
        if (!clicked) {
            Logger.debugChat(this, "FAIL: onPlayerRightClick returned false");
            return false;
        }
        ++this.placed;
        if (this.swing.isEnabled()) {
            ScaffoldModule.mc.thePlayer.swingItem();
        } else {
            dev.vibe.network.PacketDelayService.getInstance().sendSilent(new C0APacketAnimation());
        }
        Logger.debugChat(this, "SUCCESS: Placed at " + String.valueOf(ScaffoldModule.mc.objectMouseOver.getBlockPos()) + " facing " + String.valueOf(this.facing));
        return true;
    }

    public void processBlockData(double posX, double posZ) {
        BlockPos abovePos;
        if (!this.keepY.isEnabled() || !this.mode.is("Telly") && (this.jump.isEnabled() || this.rotations.is("Polar")) || ScaffoldModule.mc.gameSettings.keyBindJump.isKeyDown()) {
            this.posY = (int)(ScaffoldModule.mc.thePlayer.posY - 1.0);
        }
        int currentY = this.posY;
        if (!this.mode.is("Telly") && this.rotations.is("Polar") && !ScaffoldModule.mc.gameSettings.keyBindJump.isKeyDown() && !ScaffoldModule.mc.thePlayer.onGround && ScaffoldModule.isValidBock(abovePos = new BlockPos(ScaffoldModule.mc.thePlayer.posX, (double)(this.posY + 1), ScaffoldModule.mc.thePlayer.posZ))) {
            currentY = this.posY + 1;
        }
        this.blockPos = this.getBlockPos(posX, currentY, posZ);
        if (this.blockPos != null) {
            this.facing = this.getPlaceSide(posX, currentY, posZ);
        }
    }

    private BlockPos getBlockPos(double posX, double posY, double posZ) {
        BlockPos playerPos = new BlockPos(posX, posY, posZ);
        ArrayList<Vec3> positions = new ArrayList<Vec3>();
        HashMap<Vec3, BlockPos> hashMap = new HashMap<Vec3, BlockPos>();
        for (int y = playerPos.getY() - 1; y <= playerPos.getY(); ++y) {
            for (int x = playerPos.getX() - 5; x <= playerPos.getX() + 5; ++x) {
                for (int z = playerPos.getZ() - 5; z <= playerPos.getZ() + 5; ++z) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (!ScaffoldModule.isValidBock(candidate)) continue;
                    Block block = ScaffoldModule.mc.theWorld.getBlockState(candidate).getBlock();
                    double ex = MathHelper.clamp_double(posX, (double)candidate.getX(), (double)candidate.getX() + block.getBlockBoundsMaxX());
                    double ey = MathHelper.clamp_double(posY + 1.0, (double)candidate.getY(), (double)candidate.getY() + block.getBlockBoundsMaxY());
                    double ez = MathHelper.clamp_double(posZ, (double)candidate.getZ(), (double)candidate.getZ() + block.getBlockBoundsMaxZ());
                    Vec3 vec3 = new Vec3(ex, ey, ez);
                    positions.add(vec3);
                    hashMap.put(vec3, candidate);
                }
            }
        }
        if (positions.isEmpty()) {
            return null;
        }
        positions.sort(Comparator.comparingDouble(pos -> this.getBestBlock((Vec3)pos, posX, ScaffoldModule.mc.thePlayer.posY, posZ)));
        BlockPos selected = (BlockPos)hashMap.get(positions.get(0));
        return selected;
    }

    private EnumFacing getPlaceSide(double posX, double posY, double posZ) {
        Vec3 vec4;
        BlockPos bp;
        ArrayList<Vec3> positions = new ArrayList<Vec3>();
        HashMap<Vec3, EnumFacing> hashMap = new HashMap<Vec3, EnumFacing>();
        BlockPos playerPos = new BlockPos(posX, posY + 1.0, posZ);
        if (!(this.keepY.isEnabled() && (this.mode.is("Telly") || !this.jump.isEnabled() && !this.rotations.is("Polar")) && !ScaffoldModule.mc.gameSettings.keyBindJump.isKeyDown() || ScaffoldModule.isPosSolid(this.blockPos.add(0, 1, 0)) || this.blockPos.add(0, 1, 0).equals(playerPos) || ScaffoldModule.mc.thePlayer.onGround)) {
            bp = this.blockPos.add(0, 1, 0);
            vec4 = this.getBestHitFeet(bp, posX, posZ);
            positions.add(vec4);
            hashMap.put(vec4, EnumFacing.UP);
        }
        if (!ScaffoldModule.isPosSolid(this.blockPos.add(1, 0, 0)) && !this.blockPos.add(1, 0, 0).equals(playerPos)) {
            bp = this.blockPos.add(1, 0, 0);
            vec4 = this.getBestHitFeet(bp, posX, posZ);
            positions.add(vec4);
            hashMap.put(vec4, EnumFacing.EAST);
        }
        if (!ScaffoldModule.isPosSolid(this.blockPos.add(-1, 0, 0)) && !this.blockPos.add(-1, 0, 0).equals(playerPos)) {
            bp = this.blockPos.add(-1, 0, 0);
            vec4 = this.getBestHitFeet(bp, posX, posZ);
            positions.add(vec4);
            hashMap.put(vec4, EnumFacing.WEST);
        }
        if (!ScaffoldModule.isPosSolid(this.blockPos.add(0, 0, 1)) && !this.blockPos.add(0, 0, 1).equals(playerPos)) {
            bp = this.blockPos.add(0, 0, 1);
            vec4 = this.getBestHitFeet(bp, posX, posZ);
            positions.add(vec4);
            hashMap.put(vec4, EnumFacing.SOUTH);
        }
        if (!ScaffoldModule.isPosSolid(this.blockPos.add(0, 0, -1)) && !this.blockPos.add(0, 0, -1).equals(playerPos)) {
            bp = this.blockPos.add(0, 0, -1);
            vec4 = this.getBestHitFeet(bp, posX, posZ);
            positions.add(vec4);
            hashMap.put(vec4, EnumFacing.NORTH);
        }
        positions.sort(Comparator.comparingDouble(vec3 -> ScaffoldAccess.getDistanceFromPosition(posX, ScaffoldModule.mc.thePlayer.posY, posZ, vec3.xCoord, vec3.yCoord, vec3.zCoord)));
        if (!positions.isEmpty()) {
            Vec3 vec5 = this.getBestHitFeet(this.blockPos, posX, posZ);
            Logger.clientChat("getPlaceSide: picked " + String.valueOf(hashMap.get(positions.get(0))));
            return (EnumFacing)hashMap.get(positions.get(0));
        }
        Logger.clientChat("getPlaceSide: no valid positions, fallback UP");
        return null;
    }

    private Vec3 getBestHitFeet(BlockPos blockPos, double posX, double posZ) {
        Block block = ScaffoldModule.mc.theWorld.getBlockState(blockPos).getBlock();
        double ex = MathHelper.clamp_double(posX, (double)blockPos.getX(), (double)blockPos.getX() + block.getBlockBoundsMaxX());
        double ey = MathHelper.clamp_double(ScaffoldModule.mc.thePlayer.posY, (double)blockPos.getY(), (double)blockPos.getY() + block.getBlockBoundsMaxY());
        double ez = MathHelper.clamp_double(posZ, (double)blockPos.getZ(), (double)blockPos.getZ() + block.getBlockBoundsMaxZ());
        return new Vec3(ex, ey, ez);
    }

    public static boolean isPosSolid(BlockPos pos) {
        Block block = Minecraft.getMinecraft().theWorld.getBlockState(pos).getBlock();
        return (block.getMaterial().isSolid() || !block.isTranslucent() || block instanceof BlockLadder || block instanceof BlockCarpet || block instanceof BlockSnow || block instanceof BlockSkull) && !block.getMaterial().isLiquid() && !(block instanceof BlockContainer);
    }

    private double getBestBlock(Vec3 vec3, double posX, double posY, double posZ) {
        return ScaffoldAccess.getDistanceFromPosition(posX, posY, posZ, vec3.xCoord, vec3.yCoord, vec3.zCoord);
    }

    public static boolean isValidBock(BlockPos blockPos) {
        Block block = Minecraft.getMinecraft().theWorld.getBlockState(blockPos).getBlock();
        return !(block instanceof BlockLiquid) && !(block instanceof BlockAir) && !(block instanceof BlockChest) && !(block instanceof BlockFurnace);
    }

    public boolean hasBlocks() {
        if (ScaffoldModule.mc.thePlayer == null) {
            return false;
        }
        int item = InventoryUtil.getBlockSlot();
        if (item == -1) {
            rotationComponent.setRotations(ScaffoldModule.mc.thePlayer.rotationYaw, ScaffoldModule.mc.thePlayer.rotationPitch, this.smoothSpeed.getFloat(), this.smoothSpeed.getFloat() / 2.0f, this.shouldMoveFix());
            return false;
        }
        return true;
    }

    // Forge lifecycle/input bridges. The methods above retain Gothaj's decisions.
    private void registerSettings(dev.vibe.setting.Setting<?>... settings) {
        for (dev.vibe.setting.Setting<?> setting : settings) addSetting(setting);
    }

    private boolean ready() {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return false;
        if (sessionPlayer != mc.thePlayer) onEnable();
        return true;
    }

    public void tickLifecycle() {
        if (sessionPlayer != mc.thePlayer) {
            if (sessionPlayer != null) {
                CombatTimerAccess.setSpeed(1.0F);
                rotationComponent.reset();
                omniSprint = false;
            }
            sessionPlayer = null;
            if (isEnabled() && mc.thePlayer != null && mc.theWorld != null) onEnable();
        }
    }

    public void renderBlockCount(net.minecraft.client.gui.ScaledResolution resolution) {
        if (ready() && renderCount.isEnabled()) blockCounter.render(resolution);
    }

    public BooleanSetting getRenderCount() { return renderCount; }

    public void renderBlockCountPreview(net.minecraft.client.gui.ScaledResolution resolution) {
        if (mc.thePlayer != null) blockCounter.render(resolution);
    }

    /** Component listeners are registered before toggled modules in Gothaj. */
    public boolean applyMoveInput(net.minecraft.util.MovementInput input) {
        boolean enabled = ready();
        if (!enabled && !rotationComponent.active) return false;
        net.minecraft.client.settings.GameSettings keys = mc.gameSettings;
        MoveInputEvent event = new MoveInputEvent(input.moveForward, input.moveStrafe, input.jump, input.sneak,
                0.3, keys.keyBindForward.isKeyDown(), keys.keyBindBack.isKeyDown(),
                keys.keyBindLeft.isKeyDown(), keys.keyBindRight.isKeyDown(),
                keys.keyBindJump.isKeyDown(), keys.keyBindSneak.isKeyDown());
        rotationComponent.onInput(event);
        if (enabled) onMoveInput(event);
        event.recomputeFromKeys();
        input.moveForward = event.getForward();
        input.moveStrafe = event.getStrafe();
        input.jump = event.isJump();
        input.sneak = event.isSneak();
        if (input.sneak) {
            input.moveForward *= (float) event.getSlowdown();
            input.moveStrafe *= (float) event.getSlowdown();
        }
        return rotationComponent.ownsRotation();
    }

    public static ScaffoldModule instance() {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null
                : vibe.getModuleManager().getModule(ScaffoldModule.class);
    }

    public static void mouseOverHook() {
        ScaffoldModule module = instance();
        if (module != null && module.ready()) module.onMouseOver();
    }

    public static void clickHook() {
        ScaffoldModule module = instance();
        if (module != null && module.ready()) module.onClick();
    }

    public static void normalUpdateHook(Object entity) {
        ScaffoldModule module = instance();
        if (module != null && entity == mc.thePlayer && (module.ready() || module.rotationComponent.active))
            module.rotationComponent.onUpdate();
    }

    public static void beforeMotionHook(Object entity) {
        ScaffoldModule module = instance();
        if (module == null || entity != mc.thePlayer) return;
        boolean enabled = module.ready();
        if (enabled || module.rotationComponent.active) module.rotationComponent.onMotion();
        if (enabled) {
            // Gothaj constructs MotionEvent before tower() changes local position.
            double x = mc.thePlayer.posX, y = mc.thePlayer.posY, z = mc.thePlayer.posZ;
            module.onMotion();
            module.towerX = mc.thePlayer.posX; module.towerY = mc.thePlayer.posY; module.towerZ = mc.thePlayer.posZ;
            module.towerPositionScoped = x != module.towerX || y != module.towerY || z != module.towerZ;
            if (module.towerPositionScoped) mc.thePlayer.setPosition(x, y, z);
        }
    }

    public static void afterMotionHook(Object entity) {
        ScaffoldModule module = instance();
        if (module == null || entity != mc.thePlayer) return;
        if (module.towerPositionScoped) {
            mc.thePlayer.setPosition(module.towerX, module.towerY, module.towerZ);
            module.towerPositionScoped = false;
        }
        if (module.ready()) module.onPostMotion();
        module.rotationComponent.afterMotion();
    }

    public static float jumpMotionHook(float vanillaMotion, Object entity) {
        ScaffoldModule module = instance();
        return module != null && entity == mc.thePlayer && module.ready()
                && module.tower.is("Intave") && mc.gameSettings.keyBindJump.isKeyDown() ? 0.41F : vanillaMotion;
    }

    public static float sprintForwardHook(float vanillaForward, Object entity) {
        ScaffoldModule module = instance();
        return module != null && entity == mc.thePlayer && module.ready() && module.omniSprint
                ? Math.max(0.8F, vanillaForward) : vanillaForward;
    }

    public static boolean sprintStateHook(boolean sprinting, Object entity) {
        ScaffoldModule module = instance();
        return sprinting || module != null && entity == mc.thePlayer && module.ready()
                && module.omniSprint && mc.thePlayer.isSprinting();
    }

    public static Object heldItemHook(Object vanillaItem) {
        ScaffoldModule module = instance();
        return module != null && module.ready() && module.itemBefore >= 0 && module.itemBefore < 9
                ? mc.thePlayer.inventory.getStackInSlot(module.itemBefore) : vanillaItem;
    }
}
