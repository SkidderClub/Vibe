package dev.vibe.setting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** A configurable ordered set, used for lists such as FastPlace's block blacklist. */
public final class MultiSelectSetting extends Setting<Set<String>> {

    private final List<String> options;

    public MultiSelectSetting(String name, Collection<String> options, Collection<String> selected) {
        this(name, options, selected, null);
    }

    public MultiSelectSetting(String name, Collection<String> options, Collection<String> selected,
            BooleanSupplier visibleWhen) {
        super(name, new LinkedHashSet<String>(selected == null ? Collections.<String>emptyList() : selected), visibleWhen);
        this.options = new ArrayList<String>(options == null ? Collections.<String>emptyList() : options);
        setValue(new LinkedHashSet<String>(selected == null ? Collections.<String>emptyList() : selected));
    }

    @Override
    protected Set<String> sanitize(Set<String> value) {
        return new LinkedHashSet<String>(value == null ? Collections.<String>emptySet() : value);
    }

    public List<String> getOptions() {
        return Collections.unmodifiableList(options);
    }

    public boolean isSelected(String value) {
        return getValue().contains(value);
    }

    public boolean isSelectedIgnoreCase(String value) {
        for (String selected : getValue()) {
            if (selected.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    public void toggle(String value) {
        Set<String> copy = new LinkedHashSet<String>(getValue());
        if (!copy.add(value)) {
            copy.remove(value);
        }
        setValue(copy);
    }

    public void addOption(String value) {
        if (value != null && !value.trim().isEmpty() && !options.contains(value)) {
            options.add(value);
        }
    }

    public Set<String> copyValue() {
        return new LinkedHashSet<String>(getValue());
    }
}
