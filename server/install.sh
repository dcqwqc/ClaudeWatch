#!/bin/bash
#
# ClaudeWatch Server-Side Interactive Installer
# This wizard will guide you through setting up all necessary
# server components for the ClaudeWatch app.
#

# --- Colors for better readability ---
C_RESET='\033[0m'
C_RED='\033[0;31m'
C_GREEN='\033[0;32m'
C_YELLOW='\033[0;33m'
C_BLUE='\033[0;34m'
C_BOLD='\033[1m'

# --- Configuration ---
GITHUB_REPO="dcqwqc/ClaudeWatch"
TARGET_DIR="$HOME/.claude-watch"

# --- Helper Functions ---
step() {
    echo -e "
${C_BLUE}==> ${C_BOLD}$1${C_RESET}"
}

prompt() {
    echo -e "${C_YELLOW}ACTION: ${C_RESET}$1"
}

info() {
    echo -e "   ${C_GREEN}✓${C_RESET} $1"
}

warn() {
    echo -e "   ${C_YELLOW}⚠️  $1${C_RESET}"
}

fail() {
    echo -e "
${C_RED}ERROR: $1${C_RESET}"
    exit 1
}

command_exists() {
    command -v "$1" &>/dev/null
}

# --- Main Setup Functions ---

check_prerequisites() {
    step "Checking for required tools..."
    local missing=0
    for cmd in git python3 pip npm jq; do
        if ! command_exists "$cmd"; then
            warn "Command not found: $cmd"
            missing=1
        else
            info "$cmd is installed."
        fi
    done
    if [ "$missing" -eq 1 ]; then
        echo -e "
${C_YELLOW}Some required tools are missing. Please install them first.${C_RESET}"
        echo "On Debian/Ubuntu: sudo apt update && sudo apt install git python3-pip npm jq"
        echo "On Red Hat/CentOS: sudo yum install git python3-pip npm jq"
        exit 1
    fi
}

download_scripts() {
    step "Downloading server scripts..."
    if [ -d "$TARGET_DIR" ]; then
        warn "Existing directory found at $TARGET_DIR. Files will be overwritten."
    else
        mkdir -p "$TARGET_DIR"
    fi

    local TEMP_DIR
    TEMP_DIR=$(mktemp -d)
    trap 'rm -rf -- "$TEMP_DIR"' EXIT

    info "Fetching latest scripts from GitHub..."
    git init -q "$TEMP_DIR"
    (
        cd "$TEMP_DIR" || exit
        git remote add origin "https://github.com/$GITHUB_REPO.git"
        git config core.sparseCheckout true
        echo "server/" >.git/info/sparse-checkout
        git pull -q --depth=1 origin main
    )

    info "Copying files to $TARGET_DIR..."
    cp -r "$TEMP_DIR"/server/* "$TARGET_DIR"
    info "Scripts installed successfully."
}

setup_firebase() {
    step "Configuring Firebase (for 'Done' notifications)..."
    prompt "Open this link in your browser to visit the Firebase Console:"
    echo -e "  ${C_BLUE}${C_BOLD}https://console.firebase.google.com/${C_RESET}"
    
    echo -e "
Follow these steps in the browser:"
    echo "  1. Create a new project (or use an existing one)."
    echo "  2. Go to ${C_BOLD}Project settings${C_RESET} (click the ⚙️ icon)."
    echo "  3. Go to the ${C_BOLD}Service Accounts${C_RESET} tab."
    echo "  4. Click the "${C_BOLD}Generate new private key${C_RESET}" button and download the JSON file."

    echo -e "
${C_YELLOW}Once downloaded, please enter the full path to that JSON file below.${C_RESET}"
    read -p "Path to your service account file: " a_p_i_key
    
    if [ -f "$a_p_i_key" ]; then
        cp "$a_p_i_key" "$TARGET_DIR/service-account.json"
        info "Firebase service account copied successfully."
    else
        fail "File not found at '$a_p_i_key'. Please check the path and run the installer again."
    fi

    info "Installing required Python libraries..."
    pip install --user --quiet --disable-pip-version-check google-auth requests
    info "Python libraries installed."
}

setup_usage_stats() {
    step "Configuring Usage Statistics..."
    if ! command_exists ccusage; then
        prompt "The 'ccusage' tool is required to fetch token usage."
        read -p "Install it globally via npm? (y/N) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            info "Installing 'ccusage' globally..."
            npm install -g ccusage
            info "'ccusage' installed."
        else
            warn "'ccusage' not installed. Usage stats will not work."
            return
        fi
    else
        info "'ccusage' is already installed."
    fi

    if [ -d "$HOME/.local/bin" ]; then
        info "Creating a symlink for 'ccusage' in ~/.local/bin..."
        ln -sf "$TARGET_DIR/claude-watch-usage.sh" "$HOME/.local/bin/ccusage"
        chmod +x "$TARGET_DIR/claude-watch-usage.sh"
        if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
            warn "Your PATH does not seem to include ~/.local/bin."
            warn "You may need to add 'export PATH=\$PATH:\$HOME/.local/bin' to your .bashrc or .profile"
        fi
        info "Usage stats script is now set up."
    else
        warn "Directory ~/.local/bin not found. Could not create symlink."
        warn "Please link '$TARGET_DIR/claude-watch-usage.sh' to a directory in your PATH manually."
    fi
}

setup_hooks() {
    step "Configuring Claude Code Hook..."
    local hook_command_path="$TARGET_DIR/claude-done-hook.sh"
    chmod +x "$hook_command_path"

    local hook_json
    hook_json=$(
        jq -n 
            --arg cmd "$hook_command_path" 
            '{hooks: {Stop: [{hooks: [{type: "command", command: $cmd}]}]}}'
    )
    
    prompt "Open your Claude Code settings file at ~/.claude/settings.json"
    echo "You need to add the following JSON block. If a 'hooks' section already exists, merge it."
    echo -e "
${C_GREEN}${hook_json}${C_RESET}
"
    read -p "Press Enter to continue when you have updated the file..."
}

# --- Main Execution ---
main() {
    echo -e "${C_GREEN}${C_BOLD}--- ClaudeWatch Server Setup Wizard ---${C_RESET}"
    
    check_prerequisites
    download_scripts
    setup_firebase
    setup_usage_stats
    setup_hooks

    echo -e "
${C_GREEN}${C_BOLD}🎉 All done! Your server is now configured for ClaudeWatch.${C_RESET}"
    echo "The final step is to add your watch's public key to your server."
    echo "On the watch, go to Settings -> Generate key pair -> Copy public key, then scan the QR code"
    echo "and paste the key into your server's ${C_BOLD}~/.ssh/authorized_keys${C_RESET} file."
}

main
