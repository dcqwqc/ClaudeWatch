#!/bin/bash
#
# ClaudeWatch Server-Side Interactive Setup Utility v3.1
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
header() { clear; echo -e "${C_GREEN}${C_BOLD}--- ClaudeWatch Server Setup Utility v3.1 ---${C_RESET}
This wizard guides you through server configuration. It saves your progress.
--------------------------------------------------------------------"; }
step() { echo -e "\n${C_BLUE}==> ${C_BOLD}$1${C_RESET}"; }
prompt() { echo -e "\n${C_YELLOW}ACTION: ${C_RESET}$1"; }
info() { echo -e "   ${C_GREEN}✓${C_RESET} $1"; }
warn() { echo -e "   ${C_YELLOW}⚠️  $1${C_RESET}"; }
pause() { read -p "Press [Enter] to return to the main menu..."; }
command_exists() { command -v "$1" &>/dev/null; }

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
            # Windows: try winget → choco → scoop, no sudo needed
            if command_exists winget; then
                # winget uses different package IDs for some tools
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
            # Linux / macOS
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
    if eval "$install_cmd"; then
        info "'$pkg_name' installed successfully."
        return 0
    else
        warn "Installation failed. Please install '$pkg_name' manually and re-run this step."
        return 1
    fi
}

check_prerequisites() {
    step "Step 0: Checking System Prerequisites..."
    local all_ok=true
    local deps=("git:git:sys" "python3:python3:sys" "pip:python3-pip:sys" "npm:npm:sys" "jq:jq:sys")
    for dep in "${deps[@]}"; do
        IFS=":" read -r cmd pkg type <<< "$dep"
        if ! command_exists "$cmd"; then
            if prompt_to_install "$cmd" "$pkg" "$type"; then
                info "$cmd is now installed."
            else
                all_ok=false
            fi
        else
            info "$cmd is already installed."
        fi
    done
    if [ "$all_ok" = false ]; then warn "Some prerequisites are still missing."; else info "All prerequisites are installed."; fi
    pause
}

# --- Main Setup Functions ---
download_scripts() {
    step "Step 1: Downloading Server Scripts..."
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
    while true; do
        prompt "Open this link in your browser: ${C_CYAN}https://console.firebase.google.com/${C_RESET}"
        echo -e "Instructions: 1. Create a project. 2. Go to ${C_BOLD}Project settings > Service Accounts${C_RESET}."
        echo -e "              3. Click '${C_BOLD}Generate new private key${C_RESET}' and download the JSON file."
        prompt "Enter the ${C_BOLD}full path${C_RESET} to the downloaded JSON file."
        echo "   Windows: C:\\Users\\YourName\\Downloads\\my-project-firebase.json"
        echo "   MSYS:    /c/Users/YourName/Downloads/my-project-firebase.json"
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
    if ! command_exists pip; then
        warn "pip not found. Please run Step 0."
    else
        info "Installing Python libraries..."
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
    chmod +x "$TARGET_DIR/claude-watch-usage.sh"
    if $IS_WINDOWS; then
        warn "Windows detected. Automatic PATH modification is not supported here."
        prompt "Add the scripts directory to your Windows PATH manually:"
        echo -e "   ${C_CYAN}$TARGET_DIR${C_RESET}"
        echo ""
        echo "   Quick method — run this in PowerShell (as your user, no Admin needed):"
        echo -e "   ${C_CYAN}[Environment]::SetEnvironmentVariable('PATH', \$env:PATH + ';$(cygpath -w "$TARGET_DIR")', 'User')${C_RESET}"
        read -p "Acknowledge and mark complete? (y/N) " -n 1 -r; echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then touch "$STATE_PATH_OK"; fi
    elif [[ "$_UNAME" == "Linux" || "$_UNAME" == "Darwin" ]]; then
        info "Detected Linux/macOS. Creating symlink..."
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
    if ! command_exists jq; then warn "jq not found. Please run Step 0 first."; pause; return; fi
    local settings_file="$HOME/.claude/settings.json"
    local backup_file="$settings_file.bak"
    local hook_cmd="$TARGET_DIR/claude-done-hook.sh"
    chmod +x "$hook_cmd"
    if [ ! -f "$settings_file" ]; then warn "Claude settings file not found at $settings_file"; pause; return; fi
    prompt "This step will automatically add the 'Stop' hook to $settings_file."
    read -p "A backup will be created. Proceed? (y/N) " -n 1 -r; echo
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
prompt "Add your watch's public key to ${C_CYAN}~/.ssh/authorized_keys${C_RESET}\n"
