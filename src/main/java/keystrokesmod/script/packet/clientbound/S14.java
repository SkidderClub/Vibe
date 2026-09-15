package keystrokesmod.script.packet.clientbound;

import java.lang.reflect.Field;
import net.minecraft.network.play.server.S14PacketEntity;

/** Relative entity movement wrapper retained for Raven packet callbacks. */
public class S14 extends SPacket {
    public int entityId;
    public byte posX, posY, posZ, yaw, pitch;
    public boolean onGround, rotating;

    public S14(S14PacketEntity value) {
        super(value);
        // S14's accessors differ between Forge mappings. Read the stable wire
        // fields reflectively so the compatibility layer works with either
        // MCP names or the obfuscated names supplied by the launcher.
        entityId = readInt(value, 0, "entityId", "field_149073_a");
        posX = (byte) readInt(value, 0, "posX", "field_149072_b");
        posY = (byte) readInt(value, 0, "posY", "field_149076_c");
        posZ = (byte) readInt(value, 0, "posZ", "field_149074_d");
        yaw = (byte) readInt(value, 0, "yaw", "field_149075_e");
        pitch = (byte) readInt(value, 0, "pitch", "field_149077_f");
        onGround = readBoolean(value, false, "onGround", "field_149079_g");
        rotating = readBoolean(value, false, "rotating", "field_149078_h");
    }

    private static int readInt(Object object, int fallback, String... names) {
        Object value = read(object, names);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static boolean readBoolean(Object object, boolean fallback, String... names) {
        Object value = read(object, names);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static Object read(Object object, String... names) {
        if (object == null) return null;
        for (String name : names) {
            try {
                Field field = S14PacketEntity.class.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (ReflectiveOperationException ignored) {
                // Try the next mapping name.
            }
        }
        return null;
    }

    public S14(int id, byte x, byte y, byte z, byte yaw, byte pitch, boolean ground) {
        super(null);
        entityId = id; posX = x; posY = y; posZ = z; this.yaw = yaw; this.pitch = pitch;
        onGround = ground; rotating = true;
    }

    public S14(int id, byte x, byte y, byte z, boolean ground) {
        this(id, x, y, z, (byte) 0, (byte) 0, ground);
        rotating = false;
    }

    public S14(int id, byte yaw, byte pitch, boolean ground) {
        this(id, (byte) 0, (byte) 0, (byte) 0, yaw, pitch, ground);
    }
}
