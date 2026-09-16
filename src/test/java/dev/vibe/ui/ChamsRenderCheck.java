package dev.vibe.ui;

import dev.vibe.module.impl.EspModule;
import dev.vibe.ui.effect.EffectProgram;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Offscreen pixel assertions use the same material passes and real LWJGL driver as the client. */
public final class ChamsRenderCheck {
    private static final int W = 384, H = 192;
    private static int texture;

    public static void main(String[] args) throws Exception {
        Path output = Paths.get("build/chams-render-check"); Files.createDirectories(output);
        Pbuffer buffer = new Pbuffer(W, H, new PixelFormat(8, 24, 8), null, null);
        buffer.makeCurrent();
        try {
            OpenGlHelper.initializeTextures();
            texture = GL11.glGenTextures();
            texture(255, 255, 255, 255);
            EspModule esp = new EspModule();
            EspModule.ChamsSettings hidden = esp.getInvisibleChams(), visible = esp.getVisibleChams();
            for (String mode : new String[] {"Flat", "Glow", "Metallic"}) {
                hidden.getMode().setValue(mode); visible.getMode().setValue(mode);
                hidden.getColor().setRgba(255, 30, 50, 255); visible.getColor().setRgba(20, 255, 80, 255);
                scene(); ChamsRenderer.draw(esp, false, 1, ChamsRenderCheck::quad);
                byte[] opaque = pixels();
                if (ChamsRenderer.hasShaderFailed()) throw new AssertionError("Chams shader failed to compile");
                for (int alpha : new int[] {0, 1, 64, 128, 255}) {
                    hidden.getColor().setRgba(255, 30, 50, alpha);
                    visible.getColor().setRgba(20, 255, 80, alpha);
                    scene(); ChamsRenderer.draw(esp, false, 1, ChamsRenderCheck::quad);
                    byte[] actual = pixels();
                    for (int x : new int[] {W / 4, W * 3 / 4}) {
                        for (int channel = 0; channel < 3; channel++) {
                            int i = (H / 2 * W + x) * 4 + channel;
                            near((opaque[i] & 255) * alpha / 255F, actual[i] & 255, 2,
                                    mode + " alpha " + alpha + " at " + x);
                        }
                    }
                }
            }

            hidden.getMode().setValue("Flat"); visible.getMode().setValue("Flat");
            hidden.getColor().setValue(0x80FF0000); visible.getColor().setValue(0x4000FF00);
            scene(); ChamsRenderer.draw(esp, false, 1, ChamsRenderCheck::quad);
            byte[] colors = pixels();
            rgb(colors, W / 4, 128, 0, 0, "Hidden red pass");
            rgb(colors, W * 3 / 4, 0, 64, 0, "Visible green pass");
            FloatBuffer depth = BufferUtils.createFloatBuffer(1);
            GL11.glReadPixels(W / 4, H / 2, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
            near(.25F, depth.get(0), .0001F, "Hidden pass changed scene depth");
            GL11.glReadPixels(W * 3 / 4, H / 2, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
            near(1, depth.get(0), .0001F, "Visible pass changed scene depth");

            hidden.getArmor().setEnabled(false);
            scene(); ChamsRenderer.draw(esp, true, 1, ChamsRenderCheck::quad);
            rgb(pixels(), W / 4, 0, 0, 0, "Hidden armor disabled");
            rgb(pixels(), W * 3 / 4, 0, 64, 0, "Visible armor enabled");
            hidden.getArmor().setEnabled(true); visible.getArmor().setEnabled(false);
            scene(); ChamsRenderer.draw(esp, true, 1, ChamsRenderCheck::quad);
            rgb(pixels(), W / 4, 128, 0, 0, "Hidden armor enabled");
            rgb(pixels(), W * 3 / 4, 0, 0, 0, "Visible armor disabled");

            hidden.getColor().setValue(0xFFFFFFFF); visible.getColor().setValue(0xFFFFFFFF);
            hidden.getShowSkin().setEnabled(true); visible.getShowSkin().setEnabled(false);
            texture(32, 64, 128, 255);
            scene(); ChamsRenderer.draw(esp, false, 1, ChamsRenderCheck::quad);
            rgb(pixels(), W / 4, 32, 64, 128, "Hidden skin texture");
            rgb(pixels(), W * 3 / 4, 255, 255, 255, "Visible untextured material");
            hidden.getShowSkin().setEnabled(false); visible.getShowSkin().setEnabled(true);
            scene(); ChamsRenderer.draw(esp, false, 1, ChamsRenderCheck::quad);
            rgb(pixels(), W / 4, 255, 255, 255, "Hidden untextured material");
            rgb(pixels(), W * 3 / 4, 32, 64, 128, "Visible skin texture");
            texture(255, 255, 255, 0);
            scene(); ChamsRenderer.draw(esp, false, 1, ChamsRenderCheck::quad);
            rgb(pixels(), W / 4, 0, 0, 0, "Transparent skin cutout");
            rgb(pixels(), W * 3 / 4, 0, 0, 0, "Transparent textured cutout");

            texture(255, 255, 255, 255);
            halo(esp);
            restoreState(esp, false); restoreState(esp, true);
            preview(esp, output.resolve("materials.png"));
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) throw new AssertionError("OpenGL error " + error);
            System.out.println("Chams OK: Flat/Glow/Metallic alpha 0/1/64/128/255, partial cover, armor, skin, cutouts, depth and exception state restoration.");
        } finally { buffer.destroy(); }
    }

    private static void halo(EspModule esp) {
        esp.getInvisibleChams().getColor().setValue(0xFFFF0000);esp.getVisibleChams().getColor().setValue(0xFF00FF00);
        esp.getVisibleChams().getShowSkin().setValue(false);esp.getInvisibleChams().getShowSkin().setValue(false);
        Runnable small=()->{GL11.glPushMatrix();GL11.glScalef(.5F,.5F,1);quad();GL11.glPopMatrix();};
        for(String mode:new String[]{"Flat","Glow"}){
            esp.getInvisibleChams().getMode().setValue(mode);esp.getVisibleChams().getMode().setValue(mode);
            scene();ChamsRenderer.beginWorld();try{ChamsRenderer.draw(esp,false,1,small);}finally{ChamsRenderer.endWorld();}
            byte[] data=pixels();int left=(H/2*W+W/4-3)*4,right=(H/2*W+W*3/4+2)*4;
            if(mode.equals("Glow")){
                if((data[left]&255)<10||(data[right+1]&255)<10)throw new AssertionError("Glow has no silhouette halo");
                near(0,data[left+1]&255,1,"Occluded halo wrong color");near(0,data[right]&255,1,"Visible halo wrong color");
            }else if((data[left]&255)!=0||(data[right+1]&255)!=0)throw new AssertionError("Flat material unexpectedly glows");
        }
        esp.getInvisibleChams().getMode().setValue("Flat");esp.getVisibleChams().getMode().setValue("Flat");
    }

    private static void restoreState(EspModule esp, boolean failure) throws Exception {
        scene();
        try (EffectProgram previous = new EffectProgram("Chams.vert", "Chams.frag")) {
            previous.bind(); int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            GL11.glEnable(GL11.GL_ALPHA_TEST); GL11.glAlphaFunc(GL11.GL_GREATER, .75F);
            GL11.glEnable(GL11.GL_FOG); GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_STENCIL_TEST); GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(0, 0, W, H); GL11.glDepthFunc(GL11.GL_GEQUAL); GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_BLEND);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_REVERSE_SUBTRACT, GL14.GL_FUNC_SUBTRACT);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
            GL11.glColorMask(true, false, true, false); GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            try {
                ChamsRenderer.draw(esp, false, 1, () -> {
                    if (failure) throw new IllegalStateException("Simulated model failure");
                    quad();
                });
                if (failure) throw new AssertionError("Expected geometry exception");
            } catch (IllegalStateException expected) { if (!failure) throw expected; }
            if (!GL11.glIsEnabled(GL11.GL_ALPHA_TEST) || !GL11.glIsEnabled(GL11.GL_FOG)
                    || !GL11.glIsEnabled(GL11.GL_LIGHTING) || !GL11.glIsEnabled(GL11.GL_STENCIL_TEST)
                    || !GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) || GL11.glIsEnabled(GL11.GL_BLEND)
                    || GL11.glIsEnabled(GL11.GL_CULL_FACE)) throw new AssertionError("Enable state leaked");
            if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != program
                    || GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != GL13.GL_TEXTURE2
                    || GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) != texture
                    || GL11.glGetInteger(GL11.GL_DEPTH_FUNC) != GL11.GL_GEQUAL
                    || !GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                    || GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB) != GL14.GL_FUNC_REVERSE_SUBTRACT
                    || GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA) != GL14.GL_FUNC_SUBTRACT
                    || GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB) != GL11.GL_ONE
                    || GL11.glGetInteger(GL14.GL_BLEND_DST_RGB) != GL11.GL_ZERO)
                throw new AssertionError("Program/texture/depth/blend state leaked");
            ByteBuffer mask = BufferUtils.createByteBuffer(16); GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, mask);
            if (mask.get(0) != 1 || mask.get(1) != 0 || mask.get(2) != 1 || mask.get(3) != 0)
                throw new AssertionError("Color mask leaked");
        }
    }

    private static void texture(int red, int green, int blue, int alpha) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        ByteBuffer data = BufferUtils.createByteBuffer(4);
        data.put((byte) red).put((byte) green).put((byte) blue).put((byte) alpha).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    private static void scene() {
        GL20.glUseProgram(0); GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glViewport(0, 0, W, H); GL11.glDisable(GL11.GL_FOG); GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_STENCIL_TEST); GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glColorMask(true, true, true, true); GL11.glDepthMask(true);
        GL11.glClearColor(0, 0, 0, 1); GL11.glClearDepth(1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(0, 0, W / 2, H); GL11.glClearDepth(.25);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT); GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(-1, 1, -1, 1, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
    }

    private static void quad() {
        GL11.glBegin(GL11.GL_QUADS); GL11.glNormal3f(.2F, .6F, .8F);
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(-1, -1, 0);
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(1, -1, 0);
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(1, 1, 0);
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(-1, 1, 0); GL11.glEnd();
    }

    private static void preview(EspModule esp, Path file) throws Exception {
        scene(); GL11.glClearColor(.035F, .045F, .07F, 1); GL11.glClearDepth(1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(-2.4, 2.4, -1.2, 1.2, -10, 10);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        ModelPlayer model = new ModelPlayer(0, false); Entity entity = new PreviewEntity();
        esp.getInvisibleChams().getColor().setValue(0xDDFF4968);
        esp.getVisibleChams().getColor().setValue(0xDD2DE2C2);
        esp.getVisibleChams().getShowSkin().setEnabled(false);
        for (int column = 0; column < 3; column++) {
            String mode = new String[] {"Flat", "Glow", "Metallic"}[column];
            esp.getInvisibleChams().getMode().setValue(mode); esp.getVisibleChams().getMode().setValue(mode);
            GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(column * W / 3, 0, W / 6, H);
            GL11.glClearDepth(.2); GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT); GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glLoadIdentity(); GL11.glTranslatef((column - 1) * 1.6F, .65F, -2);
            GL11.glRotatef(155, 0, 1, 0); GL11.glScalef(-1, -1, 1);
            ChamsRenderer.draw(esp, false, 1, () -> model.render(entity, 1, .35F, 0, 15, 0, .0625F));
        }
        byte[] pixels = pixels(); BufferedImage result = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = ((H - y - 1) * W + x) * 4;
            result.setRGB(x, y, 0xFF000000 | (pixels[i] & 255) << 16 | (pixels[i + 1] & 255) << 8 | pixels[i + 2] & 255);
        }
        ImageIO.write(result, "png", file.toFile());
    }

    private static byte[] pixels() {
        ByteBuffer data = BufferUtils.createByteBuffer(W * H * 4);
        GL11.glReadPixels(0, 0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
        byte[] bytes = new byte[data.capacity()]; data.get(bytes); return bytes;
    }

    private static void rgb(byte[] pixels, int x, int red, int green, int blue, String label) {
        int i = (H / 2 * W + x) * 4;
        near(red, pixels[i] & 255, 1, label); near(green, pixels[i + 1] & 255, 1, label); near(blue, pixels[i + 2] & 255, 1, label);
    }

    private static void near(float expected, float actual, float tolerance, String label) {
        if (Math.abs(expected - actual) > tolerance) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }

    private static final class PreviewEntity extends Entity {
        PreviewEntity() { super(null); }
        @Override protected void entityInit() { }
        @Override protected void readEntityFromNBT(NBTTagCompound tag) { }
        @Override protected void writeEntityToNBT(NBTTagCompound tag) { }
    }
}
