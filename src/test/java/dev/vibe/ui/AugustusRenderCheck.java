package dev.vibe.ui;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import java.nio.ByteBuffer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.*;
import dev.vibe.setting.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Real Java 8 natives, mouse press/release delivery and OpenGL state restoration. */
public final class AugustusRenderCheck {
    public static void main(String[] args) throws Exception {
        Pbuffer buffer=new Pbuffer(1280,720,new PixelFormat(8,24,8),null,null);
        buffer.makeCurrent();
        try {
            for(int restart=0;restart<2;restart++) {
                ImGui.createContext();
                try(AugustusBackend backend=new AugustusBackend()) {
                    backend.init();
                    ImBoolean checked=new ImBoolean(false);
                    float[] target=new float[2];
                    for(int frame=0;frame<5;frame++) {
                        if(frame==2) { backend.queueMouse(0,true); backend.queueMouse(0,false); }
                        backend.newFrame(640,480);
                        ImGui.getIO().setMousePos(target[0],target[1]);
                        ImGui.newFrame();
                        ImGui.setNextWindowPos(20,20,ImGuiCond.Always); ImGui.setNextWindowSize(400,300,ImGuiCond.Always);
                        ImGui.begin("Augustus backend check");
                        ImGui.checkbox("Select with left click",checked);
                        target[0]=ImGui.getItemRectMinX()+8; target[1]=ImGui.getItemRectMinY()+8;
                        ImGui.sliderFloat("Slider",new float[]{.5F},0,1);
                        ImGui.text("Native text and index buffers"); ImGui.end(); ImGui.render();
                        GL11.glClearColor(0,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                        int texture=GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
                        GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_ALPHA_TEST);
                        GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glMatrixMode(GL11.GL_MODELVIEW);
                        backend.render(ImGui.getDrawData());
                        if(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=texture || !GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                                || !GL11.glIsEnabled(GL11.GL_ALPHA_TEST) || GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
                                || GL11.glGetInteger(GL11.GL_MATRIX_MODE)!=GL11.GL_MODELVIEW) throw new AssertionError("ImGui leaked OpenGL state");
                        GL11.glDeleteTextures(texture);
                        if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("ImGui generated an OpenGL error");
                    }
                    if(!checked.get())throw new AssertionError("Left click press/release was lost between frames");
                    ByteBuffer pixels=BufferUtils.createByteBuffer(640*480*4); GL11.glReadPixels(0,0,640,480,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                    int bright=0; for(int i=0;i<pixels.capacity();i+=4)if((pixels.get(i)&255)>100)bright++;
                    if(bright<200)throw new AssertionError("ImGui text/geometry did not render");
                    backend.resetInput();
                } finally { ImGui.destroyContext(); }
            }
            verifyWorkspace();
            System.out.println("Augustus native rendering, workspace controls, Minecraft font, reopen and GL state checks passed: "+GL11.glGetString(GL11.GL_RENDERER));
        } finally { buffer.destroy(); }
    }

    private static void verifyWorkspace() throws Exception {
        ImGui.createContext();
        try (AugustusBackend backend = new AugustusBackend()) {
            backend.init();
            Controls controls = new Controls();
            ClickGuiModule clickGui = new ClickGuiModule();
            clickGui.getTheme().setValue("Augustus");
            List<Module> modules = Arrays.<Module>asList(controls, clickGui);
            verifyAppearance(backend, modules, clickGui);
            verifyWindowControls(backend, modules);
            verifyWindowResize(backend, modules);
            frame(backend, modules, 1280, 720, 1000, 600);
            frame(backend, modules, 1280, 720, 1000, 600);
            if (ImGui.getFont().getCharAdvance('A') != 12 || ImGui.getFont().getCharAdvance(' ') != 8)
                throw new AssertionError("Minecraft bitmap metrics were not installed");
            for (char c : new char[] {'\u00e4', '\u0416', '\u4e2d', '\u65e5'})
                if (ImGui.getFont().findGlyphNoFallback(c) == null) throw new AssertionError("Missing localized glyph: " + c);
            save("controls", 1280, 720);
            if (inkBottom(210, 320, 150, 181, 0xF0F0F0) != inkBottom(320, 370, 150, 181, 0x00FF00))
                throw new AssertionError("Boolean value is below the label baseline");
            click(backend, modules, 0, 60, 50);
            if (!controls.isEnabled()) throw new AssertionError("Left click did not toggle module");
            click(backend, modules, 1, 60, 50);
            if (!controls.isEnabled()) throw new AssertionError("Right click toggled module while opening settings");

            // Exercise the actual rendered controls through native mouse delivery.
            click(backend, modules, 0, 328, 170);
            if (controls.flag.isEnabled()) throw new AssertionError("Inline boolean did not toggle");
            click(backend, modules, 0, 350, 196);
            if (!controls.mode.is("Legit")) throw new AssertionError("Inline mode did not select");
            int commaX = Math.round(210 + ImGui.calcTextSize("Mode:").x + 6 + ImGui.calcTextSize("Basic").x + 1);
            click(backend, modules, 0, commaX, 196);
            if (!controls.mode.is("Legit")) throw new AssertionError("Clicking a comma selected a mode");
            click(backend, modules, 0, 435, 222);
            if (controls.amount.getDouble() < 60 || controls.amount.getDouble() > 80)
                throw new AssertionError("Filled slider did not change value: " + controls.amount.getDouble());
            controls.group.toggle("Players");
            click(backend, modules, 0, 1225, 110);
            if (!controls.flag.isEnabled() || !controls.mode.is("Basic") || controls.amount.getDouble() != 25
                    || !controls.group.isSelected("Players")) throw new AssertionError("Reset did not restore original defaults");
            controls.group.toggle("Players"); controls.group.resetToDefault();
            if (!controls.group.isSelected("Players")) throw new AssertionError("Reset mutated multiselect defaults");
            click(backend, modules, 0, 355, 380);
            ImGui.getIO().addInputCharacter('\u00e4');
            frame(backend, modules, 1280, 720, 355, 380);
            if (!controls.text.getValue().contains("\u00e4")) throw new AssertionError("Minecraft text field lost Unicode input");
            click(backend, modules, 0, 950, 600);

            List<Module> realModules = Arrays.<Module>asList(new AimAssistModule(), new AutoClickerModule(),
                    new BacktrackModule(), new KillAuraModule(), new ReachModule(), new TargetsModule(),
                    new VelocityModule(), new WTapModule());
            java.lang.reflect.Field selected = AugustusImGui.class.getDeclaredField("selected");
            selected.setAccessible(true); selected.set(null, realModules.get(3));
            for (int[] size : new int[][] {{1280,720}, {854,480}, {640,360}}) {
                frame(backend, realModules, size[0], size[1], size[0] - 50, size[1] - 30);
                frame(backend, realModules, size[0], size[1], size[0] - 50, size[1] - 30);
                save("augustus-" + size[0], size[0], size[1]);
            }
            backend.resetInput();
            frame(backend, realModules, 1280, 720, 1000, 600);
            frame(backend, realModules, 1280, 720, 1000, 600);
            frame(backend, realModules, 640, 360, 500, 280);
            frame(backend, realModules, 640, 360, 500, 280);
            int before = contentHash(640, 360);
            backend.newFrame(640, 360);
            ImGui.getIO().setMousePos(500, 280); ImGui.getIO().setMouseWheel(-20);
            ImGui.newFrame(); AugustusImGui.drawWorkspace(640, 360, realModules); ImGui.render();
            backend.render(ImGui.getDrawData());
            frame(backend, realModules, 640, 360, 500, 280);
            if (contentHash(640, 360) == before) throw new AssertionError("Settings did not scroll in a small window");
            save("augustus-640-scrolled", 640, 360);
        } finally { ImGui.destroyContext(); }
    }

    private static void verifyAppearance(AugustusBackend backend, List<Module> modules, ClickGuiModule clickGui) throws Exception {
        int backdrop = 0x406080;
        frame(backend, modules, 1280, 720, 1000, 700, backdrop);
        frame(backend, modules, 1280, 720, 1000, 700, backdrop);
        int x = (int) windowValue("windowX"), y = (int) windowValue("windowY");
        int width = (int) windowValue("windowWidth"), height = (int) windowValue("windowHeight");
        int[][] corners = {{x + 1, y + 1}, {x + width - 2, y + 1},
                {x + 1, y + height - 2}, {x + width - 2, y + height - 2}};
        for (int[] corner : corners) if (pixel(corner[0], corner[1], 720) != backdrop)
            throw new AssertionError("Default rounded corner is covered by window or child geometry");
        save("augustus-transparent-rounded", 1280, 720);
        frame(backend, modules, 1280, 720, x + width - 18, y + 15, backdrop);
        if (pixel(x + width - 2, y + 1, 720) != backdrop)
            throw new AssertionError("Close-button hover covers the rounded corner");
        for (int alpha : new int[] {200, 0, 128, 255}) {
            clickGui.getAugustusBackgroundAlpha().setValue((double) alpha);
            frame(backend, modules, 1280, 720, 1000, 700, backdrop);
            // Sample the title and all three child areas over a colored scene.
            assertBlend(x + 200, y + 15, 0x222222, backdrop, alpha);
            assertBlend(x + 50, y + height - 60, 0x181818, backdrop, alpha);
            assertBlend(x + 220, y + 35, 0x181818, backdrop, alpha);
            assertBlend(x + width - 40, y + height - 60, 0x181818, backdrop, alpha);
            inkBottom(x + 8, x + 95, y + 7, y + 25, 0xF0F0F0);
        }
        clickGui.getAugustusRoundedCorners().setEnabled(false);
        frame(backend, modules, 1280, 720, 1000, 700, backdrop);
        for (int[] corner : corners) if (pixel(corner[0], corner[1], 720) == backdrop)
            throw new AssertionError("Disabling rounded corners did not restore square corners");
        save("augustus-opaque-square", 1280, 720);
        clickGui.getAugustusRoundedCorners().resetToDefault();
        // Keep the existing control checks on an opaque background.
    }

    private static void assertBlend(int x, int y, int foreground, int background, int alpha) {
        int actual = pixel(x, y, 720);
        for (int shift : new int[] {16, 8, 0}) {
            int expected = Math.round(((foreground >> shift & 255) * alpha
                    + (background >> shift & 255) * (255 - alpha)) / 255F);
            if (Math.abs((actual >> shift & 255) - expected) > 1)
                throw new AssertionError("Background opacity is incorrect at " + x + "," + y + " for alpha " + alpha);
        }
    }

    private static void verifyWindowControls(AugustusBackend backend, List<Module> modules) throws Exception {
        frame(backend, modules, 1280, 720, 1000, 700);
        frame(backend, modules, 1280, 720, 1000, 700);
        if (isMaximized()) throw new AssertionError("GUI should open as a movable window");
        float initialX = windowValue("windowX"), initialY = windowValue("windowY");
        drag(backend, modules, (int) initialX + 150, (int) initialY + 15, (int) initialX + 190, (int) initialY + 35);
        if (windowValue("windowX") != initialX + 40 || windowValue("windowY") != initialY + 20)
            throw new AssertionError("Title bar did not move the window");
        float movedX = windowValue("windowX"), movedY = windowValue("windowY");
        save("augustus-windowed", 1280, 720);
        click(backend, modules, 0, (int) (movedX + windowValue("windowWidth") - 51), (int) movedY + 15);
        frame(backend, modules, 1280, 720, 1000, 700);
        if (!isMaximized() || pixel(5, 5, 720) != 0x222222 || pixel(1275, 700, 720) != 0x181818)
            throw new AssertionError("Maximize did not fill the screen");
        click(backend, modules, 0, 1229, 15);
        frame(backend, modules, 1280, 720, 1000, 700);
        if (isMaximized() || windowValue("windowX") != movedX || windowValue("windowY") != movedY
                || pixel((int) movedX + 5, (int) movedY + 5, 720) != 0x222222)
            throw new AssertionError("Restore lost window position");
        click(backend, modules, 0, (int) movedX + 150, (int) movedY + 15);
        click(backend, modules, 0, (int) movedX + 150, (int) movedY + 15);
        frame(backend, modules, 1280, 720, 1000, 700);
        if (!isMaximized()) throw new AssertionError("Double click did not maximize");
        drag(backend, modules, 500, 15, 540, 70);
        if (isMaximized()) throw new AssertionError("Dragging maximized title bar did not restore");
        if (windowValue("windowWidth") != 1100 || windowValue("windowHeight") != 640)
            throw new AssertionError("Maximize changed restored size");
        drag(backend, modules, (int) windowValue("windowX") + 150, (int) windowValue("windowY") + 15, -300, -100);
        if (windowValue("windowX") != 0 || windowValue("windowY") != 0)
            throw new AssertionError("Window can be dragged out of reach");
        AugustusImGui.closed();
        frame(backend, modules, 1280, 720, 1000, 700);
        if (windowValue("windowX") != 0 || windowValue("windowY") != 0)
            throw new AssertionError("Reopening lost window position");
        // Existing content checks use the maximized layout.
        click(backend, modules, 0, (int) windowValue("windowWidth") - 51, 15);
        frame(backend, modules, 1280, 720, 1000, 700);
        if (!isMaximized()) throw new AssertionError("Could not maximize after reopening");
    }

    private static float windowValue(String name) throws Exception {
        java.lang.reflect.Field field = AugustusImGui.class.getDeclaredField(name);
        field.setAccessible(true); return field.getFloat(null);
    }

    private static void verifyWindowResize(AugustusBackend backend, List<Module> modules) throws Exception {
        click(backend, modules, 0, 1229, 15);
        frame(backend, modules, 1280, 720, 1000, 700);
        drag(backend, modules, 150, 15, 240, 55);
        // All other edges and corners leave the size alone; the title bar may still move the window.
        for (int[] change : new int[][] {
                {1,0,-120,0}, {0,1,0,-80}, {-1,0,60,0}, {0,-1,0,30},
                {-1,-1,20,20}, {1,-1,-20,15}, {-1,1,20,-20}}) {
            float x=windowValue("windowX"), y=windowValue("windowY");
            float width=windowValue("windowWidth"), height=windowValue("windowHeight");
            int mouseX=(int)(x + (change[0]<0 ? 1 : change[0]>0 ? width-2 : width/2));
            int mouseY=(int)(y + (change[1]<0 ? 1 : change[1]>0 ? height-2 : height/2));
            drag(backend, modules, mouseX, mouseY, mouseX+change[2], mouseY+change[3]);
            if (windowValue("windowWidth") != width || windowValue("windowHeight") != height)
                throw new AssertionError("Resized outside the bottom-right grip: " + Arrays.toString(change));
            if (modules.get(0).isEnabled()) throw new AssertionError("Resizing also toggled a module");
        }
        int x=(int)windowValue("windowX"), y=(int)windowValue("windowY");
        drag(backend, modules, x+(int)windowValue("windowWidth")-2, y+(int)windowValue("windowHeight")-2, x+50, y+50);
        if (windowValue("windowWidth") != 560 || windowValue("windowHeight") != 300)
            throw new AssertionError("Resize did not respect minimum dimensions");
        drag(backend, modules, x+558, y+298, x+738, y+418);
        if (windowValue("windowWidth") != 740 || windowValue("windowHeight") != 420)
            throw new AssertionError("Window could not grow after reaching minimum size");
        if (windowValue("windowX") != x || windowValue("windowY") != y)
            throw new AssertionError("Bottom-right resize moved the window origin");
        save("augustus-resized", 1280, 720);
        click(backend, modules, 0, x+740-51, y+15);
        frame(backend, modules, 1280, 720, 1000, 700);
        click(backend, modules, 0, 1229, 15);
        frame(backend, modules, 1280, 720, 1000, 700);
        if (isMaximized() || windowValue("windowWidth") != 740 || windowValue("windowHeight") != 420)
            throw new AssertionError("Restore lost resized dimensions");
        click(backend, modules, 0, x+740-51, y+15);
        frame(backend, modules, 1280, 720, 1000, 700);
        if (!isMaximized()) throw new AssertionError("Could not maximize resized window");
    }

    private static boolean isMaximized() throws Exception {
        java.lang.reflect.Field field = AugustusImGui.class.getDeclaredField("maximized");
        field.setAccessible(true); return field.getBoolean(null);
    }

    private static void drag(AugustusBackend backend, List<Module> modules, int x, int y, int targetX, int targetY) {
        frame(backend, modules, 1280, 720, x, y);
        backend.queueMouse(0, true);
        frame(backend, modules, 1280, 720, x, y);
        frame(backend, modules, 1280, 720, targetX, targetY);
        backend.queueMouse(0, false);
        frame(backend, modules, 1280, 720, targetX, targetY);
    }

    private static int pixel(int x, int y, int height) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, height - y - 1, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        return ((pixel.get(0)&255)<<16)|((pixel.get(1)&255)<<8)|(pixel.get(2)&255);
    }

    private static int inkBottom(int left, int right, int top, int bottom, int color) {
        for (int y=bottom-1; y>=top; y--) for (int x=left; x<right; x++) if (pixel(x,y,720)==color) return y;
        throw new AssertionError("Expected text was not rendered");
    }

    private static int contentHash(int width, int height) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        int result = 1;
        for (int y=20; y<height-150; y++) for (int x=180; x<width-20; x++) result = result * 31 + pixels.get((y*width+x)*4);
        return result;
    }

    private static void click(AugustusBackend backend, List<Module> modules, int button, int x, int y) {
        frame(backend, modules, 1280, 720, x, y);
        backend.queueMouse(button, true); backend.queueMouse(button, false);
        frame(backend, modules, 1280, 720, x, y);
        frame(backend, modules, 1280, 720, x, y);
    }

    private static void frame(AugustusBackend backend, List<Module> modules, int width, int height, int x, int y) {
        frame(backend, modules, width, height, x, y, 0);
    }

    private static void frame(AugustusBackend backend, List<Module> modules, int width, int height, int x, int y, int backdrop) {
        backend.newFrame(width, height);
        ImGui.getIO().setMousePos(x, y);
        ImGui.newFrame(); AugustusImGui.drawWorkspace(width, height, modules); ImGui.render();
        GL11.glClearColor((backdrop >> 16 & 255) / 255F, (backdrop >> 8 & 255) / 255F, (backdrop & 255) / 255F, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        backend.render(ImGui.getDrawData());
        if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("Workspace generated an OpenGL error");
    }

    private static void save(String name, int width, int height) throws Exception {
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<height; y++) for (int x=0; x<width; x++) {
            int i=((height-y-1)*width+x)*4;
            image.setRGB(x,y,0xFF000000|((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));
        }
        File output = new File("build/augustus-render-check/" + name + ".png");
        output.getParentFile().mkdirs(); ImageIO.write(image,"png",output);
    }

    private static final class Controls extends Module {
        final BooleanSetting flag = addSetting(new BooleanSetting("Check Fall", true));
        final ModeSetting mode = addSetting(new ModeSetting("Mode", "Basic", "Basic", "Legit", "Push"));
        final NumberSetting amount = addSetting(new NumberSetting("Amount", 25, 0, 100, 1));
        final RangeSetting range = addSetting(new RangeSetting("Range", 2, 4, 0, 6, .1));
        final MultiSelectSetting group = addSetting(new MultiSelectSetting("Targets", Arrays.asList("Players", "Mobs"), Collections.singleton("Players")));
        final StringSetting text = addSetting(new StringSetting("Text", "Minecraft"));
        final ColorSetting color = addSetting(new ColorSetting("Color", 0xFF009DFF));
        Controls() { super("Controls", "Isolated settings for native input checks", Category.COMBAT, 0); }
    }
}
