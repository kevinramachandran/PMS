#!/usr/bin/env bash
set -euo pipefail

APP_USER="${APP_USER:-brewery}"
APP_GROUP="${APP_GROUP:-$APP_USER}"
APP_HOME="${APP_HOME:-/opt/brewery-pms}"
ENV_DIR="${ENV_DIR:-/etc/brewery-pms}"
SERVICE_NAME="${SERVICE_NAME:-brewery-pms}"

sudo groupadd --system "$APP_GROUP" 2>/dev/null || true
sudo useradd --system --gid "$APP_GROUP" --home-dir "$APP_HOME" --shell /usr/sbin/nologin "$APP_USER" 2>/dev/null || true
sudo mkdir -p "$APP_HOME" "$ENV_DIR"
sudo chown -R "$APP_USER:$APP_GROUP" "$APP_HOME"
sudo chmod 755 "$APP_HOME"
sudo chmod 750 "$ENV_DIR"

sudo cp deployment/systemd/brewery-pms.service "/etc/systemd/system/${SERVICE_NAME}.service"
sudo systemctl daemon-reload
sudo systemctl enable "$SERVICE_NAME"

echo "Service file installed. Place your JAR at ${APP_HOME}/app.jar and env file at ${ENV_DIR}/brewery-pms.env, then run:"
echo "sudo systemctl restart ${SERVICE_NAME}"
