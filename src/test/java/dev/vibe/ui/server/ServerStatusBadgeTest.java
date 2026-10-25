package dev.vibe.ui.server;

import dev.vibe.server.ServerJoinHistory;
import org.junit.Test;
import static org.junit.Assert.*;

public class ServerStatusBadgeTest {
    @Test public void bansAndConnectionFailuresHaveDifferentLabels(){
        assertEquals(ServerStatusBadge.Kind.BANNED,ServerStatusBadge.kind(new ServerJoinHistory.Result(ServerJoinHistory.Status.FAILED,0,"You are permanently banned")));
        assertEquals(ServerStatusBadge.Kind.FAILED,ServerStatusBadge.kind(new ServerJoinHistory.Result(ServerJoinHistory.Status.FAILED,0,"Connection refused")));
        assertEquals(ServerStatusBadge.Kind.JOINED,ServerStatusBadge.kind(new ServerJoinHistory.Result(ServerJoinHistory.Status.JOINED,0,"Joined successfully")));
        assertEquals(ServerStatusBadge.Kind.UNKNOWN,ServerStatusBadge.kind(new ServerJoinHistory.Result(ServerJoinHistory.Status.UNKNOWN,0,"")));
    }
}
