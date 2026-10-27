# Local command completion

Dot commands use Minecraft's existing GuiChat completion machinery: common-prefix expansion, repeated Tab cycling, wraparound and reset after editing. Command history remains available with Up and Down.

Vibe supplies a local response instead of sending a dot-command completion packet. Since its suggestions are full command lines, the replacement boundary covers the command prefix before the cursor. Text after the cursor is preserved. Normal chat and server command completions retain the vanilla server request and last-word replacement.

For example, `.t a` cycles through matching modules on repeated Tab presses. Settings and multi-select options use the same flow. Changes to the input invalidate the old list through vanilla key handling.
