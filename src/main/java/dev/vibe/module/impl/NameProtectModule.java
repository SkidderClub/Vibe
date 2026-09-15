package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.ColorSetting;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

/** Replaces the local account name in Vibe HUD and received chat with the chosen gamertag. */
public final class NameProtectModule extends Module {

    private static final EnumChatFormatting[] CHAT_COLORS = {
            EnumChatFormatting.BLACK, EnumChatFormatting.DARK_BLUE, EnumChatFormatting.DARK_GREEN,
            EnumChatFormatting.DARK_AQUA, EnumChatFormatting.DARK_RED, EnumChatFormatting.DARK_PURPLE,
            EnumChatFormatting.GOLD, EnumChatFormatting.GRAY, EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.BLUE, EnumChatFormatting.GREEN, EnumChatFormatting.AQUA,
            EnumChatFormatting.RED, EnumChatFormatting.LIGHT_PURPLE, EnumChatFormatting.YELLOW,
            EnumChatFormatting.WHITE
    };
    private static final int[] CHAT_COLOR_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private final ColorSetting nameColor = addSetting(new ColorSetting("Name Color", 0xFF2DE2C2));

    public NameProtectModule() {
        super("NameProtect", "Show your Vibe gamertag instead of the account name", Category.CLIENT, Keyboard.KEY_NONE);
        setEnabled(true);
    }

    public String getDisplayName(String original) {
        if (!isConfigured()) {
            return original;
        }
        if (Vibe.getInstance().getFriendManager() != null) {
            dev.vibe.friend.FriendManager.Friend friend = Vibe.getInstance().getFriendManager().find(original);
            if (friend != null) return friend.getAlias();
        }
        String accountName = Vibe.getInstance().getMinecraft().getSession().getUsername();
        if (original != null && !original.equalsIgnoreCase(accountName)) return original;
        return getColoredGamertag();
    }

    public String protectText(String text) {
        if (text == null) {
            return text;
        }
        String result = text;
        if (isConfigured()) {
            String accountName = Vibe.getInstance().getMinecraft().getSession().getUsername();
            if (accountName != null && !accountName.isEmpty() && !accountName.equals(Vibe.getInstance().getIdentity().getGamertag())) {
                result = replacePreserving(result, accountName, getColoredGamertag());
            }
        }
        if (Vibe.getInstance().getFriendManager() != null) {
            for (dev.vibe.friend.FriendManager.Friend friend : Vibe.getInstance().getFriendManager().getFriends()) {
                if (!friend.getName().equalsIgnoreCase(friend.getAlias())) result = replacePreserving(result, friend.getName(), friend.getAlias());
            }
        }
        return result;
    }

    private String replacePreserving(String text, String original, String replacement) {
        if (original == null || original.isEmpty() || replacement == null || replacement.isEmpty()) return text;
        StringBuilder result = new StringBuilder(text.length() + 16);
        int from = 0;
        int index;
        while ((index = text.indexOf(original, from)) >= 0) {
            result.append(text, from, index);
            // Re-apply the format which was active before the account name.
            // This keeps the selected colour restricted to the replacement,
            // instead of recolouring the remainder of the chat component.
            String restore = activeFormatting(text, index);
            result.append(replacement);
            result.append(restore.isEmpty() ? EnumChatFormatting.RESET : restore);
            from = index + original.length();
        }
        return from == 0 ? text : result.append(text, from, text.length()).toString();
    }

    public boolean isConfigured() {
        return isEnabled() && Vibe.getInstance() != null && Vibe.getInstance().getIdentity() != null
                && Vibe.getInstance().getIdentity().isConfigured();
    }

    public String getColoredGamertag() {
        if (!isConfigured()) {
            return "";
        }
        return nearestChatColor() + Vibe.getInstance().getIdentity().getGamertag();
    }

    public ColorSetting getNameColor() {
        return nameColor;
    }

    private EnumChatFormatting nearestChatColor() {
        int red = nameColor.getRed();
        int green = nameColor.getGreen();
        int blue = nameColor.getBlue();
        int selected = 0;
        int difference = Integer.MAX_VALUE;
        for (int index = 0; index < CHAT_COLOR_RGB.length; index++) {
            int color = CHAT_COLOR_RGB[index];
            int redDifference = red - ((color >>> 16) & 255);
            int greenDifference = green - ((color >>> 8) & 255);
            int blueDifference = blue - (color & 255);
            int distance = redDifference * redDifference + greenDifference * greenDifference + blueDifference * blueDifference;
            if (distance < difference) {
                difference = distance;
                selected = index;
            }
        }
        return CHAT_COLORS[selected];
    }

    private String activeFormatting(String text, int end) {
        String color = "";
        StringBuilder styles = new StringBuilder();
        for (int index = 0; index + 1 < end; index++) {
            if (text.charAt(index) != '\u00A7') {
                continue;
            }
            char code = Character.toLowerCase(text.charAt(++index));
            if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                color = "\u00A7" + code;
                styles.setLength(0);
            } else if (code == 'r') {
                color = "";
                styles.setLength(0);
            } else if ((code >= 'k' && code <= 'o') && styles.indexOf("\u00A7" + code) < 0) {
                styles.append('\u00A7').append(code);
            }
        }
        return color + styles.toString();
    }
}
