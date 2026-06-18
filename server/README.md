# Claude Watch Server Setup

Welcome! This guide contains everything you need to set up the server-side components for the Claude Watch app. These scripts work on your local PC or any remote server where you run your `tmux` and Claude Code sessions.

## 1. Quick Installation

You can install all necessary scripts to `~/.claude-watch` on your server with this single command. It will download the latest versions from the main branch of the GitHub repository.

> **Prerequisite:** You must have `git` installed on your server.

```bash
bash -c "$(curl -fsSL https://raw.githubusercontent.com/dcqwqc/ClaudeWatch/main/server/install.sh)"
```

The installer will guide you through the next steps. For manual setup or to understand what the script does, follow the sections below.

---

## 2. Configuration Deep Dive

After installation, the scripts are located in `~/.claude-watch`. You now need to configure your environment.

### a. Firebase Cloud Messaging (for "Done" Notifications)

This allows the server to send a push notification to your watch when a long-running task in Claude Code finishes.

1.  **Create a Firebase Project:** Go to the [Firebase Console](https://console.firebase.google.com/) and create a new project (it's free).
2.  **Add Android App:**
    *   In your new project, add an Android app.
    *   The package name **must** be `io.qwqc.claudewatch`.
    *   Follow the setup steps to download the `google-services.json` file. Place this file inside the Android project's `app/` directory if you are building the app from source.
3.  **Create a Service Account (for the Server):**
    *   In your Firebase project settings (click the ⚙️ icon), go to the **Service Accounts** tab.
    *   Click "**Generate new private key**". A JSON file will be downloaded.
    *   Rename this file to `service-account.json`.
    *   Place this file in your server's installation directory: `~/.claude-watch/service-account.json`. **This file is secret and should be protected.**
4.  **Install Python Dependencies:** The `send-fcm.py` script needs two Python libraries. Install them on your server:
    ```bash
    pip install --user google-auth requests
    ```

### b. Usage Statistics (`ccusage`)

The watch fetches usage data by running the `claude-watch-usage.sh` script over SSH.

1.  **Install `ccusage`:** This is the underlying tool that provides the token data.
    ```bash
    npm install -g ccusage
    ```
2.  **Install `jq`:** This is a command-line JSON processor used by the script.
    *   On Debian/Ubuntu: `sudo apt update && sudo apt install jq`
    *   On Red Hat/CentOS: `sudo yum install jq`
3.  **Expose `ccusage` Script on your PATH:** For the watch to run the command easily, create a symbolic link to a location in your system's PATH. A common choice is `~/.local/bin`.
    ```bash
    # Ensure ~/.local/bin exists and is in your PATH
    mkdir -p ~/.local/bin
    
    # Link the script
    ln -sf ~/.claude-watch/claude-watch-usage.sh ~/.local/bin/ccusage
    ```
    > **Note:** The watch app runs SSH commands through a login shell (`bash -lc '...'`). Ensure `~/.local/bin` is added to your PATH in your shell's startup files (like `.profile` or `.bashrc`).

### c. `tmux` and Claude Code Hook

The final step is to tell Claude Code to run our script when it finishes a task.

1.  **Connect to Any `tmux` Session:**
    The watch app is not hard-coded to a specific session name! In the watch app's **Settings**, you can enter the name of any `tmux` session you want to connect to (e.g., `main`, `dev`, `claude`). The default is `claude`.

2.  **Register the `Stop` Hook:**
    Edit your Claude Code settings file, located at `~/.claude/settings.json`. Add the `hooks` section as shown below, making sure the `command` path points to the script in its installation directory.

    ```json
    {
      "anthropic_api_key": "sk-...",
      "model": "claude-3-opus-20240229",
      
      "hooks": {
        "Stop": [
          {
            "hooks": [
              {
                "type": "command",
                "command": "/home/YOUR_USERNAME/.claude-watch/claude-done-hook.sh"
              }
            ]
          }
        ]
      }
    }
    ```
    > **Important:** Replace `/home/YOUR_USERNAME/` with the correct absolute path to your home directory. You can find it by running `echo $HOME` on your server.

---
You are now fully configured! Your watch will connect to your chosen `tmux` session, show your usage stats, and receive a buzz whenever Claude is done.
