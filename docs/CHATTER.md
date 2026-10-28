# Chatter

Enable **Client → Chatter** to react to incoming multiplayer chat. Toggle its
**Open Message Menu** setting to open the Skeet editor; the setting resets itself
after opening. Opening the editor does not enable Chatter. The top-right wheel
opens saved passwords and generation settings.

## Rules

Each rule has an enabled checkbox, trigger text, Message or Leave server action,
minimum/maximum delay in milliseconds, cooldown and optional Once per connection.
Add, duplicate, edit or delete rules in the left list. Save and Discard apply to
the current draft. Unsaved edits are protected when navigating or closing.

Triggers ignore case and match anywhere in the original chat text before Name
Protect changes its display. Minecraft formatting codes are stripped. `*` means
any intervening text, including nothing. Other punctuation is literal: `?` must
actually appear in the incoming message. Action-bar messages are ignored.

The default rules are enabled inside the initially disabled module:

| Trigger | Action | Message | Delay |
| --- | --- | --- | --- |
| `/register` | Message | `/register %password% %password%` | 300 - 600 ms |
| `/login` | Message | `/login %password%` | 200 - 400 ms |
| `%name%*cheat*?` | Leave server | — | 200 - 400 ms |

These defaults run once per connection. **Rearm rules** lets them match again
without reconnecting. The match-preview field only tests a pattern; it never
sends chat or disconnects.

## Message placeholders

| Placeholder | Expansion |
| --- | --- |
| `%name%` | Your real Minecraft username |
| `%password%` or `%pass%` | The saved password for that account |
| `%s` | Fresh random letters; configurable length, initially 6 |
| `%n` | Fresh random digits; configurable length, initially 3 |

Repeated password placeholders use the same value. Random placeholders are
generated separately for each occurrence. Expanded messages must fit Minecraft
1.8.9's 100-character limit; Chatter rejects oversized messages rather than
truncating a password command. Automated sends do not enter your sent-chat history.

## Passwords

Passwords are generated with SecureRandom, saved before use and reused after
reconnecting or restarting. Entries belong to a username and, by default, a server.
An explicit per-server entry takes priority over an all-server entry. Generation
settings can choose a shared password for future accounts instead. Changing the
generation policy never replaces an existing saved password.

The manager supports search, adding the current account, masked display, reveal,
copy, editing, generating a draft and deleting a local entry. Saving or generating
a local password does not change an existing server account's password: enter the
value that server accepts. New passwords default to 16 characters using uppercase,
lowercase and digits; symbols and confusing-character exclusion are configurable.

Rules, templates and passwords live in the separate AES-GCM encrypted files
`<game directory>/vibe/chatter/chatter.vault` and `chatter.key`. They are not included
in shareable module profiles. Both files are needed to recover the data. An
unreadable vault disables automation and editing rather than overwriting saved
passwords. The key and temporary files follow Vibe's owner-only account-file policy.

## Lifecycle

Actions run on client ticks through the normal multiplayer chat/disconnect paths.
Disconnecting, switching accounts, changing connections, disabling Chatter or
editing saved data cancels queued actions. Cooldowns, one queued action per rule,
echo suppression and a bounded queue prevent repeated prompts from multiplying
sends. Chatter does not run in singleplayer.

Verification: `gradlew.bat build verifyChatterRendering --offline`. The offscreen
menu check uses fake credentials and saves previews in `build/chatter-render-check`.
