package dev.vibe.ui;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.gui.GuiScreen;

/**
 * Drives a screen through GuiScreen's protected input callbacks, as Minecraft would.
 * Render checks live in other packages than the screens they exercise.
 */
public final class GuiInput {

    private static final Method MOUSE_CLICKED = find("mouseClicked", int.class, int.class, int.class);
    private static final Method MOUSE_RELEASED = find("mouseReleased", int.class, int.class, int.class);
    private static final Method MOUSE_CLICK_MOVE = find("mouseClickMove", int.class, int.class, int.class, long.class);
    private static final Method KEY_TYPED = find("keyTyped", char.class, int.class);

    private GuiInput() {
    }

    public static void mouseClicked(GuiScreen screen, int mouseX, int mouseY, int button) {
        invoke(MOUSE_CLICKED, screen, mouseX, mouseY, button);
    }

    public static void mouseReleased(GuiScreen screen, int mouseX, int mouseY, int button) {
        invoke(MOUSE_RELEASED, screen, mouseX, mouseY, button);
    }

    public static void mouseClickMove(GuiScreen screen, int mouseX, int mouseY, int button, long heldMillis) {
        invoke(MOUSE_CLICK_MOVE, screen, mouseX, mouseY, button, heldMillis);
    }

    public static void keyTyped(GuiScreen screen, char character, int keyCode) {
        invoke(KEY_TYPED, screen, character, keyCode);
    }

    private static Method find(String name, Class<?>... parameters) {
        try {
            Method method = GuiScreen.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("GuiScreen." + name + " not found", e);
        }
    }

    private static void invoke(Method method, GuiScreen screen, Object... arguments) {
        try {
            method.invoke(screen, arguments);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
