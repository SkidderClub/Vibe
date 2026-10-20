package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.camera.FpvFlight;
import dev.vibe.module.impl.movement.FreecamModule;
import dev.vibe.ui.GuiRenderState;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Goggle-style OSD driven by camera telemetry, not fabricated battery/radio readings. */
public final class FreecamHudRenderer {
    private static final int WHITE = 0xFFE9FFF5, DIM = 0xB0D5E9DF, AMBER = 0xFFFFD280;
    private static final String[] CARDINALS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private final Minecraft minecraft = Minecraft.getMinecraft();

    public void render() {
        FreecamModule module = module();
        if (module == null || !module.shouldDrawDroneHud()) return;
        ScaledResolution resolution = new ScaledResolution(minecraft);
        draw(module.getFlight(), module.getFlightMode().getValue(), module.getCameraTilt(),
                module.getDroneFov(), module.hasCollisions(), resolution.getScaledWidth(), resolution.getScaledHeight());
    }

    private FreecamModule module() {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null
                : vibe.getModuleManager().getModule(FreecamModule.class);
    }

    public void draw(FpvFlight flight, String mode, double cameraTilt, int width, int height) {
        draw(flight, mode, cameraTilt, 105, false, width, height);
    }

    public void draw(FpvFlight flight, String mode, double cameraTilt, double fov, boolean collisions,
                     int width, int height) {
        // Preserve legibility on large displays, while respecting Minecraft's GUI scale.
        float scale = Math.max(1, Math.min(width / 640F, height / 360F));
        int w = Math.round(width / scale), h = Math.round(height / scale);
        boolean compact = w < 380 || h < 240;
        int margin = 14, cx = w / 2, cy = h / 2;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT);
        GlStateManager.pushMatrix();
        try {
            GuiRenderState.prepare(false);
            GlStateManager.scale(scale, scale, 1);
            corners(w, h);
            text(mode.toUpperCase(Locale.ROOT), margin, 20, AMBER);
            String state = flight.isGrounded() ? "LANDED" : flight.isCrashing() ? "IMPACT" : "ARMED";
            text(compact && !flight.isGrounded() && !flight.isCrashing()
                    ? "C " + (collisions ? "ON" : "OFF") : state, margin, 32, flight.isCrashing() ? AMBER : WHITE);
            if (!compact) text("COLL " + (collisions ? "ON" : "OFF"), margin, 44, DIM);
            textRight(format("FLT %02d:%02d", (int) flight.flightTime / 60, (int) flight.flightTime % 60), w - margin, 20, WHITE);
            textRight(format("CAM +%.0f\u00b0", cameraTilt), w - margin, 32, DIM);
            compass(flight, cx, Math.min(90, w / 2 - 82));
            if (!compact) home(flight, cameraTilt, cx, 72);

            int extent = compact ? 24 : 46;
            tape(margin + 39, cy, extent, flight.groundSpeed() * 3.6, true, "GSPD", "km/h");
            tape(w - margin - 39, cy, extent, flight.relativeAltitude(), false, "ALT", "m REL");
            attitude(flight, cameraTilt, cx, cy, compact ? 20 : 43, !compact);
            flightPath(flight, cameraTilt, fov, h, cx, cy, compact ? 20 : 46);
            marker(cx, cy);
            if (!compact) centred(format("P %+.0f\u00b0  R %+.0f\u00b0", flight.pitch(), flight.roll()), cx, cy + 63, DIM);

            int bottom = h - 52;
            text(format("THR %3.0f%%", flight.throttle * 100), margin, bottom, WHITE);
            for (int i = 0; i < 10; i++) Gui.drawRect(margin + i * 6, bottom + 12,
                    margin + i * 6 + 4, bottom + 16, flight.throttle * 10 > i ? AMBER : 0x604B5B55);
            text(format("V/S %+.1f m/s", flight.velocityY), margin, bottom + 25, WHITE);
            if (compact) {
                home(flight, cameraTilt, cx, bottom + 4);
                centred("TRIP " + metres(flight.distanceTravelled), cx, bottom + 22, DIM);
            } else {
                centred("TRIP " + metres(flight.distanceTravelled), cx, bottom, WHITE);
                centred(format("MAX %.0f km/h", flight.maxSpeed * 3.6), cx, bottom + 14, DIM);
            }
            textRight(format("X %+.1f", flight.x), w - margin, bottom, WHITE);
            textRight(format("Z %+.1f", flight.z), w - margin, bottom + 12, WHITE);
            if (!compact) textRight(format("ABS %.1f m", flight.y), w - margin, bottom + 25, DIM);
        } finally {
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
            // Keep the driver and GlStateManager caches in agreement for the next GUI pass.
            GuiRenderState.prepare(false);
        }
    }

    private void compass(FpvFlight flight, int cx, int halfWidth) {
        double heading = FpvFlight.compassHeading(flight.yaw());
        String cardinal = CARDINALS[((int) Math.round(heading / 45)) % 8];
        centred(format("%03d\u00b0 %s", (int) Math.round(heading) % 360, cardinal), cx, 16, WHITE);
        Gui.drawRect(cx - halfWidth, 41, cx + halfWidth, 42, DIM);
        for (int tick = -5; tick <= 5; tick++) {
            double value = Math.floor(heading / 15) * 15 + tick * 15;
            double delta = value - heading;
            int x = cx + (int) Math.round(delta * 1.25);
            if (Math.abs(x - cx) > halfWidth - 8) continue;
            int bearing = ((int) value % 360 + 360) % 360;
            boolean cardinalTick = bearing % 90 == 0;
            Gui.drawRect(x, bearing % 30 == 0 ? 34 : 37, x + 1, 42, WHITE);
            if (cardinalTick) centred(CARDINALS[bearing / 45], x, 46, WHITE);
            else if (bearing % 30 == 0) centred(Integer.toString(bearing), x, 46, DIM);
        }
        Gui.drawRect(cx - 2, 29, cx + 3, 30, AMBER);
        Gui.drawRect(cx - 1, 30, cx + 2, 32, AMBER);
        Gui.drawRect(cx, 32, cx + 1, 34, AMBER);
    }

    private void home(FpvFlight flight, double cameraTilt, int cx, int y) {
        String label = "H " + metres(flight.homeDistance());
        int textX = cx - minecraft.fontRendererObj.getStringWidth(label) / 2 + 5;
        text(label, textX, y - 4, WHITE);
        double angle = flight.homeDistance() < .5 ? 0
                : flight.homeBearing() - FpvFlight.compassHeading(flight.cameraYaw(cameraTilt));
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(textX - 11, y, 0);
            GlStateManager.rotate((float) angle, 0, 0, 1);
            lines(AMBER);
            line(0, -5, -4, 3); line(-4, 3, 0, 1); line(0, 1, 4, 3); line(4, 3, 0, -5);
            endLines();
        } finally { GlStateManager.popMatrix(); }
    }

    private void tape(int x, int cy, int extent, double value, boolean left, String title, String unit) {
        centred(title, x + (left ? -17 : 17), cy - extent - 17, WHITE);
        centred(unit, x + (left ? -17 : 17), cy + extent + 9, DIM);
        Gui.drawRect(x, cy - extent, x + 1, cy + extent, DIM);
        double step = 5, pixelsPerUnit = 2;
        double base = Math.floor(value / step) * step;
        for (int i = -6; i <= 6; i++) {
            double tick = base + i * step;
            if (left && tick < 0) continue;
            int y = cy - (int) Math.round((tick - value) * pixelsPerUnit);
            if (Math.abs(y - cy) > extent || Math.abs(y - cy) < 9) continue;
            boolean major = ((int) tick) % 10 == 0;
            Gui.drawRect(left ? x - (major ? 6 : 3) : x, y, left ? x + 1 : x + (major ? 7 : 4), y + 1, DIM);
            if (major) {
                String label = Math.abs(tick) >= 1000 ? format("%.0fk", tick / 1000) : format("%.0f", tick);
                if (left) textRight(label, x - 9, y - 3, DIM); else text(label, x + 9, y - 3, DIM);
            }
        }
        int l = left ? x - 45 : x - 4, r = left ? x + 5 : x + 46;
        Gui.drawRect(l, cy - 7, r, cy + 7, 0xB00B1512);
        Gui.drawRect(l, cy - 7, r, cy - 6, WHITE); Gui.drawRect(l, cy + 6, r, cy + 7, WHITE);
        String reading = Math.abs(value) >= 1000 ? format("%.1fk", value / 1000) : format("%.1f", value);
        centred(reading, (l + r) / 2, cy - 4, WHITE);
    }

    private void attitude(FpvFlight flight, double cameraTilt, int cx, int cy, int extent, boolean labels) {
        float[] camera = flight.viewMatrix(cameraTilt);
        double angle = Math.toDegrees(Math.atan2(camera[4], camera[5]));
        double pitch = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, camera[6]))));
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(cx, cy, 0);
            GlStateManager.rotate((float) angle, 0, 0, 1);
            lines(DIM);
            for (int degrees = -90; degrees <= 90; degrees += 10) {
                double y = (degrees - pitch) * 1.25;
                if (Math.abs(y) > extent) continue;
                int length = degrees == 0 ? 38 : 22;
                if (degrees < 0) {
                    for (int x = 8; x < length; x += 6) {
                        line(x, y, x + 3, y); line(-x, y, -x - 3, y);
                    }
                } else { line(-length, y, -8, y); line(8, y, length, y); }
                line(-length, y, -length, y + (degrees < 0 ? -3 : 3));
                line(length, y, length, y + (degrees < 0 ? -3 : 3));
            }
            endLines();
            if (labels) for (int degrees = -90; degrees <= 90; degrees += 10) {
                int y = (int) Math.round((degrees - pitch) * 1.25);
                if (degrees != 0 && Math.abs(y) <= extent - 10)
                    text(Integer.toString(Math.abs(degrees)), 27, y - 3, DIM);
            }
        } finally { GlStateManager.popMatrix(); }
        int radius = extent + 14;
        lines(DIM);
        for (int degrees = -60; degrees < 60; degrees += 4) {
            double a = Math.toRadians(degrees), b = Math.toRadians(degrees + 4);
            line(cx + Math.sin(a) * radius, cy - Math.cos(a) * radius,
                    cx + Math.sin(b) * radius, cy - Math.cos(b) * radius);
        }
        for (int degrees = -60; degrees <= 60; degrees += 15) {
            double a = Math.toRadians(degrees);
            int tick = degrees % 30 == 0 ? 5 : 3;
            line(cx + Math.sin(a) * radius, cy - Math.cos(a) * radius,
                    cx + Math.sin(a) * (radius + tick), cy - Math.cos(a) * (radius + tick));
        }
        endLines();
        double roll = Math.max(-60, Math.min(60, flight.roll())), a = Math.toRadians(roll);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(cx + Math.sin(a) * (radius - 4), cy - Math.cos(a) * (radius - 4), 0);
            GlStateManager.rotate((float) roll, 0, 0, 1);
            lines(AMBER); line(0, -3, -3, 3); line(-3, 3, 3, 3); line(3, 3, 0, -3); endLines();
        } finally { GlStateManager.popMatrix(); }
        if (Math.abs(flight.roll()) > 100) centred("INVERTED", cx, cy + extent + 4, AMBER);
    }

    private void flightPath(FpvFlight flight, double tilt, double fov, int height, int cx, int cy, int extent) {
        if (flight.speed() < .7) return;
        float[] view = flight.viewMatrix(tilt);
        double x = view[0] * flight.velocityX + view[4] * flight.velocityY + view[8] * flight.velocityZ;
        double y = view[1] * flight.velocityX + view[5] * flight.velocityY + view[9] * flight.velocityZ;
        double z = view[2] * flight.velocityX + view[6] * flight.velocityY + view[10] * flight.velocityZ;
        if (z >= -.1) return;
        double focal = height * .5 / Math.tan(Math.toRadians(fov) * .5);
        double dx = -x / z * focal, dy = y / z * focal;
        if (Math.abs(dx) > 65 || Math.abs(dy) > extent) return;
        x = cx + dx; y = cy + dy;
        lines(AMBER);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6, b = (i + 1) * Math.PI / 6;
            line(x + Math.cos(a) * 3, y + Math.sin(a) * 3, x + Math.cos(b) * 3, y + Math.sin(b) * 3);
        }
        line(x - 8, y, x - 3, y); line(x + 3, y, x + 8, y); line(x, y - 7, x, y - 3);
        endLines();
    }

    private void marker(int cx, int cy) {
        lines(WHITE);
        line(cx - 13, cy, cx - 5, cy); line(cx - 5, cy, cx, cy + 4);
        line(cx, cy + 4, cx + 5, cy); line(cx + 5, cy, cx + 13, cy);
        endLines();
    }

    private void corners(int w, int h) {
        for (int x : new int[] {10, w - 10}) for (int y : new int[] {10, h - 10}) {
            int dx = x < w / 2 ? 9 : -9, dy = y < h / 2 ? 9 : -9;
            Gui.drawRect(Math.min(x, x + dx), y, Math.max(x, x + dx) + 1, y + 1, DIM);
            Gui.drawRect(x, Math.min(y, y + dy), x + 1, Math.max(y, y + dy) + 1, DIM);
        }
    }

    private static void lines(int color) {
        GlStateManager.disableTexture2D(); GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0); GL11.glLineWidth(1);
        GlStateManager.color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F,
                (color & 255) / 255F, (color >>> 24) / 255F);
        GL11.glBegin(GL11.GL_LINES);
    }
    private static void line(double x1, double y1, double x2, double y2) {
        GL11.glVertex2d(x1, y1); GL11.glVertex2d(x2, y2);
    }
    private static void endLines() {
        GL11.glEnd(); GlStateManager.enableTexture2D(); GlStateManager.color(1, 1, 1, 1);
    }
    private static String metres(double value) {
        return value >= 1000 ? format("%.2f km", value / 1000) : format("%.0f m", value);
    }
    private static String format(String value, Object... args) { return String.format(Locale.ROOT, value, args); }
    private void text(String value, int x, int y, int color) { minecraft.fontRendererObj.drawStringWithShadow(value, x, y, color); }
    private void textRight(String value, int right, int y, int color) {
        text(value, right - minecraft.fontRendererObj.getStringWidth(value), y, color);
    }
    private void centred(String value, int x, int y, int color) {
        text(value, x - minecraft.fontRendererObj.getStringWidth(value) / 2, y, color);
    }
}
