package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.game.gta8.Gta8Audio;
import dev.vibe.game.gta8.Gta8Game;
import dev.vibe.game.gta8.Gta8MapImage;
import dev.vibe.game.gta8.Gta8Missions;
import dev.vibe.game.gta8.Gta8Ped;
import dev.vibe.game.gta8.Gta8Progress;
import dev.vibe.game.gta8.Gta8Renderer;
import dev.vibe.game.gta8.Gta8Vehicle;
import dev.vibe.game.gta8.Gta8Weapon;
import dev.vibe.game.gta8.Gta8Weather;
import dev.vibe.game.gta8.Gta8World;
import dev.vibe.language.LanguageManager;
import dev.vibe.module.impl.Gta8Module;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/**
 * GTA8 screen: loading, input, a GTA-style HUD with a rotating radar and GPS, the pause map,
 * job board, shops, statistics and graphics settings. Minecraft input is sampled, never forwarded.
 */
public final class Gta8Gui extends GuiScreen {
    private static final int WHITE = 0xFFF4F2EC, MUTED = 0xFFB8BEC4, GREEN = 0xFF6FC56F, BLUE = 0xFF5A9AE0, YELLOW = 0xFFF0C83C, RED = 0xFFE0453A;
    private final Gta8Module module;
    private Gta8World world;
    private Gta8Game game;
    private Gta8Renderer renderer;
    private final Gta8Audio audio = new Gta8Audio();
    private final Gta8Game.Input input = new Gta8Game.Input();
    private BufferedImage mapImage;
    private DynamicTexture mapTexture;
    private boolean paused, captured, hudHidden;
    private long lastFrame;
    private int tab;
    private double mapZoom = 1, mapX, mapZ;
    private boolean dragging;
    private int dragX, dragY;
    private String shownZone = "", vehicleName = "";
    private double zoneUntil, vehicleUntil, hitUntil, frameAverage;
    private Gta8Vehicle lastVehicle;
    private List<double[]> route;
    private double routeTime = -10;
    private NeverLoseFont font, bold;
    private float sensitivityCache;

    public Gta8Gui(Gta8Module module) { this.module = module; }

    // ------------------------------------------------------------------ lifecycle
    @Override public void initGui() {
        if (game == null) {
            world = new Gta8World();
            Gta8Progress progress = Gta8Progress.load(mc.mcDataDir.toPath().resolve("vibe/gta8-progress.json"));
            game = new Gta8Game(world, progress);
            renderer = new Gta8Renderer();
            renderer.prepare(world);
            mapImage = Gta8MapImage.render(world);
            font = new NeverLoseFont(Font.SANS_SERIF, Font.BOLD, 20);
            bold = new NeverLoseFont(Font.SANS_SERIF, Font.BOLD, 24);
            mapX = game.player.x; mapZ = game.player.z;
        }
        lastFrame = System.nanoTime();
        capture(!paused && Display.isActive() && renderer.ready());
    }
    private void capture(boolean value) {
        if (!Mouse.isCreated()) return;
        if (captured != value || Mouse.isGrabbed() != value) {
            Mouse.setGrabbed(value);
            Mouse.getDX(); Mouse.getDY();
            captured = value;
        }
    }
    private boolean menuOpen() { return paused || game.shop != null || game.jobsOpen; }
    private void exitGame() { mc.displayGuiScreen(null); mc.setIngameFocus(); }
    @Override public void onGuiClosed() {
        capture(false);
        if (game != null) { game.progress.save(); }
        if (renderer != null) renderer.close();
        if (mapTexture != null) { mapTexture.deleteGlTexture(); mapTexture = null; }
        audio.close();
        if (font != null) font.close();
        if (bold != null) bold.close();
        if (module.isEnabled()) module.setEnabled(false);
        super.onGuiClosed();
    }
    @Override public boolean doesGuiPauseGame() { return true; }

    // ------------------------------------------------------------------ frame
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        double dt = Math.min(.1, Math.max(0, (now - lastFrame) / 1e9));
        lastFrame = now;
        applySettings();
        if (!renderer.ready()) {
            renderer.render(game, mc.displayWidth, mc.displayHeight);
            if (renderer.failure != null) { failure(); return; }
            loading();
            if (renderer.ready()) capture(Display.isActive());
            return;
        }
        if (renderer.failure != null) { failure(); return; }
        if (!Display.isActive() && !paused && !game.dead) { paused = true; capture(false); }
        if (mapTexture == null) mapTexture = new DynamicTexture(mapImage);
        audio.init();
        audio.volume = module.sound.isEnabled() ? 1 : 0;
        sample();
        if (!menuOpen()) game.advance(dt, input);
        else { game.updateCamera(0); }
        audio.pause(menuOpen());
        audio.update(game, dt);
        renderer.wasted = game.dead ? (float) Math.min(1, game.deathTimer / 1.5) : game.busted ? .6f : 0;
        renderer.hurt = (float) Math.max(Math.min(1, game.hurtFlash * 2), game.player.health < game.player.maxHealth * .25 && !game.dead ? .45 + .15 * Math.sin(game.time * 6) : 0);
        renderer.render(game, mc.displayWidth, mc.displayHeight);
        frameAverage = frameAverage * .95 + renderer.lastFrameMillis * .05;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        if (!hudHidden && !paused) hud();
        if (game.shop != null) shop(mouseX, mouseY);
        else if (paused || game.jobsOpen) pauseMenu(mouseX, mouseY);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
    }

    private void applySettings() {
        renderer.quality = module.qualityLevel();
        renderer.renderScale = module.renderScale.getDouble() / 100;
        renderer.bloomEnabled = module.bloom.isEnabled();
        renderer.fxaaEnabled = module.antialiasing.isEnabled();
        if (game != null) game.density = module.densityLevel();
    }

    private static boolean down(KeyBinding binding) {
        int key = binding.getKeyCode();
        return key < 0 ? Mouse.isCreated() && key + 100 >= 0 && key + 100 < Mouse.getButtonCount() && Mouse.isButtonDown(key + 100)
                : key > 0 && key < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(key);
    }
    private void sample() {
        GameSettings s = mc.gameSettings;
        boolean active = !menuOpen() && captured;
        input.forward = active && down(s.keyBindForward); input.back = active && down(s.keyBindBack);
        input.left = active && down(s.keyBindLeft); input.right = active && down(s.keyBindRight);
        input.jump = active && down(s.keyBindJump); input.sprint = active && down(s.keyBindSprint);
        input.crouch = active && down(s.keyBindSneak);
        input.aim = active && down(s.keyBindUseItem); input.attack = active && down(s.keyBindAttack);
        input.horn = active && Keyboard.isKeyDown(Keyboard.KEY_H);
        if (active) {
            float sensitivity = s.mouseSensitivity * .6f + .2f;
            double scale = sensitivity * sensitivity * sensitivity * 8 * .15 * (game.player.aim > .5 ? .6 : 1);
            if (game.player.aim > .5 && game.player.weapon == Gta8Weapon.SNIPER) scale *= .35;
            int dx = Mouse.getDX(), dy = Mouse.getDY();
            game.look(dx * scale, -dy * scale * (s.invertMouse ? -1 : 1));
        }
    }

    // ------------------------------------------------------------------ input events
    @Override protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            if (game.shop != null) { game.shop = null; capture(true); return; }
            if (game.jobsOpen) { game.jobsOpen = false; paused = false; capture(true); return; }
            paused = !paused;
            if (paused) { tab = 0; mapX = game.player.x; mapZ = game.player.z; }
            capture(!paused);
            lastFrame = System.nanoTime();
            return;
        }
        if (!renderer.ready() || menuOpen()) return;
        GameSettings s = mc.gameSettings;
        if (key == Keyboard.KEY_F) input.enterExit = true;
        else if (key == Keyboard.KEY_E || key == s.keyBindInventory.getKeyCode()) input.interact = true;
        else if (key == Keyboard.KEY_R || key == s.keyBindDrop.getKeyCode()) input.reload = true;
        else if (key == s.keyBindTogglePerspective.getKeyCode()) input.toggleCamera = true;
        else if (key == Keyboard.KEY_M) { paused = true; tab = 0; mapX = game.player.x; mapZ = game.player.z; capture(false); }
        else if (key == Keyboard.KEY_F1) hudHidden = !hudHidden;
        else if (key == Keyboard.KEY_J) { game.jobsOpen = true; tab = 1; capture(false); }
        else for (int i = 0; i < 9; i++) if (key == s.keyBindsHotbar[i].getKeyCode()) { input.slot = i; break; }
    }
    @Override public void handleMouseInput() throws IOException {
        if (menuOpen()) {
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0 && paused && tab == 0) mapZoom = Math.max(.4, Math.min(8, mapZoom * (wheel > 0 ? 1.25 : .8)));
            super.handleMouseInput();
            return;
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) { if (wheel > 0) input.prevWeapon = true; else input.nextWeapon = true; }
        int button = Mouse.getEventButton();
        if (button >= 0 && Mouse.getEventButtonState()) {
            int code = button - 100;
            GameSettings s = mc.gameSettings;
            for (int i = 0; i < 9; i++) if (code == s.keyBindsHotbar[i].getKeyCode()) input.slot = i;
            if (code == s.keyBindTogglePerspective.getKeyCode()) input.toggleCamera = true;
        }
    }
    @Override protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (game.shop != null) { shopClick(mx, my); return; }
        if (!paused && !game.jobsOpen) return;
        int left = 20, top = 16, w = width - 40;
        String[] tabs = {"MAP", "JOBS", "STATS", "SETTINGS", "CONTROLS"};
        int tw = Math.min(110, (w - 10) / tabs.length);
        for (int i = 0; i < tabs.length; i++) if (inside(mx, my, left + i * (tw + 2), top, left + i * (tw + 2) + tw, top + 18)) { tab = i; return; }
        int by = height - 30;
        if (inside(mx, my, width - 250, by, width - 140, by + 20)) { paused = false; game.jobsOpen = false; capture(true); lastFrame = System.nanoTime(); return; }
        if (inside(mx, my, width - 130, by, width - 20, by + 20)) { exitGame(); return; }
        if (tab == 0) {
            int[] r = mapRect();
            if (inside(mx, my, r[0], r[1], r[2], r[3])) {
                if (button == 0) { dragging = true; dragX = mx; dragY = my; }
                if (button == 1) game.waypoint = false;
                double[] w0 = screenToWorld(mx, my);
                if (button == 0 && mx == dragX && my == dragY) { pendingWaypoint = w0; }
            }
        } else if (tab == 1) jobsClick(mx, my);
        else if (tab == 3) settingsClick(mx, my);
    }
    private double[] pendingWaypoint;
    @Override protected void mouseReleased(int mx, int my, int state) {
        if (dragging && pendingWaypoint != null && Math.abs(mx - dragX) + Math.abs(my - dragY) < 3) {
            game.waypoint = true; game.waypointX = pendingWaypoint[0]; game.waypointZ = pendingWaypoint[1];
            routeTime = -10;
        }
        dragging = false; pendingWaypoint = null;
    }
    @Override protected void mouseClickMove(int mx, int my, int button, long time) {
        if (!dragging || tab != 0) return;
        double scale = mapScale();
        mapX -= (mx - dragX) / scale; mapZ -= (my - dragY) / scale;
        dragX = mx; dragY = my;
        pendingWaypoint = null;
    }
    private static boolean inside(int x, int y, int l, int t, int r, int b) { return x >= l && x < r && y >= t && y < b; }

    // ------------------------------------------------------------------ text helpers
    private void text(String s, double x, double y, int color) { font.draw(LanguageManager.translate(s), (float) x, (float) y, color); }
    private void shadowText(String s, double x, double y, int color) { String t = LanguageManager.translate(s); font.draw(t, (float) x + .8f, (float) y + .8f, 0xC0000000); font.draw(t, (float) x, (float) y, color); }
    private double textWidth(String s) { return font.width(LanguageManager.translate(s)); }
    private void rightText(String s, double right, double y, int color) { shadowText(s, right - textWidth(s), y, color); }
    private void centerText(String s, double y, int color) { shadowText(s, (width - textWidth(s)) / 2, y, color); }
    private void big(String s, double y, float scale, int color) {
        String t = LanguageManager.translate(s);
        GlStateManager.pushMatrix();
        double w = bold.width(t) * scale;
        GlStateManager.translate((width - w) / 2, y, 0);
        GlStateManager.scale(scale, scale, 1);
        bold.draw(t, 1.2f, 1.2f, 0xD0000000);
        bold.draw(t, 0, 0, color);
        GlStateManager.popMatrix();
    }
    private static void rect(double x0, double y0, double x1, double y1, int color) { Gui.drawRect((int) Math.round(x0), (int) Math.round(y0), (int) Math.round(x1), (int) Math.round(y1), color); }

    // ------------------------------------------------------------------ loading and failure
    private void loading() {
        rect(0, 0, width, height, 0xFF0A0C10);
        drawGradientRect(0, height / 2, width, height, 0x00000000, 0x80301A10);
        big("LOS VIBES", height / 2 - 60, 2.6f, WHITE);
        centerText("GTA 8", height / 2 - 20, YELLOW);
        double p = renderer.progress();
        int bw = Math.min(360, width - 80), bx = (width - bw) / 2, by = height / 2 + 20;
        rect(bx, by, bx + bw, by + 4, 0xFF2A2E34);
        rect(bx, by, bx + bw * p, by + 4, 0xFFF0C83C);
        String[] tips = {"Steal a parked car with F. Locked cars take a moment to break into.", "Hide from the police outside the search circle to lose your wanted level.",
                "Aim at a store clerk to rob the register.", "Wet roads reduce grip: brake earlier in the rain.", "Press M for the map, click to set a waypoint.",
                "The Quick Spray garage repaints your car and clears your wanted level.", "Press J to open the job board anywhere."};
        centerText(tips[(int) (System.currentTimeMillis() / 5000 % tips.length)], by + 16, MUTED);
        centerText("Building the city " + (int) (p * 100) + "%", by - 16, MUTED);
    }
    private void failure() {
        capture(false);
        rect(0, 0, width, height, 0xFF0A0C10);
        centerText("GTA8 could not start", height / 2 - 30, RED);
        centerText(String.valueOf(renderer.failure), height / 2 - 14, WHITE);
        centerText("Press Escape to return", height / 2 + 6, MUTED);
        paused = true;
    }

    // ------------------------------------------------------------------ HUD
    private void hud() {
        Gta8Ped p = game.player;
        radar();
        // Top right: money, weapon, wanted stars.
        int right = width - 14;
        rightText("$" + String.format(Locale.US, "%,d", game.progress.money), right, 12, 0xFF8FD48F);
        Gta8Weapon w = p.weapon;
        String ammo = w == Gta8Weapon.FISTS ? w.title : w.title + "   " + p.clip + " / " + game.progress.ammo(w);
        rightText(ammo, right, 26, WHITE);
        stars(right, 44);
        rightText(game.weather.clock() + "  " + game.weather.type.title, right, 62, MUTED);
        // Zone and vehicle names fade in on change.
        if (!game.zone.equals(shownZone)) { shownZone = game.zone; zoneUntil = game.time + 5; }
        if (p.vehicle != lastVehicle) { lastVehicle = p.vehicle; if (p.vehicle != null) { vehicleName = p.vehicle.model.title; vehicleUntil = game.time + 4; } }
        int baseY = height - 22;
        if (p.vehicle != null) {
            Gta8Vehicle v = p.vehicle;
            String speed = (int) Math.round(v.speedKmh()) + " km/h";
            GlStateManager.pushMatrix();
            GlStateManager.translate(right - bold.width(speed) * 1.4, baseY - 30, 0);
            GlStateManager.scale(1.4, 1.4, 1);
            bold.draw(speed, .8f, .8f, 0xC0000000); bold.draw(speed, 0, 0, WHITE);
            GlStateManager.popMatrix();
            rightText((v.reversing ? "R" : "D" + v.gear) + "   " + (int) v.rpm + " rpm", right, baseY - 6, MUTED);
            double health = v.health / 1000;
            rect(right - 90, baseY + 8, right, baseY + 11, 0x80000000);
            rect(right - 90, baseY + 8, right - 90 + 90 * health, baseY + 11, health < .3 ? RED : 0xFFC8C8C8);
            baseY -= 44;
        }
        if (game.time < zoneUntil) rightText(shownZone, right, baseY, fade(WHITE, zoneUntil - game.time));
        if (game.time < vehicleUntil) rightText(vehicleName, right, baseY - 14, fade(YELLOW, vehicleUntil - game.time));
        // Help and prompts.
        String prompt = game.time < game.helpUntil ? game.help : game.prompt();
        if (!prompt.isEmpty()) {
            double pw = textWidth(prompt) + 16;
            rect(12, 12, 12 + pw, 32, 0xB0000000);
            text(prompt, 20, 16, WHITE);
        }
        if (game.missions.active != null) {
            String objective = game.missions.objective;
            if (game.missions.limit > 0) objective += "   " + (int) Math.max(0, game.missions.limit - game.missions.timer) + "s";
            centerText(objective, height - 64, YELLOW);
        }
        if (game.time < game.messageUntil) centerText(game.message, height - 48, fade(WHITE, game.messageUntil - game.time));
        if (game.time < game.bigUntil) {
            int color = "WASTED".equals(game.bigTitle) ? 0xFFC8281E : "BUSTED".equals(game.bigTitle) ? WHITE : YELLOW;
            big(game.bigTitle, height / 2 - 40, 3.2f, fade(color, game.bigUntil - game.time));
            if (!game.bigSub.isEmpty()) centerText(game.bigSub, height / 2 + 4, fade(WHITE, game.bigUntil - game.time));
        }
        crosshair();
        if (renderer.lastFrameMillis > 0 && Keyboard.isCreated() && Keyboard.isKeyDown(Keyboard.KEY_F3)) text(String.format(Locale.ROOT, "GPU frame %.1f ms  chunks %d  cars %d  peds %d", frameAverage, renderer.drawnChunks, game.vehicles.size(), game.peds.size()), 12, 40, MUTED);
    }
    private static int fade(int argb, double remaining) {
        double a = Math.min(1, remaining / .6);
        return ((int) ((argb >>> 24) * a) << 24) | (argb & 0xFFFFFF);
    }
    private void stars(int right, int y) {
        int wanted = game.police.wanted;
        boolean blink = game.police.searching && (int) (game.time * 2.5) % 2 == 0 || game.police.flash > 0 && (int) (game.time * 8) % 2 == 0;
        for (int i = 0; i < 5; i++) {
            double cx = right - 8 - (4 - i) * 16;
            boolean on = i < wanted;
            star(cx, y + 7, 6.5, on ? (blink ? 0xFF8090A0 : WHITE) : 0x50FFFFFF);
        }
    }
    private void star(double x, double y, double r, int color) {
        GlStateManager.disableTexture2D();
        GL11.glColor4f((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2d(x, y);
        for (int i = 0; i <= 10; i++) { double a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 == 0 ? r : r * .42; GL11.glVertex2d(x + Math.cos(a) * rr, y + Math.sin(a) * rr); }
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
    }
    private void crosshair() {
        Gta8Ped p = game.player;
        if (game.dead || p.weapon == Gta8Weapon.FISTS && p.aim < .5 || p.vehicle != null && p.aim < .5) return;
        if (p.aim < .3 && !game.firstPerson) return;
        double cx = width / 2.0, cy = height / 2.0;
        if (p.weapon == Gta8Weapon.SNIPER && p.aim > .8) {
            rect(0, 0, width, height, 0x00000000);
            GlStateManager.disableTexture2D();
            GL11.glColor4f(0, 0, 0, .92f);
            double r = Math.min(width, height) * .42;
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int i = 0; i <= 64; i++) { double a = i * Math.PI * 2 / 64; GL11.glVertex2d(cx + Math.cos(a) * r, cy + Math.sin(a) * r); GL11.glVertex2d(cx + Math.cos(a) * width, cy + Math.sin(a) * width); }
            GL11.glEnd();
            GlStateManager.enableTexture2D();
            rect(cx - r, cy - .5, cx + r, cy + .5, 0xFF000000); rect(cx - .5, cy - r, cx + .5, cy + r, 0xFF000000);
            return;
        }
        double gap = 2 + game.bloom * 90 + (p.moveSpeed > 2 ? 3 : 0);
        int c = game.time < hitUntil ? 0xFFFF5A4A : 0xE0FFFFFF;
        rect(cx - .7, cy - .7, cx + .7, cy + .7, c);
        rect(cx - gap - 4, cy - .5, cx - gap, cy + .5, c); rect(cx + gap, cy - .5, cx + gap + 4, cy + .5, c);
        rect(cx - .5, cy - gap - 4, cx + .5, cy - gap, c); rect(cx - .5, cy + gap, cx + .5, cy + gap + 4, c);
        for (Gta8Game.GameEvent e : game.events) if (e.type == Gta8Game.Event.HIT) hitUntil = game.time + .15;
    }

    // ------------------------------------------------------------------ radar
    private void radar() {
        Gta8Ped p = game.player;
        int rw = Math.max(120, Math.min(220, width / 5)), rh = (int) (rw * .64);
        int x0 = 14, y1 = height - 22, y0 = y1 - rh, x1 = x0 + rw;
        rect(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xC0000000);
        double scaleFactor = mc.displayWidth / (double) width;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x0 * scaleFactor), (int) ((height - y1) * scaleFactor), (int) (rw * scaleFactor), (int) (rh * scaleFactor));
        double speed = p.vehicle != null ? Math.hypot(p.vehicle.vx, p.vehicle.vz) : 0;
        double range = 95 + Math.min(120, speed * 3);
        double pixelsPerMeter = rh / (range * 1.1);
        double cx = (x0 + x1) / 2.0, cy = y0 + rh * .62;
        double px = p.vehicle != null ? p.vehicle.x : p.x, pz = p.vehicle != null ? p.vehicle.z : p.z;
        double yaw = game.cameraYaw;
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0);
        GlStateManager.rotate((float) -yaw, 0, 0, 1);
        GlStateManager.scale(pixelsPerMeter, pixelsPerMeter, 1);
        GlStateManager.translate(-px, -pz, 0);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(mapTexture.getGlTextureId());
        GlStateManager.color(1, 1, 1, 1);
        double e = Gta8MapImage.EXTENT;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(0, 0); GL11.glVertex2d(-e, -e);
        GL11.glTexCoord2d(0, 1); GL11.glVertex2d(-e, e);
        GL11.glTexCoord2d(1, 1); GL11.glVertex2d(e, e);
        GL11.glTexCoord2d(1, 0); GL11.glVertex2d(e, -e);
        GL11.glEnd();
        GlStateManager.disableTexture2D();
        // Police search area.
        if (game.police.wanted > 0 && game.police.searching) {
            boolean red = (int) (game.time * 2) % 2 == 0;
            circle(game.police.searchX, game.police.searchZ, game.police.searchRadius(), red ? 0x40E03030 : 0x403060E0);
        }
        // GPS route to the mission marker or waypoint.
        double tx = 0, tz = 0;
        boolean target = false;
        if (game.missions.marker) { tx = game.missions.markerX; tz = game.missions.markerZ; target = true; }
        else if (game.waypoint) { tx = game.waypointX; tz = game.waypointZ; target = true; }
        if (target) {
            if (game.time - routeTime > 1) { route = world.route(px, pz, tx, tz); routeTime = game.time; }
            int routeColor = game.missions.marker ? 0xFFE8C020 : 0xFFB050E0;
            GL11.glLineWidth((float) Math.max(2, 5 * scaleFactor * .5));
            color(routeColor);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            GL11.glVertex2d(px, pz);
            if (route != null) for (int i = 1; i < route.size() - 1; i++) GL11.glVertex2d(route.get(i)[0], route.get(i)[1]);
            GL11.glVertex2d(tx, tz);
            GL11.glEnd();
            GL11.glLineWidth(1);
        }
        GlStateManager.popMatrix();
        // Blips drawn upright.
        double cos = Math.cos(Math.toRadians(yaw)), sin = Math.sin(Math.toRadians(yaw));
        for (Gta8World.Poi poi : world.pois) {
            int c = poi.type == Gta8World.Poi.GUN_SHOP ? 0xFFE08030 : poi.type == Gta8World.Poi.STORE || poi.type == Gta8World.Poi.GAS ? 0xFF70C070
                    : poi.type == Gta8World.Poi.HOSPITAL ? 0xFFE04040 : poi.type == Gta8World.Poi.POLICE ? 0xFF4080E0 : poi.type == Gta8World.Poi.SAFEHOUSE ? 0xFFF0D040
                    : poi.type == Gta8World.Poi.SPRAY ? 0xFFC060E0 : 0xFFE8E8E8;
            String label = poi.type == Gta8World.Poi.GUN_SHOP ? "G" : poi.type == Gta8World.Poi.STORE || poi.type == Gta8World.Poi.GAS ? "$" : poi.type == Gta8World.Poi.HOSPITAL ? "H"
                    : poi.type == Gta8World.Poi.POLICE ? "P" : poi.type == Gta8World.Poi.SAFEHOUSE ? "S" : poi.type == Gta8World.Poi.SPRAY ? "R" : "J";
            blip(poi.x - px, poi.z - pz, cos, sin, pixelsPerMeter, cx, cy, x0, y0, x1, y1, c, label, true);
        }
        for (Gta8Ped o : game.peds) {
            if (o.dead || o == p) continue;
            boolean cop = o.kind == Gta8Ped.Kind.COP && game.police.wanted > 0 && o.vehicle == null;
            boolean hostile = (o.kind == Gta8Ped.Kind.GANG || o.kind == Gta8Ped.Kind.TARGET) && (o.provoked || o.kind == Gta8Ped.Kind.TARGET);
            if (!cop && !hostile) continue;
            int c = cop ? ((int) (game.time * 4) % 2 == 0 ? 0xFFE03030 : 0xFF3060E0) : 0xFFE03030;
            blip(o.x - px, o.z - pz, cos, sin, pixelsPerMeter, cx, cy, x0, y0, x1, y1, c, null, false);
        }
        for (Gta8Vehicle v : game.vehicles) if (v.siren) blip(v.x - px, v.z - pz, cos, sin, pixelsPerMeter, cx, cy, x0, y0, x1, y1, (int) (game.time * 4) % 2 == 0 ? 0xFFE03030 : 0xFF3060E0, null, false);
        if (game.police.heli != null) blip(game.police.heli.x - px, game.police.heli.z - pz, cos, sin, pixelsPerMeter, cx, cy, x0, y0, x1, y1, 0xFF3060E0, "^", true);
        if (target) blip(tx - px, tz - pz, cos, sin, pixelsPerMeter, cx, cy, x0, y0, x1, y1, game.missions.marker ? 0xFFF0C820 : 0xFFB050E0, null, true);
        for (Gta8Game.Pickup pk : game.pickups) if (pk.source != null && pk.source.respawnAt <= game.time && Math.hypot(pk.x - px, pk.z - pz) < range)
            blip(pk.x - px, pk.z - pz, cos, sin, pixelsPerMeter, cx, cy, x0, y0, x1, y1, pk.type == Gta8World.Pickup.HEALTH ? 0xFF40D060 : pk.type == Gta8World.Pickup.ARMOR ? 0xFF4090F0 : 0xFFD0D0D0, null, false);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        // Player arrow.
        double facing = Math.toRadians((p.vehicle != null ? p.vehicle.yaw : p.yaw) - yaw);
        GlStateManager.disableTexture2D();
        GL11.glPushMatrix();
        GL11.glTranslated(cx, cy, 0);
        GL11.glRotated(Math.toDegrees(facing), 0, 0, 1);
        color(0xFF000000);
        GL11.glBegin(GL11.GL_TRIANGLES); GL11.glVertex2d(0, -6.5); GL11.glVertex2d(-5, 5); GL11.glVertex2d(5, 5); GL11.glEnd();
        color(0xFFFFFFFF);
        GL11.glBegin(GL11.GL_TRIANGLES); GL11.glVertex2d(0, -5); GL11.glVertex2d(-3.6, 3.6); GL11.glVertex2d(3.6, 3.6); GL11.glEnd();
        GL11.glPopMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        // North marker on the rim.
        double nx = cx - sin * rh * .45, ny = cy - cos * rh * .45;
        nx = Math.max(x0 + 5, Math.min(x1 - 9, nx)); ny = Math.max(y0 + 3, Math.min(y1 - 11, ny));
        shadowText("N", nx, ny, WHITE);
        // Health and armour below the radar.
        double hp = Math.max(0, p.health / p.maxHealth), armor = Math.max(0, p.armor / 100);
        int half = (rw - 4) / 2;
        rect(x0, y1 + 5, x0 + half, y1 + 10, 0xA0183018);
        rect(x0, y1 + 5, x0 + half * hp, y1 + 10, hp < .25 ? RED : GREEN);
        rect(x0 + half + 4, y1 + 5, x1, y1 + 10, 0xA0182038);
        rect(x0 + half + 4, y1 + 5, x0 + half + 4 + (x1 - x0 - half - 4) * armor, y1 + 10, BLUE);
    }
    private void blip(double dx, double dz, double cos, double sin, double scale, double cx, double cy, int x0, int y0, int x1, int y1, int color, String label, boolean clamp) {
        double sx = (dx * cos + dz * sin) * scale, sy = (-dx * sin + dz * cos) * scale;
        double bx = cx + sx, by = cy + sy;
        boolean outside = bx < x0 + 4 || bx > x1 - 4 || by < y0 + 4 || by > y1 - 4;
        if (outside && !clamp) return;
        bx = Math.max(x0 + 4, Math.min(x1 - 4, bx)); by = Math.max(y0 + 4, Math.min(y1 - 4, by));
        double r = label != null ? 4.5 : 2.6;
        rect(bx - r - .8, by - r - .8, bx + r + .8, by + r + .8, 0xC0000000);
        rect(bx - r, by - r, bx + r, by + r, color);
        if (label != null) { GlStateManager.pushMatrix(); GlStateManager.translate(bx - font.width(label) * .35, by - 3.4, 0); GlStateManager.scale(.7, .7, 1); font.draw(label, 0, 0, 0xFF101010); GlStateManager.popMatrix(); }
    }
    private void circle(double x, double z, double radius, int color) {
        color(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2d(x, z);
        for (int i = 0; i <= 48; i++) { double a = i * Math.PI * 2 / 48; GL11.glVertex2d(x + Math.cos(a) * radius, z + Math.sin(a) * radius); }
        GL11.glEnd();
    }
    private static void color(int argb) { GL11.glColor4f((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f); }

    // ------------------------------------------------------------------ pause menu
    private int[] mapRect() { return new int[]{20, 40, width - 20, height - 40}; }
    private double mapScale() { int[] r = mapRect(); return Math.min(r[2] - r[0], r[3] - r[1]) / (Gta8MapImage.EXTENT * 2) * mapZoom; }
    private double[] screenToWorld(int mx, int my) {
        int[] r = mapRect();
        double s = mapScale();
        return new double[]{mapX + (mx - (r[0] + r[2]) / 2.0) / s, mapZ + (my - (r[1] + r[3]) / 2.0) / s};
    }
    private void pauseMenu(int mouseX, int mouseY) {
        rect(0, 0, width, height, 0xD0080A0E);
        int left = 20, top = 16, w = width - 40;
        String[] tabs = {"MAP", "JOBS", "STATS", "SETTINGS", "CONTROLS"};
        int tw = Math.min(110, (w - 10) / tabs.length);
        for (int i = 0; i < tabs.length; i++) {
            int x = left + i * (tw + 2);
            rect(x, top, x + tw, top + 18, i == tab ? 0xFFF0C83C : 0xC0202428);
            text(tabs[i], x + (tw - textWidth(tabs[i])) / 2, top + 5, i == tab ? 0xFF101010 : WHITE);
        }
        if (tab == 0) mapTab();
        else if (tab == 1) jobsTab(mouseX, mouseY);
        else if (tab == 2) statsTab();
        else if (tab == 3) settingsTab(mouseX, mouseY);
        else controlsTab();
        int by = height - 30;
        button(width - 250, by, width - 140, by + 20, "RESUME", mouseX, mouseY, true);
        button(width - 130, by, width - 20, by + 20, "SAVE & EXIT", mouseX, mouseY, false);
        text("LOS VIBES   " + game.weather.clock() + "   " + game.weather.type.title, 22, by + 6, MUTED);
    }
    private void button(int l, int t, int r, int b, String label, int mx, int my, boolean primary) {
        boolean hover = inside(mx, my, l, t, r, b);
        rect(l, t, r, b, primary ? (hover ? 0xFFF8DA70 : 0xFFF0C83C) : (hover ? 0xFF3A4048 : 0xFF2A2E34));
        text(label, (l + r - textWidth(label)) / 2, (t + b) / 2.0 - 4, primary ? 0xFF101010 : WHITE);
    }
    private void mapTab() {
        int[] r = mapRect();
        rect(r[0], r[1], r[2], r[3], 0xFF1A1C20);
        double scaleFactor = mc.displayWidth / (double) width;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (r[0] * scaleFactor), (int) ((height - r[3]) * scaleFactor), (int) ((r[2] - r[0]) * scaleFactor), (int) ((r[3] - r[1]) * scaleFactor));
        double s = mapScale(), cx = (r[0] + r[2]) / 2.0, cy = (r[1] + r[3]) / 2.0, e = Gta8MapImage.EXTENT;
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0);
        GlStateManager.scale(s, s, 1);
        GlStateManager.translate(-mapX, -mapZ, 0);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(mapTexture.getGlTextureId());
        GlStateManager.color(1, 1, 1, 1);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(0, 0); GL11.glVertex2d(-e, -e); GL11.glTexCoord2d(0, 1); GL11.glVertex2d(-e, e);
        GL11.glTexCoord2d(1, 1); GL11.glVertex2d(e, e); GL11.glTexCoord2d(1, 0); GL11.glVertex2d(e, -e);
        GL11.glEnd();
        GlStateManager.disableTexture2D();
        if (game.police.wanted > 0 && game.police.searching) circle(game.police.searchX, game.police.searchZ, game.police.searchRadius(), 0x40E03030);
        GlStateManager.popMatrix();
        GlStateManager.enableTexture2D();
        for (Gta8World.Poi poi : world.pois) mapLabel(poi.x, poi.z, poi.name, 0xFFE8E8E8);
        mapLabel(game.player.x, game.player.z, "YOU", 0xFFFFFFFF);
        if (game.waypoint) mapLabel(game.waypointX, game.waypointZ, "WAYPOINT", 0xFFC070F0);
        if (game.missions.marker) mapLabel(game.missions.markerX, game.missions.markerZ, "JOB", YELLOW);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        text("Drag to pan, scroll to zoom, click to set a waypoint, right-click to clear it.", r[0] + 6, r[3] + 4, MUTED);
    }
    private void mapLabel(double x, double z, String label, int color) {
        int[] r = mapRect();
        double s = mapScale(), sx = (r[0] + r[2]) / 2.0 + (x - mapX) * s, sy = (r[1] + r[3]) / 2.0 + (z - mapZ) * s;
        if (sx < r[0] || sx > r[2] || sy < r[1] || sy > r[3]) return;
        rect(sx - 3, sy - 3, sx + 3, sy + 3, 0xFF000000);
        rect(sx - 2, sy - 2, sx + 2, sy + 2, color);
        if (mapZoom > 1.4 || "YOU".equals(label)) shadowText(label, sx + 5, sy - 4, color);
    }
    private void jobsTab(int mx, int my) {
        int left = 30, top = 48;
        Gta8Missions m = game.missions;
        if (m.active != null) {
            text("CURRENT JOB: " + m.active.title, left, top, YELLOW);
            text(m.objective, left, top + 14, WHITE);
            button(left, top + 30, left + 140, top + 48, "CANCEL JOB", mx, my, false);
            top += 64;
        }
        List<Gta8Missions.Offer> offers = m.offers();
        for (int i = 0; i < offers.size(); i++) {
            Gta8Missions.Offer o = offers.get(i);
            int y = top + i * 58;
            rect(left - 6, y - 4, width - 30, y + 50, 0xC0181B20);
            text(o.title.toUpperCase(Locale.ROOT) + "   $" + o.reward, left, y, YELLOW);
            text(font.fit(o.description, width - 220), left, y + 14, MUTED);
            button(width - 170, y + 22, width - 40, y + 42, "ACCEPT", mx, my, true);
        }
    }
    private void jobsClick(int mx, int my) {
        int left = 30, top = 48;
        Gta8Missions m = game.missions;
        if (m.active != null) {
            if (inside(mx, my, left, top + 30, left + 140, top + 48)) { m.cancel(); return; }
            top += 64;
        }
        List<Gta8Missions.Offer> offers = m.offers();
        for (int i = 0; i < offers.size(); i++) {
            int y = top + i * 58;
            if (inside(mx, my, width - 170, y + 22, width - 40, y + 42)) {
                m.start(offers.get(i));
                paused = false; game.jobsOpen = false; capture(true); lastFrame = System.nanoTime();
                return;
            }
        }
    }
    private void statsTab() {
        Gta8Progress p = game.progress;
        String[][] rows = {
                {"Money", "$" + String.format(Locale.US, "%,d", p.money)}, {"Kills", String.valueOf(p.kills)}, {"Police officers killed", String.valueOf(p.copKills)},
                {"Deaths", String.valueOf(p.deaths)}, {"Arrests", String.valueOf(p.arrests)}, {"Shots fired", String.valueOf(p.shots)},
                {"Accuracy", p.shots == 0 ? "-" : (int) (100.0 * p.hits / p.shots) + "%"}, {"Headshots", String.valueOf(p.headshots)},
                {"Stores robbed", String.valueOf(p.robberies)}, {"Jobs completed", String.valueOf(p.missions)}, {"Vehicles stolen", String.valueOf(p.carsStolen)},
                {"Distance driven", String.format(Locale.ROOT, "%.1f km", p.distanceDriven / 1000)}, {"Distance on foot", String.format(Locale.ROOT, "%.1f km", p.distanceWalked / 1000)},
                {"Highest wanted level", p.maxWanted + " stars"}, {"Time played", (int) (p.playSeconds / 3600) + " h " + (int) (p.playSeconds / 60 % 60) + " min"}};
        int left = 40, top = 50, col = Math.min(360, width / 2);
        for (int i = 0; i < rows.length; i++) {
            int x = left + (i / 8) * col, y = top + (i % 8) * 18;
            text(rows[i][0], x, y, MUTED);
            text(rows[i][1], x + col - 130, y, WHITE);
        }
        if (p.error() != null) text(p.error(), left, top + 160, RED);
    }
    private String[] settingRows() {
        return new String[]{"Graphics: " + module.graphics.getValue(), "Render scale: " + module.renderScale.getInt() + "%", "Traffic density: " + module.density.getValue(),
                "Bloom: " + (module.bloom.isEnabled() ? "On" : "Off"), "FXAA: " + (module.antialiasing.isEnabled() ? "On" : "Off"),
                "Game audio: " + (module.sound.isEnabled() ? "On" : "Off"), "Camera: " + (game.firstPerson ? "First person" : "Third person"),
                "Weather: " + game.weather.type.title + " (click to change)"};
    }
    private void settingsTab(int mx, int my) {
        String[] rows = settingRows();
        for (int i = 0; i < rows.length; i++) {
            int y = 50 + i * 24;
            boolean hover = inside(mx, my, 40, y, 360, y + 20);
            rect(40, y, 360, y + 20, hover ? 0xFF3A4048 : 0xC0202428);
            text(rows[i], 48, y + 6, WHITE);
        }
        text(String.format(Locale.ROOT, "Frame time %.1f ms   GPU: %s", frameAverage, GL11.glGetString(GL11.GL_RENDERER)), 40, 50 + rows.length * 24 + 8, MUTED);
    }
    private void settingsClick(int mx, int my) {
        String[] rows = settingRows();
        for (int i = 0; i < rows.length; i++) {
            int y = 50 + i * 24;
            if (!inside(mx, my, 40, y, 360, y + 20)) continue;
            switch (i) {
                case 0: cycle(module.graphics); break;
                case 1: module.renderScale.setValue(module.renderScale.getDouble() >= 150 ? 50.0 : module.renderScale.getDouble() + 25); break;
                case 2: cycle(module.density); break;
                case 3: module.bloom.toggle(); break;
                case 4: module.antialiasing.toggle(); break;
                case 5: module.sound.toggle(); break;
                case 6: game.firstPerson = !game.firstPerson; break;
                default: {
                    Gta8Weather.Type[] types = Gta8Weather.Type.values();
                    game.weather.set(types[(game.weather.type.ordinal() + 1) % types.length]);
                }
            }
            if (Vibe.getInstance() != null && Vibe.getInstance().getConfig() != null) Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
        }
    }
    private static void cycle(dev.vibe.setting.ModeSetting setting) {
        List<String> modes = setting.getModes();
        setting.setValue(modes.get((modes.indexOf(setting.getValue()) + 1) % modes.size()));
    }
    private void controlsTab() {
        GameSettings s = mc.gameSettings;
        String[][] rows = {
                {key(s.keyBindForward) + " " + key(s.keyBindLeft) + " " + key(s.keyBindBack) + " " + key(s.keyBindRight), "Move / drive"},
                {"Mouse", "Look and aim"}, {key(s.keyBindUseItem), "Aim"}, {key(s.keyBindAttack), "Shoot / punch"},
                {key(s.keyBindSprint), "Sprint"}, {key(s.keyBindSneak), "Crouch"}, {key(s.keyBindJump), "Jump / handbrake"},
                {"F", "Enter or leave a vehicle"}, {"E", "Interact (shops, safehouse, job board)"}, {"H", "Horn"}, {"R", "Reload"},
                {"1-6 / wheel", "Weapons"}, {key(s.keyBindTogglePerspective), "First / third person"}, {"M", "Map"}, {"J", "Job board"}, {"F1", "Hide HUD"}, {"F3", "Performance overlay"}};
        for (int i = 0; i < rows.length; i++) {
            int x = 40 + (i / 9) * Math.min(360, width / 2), y = 50 + (i % 9) * 18;
            text(rows[i][0], x, y, YELLOW);
            text(rows[i][1], x + 110, y, WHITE);
        }
    }
    private static String key(KeyBinding b) { return GameSettings.getKeyDisplayString(b.getKeyCode()); }

    // ------------------------------------------------------------------ shops
    private boolean gunShop() { return game.shop != null && game.shop.type == Gta8World.Poi.GUN_SHOP; }
    private void shop(int mx, int my) {
        capture(false);
        rect(0, 0, width, height, 0x90000000);
        int w = Math.min(460, width - 40), left = (width - w) / 2, top = 40;
        rect(left, top, left + w, height - 40, 0xE0121418);
        rect(left, top, left + w, top + 3, gunShop() ? 0xFFE08030 : GREEN);
        shadowText(game.shop.name, left + 12, top + 12, WHITE);
        rightText("$" + String.format(Locale.US, "%,d", game.progress.money), left + w - 12, top + 12, 0xFF8FD48F);
        int y = top + 34;
        if (gunShop()) {
            for (Gta8Weapon weapon : Gta8Weapon.values()) {
                if (weapon == Gta8Weapon.FISTS) continue;
                boolean owned = game.progress.owns(weapon);
                int cost = owned ? weapon.ammoBox() * weapon.ammoPrice : weapon.price;
                String label = owned ? weapon.title + " ammo (" + weapon.ammoBox() + " rounds)" : weapon.title;
                row(left, y, w, label, "$" + cost, mx, my, game.progress.money >= cost);
                y += 26;
            }
            row(left, y, w, "Body armour", "$500", mx, my, game.progress.money >= 500);
        } else {
            row(left, y, w, "Snack (+35 health)", "$6", mx, my, game.progress.money >= 6);
        }
        button(left + w - 120, height - 70, left + w - 12, height - 50, "CLOSE", mx, my, false);
        text("Aim a weapon at the clerk to rob the till instead.", left + 12, height - 64, MUTED);
    }
    private void row(int left, int y, int w, String label, String price, int mx, int my, boolean affordable) {
        boolean hover = inside(mx, my, left + 8, y, left + w - 8, y + 22);
        rect(left + 8, y, left + w - 8, y + 22, hover ? 0xFF2E343C : 0xFF1E2228);
        text(label, left + 16, y + 7, affordable ? WHITE : MUTED);
        text(price, left + w - 16 - textWidth(price), y + 7, affordable ? 0xFF8FD48F : RED);
    }
    private void shopClick(int mx, int my) {
        int w = Math.min(460, width - 40), left = (width - w) / 2, top = 40;
        if (inside(mx, my, left + w - 120, height - 70, left + w - 12, height - 50)) { game.shop = null; capture(true); lastFrame = System.nanoTime(); return; }
        int y = top + 34;
        if (gunShop()) {
            for (Gta8Weapon weapon : Gta8Weapon.values()) {
                if (weapon == Gta8Weapon.FISTS) continue;
                if (inside(mx, my, left + 8, y, left + w - 8, y + 22)) { game.buyWeapon(weapon); return; }
                y += 26;
            }
            if (inside(mx, my, left + 8, y, left + w - 8, y + 22)) game.buyArmor();
        } else if (inside(mx, my, left + 8, y, left + w - 8, y + 22)) game.buySnack();
    }
}
