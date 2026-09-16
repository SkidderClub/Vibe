package dev.vibe.event;

import dev.vibe.Vibe;
import dev.vibe.command.VibeChatGui;
import dev.vibe.module.Module;
import dev.vibe.module.impl.AutoClickerModule;
import dev.vibe.module.impl.AimAssistModule;
import dev.vibe.module.impl.SprintModule;
import dev.vibe.module.impl.ChestEspModule;
import dev.vibe.module.impl.ClickGuiModule;
import dev.vibe.module.impl.EagleModule;
import dev.vibe.module.impl.EspModule;
import dev.vibe.module.impl.FastPlaceModule;
import dev.vibe.module.impl.FovChangerModule;
import dev.vibe.module.impl.FullBrightModule;
import dev.vibe.module.impl.ReachModule;
import dev.vibe.module.impl.NameProtectModule;
import dev.vibe.module.impl.NoJumpDelayModule;
import dev.vibe.module.impl.NoSlowModule;
import dev.vibe.module.impl.MoveFixModule;
import dev.vibe.module.impl.TestModule;
import dev.vibe.module.impl.KillAuraModule;
import dev.vibe.module.impl.ItemEspModule;
import dev.vibe.module.impl.GirlfriendModule;
import dev.vibe.module.impl.QolModule;
import dev.vibe.module.impl.VelocityModule;
import dev.vibe.module.impl.BHopModule;
import dev.vibe.module.impl.FlyModule;
import dev.vibe.module.impl.WaifuModule;
import dev.vibe.module.impl.HudModule;
import dev.vibe.module.impl.CustomCrosshairModule;
import dev.vibe.module.impl.HitmarkerModule;
import dev.vibe.module.impl.InventoryManagerModule;
import dev.vibe.module.impl.ChestStealerModule;
import dev.vibe.module.impl.InventoryEditorModule;
import dev.vibe.module.impl.AutoToolModule;
import dev.vibe.module.impl.BedAuraModule;
import dev.vibe.module.impl.ScaffoldModule;
import dev.vibe.module.impl.FastBreakModule;
import dev.vibe.module.impl.AmbienceModule;
import dev.vibe.module.impl.NesEmulatorModule;
import dev.vibe.module.impl.WTapModule;
import dev.vibe.module.impl.FakeLagModule;
import dev.vibe.module.impl.BacktrackModule;
import dev.vibe.module.impl.BedEspModule;
import dev.vibe.module.impl.BlockChangeEspModule;
import dev.vibe.module.impl.BlockOverlayModule;
import dev.vibe.module.impl.CuteVisualsModule;
import dev.vibe.module.impl.TrajectoriesModule;
import dev.vibe.module.impl.MemeGameModule;
import dev.vibe.input.ClickStats;
import dev.vibe.network.PacketDelayService;
import dev.vibe.script.ScriptRuntime;
import dev.vibe.ui.ChestEspRenderer;
import dev.vibe.ui.EspRenderer;
import dev.vibe.ui.TargetEspRenderer;
import dev.vibe.ui.SkeletalRenderer;
import dev.vibe.ui.CosmeticsRenderer;
import dev.vibe.ui.RenderUtils;
import dev.vibe.ui.CustomCrosshairRenderer;
import dev.vibe.ui.ParticlesRenderer;
import dev.vibe.ui.QolRenderer;
import dev.vibe.ui.HitmarkerRenderer;
import dev.vibe.ui.BedEspRenderer;
import dev.vibe.ui.BlockChangeEspRenderer;
import dev.vibe.ui.BlockOverlayRenderer;
import dev.vibe.ui.BedAuraRenderer;
import dev.vibe.ui.BacktrackRenderer;
import dev.vibe.ui.CuteVisualsRenderer;
import dev.vibe.ui.TrajectoriesRenderer;
import dev.vibe.ui.ItemEspRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import dev.vibe.ui.WaifuRenderer;
import dev.vibe.ui.DebugOverlay;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public final class ClientEvents {

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final EspRenderer espRenderer = new EspRenderer();
    private final TargetEspRenderer targetEspRenderer = new TargetEspRenderer();
    private final SkeletalRenderer skeletalRenderer = new SkeletalRenderer();
    private final CosmeticsRenderer cosmeticsRenderer = new CosmeticsRenderer();
    private final ChestEspRenderer chestEspRenderer = new ChestEspRenderer();
    private final CustomCrosshairRenderer customCrosshairRenderer = new CustomCrosshairRenderer();
    private final ParticlesRenderer particlesRenderer = new ParticlesRenderer();
    private final QolRenderer qolRenderer = new QolRenderer();
    private final HitmarkerRenderer hitmarkerRenderer = new HitmarkerRenderer();
    private final dev.vibe.ui.effect.FogRenderer fogRenderer = new dev.vibe.ui.effect.FogRenderer();
    private final BedEspRenderer bedEspRenderer = new BedEspRenderer();
    private final BlockChangeEspRenderer blockChangeEspRenderer = new BlockChangeEspRenderer();
    private final BlockOverlayRenderer blockOverlayRenderer = new BlockOverlayRenderer();
    private final BedAuraRenderer bedAuraRenderer = new BedAuraRenderer();
    private final BacktrackRenderer backtrackRenderer = new BacktrackRenderer();
    private final CuteVisualsRenderer cuteVisualsRenderer = new CuteVisualsRenderer();
    private final TrajectoriesRenderer trajectoriesRenderer = new TrajectoriesRenderer();
    private final ItemEspRenderer itemEspRenderer = new ItemEspRenderer();
    private Scoreboard suppressedScoreboard;
    private ScoreObjective suppressedSidebar;
    private ScoreObjective suppressedTeamSidebar;
    private int suppressedTeamSlot = -1;
    private RenderGameOverlayEvent.Text debugText;
    private boolean deferredCrosshair;
    private final Map<NetworkPlayerInfo, IChatComponent> protectedTabEntries = new HashMap<NetworkPlayerInfo, IChatComponent>();

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (!Keyboard.getEventKeyState()) {
            return;
        }
        int key = Keyboard.getEventKey();
        ScriptRuntime scripts = Vibe.getInstance().getScriptRuntime();
        if (scripts != null && !scripts.key(Keyboard.getEventCharacter(), key)) {
            return;
        }
        ClickGuiModule clickGui = Vibe.getInstance().getModuleManager().getModule(ClickGuiModule.class);
        if ((clickGui != null && clickGui.getKey() == key)
                && minecraft.currentScreen == null) {
            Vibe.getInstance().openClickGui();
            return;
        }
        if (minecraft.currentScreen != null) {
            return;
        }
        for (Module module : Vibe.getInstance().getModuleManager().getModules()) {
            if (module.getKey() != Keyboard.KEY_NONE && module.getKey() == key && !(module instanceof ClickGuiModule)) {
                module.toggle();
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            dev.vibe.module.impl.MusicModule music = Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.MusicModule.class);
            if (music != null) music.tick();
            MoveFixModule moveFix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
            if (moveFix != null) moveFix.beginRotationTick();
            ScriptRuntime scripts = Vibe.getInstance().getScriptRuntime();
            if (scripts != null) scripts.tickStart();
            // Producers select the fake rotation before vanilla samples input.
            TestModule test = Vibe.getInstance().getModuleManager().getModule(TestModule.class);
            if (test != null) test.tick();
            KillAuraModule killaura = Vibe.getInstance().getModuleManager().getModule(KillAuraModule.class);
            if (killaura != null) killaura.tickStart();
            if (moveFix != null) {
                moveFix.tick();
                moveFix.installInputHook();
            }
            GirlfriendModule girlfriend = Vibe.getInstance().getModuleManager().getModule(GirlfriendModule.class);
            if (girlfriend != null) girlfriend.tick();
            cosmeticsRenderer.installLayers();
            skeletalRenderer.installLayers();
            PacketDelayService.getInstance().tick();
            ReachModule reach = Vibe.getInstance().getModuleManager().getModule(ReachModule.class);
            if (reach != null) reach.tick();
            // Tool selection must occur before Minecraft consumes the held
            // attack key, not at the end of the tick after a break action has
            // already begun.
            AutoToolModule autoTool = Vibe.getInstance().getModuleManager().getModule(AutoToolModule.class);
            dev.vibe.module.impl.PickenSwitchModule picken=Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.PickenSwitchModule.class);
            if(picken!=null)picken.tick();
            if (autoTool != null) autoTool.tick();
            BedAuraModule bedAura = Vibe.getInstance().getModuleManager().getModule(BedAuraModule.class);
            if (bedAura != null) bedAura.tick();
            FastBreakModule fastBreak = Vibe.getInstance().getModuleManager().getModule(FastBreakModule.class);
            if (fastBreak != null) fastBreak.tick();
            NoSlowModule noSlow = Vibe.getInstance().getModuleManager().getModule(NoSlowModule.class);
            if (noSlow != null) {
                noSlow.installInputHook();
            }
            // A normal GuiContainer click is handled before the player update
            // for this client tick.  Keeping automated container input here
            // preserves that same order instead of injecting a window click
            // after the movement update at tick end.
            InventoryManagerModule inventoryManager = Vibe.getInstance().getModuleManager().getModule(InventoryManagerModule.class);
            if (inventoryManager != null) {
                inventoryManager.tick();
            }
            ChestStealerModule chestStealer = Vibe.getInstance().getModuleManager().getModule(ChestStealerModule.class);
            if (chestStealer != null) {
                chestStealer.tick();
            }
            WTapModule wTap = Vibe.getInstance().getModuleManager().getModule(WTapModule.class);
            if (wTap != null) wTap.tick();
            BedEspModule bedEsp = Vibe.getInstance().getModuleManager().getModule(BedEspModule.class);
            if (bedEsp != null) bedEsp.tick();
            BlockChangeEspModule blockChanges = Vibe.getInstance().getModuleManager().getModule(BlockChangeEspModule.class);
            if (blockChanges != null) blockChanges.tick();
            BlockOverlayModule blockOverlay = Vibe.getInstance().getModuleManager().getModule(BlockOverlayModule.class);
            if (blockOverlay != null) blockOverlay.tick();
            BacktrackModule backtrack = Vibe.getInstance().getModuleManager().getModule(BacktrackModule.class);
            if (backtrack != null) backtrack.tick();
            CuteVisualsModule cuteVisuals = Vibe.getInstance().getModuleManager().getModule(CuteVisualsModule.class);
            if (cuteVisuals != null) cuteVisuals.tick();
            TrajectoriesModule trajectories = Vibe.getInstance().getModuleManager().getModule(TrajectoriesModule.class);
            if (trajectories != null) trajectories.tick();
            return;
        }
        if (event.phase == TickEvent.Phase.END) {
            KillAuraModule killaura = Vibe.getInstance().getModuleManager().getModule(KillAuraModule.class);
            if (killaura != null) killaura.tickEnd();
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (minecraft.thePlayer == null) return;
        dev.vibe.module.impl.TargetsModule targets = Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.TargetsModule.class);
        if (targets != null) targets.tickWarnings();
        FullBrightModule fullBright = Vibe.getInstance().getModuleManager().getModule(FullBrightModule.class);
        if (fullBright != null && fullBright.isEnabled()) {
            minecraft.gameSettings.gammaSetting = 1000.0F;
        }
        SprintModule sprint = Vibe.getInstance().getModuleManager().getModule(SprintModule.class);
        if (sprint != null && sprint.isEnabled() && minecraft.thePlayer.movementInput.moveForward > 0.0F
                && !minecraft.thePlayer.isSneaking() && !minecraft.thePlayer.isCollidedHorizontally) {
            minecraft.thePlayer.setSprinting(true);
        }
        FovChangerModule fov = Vibe.getInstance().getModuleManager().getModule(FovChangerModule.class);
        if (fov != null) {
            fov.tick();
        }
        NoJumpDelayModule noJumpDelay = Vibe.getInstance().getModuleManager().getModule(NoJumpDelayModule.class);
        if (noJumpDelay != null) {
            noJumpDelay.tick();
        }
        EagleModule eagle = Vibe.getInstance().getModuleManager().getModule(EagleModule.class);
        if (eagle != null) {
            eagle.tick();
        }
        FastPlaceModule fastPlace = Vibe.getInstance().getModuleManager().getModule(FastPlaceModule.class);
        if (fastPlace != null) {
            fastPlace.tick();
        }
        AimAssistModule aimAssist = Vibe.getInstance().getModuleManager().getModule(AimAssistModule.class);
        if (aimAssist != null) {
            aimAssist.tick();
        }
        VelocityModule velocity = Vibe.getInstance().getModuleManager().getModule(VelocityModule.class);
        if (velocity != null) {
            velocity.tick();
        }
        BHopModule bhop = Vibe.getInstance().getModuleManager().getModule(BHopModule.class);
        if (bhop != null) {
            bhop.tick();
        }
        FlyModule fly = Vibe.getInstance().getModuleManager().getModule(FlyModule.class);
        if (fly != null) {
            fly.tick();
        }
        WaifuModule waifu = Vibe.getInstance().getModuleManager().getModule(WaifuModule.class);
        if (waifu != null) {
            waifu.tick();
        }
        NesEmulatorModule nes = Vibe.getInstance().getModuleManager().getModule(NesEmulatorModule.class);
        if (nes != null) {
            nes.tick();
        }
        for (Module module : Vibe.getInstance().getModuleManager().getModules()) {
            if (module instanceof MemeGameModule) ((MemeGameModule) module).tick();
        }
        AmbienceModule ambience = Vibe.getInstance().getModuleManager().getModule(AmbienceModule.class);
        if (ambience != null) {
            ambience.tick();
        }
        ScriptRuntime scripts = Vibe.getInstance().getScriptRuntime();
        if (scripts != null) scripts.tickEnd();
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        ScriptRuntime scripts = Vibe.getInstance().getScriptRuntime();
        if (scripts != null && !scripts.mouse(Mouse.getEventButton(), Mouse.getEventButtonState())) {
            event.setCanceled(true);
            return;
        }
        if (Mouse.getEventButtonState()) {
            if (Mouse.getEventButton() == 0) ClickStats.recordLeft();
            else if (Mouse.getEventButton() == 1) ClickStats.recordRight();
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) return;
        AmbienceModule ambience = Vibe.getInstance().getModuleManager().getModule(AmbienceModule.class);
        if (ambience != null) {
            ambience.renderTick();
        }
        ScriptRuntime scripts = Vibe.getInstance().getScriptRuntime();
        if (scripts != null) scripts.renderTick(event.renderTickTime);
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onDebugText(RenderGameOverlayEvent.Text event) {
        // Keep the event itself: later subscribers can still edit or cancel its text.
        debugText = event;
    }

    @SubscribeEvent
    public void onHud(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT
                && event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (event.type == RenderGameOverlayEvent.ElementType.ALL) restoreVanillaScoreboard();
        if (minecraft.gameSettings.showDebugInfo && debugText != null && !debugText.isCanceled()) {
            DebugOverlay.begin(event.resolution.getScaledWidth(), minecraft.fontRendererObj.FONT_HEIGHT,
                    debugText.left, debugText.right, minecraft.fontRendererObj::getStringWidth);
        }
        try {
            if (event.type == RenderGameOverlayEvent.ElementType.TEXT) {
                Vibe.getInstance().getHudManager().draw();
                espRenderer.renderOverlay();
                hitmarkerRenderer.renderOverlay();
                if (deferredCrosshair) customCrosshairRenderer.render();
            } else {
                Vibe.getInstance().getHudManager().drawScoreboard(event.partialTicks);
                qolRenderer.capture();
            }
        } finally {
            // Editors and standalone games draw their own previews after this pass.
            DebugOverlay.clear();
        }
    }

    @SubscribeEvent
    public void onChatPre(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CHAT) {
            return;
        }
        // Post-processing and additive HUD glow can otherwise leak into the
        // vanilla chat rectangle, making one fading message render solid
        // black. Restore Minecraft's normal transparent GUI state first.
        GL11.glColorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @SubscribeEvent
    public void onGuiDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        // VibeClickGui draws particles between its backdrop and panels. Other
        // vanilla GUIs still receive their post-content particle layer here.
        if (!(event.gui instanceof dev.vibe.ui.VibeClickGui) && !isSkeetEditor(event.gui)) {
            particlesRenderer.draw(event.gui);
        }
        WaifuRenderer.draw(event.gui);
        drawChestCursor(event.gui);
        drawInventoryCursor(event.gui);
    }

    /**
     * GuiInventory renders its player model through a fixed-function 3D
     * transform.  Restore the few global states altered by GUI overlays
     * before vanilla enters that transform, so an overlay cannot leave its
     * culling/depth/colour state inverted for the player preview.
     */
    @SubscribeEvent
    public void onInventoryDrawPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!(event.gui instanceof GuiInventory)) return;
        dev.vibe.ui.GuiRenderState.prepare(true);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableColorMaterial();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @SubscribeEvent
    public void onGuiBackground(GuiScreenEvent.BackgroundDrawnEvent event) {
        qolRenderer.redrawBehind(event.gui);
    }

    @SubscribeEvent
    public void onGuiMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        WaifuRenderer.handleMouse(event.gui);
    }

    private void drawChestCursor(net.minecraft.client.gui.GuiScreen screen) {
        if (!(screen instanceof GuiChest) || minecraft.thePlayer == null || !(minecraft.thePlayer.openContainer instanceof net.minecraft.inventory.ContainerChest)) {
            return;
        }
        ChestStealerModule module = Vibe.getInstance().getModuleManager().getModule(ChestStealerModule.class);
        InventoryEditorModule editor = Vibe.getInstance().getModuleManager().getModule(InventoryEditorModule.class);
        if (module == null || editor == null || !editor.getDrawPosition().isEnabled() || !module.shouldDrawCursor()) return;
        net.minecraft.inventory.ContainerChest chest = (net.minecraft.inventory.ContainerChest) minecraft.thePlayer.openContainer;
        int rows = Math.max(1, (chest.getLowerChestInventory().getSizeInventory() + 8) / 9);
        ScaledResolution resolution = new ScaledResolution(minecraft);
        int left = (resolution.getScaledWidth() - 176) / 2;
        int top = (resolution.getScaledHeight() - (rows * 18 + 114)) / 2;
        int x = Math.round(left + module.getCursorX());
        int y = Math.round(top + module.getCursorY());
        int color = editor.getPositionColor().getArgb();
        Gui.drawRect(x - 7, y - 7, x + 8, y - 6, color);
        Gui.drawRect(x - 7, y + 6, x + 8, y + 7, color);
        Gui.drawRect(x - 7, y - 7, x - 6, y + 8, color);
        Gui.drawRect(x + 6, y - 7, x + 7, y + 8, color);
        Gui.drawRect(x - 1, y - 1, x + 2, y + 2, RenderUtils.alpha(color, 245));
    }

    /**
     * These editors explicitly render particles between their backdrop and
     * their Skeet window. Do not add a second foreground particle pass here.
     */
    private boolean isSkeetEditor(net.minecraft.client.gui.GuiScreen screen) {
        return screen instanceof dev.vibe.ui.InventoryEditorGui
                || screen instanceof dev.vibe.ui.ConfigEditorGui
                || screen instanceof dev.vibe.ui.FriendEditorGui
                || screen instanceof dev.vibe.ui.KeybindEditorGui
                || screen instanceof dev.vibe.ui.EspEditorGui
                || screen instanceof dev.vibe.ui.NesEmulatorGui
                || screen instanceof dev.vibe.ui.Gta7Gui
                || screen instanceof dev.vibe.ui.Battlefront3Gui
                || screen instanceof dev.vibe.ui.MemeGameGui
                || screen instanceof dev.vibe.ui.SlotsGui
                || screen instanceof dev.vibe.ui.ScriptsEditorGui;
    }

    private void drawInventoryCursor(net.minecraft.client.gui.GuiScreen screen) {
        if (!(screen instanceof net.minecraft.client.gui.inventory.GuiInventory) || minecraft.thePlayer == null) return;
        InventoryEditorModule editor = Vibe.getInstance().getModuleManager().getModule(InventoryEditorModule.class);
        InventoryManagerModule manager = Vibe.getInstance().getModuleManager().getModule(InventoryManagerModule.class);
        if (editor == null || manager == null || !editor.getDrawPosition().isEnabled() || !manager.shouldDrawCursor()) return;
        net.minecraft.inventory.Slot slot = minecraft.thePlayer.inventoryContainer.getSlot(manager.getCursorSlot());
        if (slot == null) return;
        ScaledResolution resolution = new ScaledResolution(minecraft);
        int left = (resolution.getScaledWidth() - 176) / 2;
        int top = (resolution.getScaledHeight() - 166) / 2;
        int x = left + slot.xDisplayPosition + 8;
        int y = top + slot.yDisplayPosition + 8;
        int color = editor.getPositionColor().getArgb();
        Gui.drawRect(x - 7, y - 7, x + 8, y - 6, color);
        Gui.drawRect(x - 7, y + 6, x + 8, y + 7, color);
        Gui.drawRect(x - 7, y - 7, x - 6, y + 8, color);
        Gui.drawRect(x + 6, y - 7, x + 7, y + 8, color);
    }

    @SubscribeEvent
    public void onHotbar(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) {
            return;
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    @SubscribeEvent
    public void onHotbarPost(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;
        ScaledResolution resolution = new ScaledResolution(minecraft);
        int left = resolution.getScaledWidth() / 2 - 91;
        int top = resolution.getScaledHeight() - 22;
        dev.vibe.module.impl.PickenSwitchModule picken=Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.PickenSwitchModule.class);
        if(picken!=null&&picken.hasSilentSlot()){
            int x=left+picken.getSpoofedSlot()*20;
            RenderUtils.roundedOutline(x,top,x+22,top+22,2F,1.5F,picken.getSilentColor().getArgb());
        }
        AutoToolModule autoTool = Vibe.getInstance().getModuleManager().getModule(AutoToolModule.class);
        if (autoTool != null && autoTool.hasSilentSlot()) {
            int x = left + autoTool.getSpoofedSlot() * 20;
            RenderUtils.roundedOutline(x, top, x + 22, top + 22, 2.0F, 1.5F,
                    autoTool.getSilentColor().getArgb());
        }
        ScaffoldModule scaffold = Vibe.getInstance().getModuleManager().getModule(ScaffoldModule.class);
        if (scaffold != null && scaffold.hasSilentSlot()) {
            int scaffoldX = left + scaffold.getSpoofedSlot() * 20;
            RenderUtils.roundedOutline(scaffoldX, top, scaffoldX + 22, top + 22, 2.0F, 1.5F,
                    scaffold.getSilentColor().getArgb());
        }
    }

    @SubscribeEvent
    public void onCrosshair(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            return;
        }
        CustomCrosshairModule crosshair = Vibe.getInstance().getModuleManager().getModule(CustomCrosshairModule.class);
        // The custom renderer intentionally remains first-person only, but
        // vanilla's textured crosshair must still be hidden in third person
        // while the replacement module is active.
        if (crosshair != null && crosshair.isEnabled()) {
            event.setCanceled(true);
            // F3 text is assembled later in this frame; measure it before drawing.
            if (minecraft.gameSettings.showDebugInfo) deferredCrosshair = true;
            else customCrosshairRenderer.render();
        }
    }

    @SubscribeEvent
    public void onPreAll(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        debugText = null;
        deferredCrosshair = false;
        DebugOverlay.clear();
        if (suppressedScoreboard != null) return;
        HudModule hud = Vibe.getInstance().getModuleManager().getModule(HudModule.class);
        if (hud == null || !hud.isEnabled() || !hud.getHudElements().isSelectedIgnoreCase(dev.vibe.hud.HudManager.SCOREBOARD)
            || minecraft.theWorld == null || minecraft.thePlayer == null) {
            return;
        }
        suppressedScoreboard = minecraft.theWorld.getScoreboard();
        suppressedSidebar = suppressedScoreboard.getObjectiveInDisplaySlot(1);
        ScorePlayerTeam team = suppressedScoreboard.getPlayersTeam(minecraft.thePlayer.getName());
        if (team != null && team.getChatFormat().getColorIndex() >= 0) {
            suppressedTeamSlot = 3 + team.getChatFormat().getColorIndex();
            suppressedTeamSidebar = suppressedScoreboard.getObjectiveInDisplaySlot(suppressedTeamSlot);
            suppressedScoreboard.setObjectiveInDisplaySlot(suppressedTeamSlot, null);
        }
        suppressedScoreboard.setObjectiveInDisplaySlot(1, null);
    }

    @SubscribeEvent
    public void onTabListPre(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.PLAYER_LIST || minecraft.thePlayer == null || minecraft.getNetHandler() == null) {
            return;
        }
        NameProtectModule protect = Vibe.getInstance().getModuleManager().getModule(NameProtectModule.class);
        if (protect == null || !protect.isEnabled() || !protect.isConfigured()) {
            return;
        }
        protectedTabEntries.clear();
        for (NetworkPlayerInfo info : minecraft.getNetHandler().getPlayerInfoMap()) {
            String original = info.getDisplayName() == null
                    ? info.getGameProfile().getName() : info.getDisplayName().getFormattedText();
            String replacement = protect.protectText(original);
            if (!original.equals(replacement)) {
                protectedTabEntries.put(info, info.getDisplayName());
                info.setDisplayName(new ChatComponentText(replacement));
            }
        }
    }

    @SubscribeEvent
    public void onTabListPost(RenderGameOverlayEvent.Post event) {
        if (event.type == RenderGameOverlayEvent.ElementType.PLAYER_LIST && !protectedTabEntries.isEmpty()) {
            for (Map.Entry<NetworkPlayerInfo, IChatComponent> entry : protectedTabEntries.entrySet()) {
                entry.getKey().setDisplayName(entry.getValue());
            }
            protectedTabEntries.clear();
        }
    }

    private void restoreVanillaScoreboard() {
        if (suppressedScoreboard == null) {
            return;
        }
        suppressedScoreboard.setObjectiveInDisplaySlot(1, suppressedSidebar);
        if (suppressedTeamSlot >= 0) {
            suppressedScoreboard.setObjectiveInDisplaySlot(suppressedTeamSlot, suppressedTeamSidebar);
        }
        suppressedScoreboard = null;
        suppressedSidebar = null;
        suppressedTeamSidebar = null;
        suppressedTeamSlot = -1;
    }

    @SubscribeEvent
    public void onWorldRender(RenderWorldLastEvent event) {
        fogRenderer.renderMinecraft();
        ScriptRuntime scripts = Vibe.getInstance().getScriptRuntime();
        if (scripts != null) scripts.renderWorld(event.partialTicks);
        EspModule esp = Vibe.getInstance().getModuleManager().getModule(EspModule.class);
        if (esp != null && esp.isEnabled()) {
            espRenderer.renderWorld(event);
        }
        ChestEspModule chestEsp = Vibe.getInstance().getModuleManager().getModule(ChestEspModule.class);
        if (chestEsp != null && chestEsp.isEnabled()) {
            chestEspRenderer.render(event);
        }
        bedEspRenderer.render(event);
        blockChangeEspRenderer.render(event);
        blockOverlayRenderer.render(event);
        bedAuraRenderer.render(event);
        backtrackRenderer.render(event);
        cuteVisualsRenderer.render(event);
        trajectoriesRenderer.render(event);
        itemEspRenderer.render(event);
        targetEspRenderer.render(event);
        hitmarkerRenderer.renderWorld(event);
        skeletalRenderer.render(event);
    }

    @SubscribeEvent
    public void onFogDensity(net.minecraftforge.client.event.EntityViewRenderEvent.FogDensity event) {
        if (!fogRenderer.hasFailed() && dev.vibe.ui.effect.FogRenderer.replacesVanillaFog()) {
            org.lwjgl.opengl.GL11.glFogi(org.lwjgl.opengl.GL11.GL_FOG_MODE, org.lwjgl.opengl.GL11.GL_EXP);
            event.density = 0;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerRenderPre(RenderPlayerEvent.Pre event) {
        if (event.entityPlayer == minecraft.thePlayer) {
            MoveFixModule moveFix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
            if (moveFix != null) moveFix.beginPlayerRender(minecraft.thePlayer);
        }
        cosmeticsRenderer.prepareSkin(event);
    }

    @SubscribeEvent
    public void onPlayerRender(RenderPlayerEvent.Post event) {
        if (event.entityPlayer == minecraft.thePlayer) {
            MoveFixModule moveFix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
            if (moveFix != null) moveFix.endPlayerRender(minecraft.thePlayer);
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (event.entityPlayer != minecraft.thePlayer || !(event.target instanceof EntityLivingBase)) {
            return;
        }
        ScriptRuntime scripts = Vibe.getInstance().getScriptRuntime();
        if (scripts != null && !scripts.attack(keystrokesmod.script.model.Entity.convert(event.target), keystrokesmod.script.model.Entity.convert(event.entityPlayer))) {
            event.setCanceled(true);
            return;
        }
        WTapModule wTap = Vibe.getInstance().getModuleManager().getModule(WTapModule.class);
        if (wTap != null) wTap.onAttack((EntityLivingBase) event.target);
        FakeLagModule fakeLag = Vibe.getInstance().getModuleManager().getModule(FakeLagModule.class);
        if (fakeLag != null) fakeLag.onAttack();
        BacktrackModule backtrack = Vibe.getInstance().getModuleManager().getModule(BacktrackModule.class);
        if (backtrack != null) backtrack.onAttack((EntityLivingBase) event.target);
        HitmarkerModule hitmarker = Vibe.getInstance().getModuleManager().getModule(HitmarkerModule.class);
        if (hitmarker != null) {
            hitmarker.mark((EntityLivingBase) event.target);
        }
        GirlfriendModule girlfriend = Vibe.getInstance().getModuleManager().getModule(GirlfriendModule.class);
        if (girlfriend != null) girlfriend.onAttack((EntityLivingBase) event.target);
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.entityPlayer != minecraft.thePlayer || event.pos == null) return;
        ScriptRuntime scripts = Vibe.getInstance().getScriptRuntime();
        if (scripts != null) scripts.playerInteract();
        // CuteVisuals and Girlfriend now consume C07 START/STOP/ABORT packets
        // through PacketDelayService. A left-click event only means an
        // attempted break and must not create a false break reaction.
        BlockChangeEspModule blockChanges = Vibe.getInstance().getModuleManager().getModule(BlockChangeEspModule.class);
        if (blockChanges == null || !blockChanges.isEnabled()) return;
        if (event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            blockChanges.watch(event.pos);
        } else if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            ItemStack held = minecraft.thePlayer.getHeldItem();
            if (held != null && held.getItem() instanceof ItemBlock && event.face != null) {
                blockChanges.watch(event.pos.offset(event.face));
            }
        }
    }

    @SubscribeEvent
    public void onPlayerNameplate(RenderLivingEvent.Specials.Pre event) {
        // Only player labels are replaced by the tactical 2D ESP label. This
        // deliberately leaves ArmorStand and other entity labels untouched.
        if (!(event.entity instanceof EntityPlayer) || event.entity == minecraft.thePlayer) {
            return;
        }
        EspModule esp = Vibe.getInstance().getModuleManager().getModule(EspModule.class);
        if (esp != null && esp.isEnabled() && esp.getModes().isSelected("2D") && esp.get2D(esp.resolvedProfile(esp.profileFor(event.entity))).name.enabled.isEnabled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        ScriptRuntime scripts = Vibe.getInstance().getScriptRuntime();
        if (scripts != null) scripts.gui(event.gui == null ? "" : event.gui.getClass().getSimpleName(), event.gui != null);
        if (event.gui != null && event.gui.getClass() == GuiChat.class) {
            event.gui = new VibeChatGui();
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (event.message != null) {
            // Read the original plain text before Name Protect changes its
            // presentation. Board state continues to update when closed.
            String raw = event.message.getUnformattedText();
            for (Module module : Vibe.getInstance().getModuleManager().getModules()) {
                if (module instanceof MemeGameModule) ((MemeGameModule) module).receiveChat(raw);
            }
        }
        ScriptRuntime scripts = Vibe.getInstance().getScriptRuntime();
        if (scripts != null && event.message != null && !scripts.chat(event.message.getUnformattedText())) {
            event.setCanceled(true);
            return;
        }
        NameProtectModule protect = Vibe.getInstance().getModuleManager().getModule(NameProtectModule.class);
        if (protect != null && protect.isEnabled() && event.message != null) {
            // Do not flatten a received component to a plain string.  Doing
            // so discards ChatStyle, including click and hover events used by
            // server messages.  Text leaves are rebuilt only when a protected
            // name needs replacing, and every original style/sibling remains.
            event.message = protectChatComponent(event.message, protect);
        }
    }

    @SubscribeEvent
    public void onScriptWorldJoin(EntityJoinWorldEvent event) {
        ScriptRuntime scripts = Vibe.getInstance().getScriptRuntime();
        if (scripts != null && event.entity != null) scripts.worldJoin(keystrokesmod.script.model.Entity.convert(event.entity));
    }

    @SubscribeEvent
    public void onScriptDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        minecraft.addScheduledTask(() -> {
            fogRenderer.close(); hitmarkerRenderer.close();
            HitmarkerModule markers = Vibe.getInstance().getModuleManager().getModule(HitmarkerModule.class);
            if (markers != null) markers.clear();
        });
        ScriptRuntime scripts = Vibe.getInstance().getScriptRuntime();
        if (scripts != null) scripts.disconnect();
    }

    private IChatComponent protectChatComponent(IChatComponent component, NameProtectModule protect) {
        if (!(component instanceof ChatComponentText)) {
            return component;
        }
        ChatComponentText text = (ChatComponentText) component;
        ChatComponentText copy = new ChatComponentText(protect.protectText(text.getChatComponentText_TextValue()));
        copy.setChatStyle(text.getChatStyle().createShallowCopy());
        for (IChatComponent sibling : text.getSiblings()) {
            copy.appendSibling(protectChatComponent(sibling, protect));
        }
        return copy;
    }

}
