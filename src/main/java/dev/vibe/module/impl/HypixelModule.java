package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.StringSetting;
import dev.vibe.ui.HypixelWeaponsGui;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Hypixel game helpers. All game actions are guarded by the current Hypixel server. */
public final class HypixelModule extends Module {
    public static final String MURDER = "Murder Mystery", BLOCK_PARTY = "BlockParty", PIT_BOT = "PitBot";
    private final MultiSelectSetting modes = addSetting(new MultiSelectSetting("Modes", Arrays.asList(MURDER, BLOCK_PARTY, PIT_BOT), Arrays.asList(MURDER)));
    private final BooleanSetting weaponEditor = addSetting(new BooleanSetting("Open weapon editor", false, () -> modes.isSelected(MURDER)));
    private final StringSetting murderWeapons = addSetting(new StringSetting("Murder weapons", "iron_sword,stone_sword,golden_sword,diamond_sword,wooden_sword,shears", 512, () -> modes.isSelected(MURDER)));
    private final BooleanSetting detectBow = addSetting(new BooleanSetting("Detect bow", true, () -> modes.isSelected(MURDER)));
    private final BooleanSetting murdererTracer = addSetting(new BooleanSetting("Murderer tracer", true, () -> modes.isSelected(MURDER)));
    private final BooleanSetting announce = addSetting(new BooleanSetting("Announce murderer", false, () -> modes.isSelected(MURDER)));
    private final BooleanSetting silentMurderer = addSetting(new BooleanSetting("Silent murderer", false, () -> modes.isSelected(MURDER)));
    private final ColorSetting murdererColor = addSetting(new ColorSetting("Murderer ESP color", 0xFFFF4B57, () -> modes.isSelected(MURDER)));
    private final ColorSetting bowColor = addSetting(new ColorSetting("Bow ESP color", 0xFF54A8FF, () -> modes.isSelected(MURDER) && detectBow.isEnabled()));
    private final BooleanSetting blockPartyMove = addSetting(new BooleanSetting("Move automatically", true, () -> modes.isSelected(BLOCK_PARTY)));
    private final NumberSetting blockPartyRange = addSetting(new NumberSetting("Dance floor range", 10, 4, 20, 1, () -> modes.isSelected(BLOCK_PARTY)));
    private final StringSetting pitTarget = addSetting(new StringSetting("Walk to player", "", 16, () -> modes.isSelected(PIT_BOT)));
    private final BooleanSetting pitSprint = addSetting(new BooleanSetting("Auto sprint", true, () -> modes.isSelected(PIT_BOT)));
    private final BooleanSetting pitPathfinding = addSetting(new BooleanSetting("Pathfinding", true, () -> modes.isSelected(PIT_BOT)));

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<String, String> murderers = new HashMap<String, String>();
    private final Set<String> bowUsers = new HashSet<String>();
    private final Set<String> announced = new HashSet<String>();
    private final Random random = new Random();
    private Object observedWorld;
    private boolean editorRequested, silentPending, forcedForward;
    private int silentSlot = -1, oldFps = -1;
    private int blockPartyColor = -1;
    private BlockPos blockPartyTarget;
    private static final String[] ACCUSATIONS = {
            "I saw %s with a %s", "I think %s is the murderer, he killed someone with a %s", "Watch out, %s has a %s",
            "%s is holding a %s", "%s looks like the murderer with that %s", "Careful around %s and the %s",
            "I spotted %s using a %s", "%s is suspicious with a %s", "The murderer is probably %s with a %s",
            "%s has the murderer weapon: %s", "I just saw %s carrying a %s", "Keep distance from %s, they have a %s",
            "%s may be the murderer because of the %s", "%s is armed with a %s", "%s is suspicious; I saw a %s",
            "%s was seen with a %s", "I believe %s is the murderer with a %s", "Potential murderer: %s holding a %s",
            "%s has an illegal %s", "I saw a %s in %s's hand", "%s is not safe; they have a %s", "%s looks armed with a %s"
    };

    public HypixelModule() { super("Hypixel", "Murder Mystery, BlockParty and PitBot helpers", Category.MEME, Keyboard.KEY_NONE); }
    public MultiSelectSetting getModes() { return modes; }
    public StringSetting getMurderWeapons() { return murderWeapons; }

    public void tick() {
        if (!isEnabled()) return;
        if (observedWorld != mc.theWorld) resetRound();
        observedWorld = mc.theWorld;
        if (weaponEditor.isEnabled()) { weaponEditor.setEnabled(false); editorRequested = true; }
        // A ClickGUI launched from the main menu returns to GuiMainMenu rather
        // than null.  Waiting only for null stranded this action forever.
        if (editorRequested && !(mc.currentScreen instanceof dev.vibe.ui.VibeClickGui)
                && !(mc.currentScreen instanceof HypixelWeaponsGui)) {
            editorRequested = false; mc.displayGuiScreen(new HypixelWeaponsGui(this));
        }
        if (mc.thePlayer == null || mc.theWorld == null) { releaseMovement(); restoreFps(); return; }
        if (modes.isSelected(MURDER) && onHypixel()) scanMurderers();
        if (modes.isSelected(BLOCK_PARTY) && onHypixel()) tickBlockParty();
        else if (!modes.isSelected(PIT_BOT)) releaseMovement();
        if (modes.isSelected(PIT_BOT)) tickPitBot();
        else restoreFps();
        if (!modes.isSelected(BLOCK_PARTY) && !modes.isSelected(PIT_BOT)) releaseMovement();
    }

    public void receiveChat(String raw) {
        if (!isEnabled() || raw == null) return;
        String lower = raw.replaceAll("\u00a7[0-9A-FK-ORa-fk-or]", "").toLowerCase(Locale.ROOT);
        if (lower.contains("game over") || lower.contains("murderer has won") || lower.contains("innocents win")
                || lower.contains("murderer was") || lower.contains("you died") || lower.contains("victory")) { resetRound(); return; }
        if (!modes.isSelected(BLOCK_PARTY)) return;
        if (!lower.contains("color")) return;
        String[] colors = {"white","orange","magenta","light blue","yellow","lime","pink","gray","silver","cyan","purple","blue","brown","green","red","black"};
        for (int i = 0; i < colors.length; i++) if (lower.contains(colors[i])) { blockPartyColor = i; blockPartyTarget = null; return; }
    }

    public int visualColor(EntityLivingBase entity) {
        if (!isEnabled() || !modes.isSelected(MURDER) || !(entity instanceof EntityPlayer) || !onHypixel()) return 0;
        String name = entity.getName().toLowerCase(Locale.ROOT);
        if (murderers.containsKey(name)) return murdererColor.getArgb();
        return detectBow.isEnabled() && bowUsers.contains(name) ? bowColor.getArgb() : 0;
    }

    public boolean suppressVisuals() { return isEnabled() && modes.isSelected(PIT_BOT) && mc.thePlayer != null; }
    public boolean blockEscapeMenu() { return suppressVisuals(); }

    public void renderWorld() {
        if (!isEnabled() || !modes.isSelected(MURDER) || !murdererTracer.isEnabled() || mc.thePlayer == null) return;
        EntityPlayer murderer = null;
        for (Object value : mc.theWorld.playerEntities) if (value instanceof EntityPlayer && murderers.containsKey(((EntityPlayer) value).getName().toLowerCase(Locale.ROOT))) { murderer = (EntityPlayer) value; break; }
        if (murderer == null) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA); GL11.glLineWidth(1.7F);
            int color = murdererColor.getArgb(); GL11.glColor4f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, .9F);
            double ox = mc.getRenderManager().viewerPosX, oy = mc.getRenderManager().viewerPosY, oz = mc.getRenderManager().viewerPosZ;
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(0, mc.thePlayer.getEyeHeight() - .15D, 0);
            GL11.glVertex3d(murderer.posX - ox, murderer.posY + murderer.height * .55D - oy, murderer.posZ - oz);
            GL11.glEnd();
        } finally { GL11.glPopAttrib(); }
    }

    public void renderPitBotBanner() {
        if (!suppressVisuals()) return;
        String text = "PITBOT IS ENABLED";
        int x = (new net.minecraft.client.gui.ScaledResolution(mc).getScaledWidth() - mc.fontRendererObj.getStringWidth(text) * 2) / 2;
        GL11.glPushMatrix(); GL11.glScalef(2, 2, 1);
        mc.fontRendererObj.drawStringWithShadow(text, x / 2, 8, 0xFFFF4048);
        GL11.glPopMatrix();
    }

    private void scanMurderers() {
        for (Object value : mc.theWorld.playerEntities) {
            if (!(value instanceof EntityPlayer) || value == mc.thePlayer) continue;
            EntityPlayer player = (EntityPlayer) value;
            ItemStack held = player.getHeldItem();
            String key = player.getName().toLowerCase(Locale.ROOT);
            if (detectBow.isEnabled() && held != null && held.getItem() == net.minecraft.init.Items.bow) bowUsers.add(key);
            if (held == null || !isMurderWeapon(held)) continue;
            if (!murderers.containsKey(key)) {
                String item = vanillaItemName(held);
                murderers.put(key, item);
                mc.thePlayer.addChatMessage(new ChatComponentText("\u00a78[\u00a7dVibe\u00a78] \u00a7fMurderer detected: \u00a7c" + player.getName() + " \u00a77(" + item + ")"));
                if (announce.isEnabled() && announced.add(key)) mc.thePlayer.sendChatMessage(String.format(Locale.ROOT, ACCUSATIONS[random.nextInt(ACCUSATIONS.length)], player.getName(), item));
            }
        }
    }

    private void tickBlockParty() {
        if (!blockPartyMove.isEnabled() || blockPartyColor < 0) { if (!modes.isSelected(PIT_BOT)) releaseMovement(); return; }
        if (blockPartyTarget == null || !isCorrectPartyBlock(blockPartyTarget)) blockPartyTarget = nearestPartyBlock();
        if (blockPartyTarget == null) { if (!modes.isSelected(PIT_BOT)) releaseMovement(); return; }
        double dx = blockPartyTarget.getX() + .5D - mc.thePlayer.posX, dz = blockPartyTarget.getZ() + .5D - mc.thePlayer.posZ;
        if (dx * dx + dz * dz < .20D) { if (!modes.isSelected(PIT_BOT)) releaseMovement(); return; }
        steer((float) (Math.atan2(dz, dx) * 180.0D / Math.PI - 90.0D), true, false, "HypixelBlockParty");
    }

    private void tickPitBot() {
        if (oldFps < 0) oldFps = mc.gameSettings.limitFramerate;
        mc.gameSettings.limitFramerate = 30;
        String wanted = pitTarget.getValue().trim(); if (wanted.isEmpty()) { releaseMovement(); return; }
        EntityPlayer target = null;
        for (Object value : mc.theWorld.playerEntities) if (value instanceof EntityPlayer && ((EntityPlayer) value).getName().equalsIgnoreCase(wanted)) { target = (EntityPlayer) value; break; }
        if (target == null) { releaseMovement(); return; }
        double dx = target.posX - mc.thePlayer.posX, dz = target.posZ - mc.thePlayer.posZ;
        if (dx * dx + dz * dz < 2.25D) { releaseMovement(); return; }
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI - 90.0D);
        if (pitPathfinding.isEnabled()) yaw = pathYaw(yaw, target);
        steer(yaw, pitSprint.isEnabled(), pitPathfinding.isEnabled() && mc.thePlayer.isCollidedHorizontally, "PitBot");
    }

    private void steer(float yaw, boolean sprint, boolean jump, String owner) {
        MoveFixModule fix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
        if (fix != null && fix.isEnabled()) {
            fix.setFakeRotation(owner, yaw, mc.thePlayer.rotationPitch);
            fix.setForcedMovement(owner, 1.0F, 0.0F, jump && mc.thePlayer.onGround);
        } else {
            mc.thePlayer.rotationYaw = yaw;
            net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
        }
        forcedForward = true;
        if (sprint) mc.thePlayer.setSprinting(true);
    }
    /** Chooses a clear short step which still reduces distance to the target. */
    private float pathYaw(float direct, EntityPlayer target) {
        float best = direct; double bestScore = Double.MAX_VALUE;
        for (float offset : new float[] {0, -45, 45, -90, 90}) {
            float yaw = direct + offset, radians = (float) Math.toRadians(yaw);
            double nextX = mc.thePlayer.posX - Math.sin(radians) * .8D, nextZ = mc.thePlayer.posZ + Math.cos(radians) * .8D;
            BlockPos feet = new BlockPos(nextX, mc.thePlayer.posY, nextZ);
            if (!mc.theWorld.isAirBlock(feet) || !mc.theWorld.isAirBlock(feet.up())) continue;
            double dx = target.posX - nextX, dz = target.posZ - nextZ, score = dx * dx + dz * dz + Math.abs(offset) * .014D;
            if (score < bestScore) { bestScore = score; best = yaw; }
        }
        return best;
    }

    private BlockPos nearestPartyBlock() {
        BlockPos best = null; double bestDistance = Double.MAX_VALUE;
        int range = blockPartyRange.getInt(), y = (int) Math.floor(mc.thePlayer.posY - .5D);
        for (int x = -range; x <= range; x++) for (int z = -range; z <= range; z++) {
            BlockPos pos = new BlockPos(mc.thePlayer.posX + x, y, mc.thePlayer.posZ + z);
            if (!isCorrectPartyBlock(pos)) continue;
            double dx = pos.getX() + .5D - mc.thePlayer.posX, dz = pos.getZ() + .5D - mc.thePlayer.posZ, distance = dx * dx + dz * dz;
            if (distance < bestDistance) { best = pos; bestDistance = distance; }
        }
        return best;
    }
    private boolean isCorrectPartyBlock(BlockPos pos) { return mc.theWorld.getBlockState(pos).getBlock() == Blocks.wool && mc.theWorld.getBlockState(pos).getBlock().getMetaFromState(mc.theWorld.getBlockState(pos)) == blockPartyColor; }
    private boolean isMurderWeapon(ItemStack stack) { return weaponNames().contains(itemKey(stack)); }
    private Set<String> weaponNames() { Set<String> result = new HashSet<String>(); for (String name : murderWeapons.getValue().toLowerCase(Locale.ROOT).split(",")) if (!name.trim().isEmpty()) result.add(name.trim().replace(' ', '_')); return result; }
    private String itemKey(ItemStack stack) { return stack == null ? "" : stack.getItem().getUnlocalizedName().replace("item.", "").replace("tile.", "").toLowerCase(Locale.ROOT); }
    private String vanillaItemName(ItemStack stack) { String key = itemKey(stack).replace('_', ' '); StringBuilder result = new StringBuilder(); for (String word : key.split(" ")) if (!word.isEmpty()) result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' '); return result.toString().trim(); }
    private boolean onHypixel() { ServerData server = mc.getCurrentServerData(); return server != null && server.serverIP != null && server.serverIP.toLowerCase(Locale.ROOT).contains("hypixel.net"); }
    private void resetRound() { murderers.clear(); bowUsers.clear(); announced.clear(); blockPartyColor = -1; blockPartyTarget = null; }
    private void restoreFps() { if (oldFps >= 0) { mc.gameSettings.limitFramerate = oldFps; oldFps = -1; } }
    private void releaseMovement() {
        MoveFixModule fix=Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
        if(fix!=null){fix.clearForcedMovement("HypixelBlockParty");fix.clearForcedMovement("PitBot");fix.clearFakeRotation("HypixelBlockParty");fix.clearFakeRotation("PitBot");}
        if(forcedForward)dev.vibe.input.VanillaClicks.restore(mc.gameSettings.keyBindForward);
        forcedForward=false;
    }

    @Override protected void onDisable() { resetRound(); restoreFps(); releaseMovement(); MoveFixModule fix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class); if (fix != null) { fix.clearFakeRotation("HypixelBlockParty"); fix.clearFakeRotation("PitBot"); } }
    private static HypixelModule active() { Vibe vibe = Vibe.getInstance(); return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(HypixelModule.class); }
    public static int beginAttackHook(Object target) {
        HypixelModule module = active();
        if (module == null || !module.isEnabled() || !module.silentMurderer.isEnabled() || !module.modes.isSelected(MURDER) || !module.onHypixel() || !(target instanceof EntityLivingBase) || module.mc.thePlayer == null) return -1;
        int old = module.mc.thePlayer.inventory.currentItem;
        for (int slot = 0; slot < 9; slot++) if (module.isMurderWeapon(module.mc.thePlayer.inventory.mainInventory[slot])) { module.silentSlot = old; module.silentPending = true; module.mc.thePlayer.inventory.currentItem = slot; return old; }
        return -1;
    }
    public static void endAttackHook(int ignored) {
        HypixelModule module = active();
        if (module != null && module.silentPending && module.silentSlot >= 0 && module.mc.thePlayer != null) {
            module.mc.thePlayer.inventory.currentItem = module.silentSlot;
            if (module.mc.playerController != null) module.mc.playerController.updateController();
            module.silentPending = false; module.silentSlot = -1;
        }
    }
    public static void abortAttackHook() { endAttackHook(-1); }
}
