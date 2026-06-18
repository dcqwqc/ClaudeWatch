#!/usr/bin/env bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG="/tmp/claude-watch-hook.log"
TMP_JSON="/tmp/claude-payload.json"

# Capture stdin directly to a file to avoid any shell interpolation or mangling.
cat > "$TMP_JSON"
echo "[$(date)] Received hook. Payload size: $(stat -c%s "$TMP_JSON" 2>/dev/null || echo 'unknown')" >> "$LOG"

session="${CLAUDE_TMUX_SESSION:-claude}"

# Extract text using Python directly from the file.
body=$(python3 2>>"$LOG" <<'PYEOF'
import sys, json, os

def find_assistant_text(data):
    if not isinstance(data, (dict, list)):
        return None
        
    if isinstance(data, dict):
        # 1. Direct field in the top-level or sub-objects
        if "last_assistant_message" in data and isinstance(data["last_assistant_message"], str):
            return data["last_assistant_message"]
            
        # 2. Traditional message role structure
        if data.get("role") == "assistant":
            c = data.get("content") or data.get("text")
            if isinstance(c, str):
                return c
            elif isinstance(c, list):
                return "".join(b.get("text", "") for b in c if b.get("type") == "text")
        
        # Recurse
        for v in data.values():
            res = find_assistant_text(v)
            if res: return res
            
    elif isinstance(data, list):
        for item in reversed(data):
            res = find_assistant_text(item)
            if res: return res
    return None

try:
    with open('/tmp/claude-payload.json', 'r') as f:
        data = json.load(f)
    text = find_assistant_text(data)
    if text and text.strip():
        # Clean up whitespace and newlines for a one-line preview
        clean_text = " ".join(text.split())
        if len(clean_text) > 50:
            print("..." + clean_text[-50:].strip(), end="")
        else:
            print(clean_text.strip(), end="")
except Exception as e:
    sys.stderr.write(f"Python error: {e}\n")
PYEOF
)

body="${body:-Finished in tmux: $session}"
echo "[$(date)] Final body: $body" >> "$LOG"

# Fire push in background.
nohup python3 "$DIR/send-fcm.py" "Claude is done" "$body" >> "$LOG" 2>&1 &

exit 0
