package keystrokesmod.script.model;

import com.google.common.collect.Iterables;
import com.mojang.authlib.properties.Property;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScorePlayerTeam;

public class NetworkPlayer {
    private final NetworkPlayerInfo playerInfo;
    private static final HashMap<String, NetworkPlayer> CACHE = new HashMap<String, NetworkPlayer>();
    public NetworkPlayer(NetworkPlayerInfo value){playerInfo=value;}
    public String getCape(){return playerInfo==null||playerInfo.getLocationCape()==null?"":playerInfo.getLocationCape().getResourcePath();}
    public String getDisplayName(){if(playerInfo==null)return "";return playerInfo.getDisplayName()==null?ScorePlayerTeam.formatPlayerName(playerInfo.getPlayerTeam(),getName()):playerInfo.getDisplayName().getUnformattedText();}
    public String getName(){return playerInfo==null?"":playerInfo.getGameProfile().getName();} public int getPing(){return playerInfo==null?0:playerInfo.getResponseTime();}
    public String getSkinData(){if(playerInfo==null)return null;Property texture=Iterables.getFirst(playerInfo.getGameProfile().getProperties().get("textures"),null);return texture==null?null:new String(Base64.getDecoder().decode(texture.getValue().getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8);}
    public String getUUID(){return playerInfo==null?"":playerInfo.getGameProfile().getId().toString();}
    public static NetworkPlayer convert(NetworkPlayerInfo value){if(value==null)return null;String key=value.getGameProfile().getId().toString();NetworkPlayer result=CACHE.get(key);if(result==null){result=new NetworkPlayer(value);CACHE.put(key,result);}return result;} public static void clearCache(){CACHE.clear();}
}
