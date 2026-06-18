#!/usr/bin/env bash
#
# claude-watch-usage — emit a STABLE, normalized JSON of the REAL Claude usage.
#
# On a Pro/Max subscription there is NO published token cap, so summing ccusage
# tokens against a made-up cap is meaningless. Instead we read the same data the
# `/usage` screen shows: the unified rate-limit utilisation, straight from the
# Anthropic API response headers.
#
# How: Claude Code stores an OAuth access token (scope user:inference) in
# ~/.claude/.credentials.json. We make ONE tiny /v1/messages request purely to
# read the `anthropic-ratelimit-unified-*` response headers — utilisation is a
# true 0..1 fraction and the reset fields are absolute epochs. This is fast
# (~0.5s) and accurate.
#
# Output shape the watch parses:
#   {"block":{"utilization":0.15,"resetsInMinutes":84,"status":"allowed"},
#    "week":{"utilization":0.37,"resetsInMinutes":5400,"status":"allowed"},
#    "representative":"five_hour"}
#
set -euo pipefail

CRED=${CLAUDE_CREDS:-$HOME/.claude/.credentials.json}
CACHE="$HOME/.claude-watch/usage-cache.json"
mkdir -p "$(dirname "$CACHE")"

# If the cache is less than 2 minutes old, just use it.
if [ -f "$CACHE" ]; then
  now=$(date +%s)
  last=$(date -r "$CACHE" +%s)
  if [ $((now - last)) -lt 120 ]; then
    cat "$CACHE"
    exit 0
  fi
fi

command -v jq >/dev/null 2>&1 || { echo '{"error":"jq not installed"}'; exit 1; }
[ -f "$CRED" ] || { echo '{"error":"no credentials file"}'; exit 1; }

token=$(jq -r '.claudeAiOauth.accessToken // empty' "$CRED")
[ -n "$token" ] || { echo '{"error":"no access token in credentials"}'; exit 1; }

# Try up to 2 times with a short sleep on 429.
fetch() {
  local attempt=$1
  # -D - dumps response headers to stdout; -o /dev/null discards the (1-token) body.
  local h=$(curl -sS --max-time 12 -D - -o /dev/null -X POST https://api.anthropic.com/v1/messages \
    -H "authorization: Bearer $token" \
    -H "anthropic-version: 2023-06-01" \
    -H "anthropic-beta: oauth-2025-04-20" \
    -H "content-type: application/json" \
    -d '{"model":"claude-3-haiku-20240307","max_tokens":1,"messages":[{"role":"user","content":"."}]}' \
    2>/dev/null)
  
  local status=$(printf '%s' "$h" | grep -i '^HTTP/' | tail -1 || true)
  if [[ "$status" == *"429"* ]] && [ "$attempt" -lt 2 ]; then
    sleep 3
    fetch $((attempt + 1))
    return
  fi
  printf '%s' "$h"
}

hdr=$(fetch 1)

# Case-insensitive header lookup using sed for robustness.
h() { printf '%s' "$hdr" | sed -n "s/^$1: \(.*\)\r$/\1/pI" | head -1; }

status_line=$(printf '%s' "$hdr" | grep -i '^HTTP/' | tail -1 || true)

# If we got a 429 despite retries, and we have a cache, just use the cache and exit 0.
if [[ "$status_line" == *"429"* ]] && [ -f "$CACHE" ]; then
  cat "$CACHE"
  exit 0
fi

case "$status_line" in
  *200*) : ;;
  *401*) echo '{"error":"token expired - run claude on server to refresh"}'; exit 1 ;;
  *) echo "{\"error\":\"api: ${status_line:-no response}\"}"; exit 1 ;;
esac

now=$(date +%s)
# epoch -> whole minutes from now (clamped >=0), or the literal null.
mins() {
  local e="${1:-}"
  if [ -n "$e" ] && [ "$e" -gt 0 ] 2>/dev/null; then
    local m=$(( (e - now) / 60 )); [ "$m" -lt 0 ] && m=0; echo "$m"
  else
    echo null
  fi
}

u5=$(h anthropic-ratelimit-unified-5h-utilization); u5=${u5:-0}
r5=$(h anthropic-ratelimit-unified-5h-reset)
s5=$(h anthropic-ratelimit-unified-5h-status); s5=${s5:-unknown}
u7=$(h anthropic-ratelimit-unified-7d-utilization); u7=${u7:-0}
r7=$(h anthropic-ratelimit-unified-7d-reset)
s7=$(h anthropic-ratelimit-unified-7d-status); s7=${s7:-unknown}
rep=$(h anthropic-ratelimit-unified-representative-claim); rep=${rep:-five_hour}

JSON=$(printf '{"block":{"utilization":%s,"resetsInMinutes":%s,"status":"%s"},"week":{"utilization":%s,"resetsInMinutes":%s,"status":"%s"},"representative":"%s"}\n' \
  "$u5" "$(mins "$r5")" "$s5" "$u7" "$(mins "$r7")" "$s7" "$rep")

echo "$JSON" | tee "$CACHE"
