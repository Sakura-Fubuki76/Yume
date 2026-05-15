#!/bin/bash
set -euo pipefail

PREBUILTS_DIR="app/src/main/cpp/prebuilts"
PREBUILTS_REPO="Sakura-Fubuki76/mpv-android"
TAG=""
ABI=""

while [ $# -gt 0 ]; do
    case "$1" in
        --tag) TAG="$2"; shift 2 ;;
        *) ABI="$1"; shift ;;
    esac
done
ABI="${ABI:-arm64-v8a}"

if [ -d "$PREBUILTS_DIR/$ABI" ] && [ "$(ls -A "$PREBUILTS_DIR/$ABI" 2>/dev/null)" ]; then
    echo "Prebuilts already exist at $PREBUILTS_DIR/$ABI"
    exit 0
fi

if [ -z "$TAG" ]; then
    echo "Fetching latest prebuilts release tag from $PREBUILTS_REPO..."
    TAG=$(curl -fsSL "https://api.github.com/repos/$PREBUILTS_REPO/releases/latest" | \
        grep '"tag_name"' | head -1 | sed -E 's/.*"tag_name": *"([^"]+)".*/\1/')
    if [ -z "$TAG" ]; then
        echo "Error: could not determine latest release tag" >&2
        exit 1
    fi
fi

echo "Downloading prebuilts-$ABI from release $TAG..."
DOWNLOAD_URL="https://github.com/$PREBUILTS_REPO/releases/download/$TAG/prebuilts-$ABI.tar.gz"

curl -fsSL "$DOWNLOAD_URL" -o /tmp/prebuilts.tar.gz

mkdir -p "$PREBUILTS_DIR"
tar -xzf /tmp/prebuilts.tar.gz -C "$PREBUILTS_DIR"
rm /tmp/prebuilts.tar.gz
echo "Prebuilts downloaded to $PREBUILTS_DIR/$ABI and $PREBUILTS_DIR/include"
