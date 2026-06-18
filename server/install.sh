#!/bin/bash
#
# ClaudeWatch Server-Side Interactive Setup Utility v3.0 FINAL
# A professional-grade, platform-aware, self-updating wizard.
#

# --- Style & Color Definitions ---
C_RESET='\033[0m'
C_RED='\033[0;31m'
C_GREEN='\033[0;32m'
C_YELLOW='\033[0;33m'
C_BLUE='\033[0;34m'
C_CYAN='\033[0;36m'
C_BOLD='\033[1m'

# --- Configuration & Global Variables ---
GITHUB_REPO="dcqwqc/ClaudeWatch"
TARGET_DIR="$HOME/.claude-watch"
OS_TYPE=""

# --- State Markers ---
STATE_FIREBASE_OK="$TARGET_DIR/.state_firebase_ok"
STATE_DEPS_OK="$TARGET_DIR/.state_deps_ok"
STATE_PATH_OK="$TARGET_DIR/.state_path_ok"
STATE_HOOK_OK="$TARGET_DIR/.state_hook_ok"

# --- Helper Functions ---
header() {
    clear
    echo -e "${C_GREEN}${C_BOLD}--- ClaudeWatch Server Setup Utility v3.0 ---${C_RESET}"
    echo "This wizard guides you through configuring your server."
    echo "It saves your progress, so you can quit and resume anytime."
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

detect_os() {
    case "$(uname -s)" in
        Linux*)  OS_TYPE=Linux;;
        Darwin*) OS_TYPE=macOS;;
        *)       OS_TYPE=Windows;; # Assume Git Bash/MSYS on Windows
    esac
    info "Detected Operating System: $OS_TYPE"
}

# --- Automation & Prerequisite Functions ---

prompt_to_install() {
    local cmd_name="$1"
    local pkg_name="$2"
    
    prompt "The required tool '${C_BOLD}$cmd_name${C_RESET}' is missing."
    read -p "May I attempt to install it for you? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        warn "'$cmd_name' will not be installed. Some features may fail."
        return 1
    fi

    local install_cmd=""
    case "$OS_TYPE" in
        Linux)
            if command_exists apt; then install_cmd="sudo apt update && sudo apt install -y $pkg_name"
            elif command_exists yum; then install_cmd="sudo yum install -y $pkg_name"
            elif command_exists dnf; then install_cmd="sudo dnf install -y $pkg_name"
            else warn "Unsupported Linux package manager. Please install '$pkg_name' manually."; return 1; fi
            ;;
        macOS)
            if command_exists brew; then install_cmd="brew install $pkg_name"
            else warn "Homebrew not found. Please install it from https://brew.sh, then install '$pkg_name' manually."; return 1; fi
            ;;
        Windows)
            warn "On Windows, packages must be installed manually."
            echo "A common way to install '${C_BOLD}$pkg_name${C_RESET}' is using Chocolatey ('choco install $pkg_name') or Scoop."
            echo "Please install it in another terminal, ensure it's in your PATH, then continue."
            read -p "Press [Enter] to acknowledge."
            return 1
            ;;
    esac

    info "Running: $install_cmd"
    if eval "$install_cmd"; then
        info "'$pkg_name' installed successfully."
        return 0
    else
        warn "Installation failed. Please try installing '$pkg_name' manually."
        return 1
    fi
}

check_prerequisites() {
    step "Step 0: Checking System Prerequisites..."
    local all_ok=true
    local deps=(
        "git:git" "python3:python3" "pip:python3-pip"
        "npm:npm" "jq:jq"
    )

    for dep in "${deps[@]}"; do
        IFS=":" read -r cmd pkg <<< "$dep"
        if ! command_exists "$cmd"; then
            if prompt_to_install "$cmd" "$pkg"; then
                info "$cmd is now installed."
            else
                all_ok=false
            fi
        else
            info "$cmd is already installed."
        fi
    done

    if [ "$all_ok" = false ]; then warn "Some prerequisites are still missing."; else info "All prerequisite tools seem to be installed."; fi
    pause
}


# --- Main Setup Functions (Simplified for brevity, logic is the same) ---

download_scripts() {
    step "Step 1: Downloading Server Scripts..."
    if ! command_exists git; then warn "git is not installed. Please run Step 0."; pause; return; fi
    if [ -d "$TARGET_DIR" ]; then info "Installation directory already exists."; else mkdir -p "$TARGET_DIR"; fi
    local TEMP_DIR; TEMP_DIR=$(mktemp -d); trap 'rm -rf -- "$TEMP_DIR"' EXIT
    info "Fetching latest scripts..."; git init -q "$TEMP_DIR"; (cd "$TEMP_DIR" || exit; git remote add origin "https://github.com/$GITHUB_REPO.git" &>/dev/null; git config core.sparseCheckout true; echo "server/" >.git/info/sparse-checkout; git pull -q --depth=1 origin main)
    info "Copying files to $TARGET_DIR..."; cp -rf "$TEMP_DIR"/server/* "$TARGET_DIR"; info "Scripts downloaded."
    pause
}

setup_firebase() {
    step "Step 2: Configuring Firebase..."
    while true; do
        prompt "Open this link: ${C_CYAN}https://console.firebase.google.com/${C_RESET}"; echo "  1. Create a project -> Project settings -> Service Accounts."; echo "  2. Click "${C_BOLD}Generate new private key${C_RESET}" and download the JSON file."
        prompt "Enter the ${C_BOLD}full path${C_RESET} to the downloaded JSON file."; read -p "Path: " key_path
        if [ -f "$key_path" ]; then cp "$key_path" "$TARGET_DIR/service-account.json"; info "Firebase key copied."; touch "$STATE_FIREBASE_OK"; break; else
            warn "File not found. Please check the path."; read -p "Retry? (y/N) " -n 1 -r; echo; if [[ ! $REPLY =~ ^[Yy]$ ]]; then break; fi
        fi
    done
    pause
}

install_dependencies() {
    step "Step 3: Installing App Dependencies..."
    if ! command_exists pip; then warn "pip not found. Please run Step 0."; else
        info "Installing Python libraries..."; pip install --user --quiet --disable-pip-version-check google-auth requests; info "Python libraries installed."
    fi
    if ! command_exists npm; then warn "npm not found. Please run Step 0."; else
        if ! command_exists ccusage; then if prompt_to_install "ccusage" "ccusage" "npm"; then touch "$STATE_DEPS_OK"; fi
        else info "'ccusage' is already installed."; touch "$STATE_DEPS_OK"; fi
    fi
    pause
}

setup_path() {
    step "Step 4: Setting up Usage Command..."
    chmod +x "$TARGET_DIR/claude-watch-usage.sh"
    if [[ "$OS_TYPE" == "Linux" || "$OS_TYPE" == "macOS" ]]; then
        info "Creating symlink in ~/.local/bin..."; mkdir -p "$HOME/.local/bin"; ln -sf "$TARGET_DIR/claude-watch-usage.sh" "$HOME/.local/bin/ccusage"
        if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then warn "Your PATH may not include ~/.local/bin. Consider adding it to your .bashrc/.zprofile"; fi
        info "Command 'ccusage' is now available."; touch "$STATE_PATH_OK"
    else
        warn "On Windows, you must add a directory to your PATH manually."; prompt "Please add this directory to your User or System Environment Variables: ${C_CYAN}$TARGET_DIR${C_RESET}"; read -p "Acknowledge? (y/N) " -n 1 -r; echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then touch "$STATE_PATH_OK"; fi
    fi
    pause
}

setup_hooks() {
    step "Step 5: Configuring Claude Code Hook..."
    if ! command_exists jq; then warn "jq not found. Please run Step 0."; pause; return; fi
    local settings_file="$HOME/.claude/settings.json"; local backup_file="$settings_file.bak.$(date +%s)"; local hook_cmd="$TARGET_DIR/claude-done-hook.sh"; chmod +x "$hook_cmd"
    if [ ! -f "$settings_file" ]; then warn "Claude settings file not found at $settings_file"; pause; return; fi

    prompt "This step will automatically merge the 'Stop' hook into ${C_CYAN}$settings_file${C_RESET}."; read -p "A backup will be created. Proceed? (y/N) " -n 1 -r; echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then warn "Hook setup skipped."; pause; return; fi

    cp "$settings_file" "$backup_file"; info "Backup created at $backup_file"
    local hook_obj='{ "hooks": { "Stop": [ { "hooks": [ { "type": "command", "command": "'"$hook_cmd"'" } ] } ] } }'
    local temp_json; temp_json=$(mktemp)
    if jq -s '.[0] * .[1]' "$settings_file" <(echo "$hook_obj") > "$temp_json"; then
        mv "$temp_json" "$settings_file"; info "Successfully merged hook into settings file."; touch "$STATE_HOOK_OK"
    else
        warn "Automated merge failed! Your original file is safe."; warn "Please add the hook manually. The required JSON is:"; echo -e "${C_GREEN}${hook_obj}${C_RESET}"
    fi
    pause
}

# --- Main Execution ---
detect_os
while true; do
    mkdir -p "$TARGET_DIR"
    header
    echo "Select a step. Completed steps are marked with ✓."
    [[ -f "$STATE_FIREBASE_OK" ]] && s2="✓" || s2=" "; [[ -f "$STATE_DEPS_OK" ]] && s3="✓" || s3=" "; [[ -f "$STATE_PATH_OK" ]] && s4="✓" || s4=" "; [[ -f "$STATE_HOOK_OK" ]] && s5="✓" || s5=" "
    echo -e "
  [0] Check/Install All Prerequisites"; echo "  [1] Download/Update Server Scripts"; echo -e "  [2] Configure Firebase [$s2]"; echo -e "  [3] Install Dependencies [$s3]"; echo -e "  [4] Setup PATH Command   [$s4]"; echo -e "  [5] Configure Hook       [$s5]"; echo "  [q] Quit"
    read -p "Enter your choice: " choice
    case $choice in
    0) check_prerequisites ;; 1) download_scripts ;; 2) setup_firebase ;;
    3) install_dependencies ;; 4) setup_path ;; 5) setup_hooks ;;
    q | Q) break ;; *) warn "Invalid option." && sleep 1 ;;
    esac
done

# Final Summary
header
echo -e "${C_BOLD}Setup Exited. Current configuration status:${C_RESET}"; echo "------------------------------------------------------"
[[ -f "$STATE_FIREBASE_OK" ]] && info "Firebase Notifications" || warn "Firebase Notifications"
[[ -f "$STATE_DEPS_OK" ]]     && info "App Dependencies"     || warn "App Dependencies"
[[ -f "$STATE_PATH_OK" ]]     && info "Usage Command (PATH)" || warn "Usage Command (PATH)"
[[ -f "$STATE_HOOK_OK" ]]     && info "Claude Code Hook"     || warn "Claude Code Hook"
echo "------------------------------------------------------"; echo -e "
Don't forget the final manual step:"; prompt "Add watch public key to ${C_CYAN}~/.ssh/authorized_keys${C_RESET}"; echo -e "
${C_GREEN}Thank you for using ClaudeWatch!${C_RESET}"
