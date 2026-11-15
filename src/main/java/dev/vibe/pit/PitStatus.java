package dev.vibe.pit;

import java.util.*;
import java.util.regex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.*;
import net.minecraft.util.EnumChatFormatting;

/** Reads the actual sidebar (including team prefixes/suffixes), never the red score column. */
public final class PitStatus {
    public final int level,prestige;
    public final double gold;
    public final boolean active;
    private PitStatus(int level,int prestige,double gold,boolean active){this.level=level;this.prestige=prestige;this.gold=gold;this.active=active;}
    public static final PitStatus EMPTY=new PitStatus(-1,-1,0,false);
    private static final Pattern LEVEL=Pattern.compile("\\blevel\\s*[:»]?\\s*\\[?(?:(?:[IVXLCDM]+|\\d+)\\s*[-★]\\s*)?(\\d+)",Pattern.CASE_INSENSITIVE);
    private static final Pattern GOLD=Pattern.compile("\\bgold\\s*[:»]?\\s*([\\d][\\d,. ]*)",Pattern.CASE_INSENSITIVE);
    public static String plain(String text){return text==null?"":EnumChatFormatting.getTextWithoutFormattingCodes(text).replace('\u00a0',' ').replace('\u202f',' ').replace('\uff1a',':').replaceAll("[\\u200B-\\u200F\\u2060\\uFEFF]","").trim();}
    public static double amount(String text){try{return Double.parseDouble(text.replace(",", "").replace(" ","").trim());}catch(NumberFormatException e){return -1;}}
    public static PitStatus parse(Collection<String> lines,boolean rewoundConnection){
        int level=-1,prestige=-1;double gold=-1;boolean rewound=rewoundConnection;
        for(String raw:lines){String line=plain(raw);Matcher l=LEVEL.matcher(line),g=GOLD.matcher(line);
            if(l.find())try{level=Integer.parseInt(l.group(1));}catch(NumberFormatException ignored){}
            if(g.find())gold=amount(g.group(1));
            if(line.toLowerCase(Locale.ROOT).startsWith("prestige:"))prestige=roman(line.substring(line.indexOf(':')+1).trim());
            if(line.toLowerCase(Locale.ROOT).contains("rewound.cc"))rewound=true;
        }
        return new PitStatus(level,prestige,gold,rewound&&level>=0&&level<=120&&gold>=0&&Double.isFinite(gold));
    }
    public static PitStatus read(Minecraft mc){
        if(mc.theWorld==null||mc.thePlayer==null)return EMPTY;
        Scoreboard board=mc.theWorld.getScoreboard();if(board==null)return EMPTY;
        ScorePlayerTeam team=board.getPlayersTeam(mc.thePlayer.getName());ScoreObjective objective=null;
        if(team!=null&&team.getChatFormat().getColorIndex()>=0)objective=board.getObjectiveInDisplaySlot(3+team.getChatFormat().getColorIndex());
        if(objective==null)objective=board.getObjectiveInDisplaySlot(1);if(objective==null)return EMPTY;
        List<String> lines=new ArrayList<>();
        for(Score score:board.getSortedScores(objective))if(!score.getPlayerName().startsWith("#"))
            lines.add(ScorePlayerTeam.formatPlayerName(board.getPlayersTeam(score.getPlayerName()),score.getPlayerName()));
        String host=mc.getCurrentServerData()==null?"":mc.getCurrentServerData().serverIP.toLowerCase(Locale.ROOT).split(":")[0];
        boolean connection=host.replaceAll("\\.$","").equals("rewound.cc")||host.endsWith(".rewound.cc")||host.endsWith(".rewound.cc.");
        PitStatus parsed=parse(lines,connection);if(parsed.active)return parsed;
        // Proxy scoreboards sometimes retain the real sidebar in a colour-specific slot.
        for(int slot=1;slot<19;slot++){ScoreObjective alternative=board.getObjectiveInDisplaySlot(slot);if(alternative==null||alternative==objective)continue;
            for(Score score:board.getSortedScores(alternative))lines.add(ScorePlayerTeam.formatPlayerName(board.getPlayersTeam(score.getPlayerName()),score.getPlayerName()));
        }
        return parse(lines,connection);
    }
    private static int roman(String value){
        if(value.matches("\\d+"))try{return Integer.parseInt(value);}catch(NumberFormatException ignored){}
        if(!value.matches("[IVXLCDM]+"))return -1;
        int total=0,last=0;for(int i=value.length()-1;i>=0;i--){int n=new int[]{1,5,10,50,100,500,1000}["IVXLCDM".indexOf(value.charAt(i))];total+=n<last?-n:n;last=n;}return total;
    }
}
