#!/bin/bash
#
# Installer for the ClaudeWatch server-side components.
# This script will download the latest versions of the server scripts
# to ~/.claude-watch on your local machine or server.
#
# Usage:
#   bash -c "$(curl -fsSL https://raw.githubusercontent.com/YOUR_USERNAME/ClaudeWatch/main/server/install.sh)"
#

set -e # Exit immediately if a command exits with a non-zero status.

# --- Configuration (UPDATE THIS in the repository if your details change) ---
# Replace this with the actual GitHub username and repository name.
GITHUB_REPO="dcqwqc/ClaudeWatch"
# ---

TARGET_DIR="$HOME/.claude-watch"

# Function to check for required commands
command_exists() {
    command -v "$1" &> /dev/null
}

main() {
    echo "Installing ClaudeWatch Server Scripts..."

    # 1. Check for prerequisites
    if ! command_exists git; then
        echo "Error: Git is not installed. Please install it to continue."
        echo "On Debian/Ubuntu: sudo apt update && sudo apt install git"
        echo "On Red Hat/CentOS: sudo yum install git"
        exit 1
    fi

    # 2. Create a temporary directory for sparse checkout
    TEMP_DIR=$(mktemp -d)
    # Ensure cleanup happens on script exit
    trap 'rm -rf -- "$TEMP_DIR"' EXIT

    cd "$TEMP_DIR"

    # 3. Perform a sparse checkout of the 'server' directory
    echo "Fetching latest scripts from GitHub repository: $GITHUB_REPO..."
    git init -q
    git remote add origin "https://github.com/$GITHUB_REPO.git"
    git config core.sparseCheckout true
    # We only want the 'server' directory
    echo "server/" > .git/info/sparse-checkout
    # Pull only the main branch, single commit deep.
    git pull -q --depth=1 origin main

    # 4. Copy the scripts to the target directory
    echo "Installing scripts to $TARGET_DIR..."
    mkdir -p "$TARGET_DIR"
    # Copy the contents of the 'server' folder, not the folder itself
    cp -r server/* "$TARGET_DIR"

    # 5. Set executable permissions
    echo "Setting script permissions..."
    chmod +x "$TARGET_DIR/claude-watch-usage.sh"
    chmod +x "$TARGET_DIR/claude-done-hook.sh"
    # The installer itself doesn't need to be executable in the target dir
    chmod -x "$TARGET_DIR/install.sh"

    echo ""
    echo "✅ Installation complete!"
    echo ""
    echo "-------------------------"
    echo "--- NEXT STEPS ---"
    echo "-------------------------"
    echo "You must now configure your environment. Please follow the full guide"
    echo "in the new README file located at:"
    echo "  $TARGET_DIR/README.md"
    echo ""
    echo "Key steps will include:"
    echo "  - Setting up your Firebase service account."
    echo "  - Installing Python dependencies."
    echo "  - Linking the usage script to your PATH."
    echo "  - Configuring your Claude Code hooks."
}

# Execute the main function
main
