package dev.vibe.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * A persisted, locally adjustable copy of an authored Cosmetica accessory.
 * The catalogue provides a range for each axis; presets store the currently
 * selected value inside that range, mirroring the original Cosmetica editor.
 */
public final class CosmeticaAccessory {
    public static final int HIDE_WITH_HELMET = 0x1;
    public static final int HIDE_WITH_CHESTPLATE = 0x2;
    public static final int HIDE_WITH_LEGGINGS = 0x4;
    public static final int HIDE_WITH_BOOTS = 0x8;
    public static final int HIDE_WITH_CLOAK = 0x10;
    public static final int HIDE_WITH_ELYTRA = 0x20;
    public static final int HIDE_WITH_PARROT = 0x40;

    private final String id, name, description, attachment, modelUrl, textureUrl, thumbnailUrl;
    private final float minOffsetX, minOffsetY, minOffsetZ, maxOffsetX, maxOffsetY, maxOffsetZ;
    private final int frames, ticksPerFrame;
    private float offsetX, offsetY, offsetZ;
    private int visibilityFlags;
    private boolean mirrored;
    private boolean enabled = true;

    private CosmeticaAccessory(String id, String name, String description, String attachment, String modelUrl,
                               String textureUrl, String thumbnailUrl, float minOffsetX, float minOffsetY,
                               float minOffsetZ, float maxOffsetX, float maxOffsetY, float maxOffsetZ,
                               float offsetX, float offsetY, float offsetZ, int flags, int frames, int ticksPerFrame) {
        this.id = value(id); this.name = value(name); this.description = value(description);
        this.attachment = value(attachment).toLowerCase(java.util.Locale.ROOT);
        this.modelUrl = value(modelUrl); this.textureUrl = value(textureUrl); this.thumbnailUrl = value(thumbnailUrl);
        this.minOffsetX = Math.min(minOffsetX, maxOffsetX); this.maxOffsetX = Math.max(minOffsetX, maxOffsetX);
        this.minOffsetY = Math.min(minOffsetY, maxOffsetY); this.maxOffsetY = Math.max(minOffsetY, maxOffsetY);
        this.minOffsetZ = Math.min(minOffsetZ, maxOffsetZ); this.maxOffsetZ = Math.max(minOffsetZ, maxOffsetZ);
        this.offsetX = clamp(offsetX, this.minOffsetX, this.maxOffsetX);
        this.offsetY = clamp(offsetY, this.minOffsetY, this.maxOffsetY);
        this.offsetZ = clamp(offsetZ, this.minOffsetZ, this.maxOffsetZ);
        this.visibilityFlags = flags;
        this.frames = Math.max(1, frames); this.ticksPerFrame = Math.max(1, ticksPerFrame);
    }

    public static CosmeticaAccessory fromApi(JsonObject object) {
        if (object == null) return null;
        JsonObject value = object.has("accessory") && object.get("accessory").isJsonObject() ? object.getAsJsonObject("accessory") : object;
        String id = string(value, "id"); if (id.isEmpty()) return null;
        JsonArray range = value.has("offset") && value.get("offset").isJsonArray() ? value.getAsJsonArray("offset") : null;
        float minX = number(range, 0), minY = number(range, 1), minZ = number(range, 2);
        float maxX = range != null && range.size() >= 6 ? number(range, 3) : minX;
        float maxY = range != null && range.size() >= 6 ? number(range, 4) : minY;
        float maxZ = range != null && range.size() >= 6 ? number(range, 5) : minZ;
        return new CosmeticaAccessory(id, string(value, "name"), string(value, "description"), string(value, "attachment"),
                string(value, "model"), string(value, "texture"), string(value, "thumbnail"),
                minX, minY, minZ, maxX, maxY, maxZ, midpoint(minX, maxX), midpoint(minY, maxY), midpoint(minZ, maxZ),
                integer(value, "flags", 0), integer(value, "frames", 1), integer(value, "ticksPerFrame", 5));
    }

    public static CosmeticaAccessory fromJson(JsonObject value) {
        float x = decimal(value, "offsetX"), y = decimal(value, "offsetY"), z = decimal(value, "offsetZ");
        CosmeticaAccessory accessory = new CosmeticaAccessory(string(value, "id"), string(value, "name"), string(value, "description"),
                string(value, "attachment"), string(value, "model"), string(value, "texture"), string(value, "thumbnail"),
                decimal(value, "minOffsetX", x), decimal(value, "minOffsetY", y), decimal(value, "minOffsetZ", z),
                decimal(value, "maxOffsetX", x), decimal(value, "maxOffsetY", y), decimal(value, "maxOffsetZ", z),
                x, y, z, integer(value, "visibilityFlags", integer(value, "flags", 0)),
                integer(value, "frames", 1), integer(value, "ticksPerFrame", 5));
        accessory.mirrored = bool(value, "mirrored"); accessory.enabled = !value.has("enabled") || bool(value, "enabled");
        return accessory.getId().isEmpty() ? null : accessory;
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("id", id); object.addProperty("name", name); object.addProperty("description", description);
        object.addProperty("attachment", attachment); object.addProperty("model", modelUrl); object.addProperty("texture", textureUrl); object.addProperty("thumbnail", thumbnailUrl);
        object.addProperty("offsetX", offsetX); object.addProperty("offsetY", offsetY); object.addProperty("offsetZ", offsetZ);
        object.addProperty("minOffsetX", minOffsetX); object.addProperty("minOffsetY", minOffsetY); object.addProperty("minOffsetZ", minOffsetZ);
        object.addProperty("maxOffsetX", maxOffsetX); object.addProperty("maxOffsetY", maxOffsetY); object.addProperty("maxOffsetZ", maxOffsetZ);
        object.addProperty("visibilityFlags", visibilityFlags); object.addProperty("frames", frames); object.addProperty("ticksPerFrame", ticksPerFrame);
        object.addProperty("mirrored", mirrored); object.addProperty("enabled", enabled); return object;
    }

    public CosmeticaAccessory copy() {
        CosmeticaAccessory copy = new CosmeticaAccessory(id, name, description, attachment, modelUrl, textureUrl, thumbnailUrl,
                minOffsetX, minOffsetY, minOffsetZ, maxOffsetX, maxOffsetY, maxOffsetZ, offsetX, offsetY, offsetZ,
                visibilityFlags, frames, ticksPerFrame);
        copy.mirrored = mirrored; copy.enabled = enabled; return copy;
    }

    public String getId() { return id; } public String getName() { return name; } public String getDescription() { return description; }
    public String getAttachment() { return attachment; } public String getModelUrl() { return modelUrl; } public String getTextureUrl() { return textureUrl; } public String getThumbnailUrl() { return thumbnailUrl; }
    public float getOffsetX() { return offsetX; } public float getOffsetY() { return offsetY; } public float getOffsetZ() { return offsetZ; }
    public float getMinOffsetX() { return minOffsetX; } public float getMinOffsetY() { return minOffsetY; } public float getMinOffsetZ() { return minOffsetZ; }
    public float getMaxOffsetX() { return maxOffsetX; } public float getMaxOffsetY() { return maxOffsetY; } public float getMaxOffsetZ() { return maxOffsetZ; }
    public boolean isAdjustableX() { return minOffsetX != maxOffsetX; } public boolean isAdjustableY() { return minOffsetY != maxOffsetY; } public boolean isAdjustableZ() { return minOffsetZ != maxOffsetZ; }
    public void setOffsetX(float value) { offsetX = clamp(value, minOffsetX, maxOffsetX); }
    public void setOffsetY(float value) { offsetY = clamp(value, minOffsetY, maxOffsetY); }
    public void setOffsetZ(float value) { offsetZ = clamp(value, minOffsetZ, maxOffsetZ); }
    public int getFrames() { return frames; } public int getTicksPerFrame() { return ticksPerFrame; }
    public boolean isMirrored() { return mirrored; } public void setMirrored(boolean value) { mirrored = value; }
    public boolean isEnabled() { return enabled; } public void setEnabled(boolean value) { enabled = value; }
    public boolean hasVisibilityFlag(int flag) { return (visibilityFlags & flag) != 0; }
    public void setVisibilityFlag(int flag, boolean value) { visibilityFlags = value ? visibilityFlags | flag : visibilityFlags & ~flag; }

    private static String value(String value) { return value == null ? "" : value; }
    private static String string(JsonObject object, String key) { try { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : ""; } catch (Exception ignored) { return ""; } }
    private static float number(JsonArray values, int index) { try { return values != null && values.size() > index ? values.get(index).getAsFloat() : 0.0F; } catch (Exception ignored) { return 0.0F; } }
    private static float decimal(JsonObject object, String key) { return decimal(object, key, 0.0F); }
    private static float decimal(JsonObject object, String key, float fallback) { try { return object.has(key) ? object.get(key).getAsFloat() : fallback; } catch (Exception ignored) { return fallback; } }
    private static int integer(JsonObject object, String key, int fallback) { try { return object.has(key) ? object.get(key).getAsInt() : fallback; } catch (Exception ignored) { return fallback; } }
    private static boolean bool(JsonObject object, String key) { try { return object.has(key) && object.get(key).getAsBoolean(); } catch (Exception ignored) { return false; } }
    private static float midpoint(float a, float b) { return (a + b) * 0.5F; }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
