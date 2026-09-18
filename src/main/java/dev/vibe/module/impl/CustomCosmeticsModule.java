package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

/** Client-side, third-person cosmetic effects independent from catalog cosmetics. */
public final class CustomCosmeticsModule extends Module {
    public static final class TimedPoint {
        public final Vec3 point;
        public final long created;
        TimedPoint(Vec3 point, long created) { this.point = point; this.created = created; }
    }

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final MultiSelectSetting effects = addSetting(new MultiSelectSetting("Cosmetics",
            Arrays.asList("China Hat", "Trail", "Jump Circle"), Arrays.asList("China Hat")));
    private final BooleanSetting onlyThirdPerson = addSetting(new BooleanSetting("Only Third Person", true));
    private final ColorSetting primaryColor = addSetting(new ColorSetting("Primary Color", 0xFFFF4FA3));
    private final ColorSetting secondaryColor = addSetting(new ColorSetting("Secondary Color", 0xFF6A5CFF));
    private final BooleanSetting rainbow = addSetting(new BooleanSetting("Rainbow", false));
    private final NumberSetting rainbowSpeed = addSetting(new NumberSetting("Rainbow Speed", 1.0D, 0.05D, 5.0D, 0.05D,
            () -> rainbow.isEnabled()));
    private final NumberSetting opacity = addSetting(new NumberSetting("Opacity", 0.85D, 0.05D, 1.0D, 0.05D));

    private final NumberSetting hatRadius = addSetting(new NumberSetting("Hat Radius", 0.62D, 0.15D, 1.5D, 0.01D,
            () -> active("China Hat")));
    private final NumberSetting hatHeight = addSetting(new NumberSetting("Hat Height", 0.48D, 0.05D, 1.5D, 0.01D,
            () -> active("China Hat")));
    private final NumberSetting hatSegments = addSetting(new NumberSetting("Hat Detail", 48.0D, 12.0D, 128.0D, 4.0D,
            () -> active("China Hat")));
    private final BooleanSetting followHeadPitch = addSetting(new BooleanSetting("Hat Follow Head Pitch", true,
            () -> active("China Hat")));
    private final BooleanSetting hatFill = addSetting(new BooleanSetting("Hat Fill", true, () -> active("China Hat")));
    private final BooleanSetting hatOutline = addSetting(new BooleanSetting("Hat Outline", true, () -> active("China Hat")));

    private final ModeSetting trailMode = addSetting(new ModeSetting("Trail Mode", "Ribbon", () -> active("Trail"),
            "Line", "Ribbon", "Dots"));
    private final NumberSetting trailLength = addSetting(new NumberSetting("Trail Length", 1.5D, 0.2D, 6.0D, 0.1D,
            () -> active("Trail")));
    private final NumberSetting trailWidth = addSetting(new NumberSetting("Trail Width", 2.0D, 0.5D, 8.0D, 0.1D,
            () -> active("Trail")));
    private final BooleanSetting trailFade = addSetting(new BooleanSetting("Trail Fade", true, () -> active("Trail")));

    private final ModeSetting jumpMode = addSetting(new ModeSetting("Jump Circle Mode", "Pulse", () -> active("Jump Circle"),
            "Ring", "Pulse", "Star", "Flower", "Spiral"));
    private final NumberSetting jumpRadius = addSetting(new NumberSetting("Jump Radius", 1.1D, 0.2D, 5.0D, 0.05D,
            () -> active("Jump Circle")));
    private final NumberSetting jumpDuration = addSetting(new NumberSetting("Jump Duration", 900.0D, 150.0D, 4000.0D, 50.0D,
            () -> active("Jump Circle")));
    private final NumberSetting jumpDetail = addSetting(new NumberSetting("Jump Detail", 72.0D, 16.0D, 160.0D, 4.0D,
            () -> active("Jump Circle")));
    private final NumberSetting jumpLineWidth = addSetting(new NumberSetting("Jump Line Width", 2.0D, 0.5D, 6.0D, 0.1D,
            () -> active("Jump Circle")));
    private final BooleanSetting jumpFill = addSetting(new BooleanSetting("Jump Fill", true, () -> active("Jump Circle")));
    private final BooleanSetting jumpFade = addSetting(new BooleanSetting("Jump Fade", true, () -> active("Jump Circle")));

    private final Deque<TimedPoint> trail = new ArrayDeque<TimedPoint>();
    private final Deque<TimedPoint> circles = new ArrayDeque<TimedPoint>();
    private boolean wasOnGround;

    public CustomCosmeticsModule() {
        super("CustomCosmetics", "China hat, player trail and detailed jump circles", Category.VISUAL, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null) { clear(); return; }
        long now = System.currentTimeMillis();
        if (active("Trail")) {
            Vec3 point = new Vec3(minecraft.thePlayer.posX, minecraft.thePlayer.posY + 0.08D, minecraft.thePlayer.posZ);
            TimedPoint newest = trail.peekLast();
            if (newest == null || newest.point.squareDistanceTo(point) > 0.0016D) trail.addLast(new TimedPoint(point, now));
        } else trail.clear();
        if (active("Jump Circle") && wasOnGround && !minecraft.thePlayer.onGround && minecraft.thePlayer.motionY > 0.05D) {
            circles.addLast(new TimedPoint(new Vec3(minecraft.thePlayer.posX, minecraft.thePlayer.posY + 0.03D,
                    minecraft.thePlayer.posZ), now));
        }
        wasOnGround = minecraft.thePlayer.onGround;
        long trailAge = (long) (trailLength.getDouble() * 1000.0D);
        while (!trail.isEmpty() && now - trail.peekFirst().created > trailAge) trail.removeFirst();
        while (!circles.isEmpty() && now - circles.peekFirst().created > jumpDuration.getInt()) circles.removeFirst();
    }

    @Override protected void onDisable() { clear(); }
    private void clear() { trail.clear(); circles.clear(); wasOnGround = false; }
    private boolean active(String effect) { return effects.isSelected(effect); }
    public boolean isActive(String effect) { return active(effect); }
    public MultiSelectSetting getEffects() { return effects; }
    public BooleanSetting getOnlyThirdPerson() { return onlyThirdPerson; }
    public ColorSetting getPrimaryColor() { return primaryColor; }
    public ColorSetting getSecondaryColor() { return secondaryColor; }
    public BooleanSetting getRainbow() { return rainbow; }
    public NumberSetting getRainbowSpeed() { return rainbowSpeed; }
    public NumberSetting getOpacity() { return opacity; }
    public NumberSetting getHatRadius() { return hatRadius; }
    public NumberSetting getHatHeight() { return hatHeight; }
    public NumberSetting getHatSegments() { return hatSegments; }
    public BooleanSetting getFollowHeadPitch() { return followHeadPitch; }
    public BooleanSetting getHatFill() { return hatFill; }
    public BooleanSetting getHatOutline() { return hatOutline; }
    public ModeSetting getTrailMode() { return trailMode; }
    public NumberSetting getTrailWidth() { return trailWidth; }
    public BooleanSetting getTrailFade() { return trailFade; }
    public ModeSetting getJumpMode() { return jumpMode; }
    public NumberSetting getJumpRadius() { return jumpRadius; }
    public NumberSetting getJumpDuration() { return jumpDuration; }
    public NumberSetting getJumpDetail() { return jumpDetail; }
    public NumberSetting getJumpLineWidth() { return jumpLineWidth; }
    public BooleanSetting getJumpFill() { return jumpFill; }
    public BooleanSetting getJumpFade() { return jumpFade; }
    public Deque<TimedPoint> getTrail() { return trail; }
    public Deque<TimedPoint> getCircles() { return circles; }
}
