# Claude Watch ⌚🧡

> **Disclaimer:** This is an unofficial, fan-made application and is not affiliated with, endorsed, or sponsored by Anthropic.

**⚖️ Licensing & Trademarks:**
* **Code:** The custom application code in this repository is open-source and published under the [MIT License](ClaudeWatch/LICENSE).
* **Theme & Trademarks:** The thematic inspiration, the name "Claude", and related branding concepts belong to their respective trademark holders (Anthropic).
* **Assets:** This repository may include third-party assets (like images or animations) that are copyrighted and **NOT** covered by the MIT license. Please review [**`LICENSE-ASSETS.md`**](LICENSE-ASSETS.md) for details before using or redistributing this project.

A personal **Wear OS** app for the **Galaxy Watch 5 Pro** that connects to your
server's Claude Code tmux session. Clean Claude-orange-on-black UI with a cute,
original animated mascot.

| Feature | How it works |
|---|---|
| 📊 **Usage rings** | 5-hour block + rolling-week token usage via `ccusage` over SSH |
| ▮ **Live terminal** | Real PTY attached to your `tmux` session, rendered by a built-in VT100/xterm emulator |
| 🎤 **Voice prompting** | On-watch speech-to-text → `tmux send-keys` into the Claude session |
| 🔔 **"Done" buzz** | Claude Code `Stop` hook → Firebase Cloud Messaging → wrist vibration + notification |
| 🧡 **Mascot** | Animated character displayed in the app. |

---

## Architecture

```
 Galaxy Watch 5 Pro                         Your Server
 ┌─────────────────────┐    SSH :22         ┌──────────────────────────┐
 │ Compose for Wear OS │ ───────────────▶   │ ccusage  (usage JSON)    │
 │  • Usage (JSch exec)│   exec / shell+PTY │ tmux -t claude (PTY)     │
 │  • Terminal (VT100) │ ◀───────────────   │ tmux send-keys (voice)   │
 │  • Voice (STT)      │                    │                          │
 │  • FCM receiver     │ ◀──┐               │ Claude Code Stop hook    │
 └─────────────────────┘    │  FCM push     │   └─ send-fcm.py ───┐    │
                            └───────────────┤◀────────────────────┘    │
                              (Firebase)    └──────────────────────────┘
```

The watch never needs an inbound port: it pulls usage/terminal over SSH, and the
server pushes "done" events out to Firebase, which delivers them to the watch.

---

## Build & Deploy (Android Studio)

**Prerequisites:** Android Studio (Koala or newer), JDK 17, a Wear OS watch with
developer mode + ADB debugging enabled.

1. **Open Project:** Open the `ClaudeWatch/` folder in Android Studio. Let it sync Gradle.
2. **(Optional) Add Firebase:** For "Done" notifications to work, you need Firebase. Follow the guide in [`server/README.md`](server/README.md) to generate your `google-services.json` file and place it in the `app/` folder.
3. **Pair Watch:** On your watch, go to Settings → Developer options → *Wireless debugging*. Then, in a terminal on your computer, run `adb pair <ip:port>` and `adb connect <ip:port>`.
4. **Run App:** Select the `app` run configuration in Android Studio and run it on your watch device.

---

## First-Run Setup

### 1. Server-Side Setup
Before using the watch app, you need to configure your server. This includes scripts for usage stats and push notifications. We've made this easy with a one-line installer.

**On your server**, run the following command:
```bash
bash -c "$(curl -fsSL https://raw.githubusercontent.com/dcqwqc/ClaudeWatch/main/server/install.sh)"
```
This will download the necessary scripts to `~/.claude-watch` and print out instructions for the final configuration steps (like setting up Firebase and Claude Code hooks). The full guide is also in `server/README.md`.

### 2. Watch App Setup
Once the server is ready and the app is on your watch:

1. Open the app and swipe to the **Settings** screen.
2. **Host:** Enter your server's address (e.g., `your.server.com`).
3. **User:** Enter your SSH username for the server.
4. **Port:** `22` is standard for SSH.
5. **Tmux Session:** Enter the name of the `tmux` session you want to connect to. This can be **any session**, not just `claude`.
6. **Generate & Copy Key:** Tap **Generate key pair**, then **Copy public key**.
7. **Authorize Key:** Paste the public key you just copied into your server's `~/.ssh/authorized_keys` file.
8. **Save & Test:** Tap **Save**. Go back to the **Usage** or **Terminal** screens to confirm the connection is working.

---

## Disclaimer & Credits
* **Claude & Clawd Identity:** "Claude Code" and the "Clawd" mascot character belong entirely to Anthropic. This is a free, non-commercial community project designed to provide usage tracking and a WearOS interface for Claude. It has no official affiliation, endorsement, or connection with Anthropic.
* **Animated GIF:** The animated mascot displayed in the app was created by an independent developer online. To shield them from automated legal bots or trademark sweeps targeting the mascot's identity, I am intentionally keeping their name and blog link anonymous. 
If Anthropic or the original animator wishes to have this repository modified or removed, please open a GitHub issue or submit a formal request, and it will be taken down immediately. 
