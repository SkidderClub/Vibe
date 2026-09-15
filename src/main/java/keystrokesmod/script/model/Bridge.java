package keystrokesmod.script.model;

import java.util.HashMap;
import java.util.Map;

/** Shared in-process script data bridge retained for Raven scripts. */
public class Bridge {
    private static final Map<String, Object> VALUES = new HashMap<String, Object>();
    public void add(String key, Object value) { VALUES.put(key, value); }
    public void add(String key) { VALUES.put(key, null); }
    public void remove(String key) { VALUES.remove(key); }
    public boolean has(String key) { return VALUES.containsKey(key); }
    public Object get(String key) { return VALUES.get(key); }
    public void clear() { VALUES.clear(); }
}
