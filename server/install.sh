#!/bin/bash
#
# ClaudeWatch Server-Side Interactive Setup Utility
# A persistent, menu-driven wizard to guide you through setup.
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
# The script will create these files to track completed steps
STATE_FIREBASE_OK="$TARGET_DIR/.state_firebase_ok"
STATE_DEPS_OK="$TARGET_DIR/.state_deps_ok"
STATE_USAGE_OK="$TARGET_DIR/.state_usage_ok"
STATE_HOOK_OK="$TARGET_DIR/.state_hook_ok"

# --- Helper Functions ---
header() {
    clear
    echo -e "${C_GREEN}${C_BOLD}--- ClaudeWatch Server Setup Utility ---${C_RESET}"
    echo "This wizard will guide you through configuring your server."
    echo -e "It saves your progress, so you can quit and resume at any time."
    echo "---------------------------------------------------"
}

step() {
    echo -e "
${C_BLUE}==> ${C_BOLD}$1${C_RESET}"
}

prompt() {
    echo -e "
${C_YELLOW}ACTION: ${C_RESET}$1"
}

info() {
    echo -e "   ${C_GREEN}✓${C_RESET} $1"
}

warn() {
    echo -e "   ${C_YELLOW}⚠️  $1${C_RESET}"
}

pause() {
    read -p "Press [Enter] to return to the main menu..."
}

command_exists() {
    command -v "$1" &>/dev/null
}

# --- Setup Logic Functions ---

check_prerequisites() {
    step "Step 0: Checking Prerequisites..."
    local all_ok=true
    for cmd in git python3 pip npm jq; do
        if ! command_exists "$cmd"; then
            warn "Command not found: '$cmd'. This is required."
            all_ok=false
        fi
    done

    if [ "$all_ok" = false ]; then
        prompt "Please install the missing tools listed above, then re-run this utility."
    else
        info "All prerequisite tools are installed."
    fi
    pause
}

download_scripts() {
    step "Step 1: Downloading Server Scripts..."
    if [ -d "$TARGET_DIR" ]; then
        info "Directory $TARGET_DIR already exists."
    else
        info "Creating installation directory at $TARGET_DIR..."
        mkdir -p "$TARGET_DIR"
    fi

    local TEMP_DIR
    TEMP_DIR=$(mktemp -d)
    trap 'rm -rf -- "$TEMP_DIR"' EXIT

    info "Fetching latest scripts from GitHub..."
    git init -q "$TEMP_DIR"
    (
        cd "$TEMP_DIR" || exit
        git remote add origin "https://github.com/$GITHUB_REPO.git" &>/dev/null
        git config core.sparseCheckout true
        echo "server/" >.git/info/sparse-checkout
        git pull -q --depth=1 origin main
    )

    info "Copying files to $TARGET_DIR..."
    cp -rf "$TEMP_DIR"/server/* "$TARGET_DIR"
    info "Scripts downloaded and installed."
    pause
}

setup_firebase() {
    step "Step 2: Configuring Firebase Notifications..."
    while true; do
        prompt "Open this link in your browser: ${C_CYAN}https://console.firebase.google.com/${C_RESET}"
        echo "In the Firebase Console, follow these steps:"
        echo "  1. Create a project."
        echo "  2. Go to ${C_BOLD}Project settings > Service Accounts${C_RESET}."
        echo "  3. Click "${C_BOLD}Generate new private key${C_RESET}" and download the JSON file."
        
        prompt "Now, please enter the ${C_BOLD}full path${C_RESET} to the downloaded JSON file."
        echo "   (Example for Linux: /home/user/Downloads/my-project-123.json)"
        echo "   (Example for Windows/Git Bash: /c/Users/User/Downloads/my-project-123.json)"
        read -p "Path: " key_path

        if [ -f "$key_path" ]; then
            cp "$key_path" "$TARGET_DIR/service-account.json"
            info "Firebase service account key successfully copied."
            touch "$STATE_FIREBASE_OK"
            break
        else
            warn "File not found at '$key_path'. Please check the path and try again."
            read -p "Retry? (y/N) " -n 1 -r
            echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                break
            fi
        fi
    done
    pause
}

install_dependencies() {
    step "Step 3: Installing Dependencies..."
    info "Installing Python libraries ('google-auth', 'requests')..."
    pip install --user --quiet --disable-pip-version-check google-auth requests
    info "Python libraries installed."

    if ! command_exists ccusage; then
        prompt "The 'ccusage' Node.js package is required for usage stats."
        read -p "Install it globally via npm? (y/N) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            info "Installing 'ccusage' globally. This may take a moment..."
            npm install -g ccusage
            info "'ccusage' installed."
            touch "$STATE_DEPS_OK"
        else
            warn "'ccusage' not installed. Usage stats will not function."
        fi
    else
        info "'ccusage' is already installed."
        touch "$STATE_DEPS_OK"
    fi
    pause
}

setup_path() {
    step "Step 4: Setting up Usage Command..."
    chmod +x "$TARGET_DIR/claude-watch-usage.sh"

    local os
    os=$(uname -s)
    if [[ "$os" == "Linux" || "$os" == "Darwin" ]]; then
        info "Detected Linux/macOS. Creating symlink in ~/.local/bin..."
        mkdir -p "$HOME/.local/bin"
        ln -sf "$TARGET_DIR/claude-watch-usage.sh" "$HOME/.local/bin/ccusage"
        if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
            warn "Your PATH does not seem to include ~/.local/bin."
            warn "You may need to add 'export PATH=\$PATH:\$HOME/.local/bin' to your .bashrc or .profile"
        fi
        info "Usage command 'ccusage' is now available."
        touch "$STATE_USAGE_OK"
    else # Likely Windows (Git Bash / MSYS)
        warn "Detected Windows-like environment. Symlinks are unreliable."
        prompt "Please add the scripts directory to your system's PATH variable manually."
        echo "Directory to add: ${C_CYAN}$TARGET_DIR${C_RESET}"
        echo "After adding it, you can run 'ccusage' from a new terminal."
        read -p "Acknowledge and mark this step as complete? (y/N) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            touch "$STATE_USAGE_OK"
        fi
    fi
    pause
}

setup_hooks() {
    step "Step 5: Configuring Claude Code Hook..."
    chmod +x "$TARGET_DIR/claude-done-hook.sh"
    
    # Replace placeholder with actual home directory path
    local real_hook_path="$TARGET_DIR/claude-done-hook.sh"

    local hook_json
    hook_json=$(jq -n --arg cmd "$real_hook_path" '{hooks: {Stop: [{hooks: [{type: "command", command: $cmd}]}]}}')

    prompt "Please open your Claude Code settings file at: ${C_CYAN}~/.claude/settings.json${C_RESET}"
    echo "You must add the following JSON block. If a 'hooks' section already exists, you may need to merge this 'Stop' event carefully."
    echo -e "
${C_GREEN}${hook_json}${C_RESET}
"
    
    read -p "Acknowledge and mark this step as complete? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        touch "$STATE_HOOK_OK"
    fi
    pause
}

# --- Main Menu ---
while true; do
    header
    echo "Select a step to run. Completed steps are marked with ✓."
    
    # Check states and set display strings
    [[ -f "$STATE_FIREBASE_OK" ]] && s2="✓" || s2=" "
    [[ -f "$STATE_DEPS_OK" ]] && s3="✓" || s3=" "
    [[ -f "$STATE_USAGE_OK" ]] && s4="✓" || s4=" "
    [[ -f "$STATE_HOOK_OK" ]] && s5="✓" || s5=" "

    echo -e "
  [0] Check Prerequisites"
    echo "  [1] Download/Update Server Scripts"
    echo -e "  [2] Configure Firebase Notifications  [$s2]"
    echo -e "  [3] Install Dependencies (Python/npm) [$s3]"
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
    *) warn "Invalid option. Please try again." && sleep 1 ;;
    esac
done

echo -e "
${C_GREEN}Setup utility exited. Thank you for using ClaudeWatch!${C_RESET}"
