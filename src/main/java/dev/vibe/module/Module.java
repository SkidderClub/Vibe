package dev.vibe.module;

import dev.vibe.Vibe;
import dev.vibe.language.LanguageManager;
import dev.vibe.setting.Setting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {

    private final String name;
    private final String description;
    private final Category category;
    private int key;
    private boolean enabled;
    private final List<Setting<?>> settings = new ArrayList<Setting<?>>();

    protected Module(String name, String description, Category category, int key) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.key = key;
    }

    public void toggle() {
        if (!isToggleable()) {
            return;
        }
        setEnabled(!enabled);
    }

    public void setEnabled(boolean enabled) {
        if (!enabled && !isToggleable()) {
            return;
        }
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
        if (Vibe.getInstance() != null && Vibe.getInstance().getConfig() != null
                && Vibe.getInstance().getModuleManager() != null && !Vibe.getInstance().getConfig().isLoading()) {
            Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
        }
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /** One-shot GUI modules must never reopen while a profile is being applied. */
    protected final boolean isConfigLoading() {
        return Vibe.getInstance() != null && Vibe.getInstance().getConfig() != null
                && Vibe.getInstance().getConfig().isLoading();
    }

    public String getName() {
        return LanguageManager.translate(name);
    }

    /** Stable, untranslated identifier text used by config and commands. */
    public String getRawName() { return name; }

    public String getDescription() {
        return LanguageManager.translate(description);
    }

    public Category getCategory() {
        return category;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
        if (Vibe.getInstance() != null && Vibe.getInstance().getConfig() != null
                && Vibe.getInstance().getModuleManager() != null && !Vibe.getInstance().getConfig().isLoading()) {
            Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Modules such as Targets provide shared settings and must always run. */
    public boolean isToggleable() {
        return true;
    }

    protected <T extends Setting<?>> T addSetting(T setting) {
        settings.add(setting);
        return setting;
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    public String getId() {
        return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "").replace(" ", "");
    }
}
