package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.SkeletalModule;
import dev.vibe.module.impl.TargetsModule;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

/** Captures player-model limb angles and uses them for a lightweight skeleton. */
public final class SkeletalRenderer {

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Map<UUID, Pose> poses = new HashMap<UUID, Pose>();
    private final java.util.Set<net.minecraft.client.renderer.entity.RenderPlayer> installed =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<net.minecraft.client.renderer.entity.RenderPlayer, Boolean>());

    public void installLayers() {
        if (minecraft.getRenderManager() == null) return;
        for (net.minecraft.client.renderer.entity.RenderPlayer renderer : minecraft.getRenderManager().getSkinMap().values()) {
            if (!installed.add(renderer)) continue;
            renderer.addLayer(new net.minecraft.client.renderer.entity.layers.LayerRenderer<net.minecraft.client.entity.AbstractClientPlayer>() {
                @Override public void doRenderLayer(net.minecraft.client.entity.AbstractClientPlayer player, float swing,
                        float amount, float partial, float age, float yaw, float pitch, float scale) {
                    SkeletalModule module = Vibe.getInstance().getModuleManager().getModule(SkeletalModule.class);
                    if (module == null || !module.isEnabled() || player.worldObj != minecraft.theWorld) return;
                    ModelPlayer model = renderer.getMainModel();
                    poses.put(player.getUniqueID(), new Pose(model.bipedHead,
                            model.bipedRightArm, model.bipedLeftArm, model.bipedRightLeg, model.bipedLeftLeg));
                    Pose pose = poses.get(player.getUniqueID());
                    GL11.glPushMatrix();
                    if (model.isSneak) GL11.glTranslatef(0, .2F, 0);
                    GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, pose.matrix);
                    GL11.glPopMatrix();
                }
                @Override public boolean shouldCombineTextures() { return false; }
            });
        }
    }

    public void render(RenderWorldLastEvent event) {
        SkeletalModule module = Vibe.getInstance().getModuleManager().getModule(SkeletalModule.class);
        if (module == null || !module.isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null) {
            poses.clear();
            return;
        }
        WorldRenderUtils.begin(module.getThroughWalls().isEnabled());
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(module.getLineWidth().getFloat());
        try {
            for (Object object : minecraft.theWorld.playerEntities) {
                if (!(object instanceof EntityPlayer)) {
                    continue;
                }
                EntityPlayer player = (EntityPlayer) object;
                Pose pose = poses.get(player.getUniqueID());
                if (pose == null || player.isDead) {
                    continue;
                }
                if (player == minecraft.thePlayer) {
                    continue;
                }
                if (module.getOnlyTargets().isEnabled()) {
                    TargetsModule targets = Vibe.getInstance().getModuleManager().getModule(TargetsModule.class);
                    if (targets == null || !targets.canTarget(player)) {
                        continue;
                    }
                }
                if (module.getDepthBackplate().isEnabled()) {
                    GL11.glLineWidth(module.getLineWidth().getFloat() + 2.0F);
                    drawPlayer(player, pose, module, true);
                }
                GL11.glLineWidth(module.getLineWidth().getFloat());
                drawPlayer(player, pose, module, false);
            }
        } finally {
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            WorldRenderUtils.end(module.getThroughWalls().isEnabled());
            poses.clear();
        }
    }

    private void drawPlayer(EntityPlayer player, Pose pose, SkeletalModule module, boolean backplate) {
        WorldRenderUtils.color(backplate ? 0xB8000000 : color(module, player.getEntityId() * 0.11F));
        GL11.glPushMatrix();
        try {
            // Includes the renderer's scale, body yaw, riding/death rotations,
            // and sneak offset. Model parts are siblings, not body children.
            GL11.glLoadMatrix(pose.matrix);
            double[] rightHip = pivot(pose.rightLeg), leftHip = pivot(pose.leftLeg);
            double[] rightShoulder = pivot(pose.rightArm), leftShoulder = pivot(pose.leftArm);
            line(rightHip, leftHip);
            line(rightShoulder, leftShoulder);
            line(midpoint(rightHip, leftHip), midpoint(rightShoulder, leftShoulder));
            line(midpoint(rightShoulder, leftShoulder), pivot(pose.head));
            limb(pose.rightLeg, 12);
            limb(pose.leftLeg, 12);
            limb(pose.rightArm, 10);
            limb(pose.leftArm, 10);
            limb(pose.head, -6);
        } finally { GL11.glPopMatrix(); }
    }

    private double[] pivot(Angle part) {
        return point(part, 0);
    }
    private double[] point(Angle part, double length) {
        // Trace the center of both standard and slim arms, rather than the
        // inner-edge pivot of their asymmetrical boxes.
        double x = part.cx / 16.0, y = length / 16.0, z = part.cz / 16.0;
        double nextY = y * Math.cos(part.x) - z * Math.sin(part.x);
        z = y * Math.sin(part.x) + z * Math.cos(part.x); y = nextY;
        double nextX = x * Math.cos(part.y) + z * Math.sin(part.y);
        z = -x * Math.sin(part.y) + z * Math.cos(part.y); x = nextX;
        nextX = x * Math.cos(part.z) - y * Math.sin(part.z);
        y = x * Math.sin(part.z) + y * Math.cos(part.z); x = nextX;
        return new double[] {part.px / 16.0 + part.ox + x, part.py / 16.0 + part.oy + y, part.pz / 16.0 + part.oz + z};
    }
    private double[] midpoint(double[] a, double[] b) {
        return new double[] {(a[0]+b[0])*.5, (a[1]+b[1])*.5, (a[2]+b[2])*.5};
    }
    private void line(double[] a, double[] b) { line(a[0], a[1], a[2], b[0], b[1], b[2]); }
    private void limb(Angle part, double pixels) {
        line(point(part, 0), point(part, pixels));
    }
    private void line(double x1, double y1, double z1, double x2, double y2, double z2) {
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glEnd();
    }

    private int color(SkeletalModule module, float phase) {
        if (!module.getRainbow().isEnabled()) {
            return module.getColor().getArgb();
        }
        float hue = (float) ((System.currentTimeMillis() % 8000L) / 8000.0D + phase) % 1.0F;
        return Color.HSBtoRGB(hue, 0.75F, 1.0F) | 0xFF000000;
    }

    private static final class Pose {
        private final java.nio.FloatBuffer matrix = org.lwjgl.BufferUtils.createFloatBuffer(16);
        private final Angle head;
        private final Angle rightArm;
        private final Angle leftArm;
        private final Angle rightLeg;
        private final Angle leftLeg;

        private Pose(ModelRenderer head, ModelRenderer rightArm, ModelRenderer leftArm,
                     ModelRenderer rightLeg, ModelRenderer leftLeg) {
            this.head = new Angle(head);
            this.rightArm = new Angle(rightArm);
            this.leftArm = new Angle(leftArm);
            this.rightLeg = new Angle(rightLeg);
            this.leftLeg = new Angle(leftLeg);
        }
    }

    private static final class Angle {
        private final float px, py, pz, ox, oy, oz;
        private final float cx, cz;
        private final float x;
        private final float y;
        private final float z;

        private Angle(ModelRenderer model) {
            net.minecraft.client.model.ModelBox box = model.cubeList.isEmpty() ? null : model.cubeList.get(0);
            cx = box == null ? 0 : (box.posX1 + box.posX2) * .5F;
            cz = box == null ? 0 : (box.posZ1 + box.posZ2) * .5F;
            px = model.rotationPointX; py = model.rotationPointY; pz = model.rotationPointZ;
            ox = model.offsetX; oy = model.offsetY; oz = model.offsetZ;
            x = model.rotateAngleX;
            y = model.rotateAngleY;
            z = model.rotateAngleZ;
        }
    }
}
