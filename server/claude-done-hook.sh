#!/usr/bin/env bash
#
# Claude Code "Stop" hook → buzz the watch.
#
# Wire it up in ~/.claude/settings.json (see server/README.md):
#
#   "hooks": {
#     "Stop": [
#       { "hooks": [ { "type": "command",
#                      "command": "/home/youruser/.claude-watch/claude-done-hook.sh" } ] }
#     ]
#   }
#
# Hooks must return promptly, so we fire the push in the background.
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG="/tmp/claude-watch-hook.log"

# Consume the hook's JSON payload from stdin.
payload="$(cat 2>/dev/null || true)"
echo "[$(date)] Received hook. Payload size: ${#payload}" >> "$LOG"

session="${CLAUDE_TMUX_SESSION:-claude}"

# Extract the last 50 chars of Claude's last assistant response using Python.
body=""
if [ -n "$payload" ]; then
    snippet=$(echo "$payload" | python3 - 2>>"$LOG" <<'PYEOF'
import sys, json

def find_assistant_text(data):
    if isinstance(data, dict):
        if data.get("role") == "assistant":
            c = data.get("content") or data.get("text")
            if isinstance(c, str):
                return c
            elif isinstance(c, list):
                return "".join(b.get("text", "") for b in c if b.get("type") == "text")
        for v in data.values():
            res = find_assistant_text(v)
            if res: return res
    elif isinstance(data, list):
        for item in reversed(data):
            res = find_assistant_text(item)
            if res: return res
    return None

try:
    data = json.load(sys.stdin)
    text = find_assistant_text(data)
    if text and text.strip():
        # Clean up whitespace and newlines for a one-line preview
        clean_text = " ".join(text.split())
        tail = clean_text[-50:].strip()
        print("…" + tail if len(clean_text) > 50 else tail, end="")
except Exception as e:
    sys.stderr.write(f"Python error: {e}\n")
PYEOF
)
    body="$snippet"
fi

body="${body:-Finished in tmux: $session}"
echo "[$(date)] Final body: $body" >> "$LOG"

nohup python3 "$DIR/send-fcm.py" "Claude is done" "$body" \
  >> "$LOG" 2>&1 &

exit 0
