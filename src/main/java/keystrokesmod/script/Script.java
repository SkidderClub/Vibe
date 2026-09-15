package keystrokesmod.script;

import java.io.File;
import java.lang.reflect.Method;

/** Lightweight Raven Script facade backed by Vibe's ScriptRuntime. */
public class Script {
    public String name;
    public Class<?> clazz;
    public Object instance;
    public String scriptName;
    public String codeStr;
    public boolean error;
    public int STARTING_LINE;
    public ScriptEvents event;
    public File file;

    public Script(String name) {
        this.name = name == null ? "" : name;
        this.scriptName = "sc_" + this.name.replace(" ", "");
    }

    public void setCode(String code) { this.codeStr = code == null ? "" : code; }
    public boolean run() {
        error = true;
        return false;
    }
    public void delete() { clazz = null; instance = null; }
    public boolean invoke(String methodName, Object... args) {
        if (instance == null || clazz == null) return false;
        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.getName().equalsIgnoreCase(methodName) || method.getParameterTypes().length != args.length) continue;
            try { method.setAccessible(true); method.invoke(instance, args); return true; }
            catch (Exception ignored) { return false; }
        }
        return false;
    }
    public int getBoolean(String methodName, Object... args) {
        if (instance == null || clazz == null) return -1;
        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.getName().equalsIgnoreCase(methodName) || method.getParameterTypes().length != args.length || method.getReturnType() != Boolean.TYPE) continue;
            try { method.setAccessible(true); return Boolean.TRUE.equals(method.invoke(instance, args)) ? 1 : 0; }
            catch (Exception ignored) { return -1; }
        }
        return -1;
    }
    public Float[] getFloatArray(String methodName, Object... args) {
        if (instance == null || clazz == null) return null;
        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.getName().equalsIgnoreCase(methodName) || method.getParameterTypes().length != args.length || method.getReturnType() != Float[].class) continue;
            try { method.setAccessible(true); Object result = method.invoke(instance, args); return result instanceof Float[] ? (Float[]) result : null; }
            catch (Exception ignored) { return null; }
        }
        return null;
    }
}
