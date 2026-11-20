#!/usr/bin/env bash
# Prepares a fresh Debian 12 / Ubuntu 24.04 server for the WhatsApp service. Safe to run again: it updates the code
# and restarts the service. Run as root (Lightsail launch script, or "sudo bash setup.sh").
#
# It does NOT copy the secrets. After it runs, copy .env and auth_info/ from the machine where the number was linked
# into /opt/cotizaia/whatsapp-service/ (see deploy/README.md) and start the service.
set -euo pipefail

REPO=${REPO:-https://github.com/GUERRREROALEXANDER/backend-cotizaIA.git}
BRANCH=${BRANCH:-main}
APP=/opt/cotizaia
SERVICE_DIR=$APP/whatsapp-service

export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q ca-certificates curl git

# Node 20 LTS from NodeSource; the distro package is too old for --env-file (needs 20.6).
if ! node --version 2>/dev/null | grep -qE '^v(2[0-9])\.'; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y -q nodejs
fi

# 512 MB machines need swap for npm ci.
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 1G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

id cotizaia >/dev/null 2>&1 || useradd --system --create-home --home-dir /home/cotizaia --shell /usr/sbin/nologin cotizaia

# The checkout belongs to the service user, so git (running as root) must be told it is safe.
if [ -d "$APP/.git" ]; then
  git -c safe.directory="$APP" -C "$APP" fetch --depth 1 origin "$BRANCH"
  git -c safe.directory="$APP" -C "$APP" reset --hard FETCH_HEAD
else
  git clone --depth 1 --branch "$BRANCH" "$REPO" "$APP"
fi

cd "$SERVICE_DIR"
npm ci --omit=dev --no-audit --no-fund
mkdir -p auth_info
chown -R cotizaia:cotizaia "$APP"
chmod 700 auth_info

install -m 644 deploy/cotizaia-whatsapp.service /etc/systemd/system/cotizaia-whatsapp.service
systemctl daemon-reload
systemctl enable cotizaia-whatsapp.service

if [ -f .env ] && [ -f auth_info/creds.json ]; then
  systemctl restart cotizaia-whatsapp.service
  echo "Service restarted."
else
  echo "Code ready. Copy .env and auth_info/ into $SERVICE_DIR, then: systemctl start cotizaia-whatsapp"
fi
