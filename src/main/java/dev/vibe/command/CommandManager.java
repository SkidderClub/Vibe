package dev.vibe.command;

import dev.vibe.Vibe;
import dev.vibe.config.VibeConfig;
import dev.vibe.module.Module;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;

/** Local dot-prefixed commands. They never leave the client as chat messages. */
public final class CommandManager {

    private static final List<String> ROOTS = Arrays.asList(".toggle", ".t", ".bind", ".b", ".binds", ".config", ".c", ".ign", ".waifu", ".friend", ".f", ".target", ".script", ".scripts", ".source", ".help");

    public boolean execute(String message) {
        if (message == null || !message.trim().startsWith(".")) {
            return false;
        }
        String[] parts = message.trim().split("\\s+");
        String command = parts[0].toLowerCase();
        if (".toggle".equals(command) || ".t".equals(command)) {
            toggle(parts);
        } else if (".bind".equals(command) || ".b".equals(command)) {
            bind(parts);
        } else if (".binds".equals(command)) {
            binds();
        } else if (".config".equals(command) || ".c".equals(command)) {
            config(parts);
        } else if (".ign".equals(command)) {
            copyIgn();
        } else if (".waifu".equals(command)) {
            Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.WaifuModule.class).getSettings();
            if (parts.length > 1 && "openfolder".equalsIgnoreCase(parts[1])) {
                Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.WaifuModule.class).getFolder().mkdirs();
                try {
                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().open(Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.WaifuModule.class).getFolder());
                    }
                } catch (Exception ignored) {
                }
            }
        } else if (".friend".equals(command) || ".f".equals(command)) {
            friend(parts);
        } else if (".target".equals(command)) {
            target(parts);
        } else if (".script".equals(command) || ".scripts".equals(command)) {
            script(parts);
        } else if (".source".equals(command)) {
            say("Vibe: GPLv3. Schizoid Fog, Torus and media HUD: AGPLv3. Open Licenses & credits in the main or pause menu.");
            say("Matching source: " + dev.vibe.ui.LicenseDocuments.sourceArchive() + ", supplied beside this build by its distributor.");
            ChatComponentText link = new ChatComponentText("Vibe source repository (local builds may differ)");
            link.getChatStyle().setUnderlined(true).setChatClickEvent(new net.minecraft.event.ClickEvent(
                    net.minecraft.event.ClickEvent.Action.OPEN_URL, dev.vibe.ui.LicenseDocuments.REPOSITORY));
            if (Minecraft.getMinecraft().thePlayer != null) Minecraft.getMinecraft().thePlayer.addChatMessage(link);
        } else if (".help".equals(command)) {
            help();
        } else {
            say("Unknown command. Use §b.help§7 for available commands.");
        }
        return true;
    }

    public List<String> getSuggestions(String input) {
        if (input == null || !input.startsWith(".")) {
            return Collections.emptyList();
        }
        String[] parts = input.split("\\s+", -1);
        List<String> results = new ArrayList<String>();
        if (parts.length <= 1) {
            addMatching(results, ROOTS, input.toLowerCase());
            return results;
        }
        String root = parts[0].toLowerCase();
        String needle = parts[parts.length - 1].toLowerCase();
        if (".toggle".equals(root) || ".t".equals(root)) {
            String payload = input.substring(root.length()).trim();
            String compact = compact(payload);
            for (Module module : Vibe.getInstance().getModuleManager().getModules()) {
                if (compact.isEmpty() || module.getId().startsWith(compact)) results.add(root + " " + module.getId());
            }
        } else if (".bind".equals(root) || ".b".equals(root)) {
            String payload = input.substring(root.length()).trim();
            int separator = payload.lastIndexOf(' ');
            String moduleText = separator < 0 ? payload : payload.substring(0, separator).trim();
            String keyNeedle = separator < 0 ? "" : payload.substring(separator + 1).toLowerCase();
            Module exact = findModule(moduleText);
            if (exact != null && separator >= 0) {
                for (int index = 0; index < Keyboard.KEYBOARD_SIZE; index++) {
                    String name = Keyboard.getKeyName(index);
                    if (name != null && name.toLowerCase().startsWith(keyNeedle)) {
                        results.add(replaceLastToken(input, name.toLowerCase()));
                    }
                }
                if ("none".startsWith(keyNeedle)) results.add(replaceLastToken(input, "none"));
            } else {
                String compact = compact(payload);
                for (Module module : Vibe.getInstance().getModuleManager().getModules()) {
                    if (compact.isEmpty() || module.getId().startsWith(compact)) results.add(root + " " + module.getId());
                }
            }
        } else if (".config".equals(root) || ".c".equals(root)) {
            if (parts.length == 2) {
                addMatchingTokens(results, input, Arrays.asList("save", "load", "list", "delete", "rename", "openFolder"), needle);
            } else if (parts.length >= 3 && ("load".equalsIgnoreCase(parts[1]) || "delete".equalsIgnoreCase(parts[1]))) {
                for (String profile : Vibe.getInstance().getConfig().list()) {
                    if (profile.toLowerCase().startsWith(needle)) {
                        results.add(replaceLastToken(input, profile));
                    }
                }
            }
        } else if (".friend".equals(root) || ".f".equals(root)) {
            if (parts.length == 2) addMatchingTokens(results, input, Arrays.asList("add", "remove", "rename", "list"), needle);
            else if (parts.length >= 3 && ("remove".equalsIgnoreCase(parts[1]) || "rename".equalsIgnoreCase(parts[1]))) {
                for (dev.vibe.friend.FriendManager.Friend friend : Vibe.getInstance().getFriendManager().getFriends()) {
                    if (friend.getName().toLowerCase().startsWith(needle)) results.add(replaceLastToken(input, friend.getName()));
                }
            }
        } else if (".target".equals(root)) {
            if (parts.length == 2) addMatchingTokens(results, input, Arrays.asList("add", "remove", "list"), needle);
            else if (parts.length >= 3 && "remove".equalsIgnoreCase(parts[1])) for (String target : Vibe.getInstance().getTargetManager().getTargets()) {
                if (target.toLowerCase().startsWith(needle)) results.add(replaceLastToken(input, target));
            }
        } else if (".script".equals(root) || ".scripts".equals(root)) {
            if (parts.length == 2) addMatchingTokens(results, input, Arrays.asList("list", "create", "reload", "load", "errors", "delete", "rename", "enable", "disable", "openFolder"), needle);
            else if (parts.length >= 3 && !"create".equalsIgnoreCase(parts[1])) {
                for (dev.vibe.script.ScriptRuntime.ScriptInfo info : Vibe.getInstance().getScriptRuntime().getScripts()) {
                    if (info.getName().toLowerCase().startsWith(needle)) results.add(replaceLastToken(input, info.getName()));
                }
            }
        }
        return results;
    }

    public String firstSuggestion(String input) {
        List<String> suggestions = getSuggestions(input);
        return suggestions.isEmpty() ? null : suggestions.get(0);
    }

    private void toggle(String[] parts) {
        if (parts.length < 2) {
            say("Usage: §b.toggle <module>");
            return;
        }
        String moduleName = joinParts(parts, 1, parts.length);
        Module module = findModule(moduleName);
        if (module == null) {
            say("No module named §c" + moduleName);
            return;
        }
        module.toggle();
        say("§f" + module.getName() + " §7is now " + (module.isEnabled() ? "§aenabled" : "§cdisabled"));
    }

    private void bind(String[] parts) {
        if (parts.length < 3) {
            say("Usage: §b.bind <module> <key|none>");
            return;
        }
        String keyText = parts[parts.length - 1];
        String moduleName = joinParts(parts, 1, parts.length - 1);
        Module module = findModule(moduleName);
        if (module == null) {
            say("No module named §c" + moduleName);
            return;
        }
        int key = "none".equalsIgnoreCase(keyText) ? Keyboard.KEY_NONE : Keyboard.getKeyIndex(keyText.toUpperCase());
        if (key == Keyboard.KEY_NONE && !"none".equalsIgnoreCase(keyText)) {
            say("Unknown key §c" + keyText);
            return;
        }
        module.setKey(key);
        say("§f" + module.getName() + " §7bound to §b" + Keyboard.getKeyName(key));
    }

    private void binds() {
        boolean found = false;
        for (Module module : Vibe.getInstance().getModuleManager().getModules()) {
            if (module.getKey() != Keyboard.KEY_NONE) {
                say("§b" + Keyboard.getKeyName(module.getKey()) + " §8» §f" + module.getName());
                found = true;
            }
        }
        if (!found) {
            say("No module keybinds are set.");
        }
    }

    private void config(String[] parts) {
        VibeConfig config = Vibe.getInstance().getConfig();
        if (parts.length < 2 || "list".equalsIgnoreCase(parts[1])) {
            List<String> profiles = config.list();
            say("Profiles: §b" + (profiles.isEmpty() ? "none" : join(profiles)) + " §7(active: " + config.getActiveName() + ")");
            return;
        }
        String action = parts[1].toLowerCase();
        if ("save".equals(action) && parts.length >= 3) {
            say(config.save(parts[2], Vibe.getInstance().getModuleManager()) ? "Saved §b" + parts[2] : "§cCould not save profile.");
        } else if ("load".equals(action) && parts.length >= 3) {
            say(config.load(parts[2], Vibe.getInstance().getModuleManager()) ? "Loaded §b" + parts[2] : "§cProfile not found or invalid.");
        } else if ("delete".equals(action) && parts.length >= 3) {
            say(config.delete(parts[2]) ? "Deleted §b" + parts[2] : "§cCould not delete profile.");
        } else if ("rename".equals(action) && parts.length >= 4) {
            say(config.rename(parts[2], parts[3]) ? "Renamed profile to §b" + parts[3] : "§cCould not rename profile.");
        } else if ("openfolder".equals(action)) {
            say(config.openDirectory() ? "Opened config folder." : "§cCould not open folder: " + config.getDirectory().getAbsolutePath());
        } else {
            say("Usage: §b.config <save|load|list|delete|rename|openFolder> [name]");
        }
    }

    private void help() {
        say(".source - source archive, repository and GPLv3/AGPLv3 licenses");
        say("§b.t <module>§7 — toggle a module");
        say("§b.b <module> <key|none>§7 — set a module keybind");
        say("§b.binds§7 — display all active keybinds");
        say("§b.c <save|load|list|delete|rename|openFolder>§7 — manage JSON profiles");
        say("§b.ign§7 — copy your in-game name to the clipboard");
        say("§b.f <add|remove|rename|list>§7 — manage friends and aliases");
        say("§b.target <add|remove|list>§7 — manage priority targets");
        say("§b.script <create|reload|load|list|errors|enable|disable|rename|delete|openFolder>§7 — manage local Raven scripts");
        say("§8Press Tab in chat for completion.");
    }

    private void friend(String[] parts) {
        dev.vibe.friend.FriendManager friends = Vibe.getInstance().getFriendManager();
        if (parts.length < 2 || "list".equalsIgnoreCase(parts[1])) {
            List<dev.vibe.friend.FriendManager.Friend> list = friends.getFriends();
            if (list.isEmpty()) { say("No friends saved."); return; }
            for (dev.vibe.friend.FriendManager.Friend friend : list) say("§a" + friend.getName() + " §7(" + friend.getAlias() + ") §8added " + friend.getAddedDate());
            return;
        }
        String action = parts[1].toLowerCase();
        if ("add".equals(action) && parts.length >= 3) {
            String alias = parts.length >= 4 ? parts[3] : parts[2];
            say(friends.add(parts[2], alias) ? "Added friend §a" + parts[2] : "§cFriend is invalid or already saved.");
        } else if ("remove".equals(action) && parts.length >= 3) {
            say(friends.remove(parts[2]) ? "Removed friend §c" + parts[2] : "§cFriend not found.");
        } else if ("rename".equals(action) && parts.length >= 4) {
            say(friends.rename(parts[2], parts[3]) ? "Renamed friend alias to §b" + parts[3] : "§cFriend or alias is invalid.");
        } else say("Usage: §b.friend <add <ign> [alias]|remove <ign>|rename <ign> <alias>|list>");
    }

    private void target(String[] parts) {
        dev.vibe.target.TargetManager targets = Vibe.getInstance().getTargetManager();
        if (parts.length < 2 || "list".equalsIgnoreCase(parts[1])) { say("Priority targets: §c" + (targets.getTargets().isEmpty() ? "none" : join(new ArrayList<String>(targets.getTargets())))); return; }
        if ("add".equalsIgnoreCase(parts[1]) && parts.length >= 3) say(targets.add(parts[2]) ? "Added priority target §c" + parts[2] : "§cTarget is invalid or already saved.");
        else if ("remove".equalsIgnoreCase(parts[1]) && parts.length >= 3) say(targets.remove(parts[2]) ? "Removed priority target §7" + parts[2] : "§cTarget not found.");
        else say("Usage: §b.target <add|remove|list> [ign]");
    }

    private void script(String[] parts) {
        dev.vibe.script.ScriptRuntime runtime = Vibe.getInstance().getScriptRuntime();
        if (runtime == null) { say("§cScript runtime is not available yet."); return; }
        if (parts.length < 2 || "list".equalsIgnoreCase(parts[1])) {
            List<dev.vibe.script.ScriptRuntime.ScriptInfo> scripts = runtime.getScripts();
            if (scripts.isEmpty()) say("No scripts loaded. §b.script create <name>");
            else for (dev.vibe.script.ScriptRuntime.ScriptInfo info : scripts) say("§b" + info.getName() + " §7— " + (info.isEnabled() ? "§aenabled" : info.isLoaded() ? "§7disabled" : "§cerror"));
            return;
        }
        String action = parts[1].toLowerCase(java.util.Locale.ROOT);
        if ("reload".equals(action) || "load".equals(action)) { runtime.reload(); say("Reloaded §b" + runtime.getScripts().size() + " §7script(s)."); return; }
        if ("errors".equals(action)) { List<String> diagnostics=runtime.getDiagnostics(); if(diagnostics.isEmpty())say("§aNo script diagnostics."); else for(String message:diagnostics)say("§c"+message); return; }
        if ("openfolder".equals(action)) { say(runtime.openDirectory() ? "Opened scripts folder." : "§cCould not open scripts folder: " + runtime.getDirectory().getAbsolutePath()); return; }
        if ("create".equals(action) && parts.length >= 3) { String name=joinParts(parts,2,parts.length); String made=runtime.create(name); say(made==null?"§cCould not create script.":"Created §b"+made+".java §7in the scripts folder."); return; }
        if ("delete".equals(action) && parts.length >= 3) { String name=joinParts(parts,2,parts.length); say(runtime.delete(name)?"Deleted §b"+name:"§cScript not found."); return; }
        if (("enable".equals(action)||"disable".equals(action)) && parts.length >= 3) { String name=joinParts(parts,2,parts.length); dev.vibe.script.ScriptModule module=runtime.getModule(name); if(module==null){say("§cScript not found.");return;} module.setEnabled("enable".equals(action));say("§b"+module.getRawName()+" §7is now "+(module.isEnabled()?"§aenabled":"§cdisabled"));return; }
        if ("rename".equals(action) && parts.length >= 4) { String old=parts[2]; String next=joinParts(parts,3,parts.length); say(runtime.rename(old,next)?"Renamed §b"+old+" §7to §b"+next:"§cCould not rename script."); return; }
        say("Usage: §b.script <create|reload|load|list|errors|enable|disable|rename|delete|openFolder> [name]");
    }

    private void copyIgn() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.getSession() == null || minecraft.getSession().getUsername() == null
                || minecraft.getSession().getUsername().trim().isEmpty()) {
            say("§cNo in-game name is available.");
            return;
        }
        try {
            String name = minecraft.getSession().getUsername();
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(name), null);
            say("Copied §b" + name + " §7to the clipboard.");
        } catch (Exception ignored) {
            say("§cCould not access the system clipboard.");
        }
    }

    private void say(String text) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null) {
            minecraft.thePlayer.addChatMessage(new ChatComponentText(rainbowPrefix() + " §7" + text));
        }
    }

    private String rainbowPrefix() {
        String[] colors = {"§c", "§6", "§e", "§a", "§b", "§9", "§d"};
        int offset = (int) ((System.currentTimeMillis() / 120L) % colors.length);
        String word = "Vibe";
        StringBuilder builder = new StringBuilder("§8[");
        for (int index = 0; index < word.length(); index++) {
            builder.append(colors[(offset + index) % colors.length]).append(word.charAt(index));
        }
        return builder.append("§8]").toString();
    }

    private void addMatching(List<String> output, List<String> options, String needle) {
        for (String option : options) {
            if (option.toLowerCase().startsWith(needle.toLowerCase())) {
                output.add(option);
            }
        }
    }

    private void addMatchingTokens(List<String> output, String input, List<String> options, String needle) {
        for (String option : options) {
            if (option.toLowerCase().startsWith(needle.toLowerCase())) output.add(replaceLastToken(input, option));
        }
    }

    private Module findModule(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        Module exact = Vibe.getInstance().getModuleManager().getModule(trimmed);
        if (exact != null) return exact;
        String id = compact(trimmed);
        for (Module module : Vibe.getInstance().getModuleManager().getModules()) {
            if (module.getId().equalsIgnoreCase(id)) return module;
        }
        return null;
    }

    private String compact(String text) {
        return text == null ? "" : text.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String joinParts(String[] parts, int from, int to) {
        StringBuilder value = new StringBuilder();
        for (int i = from; i < to && i < parts.length; i++) {
            if (value.length() > 0) value.append(' ');
            value.append(parts[i]);
        }
        return value.toString();
    }

    private String replaceLastToken(String input, String replacement) {
        int end = input.length();
        int start = input.lastIndexOf(' ') + 1;
        return input.substring(0, start) + replacement;
    }

    private String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append("§7, §b");
            }
            builder.append(value);
        }
        return builder.toString();
    }
}
