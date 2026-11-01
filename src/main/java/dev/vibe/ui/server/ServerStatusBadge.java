package dev.vibe.ui.server;

import dev.vibe.server.ServerJoinHistory;
import dev.vibe.server.ServerJoinTracker;
import dev.vibe.ui.menu.AccountScreenStyle;
import dev.vibe.ui.menu.MenuStatusChip;

/** A failed connection remains distinct from a confirmed ban. */
final class ServerStatusBadge {
    enum Kind {
        JOINED("Joined",0xFF7DDDC3),BANNED("Banned",0xFFF28594),FAILED("Failed",0xFFE4BB7E),UNKNOWN("Untried",0xFFA0A7B6),CONNECTING("Joining",0xFFB4A0FF);
        final String label;final int color;Kind(String label,int color){this.label=label;this.color=color;}
    }
    static Kind kind(ServerJoinHistory.Result result){
        switch(result.status){case JOINED:return Kind.JOINED;case FAILED:return ServerJoinTracker.isBanReason(result.reason)?Kind.BANNED:Kind.FAILED;case CONNECTING:return Kind.CONNECTING;default:return Kind.UNKNOWN;}
    }
    static void draw(int x,int y,Kind kind){
        int color=kind==Kind.CONNECTING?AccountScreenStyle.ACCENT:kind==Kind.UNKNOWN?AccountScreenStyle.MUTED:kind.color;
        MenuStatusChip.draw(x,y,76,18,dev.vibe.language.LanguageManager.translate(kind.label),color);
    }
}
