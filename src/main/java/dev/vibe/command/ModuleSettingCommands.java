package dev.vibe.command;

import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import dev.vibe.setting.Setting;
import dev.vibe.setting.StringSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parsing and validation for direct module setting commands. */
final class ModuleSettingCommands {
    private ModuleSettingCommands() { }

    static final class Result {
        final boolean changed;
        final String message;

        Result(boolean changed, String message) {
            this.changed = changed;
            this.message = message;
        }
    }

    static Result apply(Module module, String payload) {
        String text = payload == null ? "" : payload.trim();
        if (text.isEmpty()) {
            return error("Usage: §b." + module.getId() + " <setting> <value>§7. Settings: §b" + settingNames(module));
        }
        String[] words = text.split("\\s+");
        Match match = findSetting(module, words);
        if (match != null) {
            Setting<?> setting = match.setting;
            String value = join(words, match.words, words.length);
            if (setting instanceof RangeSetting) return range((RangeSetting) setting, value, module);
            if (setting instanceof MultiSelectSetting) return multi((MultiSelectSetting) setting, value, module);
            if (value.isEmpty()) return error("§cMissing value for " + setting.getRawName() + ". Usage: §b." + module.getId() + " " + setting.getRawName() + " <value>");
            String label = module.getRawName() + " §8» §f" + setting.getRawName();
            if (setting instanceof BooleanSetting) {
                BooleanSetting booleanSetting = (BooleanSetting) setting;
                Boolean next = parseBoolean(value, booleanSetting.isEnabled());
                if (next == null) return error("§cExpected true/false, on/off, or toggle for " + setting.getRawName() + ".");
                if (booleanSetting.isEnabled() == next.booleanValue()) return unchanged(label, next ? "enabled" : "disabled");
                booleanSetting.setEnabled(next);
                return changed(label, next ? "enabled" : "disabled");
            }
            if (setting instanceof NumberSetting) {
                NumberSetting number = (NumberSetting) setting;
                Double next = parseNumber(value);
                if (next == null) return error("§cExpected a finite number for " + setting.getRawName() + ".");
                if (next < number.getMinimum() || next > number.getMaximum()) return error("§c" + setting.getRawName() + " must be between " + format(number.getMinimum()) + " and " + format(number.getMaximum()) + ".");
                double old = number.getDouble();
                number.setValue(next);
                return old == number.getDouble() ? unchanged(label, format(number.getDouble())) : changed(label, format(number.getDouble()));
            }
            if (setting instanceof ModeSetting) {
                ModeSetting mode = (ModeSetting) setting;
                String option = option(mode.getModes(), value);
                if (option == null) return error("§cUnknown mode for " + setting.getRawName() + ". Choose: §b" + names(mode.getModes()));
                if (mode.is(option)) return unchanged(label, option);
                mode.setValue(option);
                return changed(label, option);
            }
            if (setting instanceof ColorSetting) {
                ColorSetting color = (ColorSetting) setting;
                int old = color.getArgb();
                if (!color.setHex(value)) return error("§cExpected #RRGGBB or #RRGGBBAA for " + setting.getRawName() + ".");
                return old == color.getArgb() ? unchanged(label, color.getHex()) : changed(label, color.getHex());
            }
            if (setting instanceof StringSetting) {
                StringSetting string = (StringSetting) setting;
                String next = "\"\"".equals(value) ? "" : value;
                if (next.length() > string.getMaxLength()) return error("§c" + setting.getRawName() + " is limited to " + string.getMaxLength() + " characters.");
                if (next.equals(string.getValue())) return unchanged(label, next.isEmpty() ? "empty" : next);
                string.setValue(next);
                return changed(label, next.isEmpty() ? "empty" : next);
            }
            return error("§cThis setting cannot be changed with a chat command.");
        }
        if ("min".equalsIgnoreCase(words[0]) || "max".equalsIgnoreCase(words[0])) {
            RangeSetting only = null;
            for (Setting<?> setting : module.getSettings()) if (setting instanceof RangeSetting) {
                if (only != null) return error("§cSeveral ranges exist. Use §b." + module.getId() + " <range setting> <min|max> <value>§c.");
                only = (RangeSetting) setting;
            }
            if (only != null) return range(only, text, module);
        }
        // A multi-select option may be written directly after the module name.
        MultiSelectSetting owner = null;
        String selected = null;
        for (Setting<?> setting : module.getSettings()) if (setting instanceof MultiSelectSetting) {
            String candidate = option(((MultiSelectSetting) setting).getOptions(), text);
            if (candidate != null) {
                if (owner != null) return error("§cThat option belongs to multiple settings. Use §b." + module.getId() + " <setting> " + candidate + "§c.");
                owner = (MultiSelectSetting) setting;
                selected = candidate;
            }
        }
        if (owner != null) return toggleOption(module, owner, selected, null);
        return error("§cUnknown setting or option for " + module.getRawName() + ". Settings: §b" + settingNames(module));
    }

    static List<String> suggestions(Module module, String input, String payload) {
        List<String> result = new ArrayList<String>();
        String trimmed = payload.trim();
        String prefix = input.substring(0, input.length() - payload.length());
        String[] words = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
        boolean afterSpace = !payload.isEmpty() && Character.isWhitespace(payload.charAt(payload.length() - 1));
        Match match = findSetting(module, words);
        if (match != null && (match.words < words.length || afterSpace)) {
            Setting<?> setting = match.setting;
            String valuePrefix = join(words, match.words, words.length);
            String base = prefix + setting.getRawName() + " ";
            if (setting instanceof RangeSetting) {
                String[] rangeWords = valuePrefix.isEmpty() ? new String[0] : valuePrefix.split("\\s+");
                if (rangeWords.length == 0 || (rangeWords.length == 1 && !afterSpace)) {
                    addOptions(result, base, valuePrefix, java.util.Arrays.asList("min", "max"));
                } else addOptions(result, base + rangeWords[0] + " ", rangeWords.length > 1 ? rangeWords[1] : "", java.util.Arrays.asList(format(((RangeSetting) setting).getMinimum()), format(((RangeSetting) setting).getMaximum())));
            } else if (setting instanceof ModeSetting) addOptions(result, base, valuePrefix, ((ModeSetting) setting).getModes());
            else if (setting instanceof MultiSelectSetting) addOptions(result, base, valuePrefix, ((MultiSelectSetting) setting).getOptions());
            else if (setting instanceof BooleanSetting) addOptions(result, base, valuePrefix, java.util.Arrays.asList("true", "false", "toggle"));
            else if (setting instanceof NumberSetting) addOptions(result, base, valuePrefix, java.util.Arrays.asList(format(((NumberSetting) setting).getMinimum()), format(((NumberSetting) setting).getMaximum())));
            else if (setting instanceof ColorSetting) result.add(base + ((ColorSetting) setting).getHex());
            return result;
        }
        for (Setting<?> setting : module.getSettings()) {
            if (compact(setting.getRawName()).startsWith(compact(trimmed))) result.add(prefix + setting.getRawName());
        }
        int ranges = 0;
        RangeSetting onlyRange = null;
        for (Setting<?> setting : module.getSettings()) if (setting instanceof RangeSetting) { ranges++; onlyRange = (RangeSetting) setting; }
        if (ranges == 1) addOptions(result, prefix, trimmed, java.util.Arrays.asList("min", "max"));
        if (ranges == 1 && words.length >= 1 && ("min".equalsIgnoreCase(words[0]) || "max".equalsIgnoreCase(words[0]))
                && (afterSpace || words.length > 1)) {
            addOptions(result, prefix + words[0] + " ", words.length > 1 ? words[1] : "",
                    java.util.Arrays.asList(format(onlyRange.getMinimum()), format(onlyRange.getMaximum())));
        }
        for (Setting<?> setting : module.getSettings()) if (setting instanceof MultiSelectSetting) {
            addOptions(result, prefix, trimmed, ((MultiSelectSetting) setting).getOptions());
        }
        return result;
    }

    private static Result range(RangeSetting range, String text, Module module) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length != 2 || !("min".equalsIgnoreCase(parts[0]) || "max".equalsIgnoreCase(parts[0]))) {
            return error("Usage: §b." + module.getId() + " " + range.getRawName() + " <min|max> <value>");
        }
        Double next = parseNumber(parts[1]);
        if (next == null) return error("§cExpected a finite number for " + range.getRawName() + ".");
        if (next < range.getMinimum() || next > range.getMaximum()) return error("§c" + range.getRawName() + " must be between " + format(range.getMinimum()) + " and " + format(range.getMaximum()) + ".");
        double oldMin = range.getMin(), oldMax = range.getMax();
        if ("min".equalsIgnoreCase(parts[0])) range.setMin(next); else range.setMax(next);
        String label = module.getRawName() + " §8» §f" + range.getRawName();
        String current = format(range.getMin()) + "–" + format(range.getMax());
        return oldMin == range.getMin() && oldMax == range.getMax() ? unchanged(label, current) : changed(label, current);
    }

    private static Result multi(MultiSelectSetting setting, String value, Module module) {
        String optionText = value;
        Boolean desired = null;
        int last = value.lastIndexOf(' ');
        if (last > 0) {
            String state = value.substring(last + 1);
            if ("true".equalsIgnoreCase(state) || "false".equalsIgnoreCase(state) || "on".equalsIgnoreCase(state) || "off".equalsIgnoreCase(state)) {
                desired = parseBoolean(state, false);
                optionText = value.substring(0, last);
            }
        }
        String option = option(setting.getOptions(), optionText);
        if (option == null) return error("§cUnknown option for " + setting.getRawName() + ". Choose: §b" + names(setting.getOptions()));
        return toggleOption(module, setting, option, desired);
    }

    private static Result toggleOption(Module module, MultiSelectSetting setting, String option, Boolean desired) {
        boolean current = setting.isSelectedIgnoreCase(option);
        String label = module.getRawName() + " §8» §f" + setting.getRawName() + " / " + option;
        if (desired != null && current == desired.booleanValue()) return unchanged(label, current ? "enabled" : "disabled");
        setting.toggle(option);
        return changed(label, setting.isSelectedIgnoreCase(option) ? "enabled" : "disabled");
    }

    private static Match findSetting(Module module, String[] words) {
        Match best = null;
        for (Setting<?> setting : module.getSettings()) {
            for (int count = 1; count <= words.length; count++) {
                if (compact(join(words, 0, count)).equals(compact(setting.getRawName()))
                        && (best == null || count > best.words)) best = new Match(setting, count);
            }
        }
        return best;
    }

    private static final class Match {
        final Setting<?> setting;
        final int words;
        Match(Setting<?> setting, int words) { this.setting = setting; this.words = words; }
    }

    private static Double parseNumber(String text) {
        try {
            double value = Double.parseDouble(text);
            return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
        } catch (NumberFormatException ignored) { return null; }
    }

    private static Boolean parseBoolean(String value, boolean current) {
        if ("toggle".equalsIgnoreCase(value)) return !current;
        if ("true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value)) return true;
        if ("false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) || "0".equals(value)) return false;
        return null;
    }

    private static String option(List<String> options, String text) {
        for (String option : options) if (option.equalsIgnoreCase(text) || compact(option).equals(compact(text))) return option;
        return null;
    }

    private static String compact(String text) { return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    private static String format(double number) { return Double.toString(number).replaceAll("\\.0$", ""); }
    private static String join(String[] words, int from, int to) {
        StringBuilder result = new StringBuilder();
        for (int i = from; i < to; i++) { if (result.length() > 0) result.append(' '); result.append(words[i]); }
        return result.toString();
    }
    private static String names(List<String> values) { return String.join(", ", values); }
    private static String settingNames(Module module) {
        List<String> names = new ArrayList<String>();
        for (Setting<?> setting : module.getSettings()) names.add(setting.getRawName());
        return names.isEmpty() ? "none" : names(names);
    }
    private static void addOptions(List<String> output, String prefix, String needle, List<String> choices) {
        for (String choice : choices) if (compact(choice).startsWith(compact(needle))) output.add(prefix + choice);
    }
    private static Result error(String message) { return new Result(false, message); }
    private static Result changed(String label, String value) { return new Result(true, "§aChanged §f" + label + " §7to §b" + value + "§7."); }
    private static Result unchanged(String label, String value) { return new Result(false, "§eNo change: §f" + label + " §7is already §b" + value + "§7."); }
}
