#!/bin/bash
#
# ClaudeWatch Server-Side Interactive Setup Utility v2.0
# A persistent, menu-driven wizard for a fully guided setup.
#

# --- Style & Color Definitions ---
C_RESET='\033[0m'
C_RED='\033[0;31m'
C_GREEN='\033[0;32m'
C_YELLOW='\033[0;33m'
C_BLUE='\033[0;34m'
C_CYAN='\033[0;36m'
C_BOLD='\033[1m'

# --- Configuration ---
GITHUB_REPO="dcqwqc/ClaudeWatch"
TARGET_DIR="$HOME/.claude-watch"

# --- State Markers ---
STATE_FIREBASE_OK="$TARGET_DIR/.state_firebase_ok"
STATE_DEPS_OK="$TARGET_DIR/.state_deps_ok"
STATE_PATH_OK="$TARGET_DIR/.state_path_ok"
STATE_HOOK_OK="$TARGET_DIR/.state_hook_ok"

# --- Helper Functions ---
header() {
    clear
    echo -e "${C_GREEN}${C_BOLD}--- ClaudeWatch Server Setup Utility v2.0 ---${C_RESET}"
    echo "This wizard will guide you through configuring your server."
    echo -e "It saves your progress, so you can quit and resume at any time."
    echo "------------------------------------------------------"
}

step() { echo -e "
${C_BLUE}==> ${C_BOLD}$1${C_RESET}"; }
prompt() { echo -e "
${C_YELLOW}ACTION: ${C_RESET}$1"; }
info() { echo -e "   ${C_GREEN}✓${C_RESET} $1"; }
warn() { echo -e "   ${C_YELLOW}⚠️  $1${C_RESET}"; }
pause() { read -p "Press [Enter] to return to the main menu..."; }
command_exists() { command -v "$1" &>/dev/null; }

# --- Automation & Prerequisite Functions ---

# Tries to install a package after asking for user permission.
# Usage: prompt_to_install <command_name> <package_name> <install_type>
prompt_to_install() {
    local cmd_name="$1"
    local pkg_name="$2"
    local install_type="$3" # 'sys' or 'npm'

    prompt "The required tool '${C_BOLD}$cmd_name${C_RESET}' is not installed."
    read -p "May I attempt to install it for you? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        warn "'$cmd_name' will not be installed. Dependent features may not work."
        return 1
    fi

    local install_cmd=""
    if [[ "$install_type" == "sys" ]]; then
        if command_exists apt; then
            install_cmd="sudo apt update && sudo apt install -y $pkg_name"
        elif command_exists yum; then
            install_cmd="sudo yum install -y $pkg_name"
        elif command_exists dnf; then
            install_cmd="sudo dnf install -y $pkg_name"
        else
            warn "Could not detect a supported package manager (apt, yum, dnf)."
            return 1
        fi
    elif [[ "$install_type" == "npm" ]]; then
        install_cmd="npm install -g $pkg_name"
    fi

    info "Running: $install_cmd"
    if eval "$install_cmd"; then
        info "'$pkg_name' installed successfully."
        return 0
    else
        warn "Installation of '$pkg_name' failed. Please try installing it manually."
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

    if [ "$all_ok" = false ]; then
        warn "Some prerequisites are still missing. Not all features may work."
    else
        info "All prerequisite tools are installed."
    fi
    pause
}


# --- Main Setup Functions ---

download_scripts() {
    step "Step 1: Downloading Server Scripts..."
    if ! command_exists git; then warn "git is not installed. Please run Step 0 first."; pause; return; fi
    
    # ... (rest of the function is unchanged)
    if [ -d "$TARGET_DIR" ]; then info "Directory $TARGET_DIR already exists."; else mkdir -p "$TARGET_DIR"; fi
    local TEMP_DIR; TEMP_DIR=$(mktemp -d); trap 'rm -rf -- "$TEMP_DIR"' EXIT
    info "Fetching latest scripts from GitHub..."; git init -q "$TEMP_DIR"; (cd "$TEMP_DIR" || exit; git remote add origin "https://github.com/$GITHUB_REPO.git" &>/dev/null; git config core.sparseCheckout true; echo "server/" >.git/info/sparse-checkout; git pull -q --depth=1 origin main)
    info "Copying files to $TARGET_DIR..."; cp -rf "$TEMP_DIR"/server/* "$TARGET_DIR"; info "Scripts downloaded and installed."
    pause
}

setup_firebase() {
    step "Step 2: Configuring Firebase Notifications..."
    # ... (rest of the function is unchanged)
    while true; do
        prompt "Open this link in your browser: ${C_CYAN}https://console.firebase.google.com/${C_RESET}"; echo "In the Firebase Console, follow these steps:"; echo "  1. Create a project."; echo "  2. Go to ${C_BOLD}Project settings > Service Accounts${C_RESET}."; echo "  3. Click "${C_BOLD}Generate new private key${C_RESET}" and download the JSON file."
        prompt "Now, please enter the ${C_BOLD}full path${C_RESET} to the downloaded JSON file."; echo "   (Example for Linux: /home/user/Downloads/my-project-123.json)"; echo "   (Example for Windows/Git Bash: /c/Users/User/Downloads/my-project-123.json)"; read -p "Path: " key_path
        if [ -f "$key_path" ]; then cp "$key_path" "$TARGET_DIR/service-account.json"; info "Firebase service account key successfully copied."; touch "$STATE_FIREBASE_OK"; break; else
            warn "File not found at '$key_path'. Please check the path and try again."; read -p "Retry? (y/N) " -n 1 -r; echo; if [[ ! $REPLY =~ ^[Yy]$ ]]; then break; fi
        fi
    done
    pause
}

install_dependencies() {
    step "Step 3: Installing Dependencies..."
    if ! command_exists pip; then warn "pip not found. Please run Step 0."; else
        info "Installing Python libraries ('google-auth', 'requests')..."; pip install --user --quiet --disable-pip-version-check google-auth requests; info "Python libraries installed."
    fi

    if ! command_exists npm; then warn "npm not found. Please run Step 0."; else
        if ! command_exists ccusage; then
            if prompt_to_install "ccusage" "ccusage" "npm"; then touch "$STATE_DEPS_OK"; fi
        else
            info "'ccusage' is already installed."; touch "$STATE_DEPS_OK"
        fi
    fi
    pause
}

setup_path() {
    step "Step 4: Setting up Usage Command..."
    # ... (rest of the function is unchanged)
    chmod +x "$TARGET_DIR/claude-watch-usage.sh"
    local os; os=$(uname -s); if [[ "$os" == "Linux" || "$os" == "Darwin" ]]; then
        info "Detected Linux/macOS. Creating symlink in ~/.local/bin..."; mkdir -p "$HOME/.local/bin"; ln -sf "$TARGET_DIR/claude-watch-usage.sh" "$HOME/.local/bin/ccusage"
        if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then warn "Your PATH does not seem to include ~/.local/bin. You may need to add it to your .bashrc or .profile"; fi
        info "Usage command 'ccusage' is now available."; touch "$STATE_PATH_OK"
    else
        warn "Detected Windows-like environment. Symlinks are unreliable."; prompt "Please add the scripts directory to your system's PATH variable manually."; echo "Directory to add: ${C_CYAN}$TARGET_DIR${C_RESET}"; read -p "Acknowledge and mark step as complete? (y/N) " -n 1 -r; echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then touch "$STATE_PATH_OK"; fi
    fi
    pause
}

setup_hooks() {
    step "Step 5: Configuring Claude Code Hook..."
    if ! command_exists jq; then warn "jq not found. Please run Step 0. Cannot automate this step."; pause; return; fi
    
    local settings_file="$HOME/.claude/settings.json"
    local backup_file="$settings_file.bak"
    local hook_cmd="$TARGET_DIR/claude-done-hook.sh"
    chmod +x "$hook_cmd"

    if [ ! -f "$settings_file" ]; then warn "Claude settings file not found at $settings_file"; pause; return; fi

    prompt "This step will automatically add the 'Stop' hook to your $settings_file."
    read -p "A backup will be created. Is it okay to proceed? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then warn "Hook setup skipped."; pause; return; fi

    # Create a backup
    cp "$settings_file" "$backup_file"
    info "Backup created at $backup_file"

    # The jq magic: create the hook object, then merge it with the existing settings
    local hook_obj='{ "hooks": { "Stop": [ { "hooks": [ { "type": "command", "command": "'"$hook_cmd"'" } ] } ] } }'
    local temp_json
    temp_json=$(mktemp)

    if jq --argjson obj "$hook_obj" '. * $obj' "$settings_file" > "$temp_json"; then
        mv "$temp_json" "$settings_file"
        info "Successfully merged hook into $settings_file."
        touch "$STATE_HOOK_OK"
    else
        warn "Automated merge failed! Your original file is safe."
        warn "Please add the hook manually. The required JSON block is:"
        echo -e "${C_GREEN}${hook_obj}${C_RESET}"
    fi
    pause
}

# --- Main Menu ---
while true; do
    mkdir -p "$TARGET_DIR" # Ensure target directory exists for state files
    header
    echo "Select a step to run. Completed steps are marked with ✓."
    
    # Check states and set display strings
    [[ -f "$STATE_FIREBASE_OK" ]] && s2="✓" || s2=" "
    [[ -f "$STATE_DEPS_OK" ]]     && s3="✓" || s3=" "
    [[ -f "$STATE_PATH_OK" ]]     && s4="✓" || s4=" "
    [[ -f "$STATE_HOOK_OK" ]]     && s5="✓" || s5=" "

    echo -e "
  [0] Check/Install All Prerequisites"
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
    q | Q) break ;;
    *) warn "Invalid option. Please try again." && sleep 0.5 ;;
    esac
done

# --- Final Summary ---
header
echo -e "${C_BOLD}Setup Complete! Here is your configuration status:${C_RESET}"
echo "------------------------------------------------------"
[[ -f "$STATE_FIREBASE_OK" ]] && info "Firebase Notifications" || warn "Firebase Notifications"
[[ -f "$STATE_DEPS_OK" ]]     && info "App Dependencies"     || warn "App Dependencies"
[[ -f "$STATE_PATH_OK" ]]     && info "Usage Command (PATH)"       || warn "Usage Command (PATH)"
[[ -f "$STATE_HOOK_OK" ]]     && info "Claude Code Hook"        || warn "Claude Code Hook"
echo "------------------------------------------------------"
echo -e "
Don't forget the final manual step:"
prompt "Add your watch's public key to your server's ${C_CYAN}~/.ssh/authorized_keys${C_RESET} file."
echo -e "
${C_GREEN}Thank you for using ClaudeWatch!${C_RESET}"
