package dev.vibe.cosmetic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A local outfit made from authored Cosmetica catalog entries.  It deliberately
 * stores references to the original model/texture URLs instead of invented
 * replacement geometry, so presets remain usable even when the public catalog
 * receives new accessories.
 */
public final class CosmeticPreset {
    private final String id;
    private String name;
    private String skinName = "xHeist_";
    private boolean onlyThirdPerson;
    private final List<CosmeticaAccessory> accessories = new ArrayList<CosmeticaAccessory>();

    public CosmeticPreset(String id, String name) {
        this.id = id;
        setName(name);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String value) { name = sanitizeName(value); }
    public String getSkinName() { return skinName; }
    public void setSkinName(String value) { skinName = sanitizeSkin(value); }
    public boolean isOnlyThirdPerson() { return onlyThirdPerson; }
    public void setOnlyThirdPerson(boolean value) { onlyThirdPerson = value; }

    public synchronized List<CosmeticaAccessory> getAccessories() {
        return Collections.unmodifiableList(new ArrayList<CosmeticaAccessory>(accessories));
    }

    /** Add or replace an authored catalog entry by its stable Cosmetica id. */
    public synchronized void equip(CosmeticaAccessory accessory) {
        if (accessory == null || accessory.getId().isEmpty()) return;
        remove(accessory.getId());
        accessories.add(accessory.copy());
    }

    public synchronized void remove(String accessoryId) {
        if (accessoryId == null) return;
        for (Iterator<CosmeticaAccessory> it = accessories.iterator(); it.hasNext();) {
            if (accessoryId.equals(it.next().getId())) it.remove();
        }
    }

    public synchronized boolean contains(String accessoryId) {
        for (CosmeticaAccessory accessory : accessories) if (accessory.getId().equals(accessoryId)) return true;
        return false;
    }

    private static String sanitizeName(String value) {
        String clean = value == null ? "Preset" : value.trim();
        return clean.isEmpty() ? "Preset" : clean.length() > 24 ? clean.substring(0, 24) : clean;
    }

    private static String sanitizeSkin(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.matches("[A-Za-z0-9_]{1,16}") ? clean : "xHeist_";
    }
}
