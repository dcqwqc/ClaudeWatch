# Server setup

These three scripts live on your server and connect Claude Code ↔ watch. Put them
in `~/.claude-watch/`:

```bash
mkdir -p ~/.claude-watch
# copy the three files here, then:
chmod +x ~/.claude-watch/claude-watch-usage.sh ~/.claude-watch/claude-done-hook.sh
# expose the usage script on PATH so `bash -lc 'claude-watch-usage'` works:
ln -sf ~/.claude-watch/claude-watch-usage.sh ~/.local/bin/claude-watch-usage
```

Make sure `~/.local/bin` is on your **login** PATH (the watch calls everything
through `bash -lc`). Most distros already add it via `~/.profile`.

---

## 1. Usage stats (`claude-watch-usage.sh`)

```bash
npm install -g ccusage        # or set CCUSAGE="npx -y ccusage@latest"
sudo apt install jq           # required
claude-watch-usage            # should print one line of JSON
```

Expected output:

```json
{"block":{"totalTokens":1234567,"costUSD":1.23,"resetsInMinutes":142},"week":{"totalTokens":8900000,"costUSD":12.34}}
```

> Claude's subscription doesn't expose an official "% of limit" API, so the watch
> shows token totals as a fraction of the **caps you set in Settings**. Tune those
> caps until the rings line up with what `/usage` reports inside Claude Code.

---

## 2. "Claude is done" push (Firebase Cloud Messaging)

**a. Create a free Firebase project** at <https://console.firebase.google.com>.

**b. Add an Android app** with package name `io.qwqc.claudewatch`. Download the
generated `google-services.json` and drop it into the app module:
`ClaudeWatch/app/google-services.json`. (The build auto-enables Firebase when this
file is present.)

**c. Create a service account** for the server sender:
Project Settings → Service accounts → *Generate new private key*. Save it as
`~/.claude-watch/service-account.json` on the server.

**d. Install the sender deps and test:**

```bash
pip install --user google-auth requests
# Open the watch app once so it registers its token over SSH:
cat ~/.claude-watch/watch-token        # should contain a long FCM token
python3 ~/.claude-watch/send-fcm.py "Test" "Hello from the server"
```

You should feel a buzz on the watch.

**e. Register the Stop hook** in `~/.claude/settings.json`:

```json
{
  "hooks": {
    "Stop": [
      { "hooks": [ { "type": "command",
                     "command": "/home/youruser/.claude-watch/claude-done-hook.sh" } ] }
    ]
  }
}
```

Now every time Claude finishes a prompt in the tmux session, the hook fires
`send-fcm.py`, and the watch buzzes + shows a notification.

---

## 3. tmux session

The watch attaches `tmux attach -t claude` (configurable in Settings). Make sure
that session exists, or the app will create it on first connect:

```bash
tmux new -s claude
# run `claude` (Claude Code) inside it
```

## Files

| File | Role |
|------|------|
| `claude-watch-usage.sh` | normalized usage JSON (ccusage + jq) |
| `claude-done-hook.sh`   | Claude Code Stop hook → fires the push |
| `send-fcm.py`           | sends one FCM message to the registered watch |
