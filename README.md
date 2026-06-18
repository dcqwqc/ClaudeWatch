# Claude Watch ⌚🧡

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

![Showcase GIF](https://i.postimg.cc/6QFb7j5j/Clawd-Laptop.gif)

> **Disclaimer:** This is an unofficial, fan-made application and is not affiliated with, endorsed, or sponsored by Anthropic.

A personal **Wear OS** app that connects to your local or remote server, giving you a watch-native interface for your Claude Code `tmux` sessions.

---

## ✨ Features

*   📊 **Live Usage Rings:** See your 5-hour and weekly token usage at a glance, updated automatically from your server.
*   ▮ **Full Terminal Emulator:** Get a real, interactive terminal for your `tmux` session right on your wrist, rendered by a built-in VT100/xterm emulator.
*   🎤 **Voice-to-Prompt:** Use your watch's microphone to dictate prompts directly into your Claude session via `tmux send-keys`.
*   🔔 **"Done" Notifications:** The watch buzzes to let you know the instant a long-running Claude task is finished, using Firebase Cloud Messaging.
*   🧡 **Customizable & Open-Source:** Connect to any named `tmux` session, build the app from source, and tweak it to your heart's content.

---

## 🚀 Quick Start

### 1. Server Setup
Before using the watch app, you need to set up your server (this can be a remote server or your local PC). We've built an intelligent, interactive wizard that makes this incredibly simple.

**On your server**, run the following command. It will download the necessary scripts and guide you through every step.

> **Windows Users:** You must run this command in a terminal that supports `bash`, like **Git Bash** (which comes with [Git for Windows](https://git-scm.com/download/win)) or **WSL**. It will not work in the standard Command Prompt or PowerShell.

```bash
bash -c "$(curl -fsSL https://raw.githubusercontent.com/dcqwqc/ClaudeWatch/main/server/install.sh)"
```

### 2. Android App Setup
Once the server setup wizard is complete, you can build and run the Android app on your watch.

1.  **Open Project:** Open the `ClaudeWatch/` folder in Android Studio (Koala or newer).
2.  **Build & Run:** Select the `app` run configuration and run it on your paired Wear OS device.
3.  **Configure Connection:** In the app on your watch, go to **Settings** and enter your server details (Host, User, Tmux Session name).
4.  **Authorize SSH Key:** Use the **Generate key pair** and **Copy public key** buttons in the watch app, then paste the key into your server's `~/.ssh/authorized_keys` file.

---

## ⚖️ Licensing & Disclaimers

*   **Code License:** The code in this repository is open-source under the **[MIT License](LICENSE)**.
*   **Trademarks:** "Claude Code" and the "Clawd" mascot are trademarks of Anthropic.
*   **Assets:** Third-party assets used in this project are detailed in the **[`LICENSE-ASSETS.md`](LICENSE-ASSETS.md)** file.
