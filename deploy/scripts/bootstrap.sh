#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)
DEPLOY_DIR="$REPO_ROOT/deploy"

if [ "$(uname -s)" != "Darwin" ]; then
  echo "This bootstrap targets the current macOS host. See README_DEPLOY.md for Linux prerequisites." >&2
  exit 1
fi

if ! /usr/bin/xcode-select -p >/dev/null 2>&1; then
  clt_label=$(/usr/sbin/softwareupdate --list 2>&1 | awk -F': ' '/Label: Command Line Tools for Xcode/{print $2}' | tail -n 1)
  if [ -z "$clt_label" ]; then
    echo "No Command Line Tools package was offered by macOS Software Update" >&2
    exit 1
  fi
  echo "Installing official macOS dependency: $clt_label"
  if [ "$(id -u)" -eq 0 ]; then
    /usr/sbin/softwareupdate --install "$clt_label" --verbose
  else
    sudo /usr/sbin/softwareupdate --install "$clt_label" --verbose
  fi
fi

if [ -x /usr/local/bin/brew ]; then
  brew_bin=/usr/local/bin/brew
elif [ -x /opt/homebrew/bin/brew ]; then
  brew_bin=/opt/homebrew/bin/brew
else
  installer=$(/usr/bin/mktemp /tmp/homebrew-install.XXXXXX)
  /usr/bin/curl --fail --location --proto '=https' --tlsv1.2 \
    https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh \
    --output "$installer"
  NONINTERACTIVE=1 /bin/bash "$installer"
  if [ -x /usr/local/bin/brew ]; then
    brew_bin=/usr/local/bin/brew
  else
    brew_bin=/opt/homebrew/bin/brew
  fi
fi

"$brew_bin" bundle --file "$DEPLOY_DIR/Brewfile"

if ! command -v docker >/dev/null 2>&1; then
  export PATH="/usr/local/bin:/opt/homebrew/bin:$PATH"
fi

if ! docker info >/dev/null 2>&1; then
  /usr/bin/open -a Docker
  waited=0
  while ! docker info >/dev/null 2>&1; do
    if [ "$waited" -ge 300 ]; then
      echo "Docker Desktop did not become ready within five minutes" >&2
      exit 1
    fi
    sleep 5
    waited=$((waited + 5))
  done
fi

echo "Dependency bootstrap complete"
git --version
docker version --format 'Docker client={{.Client.Version}} server={{.Server.Version}}'
docker compose version
