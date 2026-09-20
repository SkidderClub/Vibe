package dev.vibe.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vibe.Vibe;
import dev.vibe.language.LanguageManager;
import dev.vibe.module.Module;
import dev.vibe.module.impl.HudModule;
import dev.vibe.module.impl.NameProtectModule;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.module.impl.EspModule;
import dev.vibe.friend.FriendManager;
import dev.vibe.target.TargetManager;
import dev.vibe.ui.RenderUtils;
import dev.vibe.ui.DebugOverlay;
import dev.vibe.ui.KawaseBlur;
import dev.vibe.ui.effect.LiquidGlassRenderer;
import dev.vibe.input.ClickStats;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.GL11;

/** Owns the movable HUD elements and their standalone JSON layout. */
public final class HudManager {

    private static final int LAYOUT_VERSION = 2;

    public static final String WATERMARK = "watermark";
    public static final String ARRAY_LIST = "arraylist";
    public static final String COORDINATES = "coordinates";
    public static final String SCOREBOARD = "scoreboard";
    public static final String CLOCK = "clock";
    public static final String SESSION_INFO = "sessioninfo";
    public static final String MOTION_GRAPH = "motiongraph";
    public static final String STALKER = "stalker";
    public static final String ARMOR = "armor";
    public static final String INVENTORY = "inventory";
    public static final String HEALTH = "health";
    public static final String CPS = "cps";
    public static final String CPS_GRAPH = "cpsgraph";

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final File layoutFile;
    // Keep all optional widgets out of the chat lane. The only centered
    // information sits deliberately below the crosshair.
    private final HudElement watermark = new HudElement(WATERMARK, 9, 9, false, false);
    private final HudElement arrayList = new HudElement(ARRAY_LIST, 9, 9, true, false);
    private final HudElement coordinates = new HudElement(COORDINATES, 9, 42, false, false);
    private final HudElement scoreboard = new HudElement(SCOREBOARD, 9, 0, true, false, false, true);
    private final HudElement clock = new HudElement(CLOCK, 9, 70, false, false);
    private final HudElement sessionInfo = new HudElement(SESSION_INFO, 9, 96, false, false);
    private final HudElement motionGraph = new HudElement(MOTION_GRAPH, 9, 120, false, false);
    private final HudElement stalker = new HudElement(STALKER, 9, 192, false, false);
    private final HudElement armor = new HudElement(ARMOR, 9, 9, true, true);
    private final HudElement inventory = new HudElement(INVENTORY, 9, 46, false, true);
    // Centre-anchored by default so the readout never shifts while absorption
    // hearts appear or disappear.
    private final HudElement health = new HudElement(HEALTH, 0, 18, false, false, true);
    private final HudElement cps = new HudElement(CPS, 0, 36, false, false, true);
    private final HudElement cpsGraph = new HudElement(CPS_GRAPH, 9, 190, true, false);
    public static final String MUSIC = "Music";
    private final HudElement music = new HudElement(MUSIC, 12, 88, true, true);
    private final dev.vibe.ui.MusicHudRenderer musicRenderer = new dev.vibe.ui.MusicHudRenderer();
    private final dev.vibe.ui.MusicVisualizer musicVisualizer = new dev.vibe.ui.MusicVisualizer();
    private final long sessionStarted = System.currentTimeMillis();
    private final LiquidGlassRenderer liquidGlass = new LiquidGlassRenderer();
    private final float[] motionSamples = new float[96];
    private int motionSampleIndex;
    private long lastMotionSample;
    private final float[] leftCpsSamples = new float[96];
    private final float[] rightCpsSamples = new float[96];
    private int cpsSampleIndex;
    private long lastCpsSample;

    public HudManager(File minecraftConfigDirectory) {
        layoutFile = new File(new File(minecraftConfigDirectory, "vibe"), "hud.json");
        load();
    }

    public void draw() {
        KawaseBlur.beginHudFrame();
        if (!(minecraft.currentScreen instanceof dev.vibe.ui.Gta7Gui)) drawMusic(false);
        HudModule hud = Vibe.getInstance().getModuleManager().getModule(HudModule.class);
        if (hud == null || !hud.isEnabled() || minecraft.thePlayer == null) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(minecraft);
        FontRenderer font = minecraft.fontRendererObj;
        if (hud.getHudElements().isSelectedIgnoreCase(WATERMARK)) {
            drawWatermark(hud, resolution, font);
        }
        if (hud.getHudElements().isSelectedIgnoreCase(ARRAY_LIST)) {
            drawArrayList(hud, resolution, font);
        }
        if (hud.getHudElements().isSelectedIgnoreCase(COORDINATES)) {
            drawCoordinates(hud, resolution, font);
        }
        if (hud.getHudElements().isSelectedIgnoreCase(CLOCK)) {
            drawClock(hud, resolution, font);
        }
        if (hud.getHudElements().isSelectedIgnoreCase(SESSION_INFO)) {
            drawSessionInfo(hud, resolution, font);
        }
        if (hud.getHudElements().isSelectedIgnoreCase(MOTION_GRAPH)) {
            drawMotionGraph(hud, resolution, font);
        }
        if (hud.getHudElements().isSelectedIgnoreCase(STALKER)) {
            drawStalker(hud, resolution, font);
        }
        if (hud.getHudElements().isSelectedIgnoreCase(ARMOR)) {
            drawArmor(hud, resolution, font);
        }
        if (hud.getHudElements().isSelectedIgnoreCase(INVENTORY)) {
            drawInventory(hud, resolution, font);
        }
        if (hud.getHudElements().isSelectedIgnoreCase(HEALTH)) {
            drawHealth(hud, resolution, font);
        }
        if (hud.getHudElements().isSelectedIgnoreCase(CPS)) {
            drawCps(resolution, font);
        }
        if (hud.getHudElements().isSelectedIgnoreCase(CPS_GRAPH)) {
            drawCpsGraph(hud, resolution, font);
        }
    }

    /** Music owns its visibility; it can render even when the general HUD module is disabled. */
    public void drawMusic(boolean preview) {
        dev.vibe.module.impl.MusicModule m = Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.MusicModule.class);
        if (m == null || !m.isEnabled()) { musicRenderer.close(); musicVisualizer.clear(); return; }
        ScaledResolution resolution = new ScaledResolution(minecraft);
        dev.vibe.media.MusicService service = m.service();
        if (!m.hud.isEnabled()) { musicRenderer.close(); return; }
        dev.vibe.media.MediaTrack track = service == null ? dev.vibe.media.MediaTrack.idle("Waiting for media") : service.track();
        if (!preview && m.hideIdle.isEnabled() && !track.playing) return;
        float scale = dev.vibe.ui.MusicHudRenderer.effectiveScale(m, resolution.getScaledWidth(), resolution.getScaledHeight());
        int width = Math.round(m.hudWidth.getFloat()*scale), height = Math.round(64*scale);
        music.ensureOnScreen(resolution, width, height);
        int left = Math.max(0, music.left(resolution, width)), top = Math.max(0, music.top(resolution, height));
        music.setBounds(left, top, width, height);
        if (!preview && DebugOverlay.overlaps(left, top, left + width, top + height)) return;
        musicRenderer.draw(m, track, left, top, preview);
        if (preview && m.visualizer.isEnabled() && service != null)
            minecraft.fontRendererObj.drawStringWithShadow(service.audioStatus(), left, top+height+3, 0xFFD6DFEB);
    }

    private void drawWatermark(HudModule hud, ScaledResolution resolution, FontRenderer font) {
        String text = hud.getWatermarkText().getValue();
        if (hud.getWatermarkDetails().isSelected("FPS")) text += " §r| §b" + Minecraft.getDebugFPS();
        if (hud.getWatermarkDetails().isSelected("Version")) text += " §r| §fv" + Vibe.VERSION;
        if (hud.getWatermarkDetails().isSelected("Username")) {
            NameProtectModule protect = Vibe.getInstance().getModuleManager().getModule(NameProtectModule.class);
            text += " §r| " + (protect == null ? minecraft.thePlayer.getName() : protect.getDisplayName(minecraft.thePlayer.getName()));
        }
        int width = font.getStringWidth(text) + 20;
        watermark.ensureOnScreen(resolution, width, 26);
        int left = watermark.left(resolution, width);
        int top = watermark.getY();
        if (hideForDebug(watermark, left, top, width, 26, !isSkeet() && hud.getWatermarkOutline().isEnabled() ? 1 : 0)) return;
        if (blurEnabled(BlurModule.WATERMARK)) {
            KawaseBlur.drawRegion(left, top, left + width, top + 26, 3, 0.0F);
        }
        // The blur texture supplies the depth. A light tinted surface keeps
        // it legible without turning the rounded widget into a dark slab.
        drawHudSurface(hud, left, top, left + width, top + 26);
        if (hud.getWatermarkOutline().isEnabled()) {
            drawHudOutline(left, top, left + width, top + 26, hudOutlineColor(hud, 0.0F));
        }
        int textLeft = left + (width - font.getStringWidth(text)) / 2;
        int textTop = top + (26 - font.FONT_HEIGHT) / 2;
        font.drawStringWithShadow(text, textLeft, textTop, RenderUtils.TEXT);
        watermark.setBounds(left, top, width, 26);
    }

    private final dev.vibe.ui.ArrayListRenderer arrayRenderer = new dev.vibe.ui.ArrayListRenderer();
    private void drawArrayList(HudModule hud, ScaledResolution resolution, FontRenderer font) {
        arrayRenderer.draw(hud,arrayList,resolution,false);
    }

    /** Draw before Minecraft begins its overlay sequence, keeping the wave behind health, hotbar and item slots. */
    public void drawMusicVisualizer() {
        dev.vibe.module.impl.MusicModule module = Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.MusicModule.class);
        if (module == null || !module.isEnabled() || !module.visualizer.isEnabled() || module.service() == null) return;
        ScaledResolution resolution = new ScaledResolution(minecraft);
        musicVisualizer.draw(module, module.service().spectrum(), resolution.getScaledWidth(), resolution.getScaledHeight());
    }

    private boolean blurEnabled(String element) {
        // Only widgets with an opaque Skeet surface suppress their blur.
        boolean unthemed = BlurModule.ARRAY_LIST.equals(element) || STALKER.equals(element) || SCOREBOARD.equals(element);
        if (isSkeet() && !unthemed) return false;
        HudModule hud = Vibe.getInstance().getModuleManager().getModule(HudModule.class);
        // Glass surfaces composite their own rounded blur so the blur stays
        // inside their curved silhouette.
        if (hud != null && hud.getMode().is("LiquidGlass")) return false;
        BlurModule blur = Vibe.getInstance().getModuleManager().getModule(BlurModule.class);
        return blur != null && blur.isEnabled() && blur.getElements().isSelected(element);
    }

    private boolean isSkeet() {
        HudModule hud = Vibe.getInstance().getModuleManager().getModule(HudModule.class);
        return hud != null && hud.getMode().is("Skeet");
    }

    /** Skeet HUDs deliberately omit Vibe glow, blur and coloured outlines. */
    private void drawHudSurface(HudModule hud, int left, int top, int right, int bottom) {
        if (hud.getMode().is("LiquidGlass")) {
            float radius = Math.min(8, (bottom - top) / 2.0F);
            if (!liquidGlass.draw(left, top, right, bottom, radius, hud.getLiquidGlassBlur().isEnabled()
                    ? hud.getLiquidGlassBlurStrength().getFloat() : 0.0F, hud.getLiquidGlassRefraction().getFloat(),
                    hud.getLiquidGlassOpacity().getFloat(), hud.getLiquidGlassTint().getArgb())) {
                if (hud.getLiquidGlassBlur().isEnabled())
                    KawaseBlur.drawRoundedRegion(left, top, right, bottom, radius, 4, 0);
                RenderUtils.roundedRect(left, top, right, bottom, radius,
                        RenderUtils.alpha(hud.getLiquidGlassTint().getArgb(), Math.round(22 * hud.getLiquidGlassOpacity().getFloat())));
                RenderUtils.roundedOutline(left, top, right, bottom, radius, 1,
                        RenderUtils.alpha(0xFFFFFFFF, Math.round(95 * hud.getLiquidGlassOpacity().getFloat())));
            }
            return;
        }
        if (!hud.getMode().is("Skeet")) {
            // Vibe is layered on top of the cached blur. A low-opacity tint
            // preserves the actual blurred scene instead of replacing it with
            // a black rectangle.
            Gui.drawRect(left, top, right, bottom, RenderUtils.alpha(hud.getBackground().getArgb(), 42));
            return;
        }
        Gui.drawRect(left, top, right, bottom, 0xEE111113);
        Gui.drawRect(left, top, right, top + 1, 0xFF2E2E33);
        Gui.drawRect(left, top + 1, left + 1, bottom, 0xFF25252A);
        Gui.drawRect(right - 1, top + 1, right, bottom, 0xFF25252A);
        Gui.drawRect(left, bottom - 1, right, bottom, 0xFF25252A);
    }

    private void drawHudOutline(int left, int top, int right, int bottom, int color) {
        if (isSkeet()) return;
        HudModule hud = Vibe.getInstance().getModuleManager().getModule(HudModule.class);
        if (hud != null && hud.getMode().is("LiquidGlass")) return;
        Gui.drawRect(left - 1, top - 1, right + 1, top, color);
        Gui.drawRect(left - 1, bottom, right + 1, bottom + 1, color);
        Gui.drawRect(left - 1, top, left, bottom, color);
        Gui.drawRect(right, top, right + 1, bottom, color);
    }

    private int hudOutlineColor(HudModule hud, float phase) {
        float progress = (float) ((System.currentTimeMillis() % 4200L) / 4200.0D);
        float blend = (float) ((Math.sin((progress + phase) * Math.PI * 2.0D) + 1.0D) * 0.5D);
        return RenderUtils.blend(hud.getArrayPrimaryColor().getArgb(), hud.getArraySecondaryColor().getArgb(), blend);
    }

    /** Multi-pass glow that stays outside the translucent blurred widget. */
    private void drawWidgetGlow(int left, int top, int right, int bottom, float radius, int color) {
        RenderUtils.roundedOutline(left - 3, top - 3, right + 3, bottom + 3, radius + 3.0F, 1.0F, RenderUtils.alpha(color, 30));
        RenderUtils.roundedOutline(left - 2, top - 2, right + 2, bottom + 2, radius + 2.0F, 1.0F, RenderUtils.alpha(color, 52));
        RenderUtils.roundedOutline(left - 1, top - 1, right + 1, bottom + 1, radius + 1.0F, 1.0F, RenderUtils.alpha(color, 86));
        RenderUtils.roundedOutline(left, top, right, bottom, radius, 1.0F, color);
    }

    private void drawCoordinates(HudModule hud, ScaledResolution resolution, FontRenderer font) {
        int x = net.minecraft.util.MathHelper.floor_double(minecraft.thePlayer.posX);
        int y = net.minecraft.util.MathHelper.floor_double(minecraft.thePlayer.posY);
        int z = net.minecraft.util.MathHelper.floor_double(minecraft.thePlayer.posZ);
        String text = "XYZ " + x + " / " + y + " / " + z;
        int width = font.getStringWidth(text) + 20;
        coordinates.ensureOnScreen(resolution, width, 24);
        int left = coordinates.left(resolution, width);
        int top = coordinates.top(resolution, 24);
        coordinates.setBounds(left, top, width, 24);
        if (hideForDebug(coordinates, left, top, width, 24, !isSkeet() && hud.getCoordinatesOutline().isEnabled() ? 1 : 0)) return;
        if (blurEnabled(BlurModule.COORDINATES)) {
            KawaseBlur.drawRegion(left, top, left + width, top + 24, 3, 0.0F);
        }
        drawHudSurface(hud, left, top, left + width, top + 24);
        if (hud.getCoordinatesOutline().isEnabled()) {
            drawHudOutline(left, top, left + width, top + 24, hudOutlineColor(hud, 0.5F));
        }
        int textLeft = left + (width - font.getStringWidth(text)) / 2;
        int textTop = top + (24 - font.FONT_HEIGHT) / 2;
        font.drawStringWithShadow(text, textLeft, textTop, RenderUtils.TEXT);
    }

    private void drawClock(HudModule hud, ScaledResolution resolution, FontRenderer font) {
        drawSimpleWidget(clock, new SimpleDateFormat("HH:mm:ss").format(new Date()), hud, resolution, font, 0.22F);
    }

    private void drawSessionInfo(HudModule hud, ScaledResolution resolution, FontRenderer font) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - sessionStarted) / 1000L;
        String time = String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", elapsed / 3600L, (elapsed / 60L) % 60L, elapsed % 60L);
        int kills = Vibe.getInstance().getStatistics() == null ? 0 : Vibe.getInstance().getStatistics().getSessionKills();
        drawSimpleWidget(sessionInfo, "Session " + time + " | Kills " + kills, hud, resolution, font, 0.72F);
    }

    private void drawMotionGraph(HudModule hud, ScaledResolution resolution, FontRenderer font) {
        long now = System.currentTimeMillis();
        if (lastMotionSample == 0L || now - lastMotionSample >= 50L) {
            double horizontalSpeed = Math.sqrt(minecraft.thePlayer.motionX * minecraft.thePlayer.motionX
                    + minecraft.thePlayer.motionZ * minecraft.thePlayer.motionZ) * 20.0D;
            motionSamples[motionSampleIndex] = (float) Math.min(12.0D, horizontalSpeed);
            motionSampleIndex = (motionSampleIndex + 1) % motionSamples.length;
            lastMotionSample = now;
        }
        int widgetWidth = 196;
        int widgetHeight = 64;
        motionGraph.ensureOnScreen(resolution, widgetWidth, widgetHeight);
        int left = motionGraph.left(resolution, widgetWidth);
        int top = motionGraph.top(resolution, widgetHeight);
        if (hideForDebug(motionGraph, left, top, widgetWidth, widgetHeight, isSkeet() ? 0 : 1)) return;
        int right = left + widgetWidth;
        int bottom = top + widgetHeight;
        if (blurEnabled(MOTION_GRAPH)) KawaseBlur.drawRegion(left, top, right, bottom, 3, 0.0F);
        drawHudSurface(hud, left, top, right, bottom);
        drawHudOutline(left, top, right, bottom, hudOutlineColor(hud, 0.34F));
        font.drawStringWithShadow(LanguageManager.translate("MOTION GRAPH"), left + 7, top + 5, RenderUtils.TEXT);
        double speed = Math.sqrt(minecraft.thePlayer.motionX * minecraft.thePlayer.motionX
                + minecraft.thePlayer.motionZ * minecraft.thePlayer.motionZ) * 20.0D;
        String speedText = String.format(java.util.Locale.ROOT, "%.2f b/s", speed);
        font.drawStringWithShadow(speedText, right - 7 - font.getStringWidth(speedText), top + 5, 0xFF8FE8FF);
        int graphLeft = left + 7;
        int graphTop = top + 23;
        int graphRight = right - 7;
        int graphBottom = bottom - 7;
        Gui.drawRect(graphLeft, graphBottom - 1, graphRight, graphBottom, 0x557FA1BD);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(1.5F);
        GL11.glColor4f(0.18F, 0.89F, 0.78F, 0.95F);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int index = 0; index < motionSamples.length; index++) {
            int sample = (motionSampleIndex + index) % motionSamples.length;
            float x = graphLeft + (graphRight - graphLeft) * index / (float) (motionSamples.length - 1);
            float y = graphBottom - (motionSamples[sample] / 12.0F) * (graphBottom - graphTop);
            GL11.glVertex2f(x, y);
        }
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        motionGraph.setBounds(left, top, widgetWidth, widgetHeight);
    }

    private void drawStalker(HudModule hud, ScaledResolution resolution, FontRenderer font) {
        List<EntityPlayer> tracked = new ArrayList<EntityPlayer>();
        FriendManager friends = Vibe.getInstance().getFriendManager();
        TargetManager targets = Vibe.getInstance().getTargetManager();
        for (Object value : minecraft.theWorld.playerEntities) {
            if (!(value instanceof EntityPlayer) || value == minecraft.thePlayer) continue;
            EntityPlayer player = (EntityPlayer) value;
            if ((friends != null && friends.isFriend(player)) || (targets != null && targets.isTarget(player))) tracked.add(player);
        }
        int visible = Math.min(8, tracked.size());
        int width = 212;
        int height = Math.max(18, visible * 50);
        stalker.ensureOnScreen(resolution, width, height);
        int left = stalker.left(resolution, width);
        int top = stalker.top(resolution, height);
        if (visible == 0) {
            stalker.setBounds(left, top, width, 0);
            return;
        }
        if (hideForDebug(stalker, left, top, width, height, 1)) return;
        if (blurEnabled(STALKER)) KawaseBlur.drawRegion(left, top, left + width, top + height, 3, 0.0F);
        for (int index = 0; index < visible; index++) {
            EntityPlayer player = tracked.get(index);
            int rowTop = top + index * 50;
            FriendManager.Friend friend = friends == null ? null : friends.find(player.getName());
            boolean isTarget = targets != null && targets.isTarget(player);
            int accent = isTarget ? 0xFFFF5B6E : (friend != null ? 0xFF5BE8A6 : 0xFF8FA5C4);
            if (hud.getMode().is("LiquidGlass")) drawHudSurface(hud, left, rowTop, left + width, rowTop + 48);
            else Gui.drawRect(left, rowTop, left + width, rowTop + 48, RenderUtils.alpha(hud.getBackground().getArgb(), 188));
            drawHudOutline(left, rowTop, left + width, rowTop + 48, accent);
            drawPlayerFace(player, left + 4, rowTop + 4);
            NameProtectModule protect = Vibe.getInstance().getModuleManager().getModule(NameProtectModule.class);
            String name = protect == null ? player.getName() : protect.protectText(player.getName());
            if (friend != null) name = friend.getAlias();
            String label = name + (isTarget ? " §c[T]" : " §a[F]");
            font.drawStringWithShadow(label, left + 41, rowTop + 5, accent);
            String healthText = String.format(java.util.Locale.ROOT, "%.1f HP", player.getHealth());
            font.drawStringWithShadow(healthText, left + 41, rowTop + 17, RenderUtils.TEXT);
            float healthRatio = Math.max(0.0F, Math.min(1.0F, player.getHealth() / Math.max(1.0F, player.getMaxHealth())));
            Gui.drawRect(left + 41, rowTop + 30, left + 109, rowTop + 34, 0xAA18283D);
            Gui.drawRect(left + 41, rowTop + 30, left + 41 + Math.round(68.0F * healthRatio), rowTop + 34,
                    RenderUtils.blend(0xFFFF5B6E, 0xFF5BE8A6, healthRatio));
            int distance = (int) Math.round(minecraft.thePlayer.getDistanceToEntity(player));
        font.drawStringWithShadow(LanguageManager.format("%sm", distance), left + 41, rowTop + 36, 0xFFB8C9DF);
            drawPlayerItems(player, left + 112, rowTop + 14);
        }
        stalker.setBounds(left, top, width, visible * 50);
    }

    private void drawPlayerFace(EntityPlayer player, int x, int y) {
        if (minecraft.getNetHandler() == null) return;
        NetworkPlayerInfo info = minecraft.getNetHandler().getPlayerInfo(player.getUniqueID());
        if (info == null || info.getLocationSkin() == null) return;
        minecraft.getTextureManager().bindTexture(info.getLocationSkin());
        Gui.drawScaledCustomSizeModalRect(x, y, 8.0F, 8.0F, 8, 8, 32, 32, 64.0F, 64.0F);
    }

    private void drawPlayerItems(EntityPlayer player, int x, int y) {
        net.minecraft.client.renderer.entity.RenderItem renderer = minecraft.getRenderItem();
        GlStateManager.enableDepth();
        for (int index = 0; index < 4; index++) {
            ItemStack armor = player.getCurrentArmor(3 - index);
            if (armor != null) renderer.renderItemAndEffectIntoGUI(armor, x + index * 16, y);
        }
        ItemStack held = player.getHeldItem();
        if (held != null) renderer.renderItemAndEffectIntoGUI(held, x + 68, y);
        GlStateManager.disableDepth();
    }

    private void drawSimpleWidget(HudElement element, String text, HudModule hud, ScaledResolution resolution, FontRenderer font, float phase) {
        int widgetWidth = font.getStringWidth(text) + 14;
        int widgetHeight = 18;
        element.ensureOnScreen(resolution, widgetWidth, widgetHeight);
        int left = element.left(resolution, widgetWidth);
        int top = element.top(resolution, widgetHeight);
        if (hideForDebug(element, left, top, widgetWidth, widgetHeight, isSkeet() ? 0 : 1)) return;
        if (blurEnabled(element.getId())) {
            KawaseBlur.drawRegion(left, top, left + widgetWidth, top + widgetHeight, 3, 0.0F);
        }
        drawHudSurface(hud, left, top, left + widgetWidth, top + widgetHeight);
        drawHudOutline(left, top, left + widgetWidth, top + widgetHeight, hudOutlineColor(hud, phase));
        font.drawStringWithShadow(text, left + 7, top + 5, RenderUtils.TEXT);
        element.setBounds(left, top, widgetWidth, widgetHeight);
    }

    /** Renders the four equipped armour slots without requiring the inventory to be open. */
    private void drawArmor(HudModule hud, ScaledResolution resolution, FontRenderer font) {
        boolean vertical = hud.getArmorDisplayMode().is("Vertical");
        int contentWidth = vertical ? 18 : 72;
        int contentHeight = vertical ? 72 : 18;
        int widgetWidth = contentWidth + 10;
        int widgetHeight = contentHeight + 10;
        armor.ensureOnScreen(resolution, widgetWidth, widgetHeight);
        int left = armor.left(resolution, widgetWidth);
        int top = armor.top(resolution, widgetHeight);
        if (hideForDebug(armor, left, top, widgetWidth, widgetHeight, isSkeet() ? 0 : 1)) return;
        drawHudPanel(ARMOR, left, top, widgetWidth, widgetHeight, hud, 0.14F);
        net.minecraft.client.renderer.entity.RenderItem renderer = minecraft.getRenderItem();
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        for (int index = 0; index < 4; index++) {
            ItemStack stack = minecraft.thePlayer.getCurrentArmor(3 - index);
            if (stack == null) continue;
            int itemX = left + 5 + (vertical ? 0 : index * 18);
            int itemY = top + 5 + (vertical ? index * 18 : 0);
            renderer.renderItemAndEffectIntoGUI(stack, itemX, itemY);
            renderer.renderItemOverlayIntoGUI(font, stack, itemX, itemY, null);
        }
        GlStateManager.disableDepth();
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        armor.setBounds(left, top, widgetWidth, widgetHeight);
    }

    /** A compact 3x9 read-only view of main inventory slots; hotbar slots are deliberately excluded. */
    private void drawInventory(HudModule hud, ScaledResolution resolution, FontRenderer font) {
        final int columns = 9;
        final int rows = 3;
        final int widgetWidth = columns * 18 + 10;
        final int widgetHeight = rows * 18 + 10;
        inventory.ensureOnScreen(resolution, widgetWidth, widgetHeight);
        int left = inventory.left(resolution, widgetWidth);
        int top = inventory.top(resolution, widgetHeight);
        if (hideForDebug(inventory, left, top, widgetWidth, widgetHeight, isSkeet() ? 0 : 1)) return;
        drawHudPanel(INVENTORY, left, top, widgetWidth, widgetHeight, hud, 0.49F);
        net.minecraft.client.renderer.entity.RenderItem renderer = minecraft.getRenderItem();
        // Draw GUI slot chrome before turning on item lighting.  Leaving the
        // directional inventory lights enabled for Gui.drawRect was what made
        // empty slots appear dark, textured, or randomly tinted on some GPUs.
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int itemX = left + 5 + column * 18;
                int itemY = top + 5 + row * 18;
                Gui.drawRect(itemX - 1, itemY - 1, itemX + 17, itemY + 17, 0x4523334D);
            }
        }
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int slot = 9 + row * columns + column;
                int itemX = left + 5 + column * 18;
                int itemY = top + 5 + row * 18;
                ItemStack stack = minecraft.thePlayer.inventory.mainInventory[slot];
                if (stack == null) continue;
                renderer.renderItemAndEffectIntoGUI(stack, itemX, itemY);
                renderer.renderItemOverlayIntoGUI(font, stack, itemX, itemY, null);
            }
        }
        GlStateManager.disableDepth();
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        inventory.setBounds(left, top, widgetWidth, widgetHeight);
    }

    /** Health HUD with an actual min-to-max colour interpolation and optional absorption readout. */
    private void drawHealth(HudModule hud, ScaledResolution resolution, FontRenderer font) {
        float healthValue = Math.max(0.0F, minecraft.thePlayer.getHealth());
        float maximum = Math.max(1.0F, minecraft.thePlayer.getMaxHealth());
        float absorption = Math.max(0.0F, minecraft.thePlayer.getAbsorptionAmount());
        if (hud.getHealthHideFull().isEnabled() && healthValue >= maximum && (!hud.getHealthAbsorption().isEnabled() || absorption <= 0.0F)) {
            return;
        }
        float healthRatio = Math.max(0.0F, Math.min(1.0F, healthValue / maximum));
        int healthColor = RenderUtils.blend(hud.getHealthMinimumColor().getArgb(), hud.getHealthMaximumColor().getArgb(), healthRatio);
        String healthText = String.format(java.util.Locale.ROOT, "%.1f HP", healthValue);
        String absorptionText = hud.getHealthAbsorption().isEnabled() && absorption > 0.0F
                ? String.format(java.util.Locale.ROOT, " +%.1f", absorption) : "";
        int textWidth = font.getStringWidth(healthText) + (absorptionText.isEmpty() ? 0 : font.getStringWidth(absorptionText));
        int widgetWidth = textWidth + 4;
        int widgetHeight = 18;
        health.ensureOnScreen(resolution, widgetWidth, widgetHeight);
        int left = health.left(resolution, widgetWidth);
        int top = health.top(resolution, widgetHeight);
        int textLeft = left + (widgetWidth - textWidth) / 2;
        health.setBounds(left, top, widgetWidth, widgetHeight);
        if (DebugOverlay.overlaps(textLeft, top + 5, textLeft + textWidth + 1, top + 5 + font.FONT_HEIGHT)) return;
        font.drawStringWithShadow(healthText, textLeft, top + 5, healthColor);
        if (!absorptionText.isEmpty()) {
            font.drawStringWithShadow(absorptionText, textLeft + font.getStringWidth(healthText), top + 5,
                    hud.getHealthAbsorptionColor().getArgb());
        }
        health.setBounds(left, top, widgetWidth, widgetHeight);
    }

    /** Minimal click readout: deliberately no panel, outline, or blur. */
    private void drawCps(ScaledResolution resolution, FontRenderer font) {
        int leftCps = ClickStats.leftCps();
        int rightCps = ClickStats.rightCps();
        String text = "[" + leftCps + " | " + rightCps + "]";
        int width = font.getStringWidth(text);
        int height = font.FONT_HEIGHT;
        cps.ensureOnScreen(resolution, width, height + 2);
        int left = cps.left(resolution, width);
        int top = cps.top(resolution, height + 2);
        if (hideForDebug(cps, left, top, width, height + 2, 0)) return;
        font.drawStringWithShadow(text, left, top, RenderUtils.TEXT);
        cps.setBounds(left, top, width, height + 2);
    }

    /** Two click-rate traces sharing one coordinate system (left cyan/right purple). */
    private void drawCpsGraph(HudModule hud, ScaledResolution resolution, FontRenderer font) {
        long now = System.currentTimeMillis();
        if (lastCpsSample == 0L || now - lastCpsSample >= 50L) {
            leftCpsSamples[cpsSampleIndex] = ClickStats.leftCps();
            rightCpsSamples[cpsSampleIndex] = ClickStats.rightCps();
            cpsSampleIndex = (cpsSampleIndex + 1) % leftCpsSamples.length;
            lastCpsSample = now;
        }
        int widgetWidth = 196;
        int widgetHeight = 64;
        cpsGraph.ensureOnScreen(resolution, widgetWidth, widgetHeight);
        int left = cpsGraph.left(resolution, widgetWidth);
        int top = cpsGraph.top(resolution, widgetHeight);
        if (hideForDebug(cpsGraph, left, top, widgetWidth, widgetHeight, isSkeet() ? 0 : 1)) return;
        int right = left + widgetWidth;
        int bottom = top + widgetHeight;
        if (blurEnabled(CPS_GRAPH)) KawaseBlur.drawRegion(left, top, right, bottom, 3, 0.0F);
        drawHudSurface(hud, left, top, right, bottom);
        drawHudOutline(left, top, right, bottom, hudOutlineColor(hud, 0.88F));
        font.drawStringWithShadow(LanguageManager.translate("CPS GRAPH"), left + 7, top + 5, RenderUtils.TEXT);
        font.drawStringWithShadow("L", left + 7, top + 16, 0xFF55E8FF);
        font.drawStringWithShadow("R", left + 19, top + 16, 0xFFC17CFF);
        int graphLeft = left + 7;
        int graphTop = top + 28;
        int graphRight = right - 7;
        int graphBottom = bottom - 7;
        Gui.drawRect(graphLeft, graphBottom - 1, graphRight, graphBottom, 0x557FA1BD);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawCpsLine(leftCpsSamples, graphLeft, graphTop, graphRight, graphBottom, 0xFF55E8FF);
        drawCpsLine(rightCpsSamples, graphLeft, graphTop, graphRight, graphBottom, 0xFFC17CFF);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        cpsGraph.setBounds(left, top, widgetWidth, widgetHeight);
    }

    private void drawCpsLine(float[] samples, int left, int top, int right, int bottom, int color) {
        GL11.glLineWidth(1.5F);
        GL11.glColor4f(((color >>> 16) & 255) / 255.0F, ((color >>> 8) & 255) / 255.0F,
                (color & 255) / 255.0F, ((color >>> 24) & 255) / 255.0F);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int index = 0; index < samples.length; index++) {
            int sample = (cpsSampleIndex + index) % samples.length;
            float value = Math.max(0.0F, Math.min(20.0F, samples[sample]));
            float x = left + (right - left) * index / (float) (samples.length - 1);
            float y = bottom - (value / 20.0F) * (bottom - top);
            GL11.glVertex2f(x, y);
        }
        GL11.glEnd();
    }

    /** Refresh bounds even while hidden, so moving/resizing an element cannot leave stale geometry. */
    private boolean hideForDebug(HudElement element, int left, int top, int width, int height, int margin) {
        element.setBounds(left, top, width, height);
        return DebugOverlay.overlaps(left - margin, top - margin, left + width + margin, top + height + margin);
    }

    private void drawHudPanel(String blurElement, int left, int top, int width, int height, HudModule hud, float phase) {
        if (blurEnabled(blurElement)) {
            KawaseBlur.drawRegion(left, top, left + width, top + height, 3, 0.0F);
        }
        drawHudSurface(hud, left, top, left + width, top + height);
        drawHudOutline(left, top, left + width, top + height, hudOutlineColor(hud, phase));
    }

    /** Draws the vanilla-style sidebar at its HUD-editor position. */
    public void drawScoreboard(float partialTicks) {
        HudModule hud = Vibe.getInstance().getModuleManager().getModule(HudModule.class);
        if (hud == null || !hud.isEnabled() || !hud.getHudElements().isSelectedIgnoreCase(SCOREBOARD) || minecraft.theWorld == null || minecraft.thePlayer == null) {
            return;
        }
        Scoreboard board = minecraft.theWorld.getScoreboard();
        ScoreObjective objective = null;
        ScorePlayerTeam playerTeam = board.getPlayersTeam(minecraft.thePlayer.getName());
        if (playerTeam != null && playerTeam.getChatFormat().getColorIndex() >= 0) {
            objective = board.getObjectiveInDisplaySlot(3 + playerTeam.getChatFormat().getColorIndex());
        }
        if (objective == null) {
            objective = board.getObjectiveInDisplaySlot(1);
        }
        if (objective == null) {
            return;
        }
        List<Score> visible = new ArrayList<Score>();
        for (Score score : board.getSortedScores(objective)) {
            if (!score.getPlayerName().startsWith("#")) {
                visible.add(score);
            }
        }
        if (visible.isEmpty()) {
            return;
        }
        if (visible.size() > 15) {
            visible = new ArrayList<Score>(visible.subList(visible.size() - 15, visible.size()));
        }
        FontRenderer font = minecraft.fontRendererObj;
        String title = hud.getReplaceScoreboardServer().isEnabled() ? "bipas.gay" : objective.getDisplayName();
        int contentWidth = font.getStringWidth(title);
        for (Score score : visible) {
            ScorePlayerTeam team = board.getPlayersTeam(score.getPlayerName());
            String line = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName()) + " " + EnumChatFormatting.RED + score.getScorePoints();
            contentWidth = Math.max(contentWidth, font.getStringWidth(line));
        }
        int width = contentWidth + 6;
        int height = visible.size() * font.FONT_HEIGHT + font.FONT_HEIGHT + 3;
        ScaledResolution resolution = new ScaledResolution(minecraft);
        scoreboard.ensureOnScreen(resolution, width + 2, height);
        int left = scoreboard.left(resolution, width);
        int top = scoreboard.top(resolution, height);
        int backgroundLeft = left - 2;
        int backgroundRight = left + width;
        int backgroundBottom = top + height;
        if (hideForDebug(scoreboard, backgroundLeft, top, backgroundRight - backgroundLeft, height, 0)) return;
        if (blurEnabled(BlurModule.SCOREBOARD)) {
            KawaseBlur.drawRegion(backgroundLeft, top, backgroundRight, backgroundBottom, 4, partialTicks);
        }
        // Keep the scoreboard a single HUD surface. Per-row black rectangles
        // were painting over Vibe blur and LiquidGlass completely.
        drawHudSurface(hud, backgroundLeft, top, backgroundRight, backgroundBottom);
        int y = top;
        for (Score score : visible) {
            ScorePlayerTeam team = board.getPlayersTeam(score.getPlayerName());
            String player = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            String points = EnumChatFormatting.RED + "" + score.getScorePoints();
            font.drawString(player, left, y, 0xFFFFFFFF);
            font.drawStringWithShadow(points, left + width - 3 - font.getStringWidth(points), y, 0xFFFFFFFF);
            y += font.FONT_HEIGHT;
        }
        font.drawString(title, left + (contentWidth - font.getStringWidth(title)) / 2,
                y + 2, 0xFFFFFFFF);
        scoreboard.setBounds(backgroundLeft, top, backgroundRight - backgroundLeft, height);
    }

    public void openEditor() {
        minecraft.displayGuiScreen(new HudEditorGui(this));
    }

    public void openSettings(String element) {
        minecraft.displayGuiScreen(new HudSettingsGui(this, element));
    }

    public HudElement getElement(String id) {
        if (MUSIC.equals(id)) return music;
        if (WATERMARK.equals(id)) {
            return watermark;
        }
        if (ARRAY_LIST.equals(id)) {
            return arrayList;
        }
        if (COORDINATES.equals(id)) {
            return coordinates;
        }
        if (SCOREBOARD.equals(id)) {
            return scoreboard;
        }
        if (CLOCK.equals(id)) {
            return clock;
        }
        if (SESSION_INFO.equals(id)) {
            return sessionInfo;
        }
        if (MOTION_GRAPH.equals(id)) {
            return motionGraph;
        }
        if (STALKER.equals(id)) {
            return stalker;
        }
        if (ARMOR.equals(id)) {
            return armor;
        }
        if (INVENTORY.equals(id)) {
            return inventory;
        }
        if (HEALTH.equals(id)) {
            return health;
        }
        if (CPS.equals(id)) return cps;
        if (CPS_GRAPH.equals(id)) return cpsGraph;
        return null;
    }

    public String[] getElementIds() {
        return new String[] {WATERMARK, ARRAY_LIST, COORDINATES, SCOREBOARD, CLOCK, SESSION_INFO, MOTION_GRAPH, STALKER,
                ARMOR, INVENTORY, HEALTH, CPS, CPS_GRAPH, MUSIC};
    }

    public boolean isEnabled(String id) {
        if (MUSIC.equals(id)) {
            dev.vibe.module.impl.MusicModule m = Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.MusicModule.class);
            return m != null && m.isEnabled() && m.hud.isEnabled();
        }
        HudModule hud = Vibe.getInstance().getModuleManager().getModule(HudModule.class);
        return hud != null && hud.getHudElements().isSelectedIgnoreCase(id);
    }

    public void drawPreview(HudElement element, FontRenderer font) {
        if (element == music) { drawMusic(true); return; }
        if (element == watermark) {
            Gui.drawRect(element.getLeft() - 2, element.getTop() - 2, element.getLeft() + Math.max(110, element.getWidth()) + 2,
                    element.getTop() + 22, 0xAA2DE2C2);
            font.drawStringWithShadow(LanguageManager.translate("WATERMARK"), element.getLeft() + 4, element.getTop() + 6, 0xFFFFFFFF);
        } else if (element == arrayList) {
            arrayRenderer.draw(Vibe.getInstance().getModuleManager().getModule(HudModule.class), arrayList,
                    new ScaledResolution(minecraft), true);
        } else if (element == coordinates) {
            Gui.drawRect(element.getLeft() - 2, element.getTop() - 2, element.getLeft() + Math.max(110, element.getWidth()) + 2,
                    element.getTop() + 22, 0xAA60D5FF);
            font.drawStringWithShadow("XYZ 0 / 64 / 0", element.getLeft() + 4, element.getTop() + 6, 0xFFFFFFFF);
        } else if (element == clock) {
            Gui.drawRect(element.getLeft() - 2, element.getTop() - 2, element.getLeft() + Math.max(90, element.getWidth()) + 2,
                    element.getTop() + 22, 0xAA76D7FF);
            font.drawStringWithShadow("12:34:56", element.getLeft() + 4, element.getTop() + 6, 0xFFFFFFFF);
        } else if (element == sessionInfo) {
            Gui.drawRect(element.getLeft() - 2, element.getTop() - 2, element.getLeft() + Math.max(145, element.getWidth()) + 2,
                    element.getTop() + 22, 0xAAFFBD59);
            font.drawStringWithShadow(LanguageManager.translate("Session 00:12:34 | Kills 3"), element.getLeft() + 4, element.getTop() + 6, 0xFFFFFFFF);
        } else if (element == motionGraph) {
            Gui.drawRect(element.getLeft() - 2, element.getTop() - 2, element.getLeft() + Math.max(196, element.getWidth()) + 2,
                    element.getTop() + 64, 0xAA2DE2C2);
            font.drawStringWithShadow(LanguageManager.translate("MOTION GRAPH"), element.getLeft() + 7, element.getTop() + 6, 0xFFFFFFFF);
            font.drawStringWithShadow("▁▂▅▃▆▇▅▂▃▅", element.getLeft() + 7, element.getTop() + 28, 0xFF8FE8FF);
        } else if (element == stalker) {
            Gui.drawRect(element.getLeft() - 2, element.getTop() - 2, element.getLeft() + Math.max(212, element.getWidth()) + 2,
                    element.getTop() + 50, 0xAA2DE2C2);
            font.drawStringWithShadow(LanguageManager.translate("Stalker  Friend / Target"), element.getLeft() + 7, element.getTop() + 6, 0xFFFFFFFF);
            font.drawStringWithShadow(LanguageManager.translate("Skin  ♥  Armor  Item  12m"), element.getLeft() + 7, element.getTop() + 25, 0xFFD5E1F5);
        } else if (element == armor) {
            Gui.drawRect(element.getLeft() - 2, element.getTop() - 2, element.getLeft() + Math.max(82, element.getWidth()) + 2,
                    element.getTop() + Math.max(28, element.getHeight()) + 2, 0xAA5BE8A6);
            font.drawStringWithShadow(LanguageManager.translate("ARMOR  [ ][ ][ ][ ]"), element.getLeft() + 5, element.getTop() + 8, 0xFFFFFFFF);
        } else if (element == inventory) {
            Gui.drawRect(element.getLeft() - 2, element.getTop() - 2, element.getLeft() + Math.max(172, element.getWidth()) + 2,
                    element.getTop() + Math.max(64, element.getHeight()) + 2, 0xAA7EAEFF);
            font.drawStringWithShadow(LanguageManager.translate("INVENTORY"), element.getLeft() + 5, element.getTop() + 5, 0xFFFFFFFF);
            font.drawStringWithShadow("[ ][ ][ ][ ][ ][ ][ ][ ][ ]", element.getLeft() + 5, element.getTop() + 24, 0xFFD5E1F5);
            font.drawStringWithShadow("[ ][ ][ ][ ][ ][ ][ ][ ][ ]", element.getLeft() + 5, element.getTop() + 39, 0xFFD5E1F5);
        } else if (element == health) {
            font.drawStringWithShadow(LanguageManager.translate("20.0 HP"), element.getLeft() + 2, element.getTop() + 6, 0xFF5BE8A6);
        } else if (element == cps) {
            font.drawStringWithShadow("[8 | 2]", element.getLeft(), element.getTop() + 4, 0xFFFFFFFF);
        } else if (element == cpsGraph) {
            Gui.drawRect(element.getLeft() - 2, element.getTop() - 2, element.getLeft() + 198,
                    element.getTop() + 66, 0xAA2DE2C2);
            font.drawStringWithShadow(LanguageManager.translate("CPS GRAPH"), element.getLeft() + 7, element.getTop() + 6, 0xFFFFFFFF);
            font.drawStringWithShadow("╱╲╱╲╱╲", element.getLeft() + 7, element.getTop() + 28, 0xFF55E8FF);
        } else {
            int previewWidth = Math.max(110, element.getWidth());
            int previewHeight = Math.max(42, element.getHeight());
            Gui.drawRect(element.getLeft() - 2, element.getTop() - 2, element.getLeft() + previewWidth + 2,
                    element.getTop() + previewHeight + 2, 0xAAFF5B6E);
            font.drawStringWithShadow(LanguageManager.translate("SCOREBOARD"), element.getLeft() + 4, element.getTop() + 5, 0xFFFFFFFF);
            font.drawStringWithShadow(LanguageManager.translate("Player      12"), element.getLeft() + 4, element.getTop() + 17, 0xFFD5E1F5);
            font.drawStringWithShadow("Vibe         8", element.getLeft() + 4, element.getTop() + 29, 0xFFD5E1F5);
        }
    }

    public void save() {
        File parent = layoutFile.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            return;
        }
        JsonObject root = toJson();
        try {
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(layoutFile), StandardCharsets.UTF_8);
            try {
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            } finally {
                writer.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void load() {
        if (!layoutFile.isFile()) {
            return;
        }
        try {
            InputStreamReader reader = new InputStreamReader(new FileInputStream(layoutFile), StandardCharsets.UTF_8);
            JsonObject root;
            try {
                root = new JsonParser().parse(reader).getAsJsonObject();
            } finally {
                reader.close();
            }
            fromJson(root);
        } catch (Exception ignored) {
        }
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("layoutVersion", LAYOUT_VERSION);
        root.add(WATERMARK, watermark.toJson());
        root.add(ARRAY_LIST, arrayList.toJson());
        root.add(COORDINATES, coordinates.toJson());
        root.add(SCOREBOARD, scoreboard.toJson());
        root.add(CLOCK, clock.toJson());
        root.add(SESSION_INFO, sessionInfo.toJson());
        root.add(MOTION_GRAPH, motionGraph.toJson());
        root.add(STALKER, stalker.toJson());
        root.add(ARMOR, armor.toJson());
        root.add(INVENTORY, inventory.toJson());
        root.add(HEALTH, health.toJson());
        root.add(CPS, cps.toJson());
        root.add(CPS_GRAPH, cpsGraph.toJson());
        root.add(MUSIC, music.toJson());
        return root;
    }

    public void fromJson(JsonObject root) {
        if (root == null) {
            return;
        }
        watermark.fromJson(root.has(WATERMARK) && root.get(WATERMARK).isJsonObject() ? root.getAsJsonObject(WATERMARK) : null);
        arrayList.fromJson(root.has(ARRAY_LIST) && root.get(ARRAY_LIST).isJsonObject() ? root.getAsJsonObject(ARRAY_LIST) : null);
        coordinates.fromJson(root.has(COORDINATES) && root.get(COORDINATES).isJsonObject() ? root.getAsJsonObject(COORDINATES) : null);
        scoreboard.fromJson(root.has(SCOREBOARD) && root.get(SCOREBOARD).isJsonObject() ? root.getAsJsonObject(SCOREBOARD) : null);
        clock.fromJson(root.has(CLOCK) && root.get(CLOCK).isJsonObject() ? root.getAsJsonObject(CLOCK) : null);
        sessionInfo.fromJson(root.has(SESSION_INFO) && root.get(SESSION_INFO).isJsonObject() ? root.getAsJsonObject(SESSION_INFO) : null);
        motionGraph.fromJson(root.has(MOTION_GRAPH) && root.get(MOTION_GRAPH).isJsonObject() ? root.getAsJsonObject(MOTION_GRAPH) : null);
        stalker.fromJson(root.has(STALKER) && root.get(STALKER).isJsonObject() ? root.getAsJsonObject(STALKER) : null);
        armor.fromJson(root.has(ARMOR) && root.get(ARMOR).isJsonObject() ? root.getAsJsonObject(ARMOR) : null);
        inventory.fromJson(root.has(INVENTORY) && root.get(INVENTORY).isJsonObject() ? root.getAsJsonObject(INVENTORY) : null);
        if (root.has(HEALTH) && root.get(HEALTH).isJsonObject()) {
            JsonObject healthData = root.getAsJsonObject(HEALTH);
            health.fromJson(healthData);
        }
        cps.fromJson(root.has(CPS) && root.get(CPS).isJsonObject() ? root.getAsJsonObject(CPS) : null);
        cpsGraph.fromJson(root.has(CPS_GRAPH) && root.get(CPS_GRAPH).isJsonObject() ? root.getAsJsonObject(CPS_GRAPH) : null);
        music.fromJson(root.has(MUSIC) && root.get(MUSIC).isJsonObject() ? root.getAsJsonObject(MUSIC) : null);
        // Move only entries that still match the exact v0.0.2 defaults. A
        // deliberately placed element stays untouched; untouched layouts are
        // upgraded out of the chat lane and gain a vertically centred board.
        if (!root.has("layoutVersion") || root.get("layoutVersion").getAsInt() < LAYOUT_VERSION) {
            migrateLegacyDefaults();
        }
    }

    private void migrateLegacyDefaults() {
        if (coordinates.matches(9, 9, false)) coordinates.resetToDefault();
        if (scoreboard.matches(9, 94, false)) scoreboard.resetToDefault();
        if (clock.matches(9, 39, false)) clock.resetToDefault();
        if (sessionInfo.matches(9, 40, false)) sessionInfo.resetToDefault();
        if (motionGraph.matches(9, 70, false)) motionGraph.resetToDefault();
        if (stalker.matches(9, 142, false)) stalker.resetToDefault();
        if (inventory.matches(9, 46, false)) inventory.resetToDefault();
        if (cpsGraph.matches(9, 70, false)) cpsGraph.resetToDefault();
    }

    public static final class HudElement {
        private final String id;
        private int x;
        private int y;
        private boolean rightAnchored;
        private final boolean bottomAnchored;
        private boolean centred;
        private boolean verticallyCentred;
        private final int defaultX;
        private final int defaultY;
        private final boolean defaultRightAnchored;
        private final boolean defaultVerticallyCentred;
        private int left;
        private int top;
        private int width;
        private int height;

        private HudElement(String id, int x, int y, boolean rightAnchored, boolean bottomAnchored) {
            this(id, x, y, rightAnchored, bottomAnchored, false);
        }

        private HudElement(String id, int x, int y, boolean rightAnchored, boolean bottomAnchored, boolean centred) {
            this(id, x, y, rightAnchored, bottomAnchored, centred, false);
        }

        private HudElement(String id, int x, int y, boolean rightAnchored, boolean bottomAnchored, boolean centred,
                boolean verticallyCentred) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.rightAnchored = rightAnchored;
            this.bottomAnchored = bottomAnchored;
            this.centred = centred;
            this.verticallyCentred = verticallyCentred;
            this.defaultX = x;
            this.defaultY = y;
            this.defaultRightAnchored = rightAnchored;
            this.defaultVerticallyCentred = verticallyCentred;
        }

        public int left(ScaledResolution resolution, int width) {
            if (centred) {
                return resolution.getScaledWidth() / 2 + x - width / 2;
            }
            return rightAnchored ? resolution.getScaledWidth() - x - width : x;
        }

        public int top(ScaledResolution resolution, int height) {
            return verticallyCentred ? resolution.getScaledHeight() / 2 + y - height / 2
                    : centred ? resolution.getScaledHeight() / 2 + y
                    : (bottomAnchored ? resolution.getScaledHeight() - y - height : y);
        }

        public void setBounds(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        /** Restore defaults if a resolution/GUI-scale change pushes this HUD item offscreen. */
        public void ensureOnScreen(ScaledResolution resolution, int expectedWidth, int expectedHeight) {
            int currentLeft = left(resolution, expectedWidth);
            int currentTop = top(resolution, expectedHeight);
            if (currentLeft < 0 || currentTop < 0 || currentLeft + expectedWidth > resolution.getScaledWidth()
                    || currentTop + expectedHeight > resolution.getScaledHeight()) {
                x = defaultX;
                y = defaultY;
                rightAnchored = defaultRightAnchored;
                centred = HEALTH.equals(id) || CPS.equals(id);
                verticallyCentred = defaultVerticallyCentred;
            }
        }

        public void moveTo(int desiredLeft, int desiredTop, ScaledResolution resolution) {
            int safeLeft = Math.max(0, Math.min(resolution.getScaledWidth() - Math.max(1, width), desiredLeft));
            int safeTop = Math.max(0, Math.min(resolution.getScaledHeight() - Math.max(1, height), desiredTop));
            centred = false;
            verticallyCentred = false;
            x = rightAnchored ? resolution.getScaledWidth() - safeLeft - width : safeLeft;
            y = bottomAnchored ? resolution.getScaledHeight() - safeTop - height : safeTop;
            left = safeLeft;
            top = safeTop;
        }

        public void setRightAnchored(boolean rightAnchored, ScaledResolution resolution, int currentWidth) {
            if (this.rightAnchored == rightAnchored) {
                return;
            }
            int currentLeft = left(resolution, currentWidth);
            centred = false;
            x = rightAnchored ? resolution.getScaledWidth() - currentLeft - currentWidth : currentLeft;
            this.rightAnchored = rightAnchored;
        }

        private JsonObject toJson() {
            JsonObject value = new JsonObject();
            value.addProperty("x", x);
            value.addProperty("y", y);
            value.addProperty("rightAnchored", rightAnchored);
            value.addProperty("centred", centred);
            value.addProperty("verticallyCentred", verticallyCentred);
            return value;
        }

        private void fromJson(JsonObject value) {
            if (value != null) {
                if (value.has("x")) {
                    x = value.get("x").getAsInt();
                }
                if (value.has("y")) {
                    y = value.get("y").getAsInt();
                }
                if (value.has("rightAnchored")) rightAnchored = value.get("rightAnchored").getAsBoolean();
                if (value.has("centred")) {
                    centred = value.get("centred").getAsBoolean();
                }
                if (value.has("verticallyCentred")) {
                    verticallyCentred = value.get("verticallyCentred").getAsBoolean();
                }
            }
        }

        private boolean matches(int expectedX, int expectedY, boolean expectedCentred) {
            return x == expectedX && y == expectedY && centred == expectedCentred;
        }

        private void resetToDefault() {
            x = defaultX;
            y = defaultY;
            rightAnchored = defaultRightAnchored;
            centred = HEALTH.equals(id) || CPS.equals(id);
            verticallyCentred = defaultVerticallyCentred;
        }

        public String getId() { return id; }
        public int getLeft() { return left; }
        public int getTop() { return top; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getY() { return y; }
    }

    private static final class ArrayRow {
        private final int left;
        private final int right;
        private final int top;
        private final int bottom;
        private final int color;

        private ArrayRow(int left, int right, int top, int bottom, int color) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.color = color;
        }
    }
}
