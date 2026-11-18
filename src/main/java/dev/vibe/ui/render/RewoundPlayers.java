package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.module.impl.client.QolModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.EnumChatFormatting;

/** Presentation only: never removes player info, entities or combat candidates. */
public final class RewoundPlayers {
    private RewoundPlayers() { }
    public static boolean filtering(String view) {
        Vibe vibe=Vibe.getInstance();
        QolModule qol=vibe==null||vibe.getModuleManager()==null?null:vibe.getModuleManager().getModule(QolModule.class);
        return qol!=null&&qol.isEnabled()&&qol.getFeatures().isSelected(QolModule.REAL_REWOUND_PLAYERS)
                &&qol.getRealPlayerViews().isSelected(view);
    }
    public static boolean starred(String name) {
        String plain=EnumChatFormatting.getTextWithoutFormattingCodes(name);
        return plain!=null&&plain.trim().startsWith("*");
    }
    public static boolean real(NetworkPlayerInfo info) {
        if(info==null||info.getGameProfile()==null)return false;
        String name=info.getDisplayName()!=null?info.getDisplayName().getUnformattedText()
                :ScorePlayerTeam.formatPlayerName(info.getPlayerTeam(),info.getGameProfile().getName());
        return starred(name);
    }
    public static boolean showEsp(Entity entity) {
        if(!(entity instanceof EntityPlayer)||!filtering("ESP"))return true;
        Minecraft mc=Minecraft.getMinecraft();
        return mc!=null&&mc.getNetHandler()!=null&&real(mc.getNetHandler().getPlayerInfo(entity.getUniqueID()));
    }
    public static List<NetworkPlayerInfo> tabEntries(List<NetworkPlayerInfo> original) {
        if(!filtering("Tab"))return original;
        List<NetworkPlayerInfo> visible=new ArrayList<>();
        for(NetworkPlayerInfo info:original)if(real(info))visible.add(info);
        return visible;
    }
}
