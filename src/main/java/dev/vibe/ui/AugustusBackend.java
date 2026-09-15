package dev.vibe.ui;

import imgui.ImDrawData;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec4;
import imgui.flag.ImGuiKey;
import imgui.type.ImInt;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

/** LWJGL 2 / OpenGL 2 backend for the bundled ImGui 1.86 ABI. */
final class AugustusBackend implements AutoCloseable {
    private int fontTexture;
    private ByteBuffer indices = BufferUtils.createByteBuffer(1);
    private final boolean[] mouseDown = new boolean[5];
    private final ArrayDeque<boolean[]> mouseEvents = new ArrayDeque<boolean[]>();
    private float wheel;
    private long lastFrame;
    private boolean fontDirty;

    void init() {
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.setBackendPlatformName("vibe_lwjgl2"); io.setBackendRendererName("vibe_opengl2");
        int[] keys = {Keyboard.KEY_TAB,Keyboard.KEY_LEFT,Keyboard.KEY_RIGHT,Keyboard.KEY_UP,Keyboard.KEY_DOWN,
                Keyboard.KEY_PRIOR,Keyboard.KEY_NEXT,Keyboard.KEY_HOME,Keyboard.KEY_END,Keyboard.KEY_INSERT,
                Keyboard.KEY_DELETE,Keyboard.KEY_BACK,Keyboard.KEY_SPACE,Keyboard.KEY_RETURN,Keyboard.KEY_ESCAPE,
                Keyboard.KEY_NUMPADENTER,Keyboard.KEY_A,Keyboard.KEY_C,Keyboard.KEY_V,Keyboard.KEY_X,Keyboard.KEY_Y,Keyboard.KEY_Z};
        for (int i=0; i<Math.min(keys.length,ImGuiKey.COUNT); i++) io.setKeyMap(i,keys[i]);
        uploadFont();
    }

    private void uploadFont() {
        ImGuiIO io = ImGui.getIO();
        ImInt width = new ImInt(), height = new ImInt();
        ByteBuffer pixels;
        try { pixels = AugustusMinecraftFont.build(io.getFonts(), width, height); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Could not load Minecraft interface font", failure); }
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT); GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
        try {
            if (fontTexture == 0) fontTexture = GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,fontTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0); GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,width.get(),height.get(),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            io.getFonts().setTexID(fontTexture);
        } finally { GL11.glPopClientAttrib(); GL11.glPopAttrib(); }
    }

    void mouse() {
        int button = Mouse.getEventButton();
        if (button >= 0 && button < 5) queueMouse(button,Mouse.getEventButtonState());
        wheel += Mouse.getEventDWheel() / 120F;
    }

    void queueMouse(int button, boolean down) {
        // Preserve press/release pairs arriving between frames, including left button zero.
        boolean[] state = mouseEvents.isEmpty() ? mouseDown.clone() : mouseEvents.peekLast().clone();
        state[button] = down; mouseEvents.add(state);
    }

    void key() {
        ImGuiIO io = ImGui.getIO();
        int key = Keyboard.getEventKey();
        if (key > 0 && key < 256) io.setKeysDown(key,Keyboard.getEventKeyState());
        char character = Keyboard.getEventCharacter();
        if (Keyboard.getEventKeyState() && character >= 32 && character != 127) io.addInputCharacter(character);
    }

    void newFrame(int width, int height) {
        if (fontDirty) { uploadFont(); fontDirty = false; }
        ImGuiIO io = ImGui.getIO();
        long now = System.nanoTime();
        io.setDeltaTime(lastFrame == 0 ? 1F/60 : Math.max(.001F, Math.min(.1F,(now-lastFrame)/1_000_000_000F)));
        lastFrame = now;
        io.setDisplaySize(width,height); io.setDisplayFramebufferScale(1,1);
        if (Mouse.isCreated()) io.setMousePos(Mouse.getX(),height-Mouse.getY()-1);
        if (!mouseEvents.isEmpty()) System.arraycopy(mouseEvents.removeFirst(),0,mouseDown,0,5);
        io.setMouseDown(mouseDown); io.setMouseWheel(wheel); wheel=0;
        if (Keyboard.isCreated()) {
            io.setKeyCtrl(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)||Keyboard.isKeyDown(Keyboard.KEY_RCONTROL));
            io.setKeyShift(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)||Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
            io.setKeyAlt(Keyboard.isKeyDown(Keyboard.KEY_LMENU)||Keyboard.isKeyDown(Keyboard.KEY_RMENU));
            io.setKeySuper(Keyboard.isKeyDown(Keyboard.KEY_LMETA)||Keyboard.isKeyDown(Keyboard.KEY_RMETA));
        }
    }

    void resetInput() {
        fontDirty = true; // Refresh resource-pack fonts when reopening the GUI.
        mouseEvents.clear(); java.util.Arrays.fill(mouseDown,false); wheel=0; lastFrame=0;
        ImGui.getIO().setMouseDown(mouseDown); ImGui.getIO().setKeysDown(new boolean[512]);
        ImGui.getIO().clearInputCharacters();
    }

    void render(ImDrawData data) {
        int width = (int)(data.getDisplaySizeX()*data.getFramebufferScaleX());
        int height = (int)(data.getDisplaySizeY()*data.getFramebufferScaleY());
        if (width<=0 || height<=0) return;
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int clientActive=GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE), array=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int element=GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING), mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT | GL11.GL_CLIENT_PIXEL_STORE_BIT);
        GL13.glActiveTexture(GL13.GL_TEXTURE0); GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
        try {
            GL20.glUseProgram(0); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,0); GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,0);
            GL11.glEnable(GL11.GL_BLEND); GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG); GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColorMask(true,true,true,true); GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_FILL);
            GL11.glShadeModel(GL11.GL_SMOOTH); GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY); GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY); GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY); GL11.glViewport(0,0,width,height);
            float x=data.getDisplayPosX(), y=data.getDisplayPosY();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
            GL11.glOrtho(x,x+data.getDisplaySizeX(),y+data.getDisplaySizeY(),y,-1,1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
            int stride=ImDrawData.sizeOfImDrawVert(), indexSize=ImDrawData.sizeOfImDrawIdx();
            for(int list=0;list<data.getCmdListsCount();list++) {
                ByteBuffer source=data.getCmdListIdxBufferData(list);
                if(indices.capacity()<source.remaining()) indices=BufferUtils.createByteBuffer(source.remaining());
                indices.clear(); indices.put(source); indices.flip();
                ByteBuffer vertices=data.getCmdListVtxBufferData(list);
                for(int command=0;command<data.getCmdListCmdBufferSize(list);command++) {
                    int base=data.getCmdListCmdBufferVtxOffset(list,command)*stride;
                    vertices.position(base); GL11.glVertexPointer(2,GL11.GL_FLOAT,stride,vertices);
                    vertices.position(base+8); GL11.glTexCoordPointer(2,GL11.GL_FLOAT,stride,vertices);
                    vertices.position(base+16); GL11.glColorPointer(4,GL11.GL_UNSIGNED_BYTE,stride,vertices);
                    ImVec4 clip=data.getCmdListCmdBufferClipRect(list,command);
                    int left=Math.max(0,(int)((clip.x-x)*data.getFramebufferScaleX()));
                    int top=Math.max(0,(int)((clip.y-y)*data.getFramebufferScaleY()));
                    int right=Math.min(width,(int)((clip.z-x)*data.getFramebufferScaleX()));
                    int bottom=Math.min(height,(int)((clip.w-y)*data.getFramebufferScaleY()));
                    if(right<=left || bottom<=top) continue;
                    GL11.glScissor(left,height-bottom,right-left,bottom-top);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D,data.getCmdListCmdBufferTextureId(list,command));
                    indices.position(data.getCmdListCmdBufferIdxOffset(list,command)*indexSize);
                    GL11.glDrawElements(GL11.GL_TRIANGLES,data.getCmdListCmdBufferElemCount(list,command),indexSize==2?GL11.GL_UNSIGNED_SHORT:GL11.GL_UNSIGNED_INT,indices);
                }
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix(); GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glPopClientAttrib(); GL11.glPopAttrib();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,array); GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,element);
            GL20.glUseProgram(program); GL13.glActiveTexture(active); GL13.glClientActiveTexture(clientActive); GL11.glMatrixMode(mode);
        }
    }

    @Override public void close() {
        if(fontTexture!=0) { GL11.glDeleteTextures(fontTexture); fontTexture=0; }
    }
}
