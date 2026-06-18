#!/bin/bash
#
# ClaudeWatch Server-Side Interactive Setup Utility v3.2
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
STATE_FIREBASE_OK="$TARGET_DIR/.state_firebase_ok"; STATE_DEPS_OK="$TARGET_DIR/.state_deps_ok"; STATE_PATH_OK="$TARGET_DIR/.state_path_ok"; STATE_HOOK_OK="$TARGET_DIR/.state_hook_ok"

# --- Helper Functions ---
header() { clear; echo -e "${C_GREEN}${C_BOLD}--- ClaudeWatch Server Setup Utility v3.2 ---${C_RESET}
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

# --- Main Execution ---
self_update
while true; do
    mkdir -p "$TARGET_DIR"; header
    echo "Select a step to run. Completed steps are marked with ✓."
    [[ -f "$STATE_FIREBASE_OK" ]] && s2="✓" || s2=" "
    [[ -f "$STATE_DEPS_OK" ]] && s3="✓" || s3=" "
    [[ -f "$STATE_PATH_OK" ]] && s4="✓" || s4=" "
    [[ -f "$STATE_HOOK_OK" ]] && s5="✓" || s5=" "
    echo -e "\n  [0] Check/Install All Prerequisites"
    echo "  [1] Download/Update Server Scripts"
    echo -e "  [2] Configure Firebase Notifications  [$s2]"
    echo -e "  [3] Install App Dependencies          [$s3]"
    echo -e "  [4] Set up Usage Command (PATH)       [$s4]"
    echo -e "  [5] Configure Claude Code Hook        [$s5]"
    echo "  [q] Quit"
    read -p "Enter your choice: " choice
    case $choice in
        0) check_prerequisites ;;
        1) download_scripts ;;
        2) setup_firebase ;;
        3) install_dependencies ;;
        4) setup_path ;;
        5) setup_hooks ;;
        q|Q) break ;;
        *) warn "Invalid option." && sleep 1 ;;
    esac
done

# --- Final Summary ---
header
echo -e "${C_BOLD}Setup Exited. Current configuration status:${C_RESET}\n------------------------------------------------------"
[[ -f "$STATE_FIREBASE_OK" ]] && info "Firebase Notifications" || warn "Firebase Notifications"
[[ -f "$STATE_DEPS_OK" ]] && info "App Dependencies" || warn "App Dependencies"
[[ -f "$STATE_PATH_OK" ]] && info "Usage Command (PATH)" || warn "Usage Command (PATH)"
[[ -f "$STATE_HOOK_OK" ]] && info "Claude Code Hook" || warn "Claude Code Hook"
echo -e "------------------------------------------------------"
prompt "Don't forget: add your watch's public key to ${C_CYAN}~/.ssh/authorized_keys${C_RESET}\n"
