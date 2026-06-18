#!/bin/bash
#
# ClaudeWatch Server-Side Interactive Setup Utility v3.3
# A self-updating, persistent, menu-driven wizard for a fully guided setup.
#

# --- Style & Color Definitions ---
C_RESET='\033[0m'; C_RED='\033[0;31m'; C_GREEN='\033[0;32m'; C_YELLOW='\033[0;33m'; C_BLUE='\033[0;34m'; C_CYAN='\033[0;36m'; C_BOLD='\033[1m'

# --- Configuration ---
GITHUB_REPO="dcqwqc/ClaudeWatch"
SCRIPT_URL="https://raw.githubusercontent.com/$GITHUB_REPO/main/server/install.sh"
TARGET_DIR="$HOME/.claude-watch"

# --- OS Detection ---
_UNAME=$(uname -s)
case "$_UNAME" in
    MINGW*|MSYS*|CYGWIN*) IS_WINDOWS=true ;;
    *) IS_WINDOWS=false ;;
esac

# --- State Markers ---
STATE_FIREBASE_OK="$TARGET_DIR/.state_firebase_ok"
STATE_DEPS_OK="$TARGET_DIR/.state_deps_ok"
STATE_PATH_OK="$TARGET_DIR/.state_path_ok"
STATE_HOOK_OK="$TARGET_DIR/.state_hook_ok"
STATE_SSH_OK="$TARGET_DIR/.state_ssh_ok"
STATE_CONN_OK="$TARGET_DIR/.state_conn_ok"

# --- Helper Functions ---
header() { clear; echo -e "${C_GREEN}${C_BOLD}--- ClaudeWatch Server Setup Utility v3.3 ---${C_RESET}
This wizard guides you through server configuration. It saves your progress.
--------------------------------------------------------------------"; }
step() { echo -e "\n${C_BLUE}==> ${C_BOLD}$1${C_RESET}"; }
prompt() { echo -e "\n${C_YELLOW}ACTION: ${C_RESET}$1"; }
info() { echo -e "   ${C_GREEN}✓${C_RESET} $1"; }
warn() { echo -e "   ${C_YELLOW}⚠️  $1${C_RESET}"; }
pause() { read -p "Press [Enter] to return to the main menu..."; }
command_exists() { command -v "$1" &>/dev/null; }

# Check if a command is available anywhere (POSIX PATH or Windows PATH).
# If found only via Windows PATH, inject that directory into the current session
# so subsequent command_exists calls also succeed.
resolve_command() {
    local cmd_name="$1"
    command_exists "$cmd_name" && return 0
    $IS_WINDOWS || return 1
    local win_path; win_path=$(cmd //c where "$cmd_name" 2>/dev/null | head -1 | tr -d '\r\n')
    [ -z "$win_path" ] && return 1
    local posix_dir; posix_dir=$(cygpath -u "$(dirname "$win_path")" 2>/dev/null)
    [ -n "$posix_dir" ] && export PATH="$PATH:$posix_dir"
    command_exists "$cmd_name"
}

# --- Self-Updating Logic ---
self_update() {
    step "Checking for updates to the installer..."
    # When run via `bash -c "$(curl ...)"`, $0 is 'bash' — not a file on disk.
    # In that case we already have the latest version, so skip the comparison.
    if [ ! -f "$0" ]; then
        info "Running latest version (fetched directly from GitHub)."
        return
    fi
    local latest_script; latest_script=$(curl -fsSL "$SCRIPT_URL") || { warn "Could not reach GitHub. Continuing with current version."; return; }
    local current_script; current_script=$(cat "$0")
    if [ "$latest_script" != "$current_script" ]; then
        prompt "A new version of this installer is available."
        read -p "Update and re-launch? (y/N) " -n 1 -r; echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            info "Updating and re-launching..."
            echo "$latest_script" > "$0"
            exec bash "$0"
        else
            warn "Continuing with the old version."
        fi
    else
        info "You are running the latest version."
    fi
}

# --- Prerequisite & Automation Functions ---
prompt_to_install() {
    local cmd_name="$1"; local pkg_name="$2"; local install_type="$3"
    prompt "The required tool '${C_BOLD}$cmd_name${C_RESET}' is not installed."
    read -p "May I attempt to install it automatically? (y/N) " -n 1 -r; echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then warn "'$cmd_name' will not be installed."; return 1; fi

    local install_cmd=""
    if [[ "$install_type" == "sys" ]]; then
        if $IS_WINDOWS; then
            if command_exists winget; then
                local winget_pkg="$pkg_name"
                [[ "$pkg_name" == "jq" ]] && winget_pkg="jqlang.jq"
                install_cmd="winget install --id $winget_pkg -e --accept-package-agreements --accept-source-agreements"
            elif command_exists choco; then
                install_cmd="choco install $pkg_name -y"
            elif command_exists scoop; then
                install_cmd="scoop install $pkg_name"
            else
                warn "No supported package manager found (winget / choco / scoop)."
                warn "Please install '${cmd_name}' manually and re-run this step."
                if [[ "$cmd_name" == "jq" ]]; then
                    warn "Download jq.exe from https://jqlang.github.io/jq/download/"
                    warn "Place it somewhere on your PATH (e.g. C:\\Windows\\System32)."
                fi
                return 1
            fi
        else
            if command_exists apt; then install_cmd="sudo apt update && sudo apt install -y $pkg_name"
            elif command_exists yum; then install_cmd="sudo yum install -y $pkg_name"
            elif command_exists dnf; then install_cmd="sudo dnf install -y $pkg_name"
            elif command_exists brew; then install_cmd="brew install $pkg_name"
            else
                warn "Unsupported package manager. Please install '$pkg_name' manually."
                return 1
            fi
        fi
    elif [[ "$install_type" == "npm" ]]; then
        install_cmd="npm install -g $pkg_name"
    fi

    info "Running: $install_cmd"
    # Capture output so we can detect winget's "already at latest" non-zero exit
    local output; output=$(eval "$install_cmd" 2>&1); local exit_code=$?
    echo "$output"

    # Success path 1: exited 0 — try to inject into POSIX PATH on Windows in
    # case the installer added it to the registry PATH but not this session.
    if [ $exit_code -eq 0 ]; then
        info "'$cmd_name' installed successfully."
        resolve_command "$cmd_name" &>/dev/null  # best-effort PATH injection
        if $IS_WINDOWS; then
            echo ""
            warn "PATH notice: Windows PATH changes don't apply to the current Git Bash"
            warn "session automatically. '$cmd_name' has been injected for this session,"
            warn "but if Step 5 fails, restart Git Bash first — it will work after that."
        fi
        return 0
    fi

    # Success path 2: winget exits non-zero when the package is already at the
    # latest version. Detect this by checking the output text, then locate the
    # binary via Windows PATH and inject it into the current POSIX session.
    if $IS_WINDOWS && echo "$output" | grep -qiE "already installed|No available upgrade|No newer package"; then
        if resolve_command "$cmd_name"; then
            info "'$cmd_name' is already installed and up to date."
            echo ""
            warn "PATH notice: '$cmd_name' was found via Windows PATH and injected"
            warn "into this session. If Step 5 still reports it missing, restart"
            warn "Git Bash and try Step 5 directly — it should work after that."
            return 0
        else
            warn "'$cmd_name' is installed by Windows but could not be located."
            warn "Restart Git Bash and try Step 5 directly — it may work after that."
            return 1
        fi
    fi

    warn "Installation failed. Please install '$pkg_name' manually and re-run this step."
    return 1
}

check_prerequisites() {
    step "Step 0: Checking System Prerequisites..."
    echo "   ClaudeWatch needs a few standard tools to work. This step checks"
    echo "   for each one and offers to install any that are missing."
    local all_ok=true
    local deps=("git:git:sys" "python3:python3:sys" "pip:python3-pip:sys" "npm:npm:sys" "jq:jq:sys")
    for dep in "${deps[@]}"; do
        IFS=":" read -r cmd pkg type <<< "$dep"
        if resolve_command "$cmd"; then
            info "$cmd is already installed."
        elif prompt_to_install "$cmd" "$pkg" "$type"; then
            info "$cmd is now installed."
        else
            all_ok=false
        fi
    done
    if [ "$all_ok" = false ]; then warn "Some prerequisites are still missing."; else info "All prerequisites are installed."; fi
    pause
}

# --- Main Setup Functions ---
download_scripts() {
    step "Step 1: Downloading Server Scripts..."
    echo "   This downloads the ClaudeWatch server scripts from GitHub into"
    echo "   $TARGET_DIR on your computer."
    if ! command_exists git; then warn "git is not installed. Please run Step 0."; pause; return; fi
    if [ -d "$TARGET_DIR" ] && [ "$(ls -A "$TARGET_DIR")" ]; then
        prompt "The directory $TARGET_DIR is not empty."
        read -p "Perform a clean install by removing existing files first? (y/N) " -n 1 -r; echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then info "Cleaning target directory..."; rm -rf "${TARGET_DIR:?}"/*; fi
    fi
    mkdir -p "$TARGET_DIR"
    local TEMP_DIR; TEMP_DIR=$(mktemp -d); trap 'rm -rf -- "$TEMP_DIR"' EXIT
    info "Fetching latest scripts from GitHub..."
    git init -q "$TEMP_DIR"
    (cd "$TEMP_DIR" || exit
     git remote add origin "https://github.com/$GITHUB_REPO.git" &>/dev/null
     git config core.sparseCheckout true
     echo "server/" > .git/info/sparse-checkout
     git pull -q --depth=1 origin main)
    info "Copying files to $TARGET_DIR..."
    cp -rf "$TEMP_DIR"/server/* "$TARGET_DIR"
    info "Scripts downloaded and installed."
    pause
}

setup_firebase() {
    step "Step 2: Configuring Firebase Notifications..."
    echo "   ClaudeWatch sends push notifications to your watch via Firebase."
    echo "   This step connects it to YOUR Firebase project using a private key"
    echo "   you download from the Firebase website (it stays on your PC only)."
    while true; do
        prompt "Open this link in your browser: ${C_CYAN}https://console.firebase.google.com/${C_RESET}"
        echo -e "   1. Create a project (any name)."
        echo -e "   2. Go to ${C_BOLD}Project settings${C_RESET} (gear icon) → ${C_BOLD}Service Accounts${C_RESET} tab."
        echo -e "   3. Click '${C_BOLD}Generate new private key${C_RESET}' → confirm → save the JSON file."
        prompt "Enter the full path to that downloaded JSON file."
        echo "   Windows path example: C:\\Users\\YourName\\Downloads\\my-project-firebase.json"
        echo "   MSYS path example:    /c/Users/YourName/Downloads/my-project-firebase.json"
        # -r prevents read from eating backslashes in Windows paths
        read -r -p "Path: " key_path
        # Strip surrounding quotes that users may copy-paste on Windows
        key_path="${key_path#\"}"; key_path="${key_path%\"}"
        # Convert Windows backslash path to MSYS/POSIX path
        if $IS_WINDOWS && command_exists cygpath; then
            key_path=$(cygpath -u "$key_path" 2>/dev/null || echo "$key_path")
        fi
        if [ -f "$key_path" ]; then
            cp "$key_path" "$TARGET_DIR/service-account.json"
            info "Firebase service account key successfully copied."
            touch "$STATE_FIREBASE_OK"; break
        else
            warn "File not found at: $key_path"
            read -p "Retry? (y/N) " -n 1 -r; echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then break; fi
        fi
    done
    pause
}

install_dependencies() {
    step "Step 3: Installing App Dependencies..."
    echo "   This installs the Python and Node.js libraries that the ClaudeWatch"
    echo "   server scripts need to send notifications and report usage."
    if ! command_exists pip; then
        warn "pip not found. Please run Step 0."
    else
        info "Installing Python libraries (google-auth, requests)..."
        pip install --user --quiet --disable-pip-version-check google-auth requests
        info "Python libraries installed."
    fi
    if ! command_exists npm; then
        warn "npm not found. Please run Step 0."
    else
        if ! command_exists ccusage; then
            if prompt_to_install "ccusage" "ccusage" "npm"; then touch "$STATE_DEPS_OK"; fi
        else
            info "'ccusage' is already installed."
            touch "$STATE_DEPS_OK"
        fi
    fi
    pause
}

setup_path() {
    step "Step 4: Setting up Usage Command (PATH)..."
    echo "   Your computer has a list of folders called PATH that it searches"
    echo "   whenever you type a command. This step adds ClaudeWatch's folder"
    echo "   to that list so you can run 'ccusage' from any terminal window."
    chmod +x "$TARGET_DIR/claude-watch-usage.sh"
    if $IS_WINDOWS; then
        local win_dir; win_dir=$(cygpath -w "$TARGET_DIR" 2>/dev/null || echo "$TARGET_DIR")
        echo ""
        echo -e "   ${C_BOLD}What to do:${C_RESET} Open PowerShell (Win+X → Terminal) and run this command:"
        echo ""
        echo -e "   ${C_CYAN}[Environment]::SetEnvironmentVariable('PATH', \$env:PATH + ';${win_dir}', 'User')${C_RESET}"
        echo ""
        echo "   That command permanently adds ClaudeWatch to your PATH for your user"
        echo "   account (no admin rights needed). After running it, restart your terminal."
        echo ""
        read -p "   Have you run the command above? Mark step as complete? (y/N) " -n 1 -r; echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then touch "$STATE_PATH_OK"; fi
    elif [[ "$_UNAME" == "Linux" || "$_UNAME" == "Darwin" ]]; then
        info "Detected Linux/macOS. Creating symlink in ~/.local/bin..."
        mkdir -p "$HOME/.local/bin"
        ln -sf "$TARGET_DIR/claude-watch-usage.sh" "$HOME/.local/bin/ccusage"
        if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
            warn "~/.local/bin is not in your PATH. Add it to your .bashrc or .profile."
        fi
        info "Usage command 'ccusage' is now available."
        touch "$STATE_PATH_OK"
    fi
    pause
}

setup_hooks() {
    step "Step 5: Configuring Claude Code Hook..."
    echo "   Claude Code supports 'hooks' — commands that run automatically at"
    echo "   certain moments. This step registers the ClaudeWatch notifier as a"
    echo "   'Stop' hook so your watch buzzes every time Claude finishes a task."
    if ! resolve_command jq; then warn "jq not found. Please run Step 0 first."; pause; return; fi
    local settings_file="$HOME/.claude/settings.json"
    local backup_file="$settings_file.bak"
    local hook_cmd="$TARGET_DIR/claude-done-hook.sh"
    chmod +x "$hook_cmd"
    if [ ! -f "$settings_file" ]; then warn "Claude settings file not found at $settings_file"; pause; return; fi
    prompt "This step will automatically edit $settings_file to add the Stop hook."
    echo "   A backup copy will be saved first so you can always undo."
    read -p "   Proceed? (y/N) " -n 1 -r; echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then warn "Hook setup skipped."; pause; return; fi
    cp "$settings_file" "$backup_file"; info "Backup created at $backup_file"
    local hook_obj='{ "hooks": { "Stop": [ { "hooks": [ { "type": "command", "command": "'"$hook_cmd"'" } ] } ] } }'
    local temp_json; temp_json=$(mktemp)
    if jq --argjson obj "$hook_obj" '. * $obj' "$settings_file" > "$temp_json"; then
        mv "$temp_json" "$settings_file"
        info "Successfully merged hook into $settings_file."
        touch "$STATE_HOOK_OK"
    else
        warn "Automated merge failed! Your original file is safe at $backup_file."
        warn "Please add the hook manually. Required JSON:"
        echo -e "${C_GREEN}${hook_obj}${C_RESET}"
    fi
    pause
}

_add_pub_to_authorized_keys() {
    local pub_file="$1"
    mkdir -p "$HOME/.ssh"; chmod 700 "$HOME/.ssh"
    touch "$HOME/.ssh/authorized_keys"; chmod 600 "$HOME/.ssh/authorized_keys"
    local pub_key; pub_key=$(cat "$pub_file")
    if ! grep -qF "$pub_key" "$HOME/.ssh/authorized_keys" 2>/dev/null; then
        echo "$pub_key" >> "$HOME/.ssh/authorized_keys"
        info "Public key added to ~/.ssh/authorized_keys"
    else
        info "Public key was already in ~/.ssh/authorized_keys"
    fi
}

setup_ssh_key() {
    step "Step 6: Watch SSH Key Setup..."
    echo "   Your watch is the SSH client — it connects INTO this PC (and any other"
    echo "   machines you set up). The watch has ONE key pair:"
    echo ""
    echo "     • PRIVATE key → stays on the watch (paste into the ClaudeWatch app)"
    echo "     • PUBLIC key  → goes on every PC/server the watch needs to connect to"
    echo ""
    echo "   This step generates the key and adds it to THIS PC. For other machines"
    echo "   (laptop, home server, etc.) you copy the same public key to each of them."
    echo ""

    if ! command_exists ssh-keygen; then
        warn "ssh-keygen not found. Please install OpenSSH:"
        $IS_WINDOWS && warn "  PowerShell (Admin): Add-WindowsCapability -Online -Name OpenSSH.Client~~~~0.0.1.0" \
                     || warn "  sudo apt install openssh-client"
        pause; return
    fi

    local key_file="$TARGET_DIR/watch_key"

    echo "   [g] Generate a new key for the watch"
    echo "   [s] Show existing key  (re-display to copy into app or add to another machine)"
    echo "   [q] Done"
    read -p "   Choice: " -n 1 key_choice; echo

    case $key_choice in
    g)
        if [ -f "$key_file" ]; then
            warn "A watch key already exists."
            read -p "   Overwrite it? The old key will stop working everywhere. (y/N) " -n 1 -r; echo
            [[ ! $REPLY =~ ^[Yy]$ ]] && { pause; return; }
            rm -f "$key_file" "${key_file}.pub"
        fi
        info "Generating watch SSH key (ed25519)..."
        ssh-keygen -t ed25519 -f "$key_file" -N "" -C "ClaudeWatch-Watch" -q
        chmod 600 "$key_file"
        _add_pub_to_authorized_keys "${key_file}.pub"
        _display_key_instructions "$key_file"
        touch "$STATE_SSH_OK"
        ;;
    s)
        if [ ! -f "$key_file" ]; then
            warn "No watch key found yet. Choose [g] to generate one first."
        else
            _display_key_instructions "$key_file"
        fi
        ;;
    q|Q) return ;;
    *) warn "Invalid option." ;;
    esac
    pause
}

_display_key_instructions() {
    local key_file="$1"
    echo ""
    echo -e "   ${C_BOLD}── PRIVATE KEY (paste this into the ClaudeWatch app on your watch) ──${C_RESET}"
    echo "   App → Settings → SSH Private Key — paste everything including the dashes"
    echo ""
    echo -e "${C_YELLOW}"
    cat "$key_file"
    echo -e "${C_RESET}"
    echo -e "   ${C_BOLD}── PUBLIC KEY (add this to every machine the watch should connect to) ──${C_RESET}"
    echo "   On each additional PC/server, run:"
    echo -e "   ${C_CYAN}echo '$(cat "${key_file}.pub")' >> ~/.ssh/authorized_keys${C_RESET}"
    echo ""
    echo "   Public key file on this PC: ${key_file}.pub"
}

# Connection setup helpers write results into these globals
_CONN_HOST=""; _CONN_PORT="22"

_setup_conn_local() {
    _CONN_HOST=""; _CONN_PORT="22"
    echo ""
    echo -e "   ${C_BOLD}Local Network Setup${C_RESET}"
    echo "   This lets the watch connect to your PC when both are on the same WiFi."
    echo "   It will NOT work when you leave home."
    echo ""

    # Auto-detect local IP
    local detected_ip=""
    if $IS_WINDOWS; then
        detected_ip=$(powershell -Command "
            (Get-NetIPAddress -AddressFamily IPv4 |
             Where-Object { \$_.IPAddress -notmatch '^127\\.' -and \$_.IPAddress -notmatch '^169\\.' } |
             Sort-Object PrefixLength -Descending |
             Select-Object -First 1).IPAddress" 2>/dev/null | tr -d '\r\n')
    else
        detected_ip=$(hostname -I 2>/dev/null | awk '{print $1}')
    fi

    if [ -n "$detected_ip" ]; then
        info "Detected local IP: $detected_ip"
        read -p "   Use this? (Y/n) " -n 1 -r; echo
        [[ ! $REPLY =~ ^[Nn]$ ]] && _CONN_HOST="$detected_ip"
    fi

    if [ -z "$_CONN_HOST" ]; then
        echo "   Where to find your IP: open Command Prompt and run 'ipconfig'."
        echo "   Look for 'IPv4 Address' under your WiFi or Ethernet adapter."
        read -r -p "   Enter local IP: " _CONN_HOST
    fi

    read -r -p "   SSH port (press Enter for 22): " _p
    [ -n "$_p" ] && _CONN_PORT="$_p"
    info "Local address set: $_CONN_HOST:$_CONN_PORT"
}

_setup_conn_domain() {
    _CONN_HOST=""; _CONN_PORT="22"
    echo ""
    echo -e "   ${C_BOLD}Domain / DDNS Setup${C_RESET}"
    echo "   A domain name (like yourname.duckdns.org) always points to your PC"
    echo "   even when your home internet IP changes. Works from anywhere."
    echo ""

    echo -e "   ${C_BOLD}Don't have a domain yet?${C_RESET} Get a free one:"
    echo -e "   • DuckDNS    ${C_CYAN}https://www.duckdns.org${C_RESET}"
    echo "                Sign up, pick a name (e.g. mywatch.duckdns.org),"
    echo "                install their tiny update app on this PC, done."
    echo -e "   • desec.io   ${C_CYAN}https://desec.io${C_RESET}  (more features, also free)"
    echo ""
    echo "   Also make sure your router forwards port 22 (SSH) to this PC."
    echo "   (Router admin page → Port Forwarding → add rule: TCP port 22 → this PC's local IP)"
    echo ""
    read -r -p "   Your domain or hostname: " _CONN_HOST
    read -r -p "   SSH port (press Enter for 22, or enter your forwarded port): " _p
    [ -n "$_p" ] && _CONN_PORT="$_p"
    [ -n "$_CONN_HOST" ] && info "Domain address set: $_CONN_HOST:$_CONN_PORT"
}

_setup_conn_tailscale() {
    _CONN_HOST=""; _CONN_PORT="22"
    echo ""
    echo -e "   ${C_BOLD}Tailscale Setup${C_RESET}"
    echo "   Tailscale is a free service that creates a private encrypted network"
    echo "   between your devices. Once your PC and watch are both on Tailscale,"
    echo "   they get permanent addresses (like 100.x.x.x) that never change —"
    echo "   and it works from anywhere without any port forwarding or domain."
    echo ""
    echo "   It's the easiest option if you have no domain or router access."
    echo ""

    if ! resolve_command tailscale; then
        warn "Tailscale is not installed on this PC."
        echo ""
        echo -e "   ${C_BOLD}Install Tailscale on this PC:${C_RESET}"
        if $IS_WINDOWS; then
            echo -e "   Automatic: ${C_CYAN}winget install tailscale.tailscale${C_RESET}"
            echo -e "   Manual:    ${C_CYAN}https://tailscale.com/download${C_RESET}"
            echo ""
            read -p "   Install via winget now? (y/N) " -n 1 -r; echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                winget install tailscale.tailscale --accept-package-agreements --accept-source-agreements
                warn "After install: launch Tailscale from the Start menu and log in."
            fi
        else
            echo -e "   Run: ${C_CYAN}curl -fsSL https://tailscale.com/install.sh | sh && sudo tailscale up${C_RESET}"
        fi
        echo ""
        echo -e "   ${C_BOLD}Also install Tailscale on your phone${C_RESET} (the watch connects through it):"
        echo -e "   ${C_CYAN}https://tailscale.com/download${C_RESET}"
        echo "   Log in with the SAME Tailscale account on all devices."
        echo ""
        echo "   Once everything is connected, re-run this step."
        return
    fi

    info "Tailscale is installed."
    local ts_ip; ts_ip=$(tailscale ip -4 2>/dev/null | head -1 | tr -d '\r\n')
    if [ -n "$ts_ip" ]; then
        info "This PC's Tailscale IP: ${C_CYAN}$ts_ip${C_RESET} — enter this in the watch app."
        read -p "   Use this IP? (Y/n) " -n 1 -r; echo
        [[ ! $REPLY =~ ^[Nn]$ ]] && _CONN_HOST="$ts_ip"
    else
        warn "Could not read Tailscale IP. Is Tailscale running and logged in?"
        echo "   You can find your PC's Tailscale IP in the Tailscale app (tray icon)."
        read -r -p "   Enter Tailscale IP manually: " _CONN_HOST
    fi
    _CONN_PORT="22"
    [ -n "$_CONN_HOST" ] && info "Tailscale address set: $_CONN_HOST:$_CONN_PORT"
}

setup_connection() {
    step "Step 7: Configuring Connection Address..."
    echo "   Your watch needs to know WHERE to find this PC. Choose the method"
    echo "   that best fits your situation:"
    echo ""
    echo -e "   ${C_BOLD}[1] Local network only${C_RESET}  — same WiFi as your PC, simple, no extra setup"
    echo -e "   ${C_BOLD}[2] Domain / DDNS${C_RESET}       — works from anywhere, needs a domain + port forwarding"
    echo -e "   ${C_BOLD}[3] Tailscale${C_RESET}           — works from anywhere, no domain needed (recommended)"
    echo ""
    read -p "   Choose [1/2/3]: " -n 1 conn_choice; echo

    local primary_host="" primary_port="22" fallback_host="" fallback_port="22"

    case $conn_choice in
        1) _setup_conn_local;     primary_host="$_CONN_HOST"; primary_port="$_CONN_PORT" ;;
        2) _setup_conn_domain;    primary_host="$_CONN_HOST"; primary_port="$_CONN_PORT" ;;
        3) _setup_conn_tailscale; primary_host="$_CONN_HOST"; primary_port="$_CONN_PORT" ;;
        *) warn "Invalid choice."; pause; return ;;
    esac

    [ -z "$primary_host" ] && { warn "No address entered. Step not completed."; pause; return; }

    # Optional fallback
    echo ""
    echo -e "   ${C_BOLD}Fallback address (optional but recommended)${C_RESET}"
    echo "   Add a secondary address. The watch tries primary first; if it can't"
    echo "   connect (e.g. you're away from home), it automatically tries the fallback."
    read -p "   Add a fallback address? (y/N) " -n 1 -r; echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo ""
        echo "   Choose fallback type:"
        echo "   [1] Local IP  [2] Domain/DDNS  [3] Tailscale"
        read -p "   Choose [1/2/3]: " -n 1 fb_choice; echo
        case $fb_choice in
            1) _setup_conn_local;     fallback_host="$_CONN_HOST"; fallback_port="$_CONN_PORT" ;;
            2) _setup_conn_domain;    fallback_host="$_CONN_HOST"; fallback_port="$_CONN_PORT" ;;
            3) _setup_conn_tailscale; fallback_host="$_CONN_HOST"; fallback_port="$_CONN_PORT" ;;
        esac
    fi

    # SSH username
    local ssh_user; ssh_user=$(whoami | tr -d '\r')
    read -r -p "   SSH username on this PC (Enter for '$ssh_user'): " input_user
    [ -n "$input_user" ] && ssh_user="$input_user"

    # The watch has one key; reference it directly
    local ssh_key_path="$TARGET_DIR/watch_key"
    if [ ! -f "$ssh_key_path" ]; then
        warn "No watch key found at $ssh_key_path — run Step 6 first to generate one."
    else
        info "Using watch key: $ssh_key_path"
    fi

    # Write config
    local config_file="$TARGET_DIR/connection.conf"
    cat > "$config_file" <<EOF
# ClaudeWatch Connection Configuration — generated $(date)
PRIMARY_HOST=$primary_host
PRIMARY_PORT=$primary_port
FALLBACK_HOST=$fallback_host
FALLBACK_PORT=$fallback_port
SSH_USER=$ssh_user
SSH_KEY_PATH=$ssh_key_path
EOF
    info "Config saved to: $config_file"
    echo ""
    echo -e "   ${C_BOLD}Connection summary:${C_RESET}"
    echo "   Primary:  $ssh_user@$primary_host:$primary_port"
    [ -n "$fallback_host" ] && echo "   Fallback: $ssh_user@$fallback_host:$fallback_port"
    echo ""
    echo "   Enter these details in the ClaudeWatch app on your watch."
    touch "$STATE_CONN_OK"
    pause
}

# --- Main Execution ---
self_update
while true; do
    mkdir -p "$TARGET_DIR"; header
    echo "Select a step to run. Completed steps are marked with ✓."
    [[ -f "$STATE_FIREBASE_OK" ]] && s2="✓" || s2=" "
    [[ -f "$STATE_DEPS_OK" ]] && s3="✓" || s3=" "
    [[ -f "$STATE_PATH_OK" ]] && s4="✓" || s4=" "
    [[ -f "$STATE_HOOK_OK" ]] && s5="✓" || s5=" "
    [[ -f "$STATE_SSH_OK" ]] && s6="✓" || s6=" "
    [[ -f "$STATE_CONN_OK" ]] && s7="✓" || s7=" "
    echo -e "\n  [0] Check/Install All Prerequisites"
    echo "  [1] Download/Update Server Scripts"
    echo -e "  [2] Configure Firebase Notifications  [$s2]"
    echo -e "  [3] Install App Dependencies          [$s3]"
    echo -e "  [4] Set up Usage Command (PATH)       [$s4]"
    echo -e "  [5] Configure Claude Code Hook        [$s5]"
    echo -e "  [6] Generate SSH Key for Watch        [$s6]"
    echo -e "  [7] Configure Connection Address      [$s7]"
    echo "  [q] Quit"
    read -p "Enter your choice: " choice
    case $choice in
        0) check_prerequisites ;;
        1) download_scripts ;;
        2) setup_firebase ;;
        3) install_dependencies ;;
        4) setup_path ;;
        5) setup_hooks ;;
        6) setup_ssh_key ;;
        7) setup_connection ;;
        q|Q) break ;;
        *) warn "Invalid option." && sleep 1 ;;
    esac
done

# --- Final Summary ---
header
echo -e "${C_BOLD}Setup Exited. Current configuration status:${C_RESET}\n------------------------------------------------------"
[[ -f "$STATE_FIREBASE_OK" ]] && info "Firebase Notifications" || warn "Firebase Notifications (run Step 2)"
[[ -f "$STATE_DEPS_OK" ]] && info "App Dependencies"       || warn "App Dependencies       (run Step 3)"
[[ -f "$STATE_PATH_OK" ]] && info "Usage Command (PATH)"   || warn "Usage Command (PATH)   (run Step 4)"
[[ -f "$STATE_HOOK_OK" ]] && info "Claude Code Hook"       || warn "Claude Code Hook       (run Step 5)"
[[ -f "$STATE_SSH_OK"  ]] && info "SSH Key for Watch"      || warn "SSH Key for Watch      (run Step 6)"
[[ -f "$STATE_CONN_OK" ]] && info "Connection Address"     || warn "Connection Address     (run Step 7)"
echo -e "------------------------------------------------------"
