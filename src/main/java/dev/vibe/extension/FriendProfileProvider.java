package dev.vibe.extension;

import dev.vibe.friend.FriendManager.Friend;

/** Optional appearance choices displayed by the core friend editor. */
public interface FriendProfileProvider {
    String name(Friend friend);
    void cycle(Friend friend, boolean backwards);
}
