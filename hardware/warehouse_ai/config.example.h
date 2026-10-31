#pragma once
// Copy to config.local.h (git-ignored). Never commit Wi-Fi passwords or device keys.
#define WAREHOUSE_WIFI_SSID "replace-with-wifi-name"
#define WAREHOUSE_WIFI_PASSWORD "replace-with-wifi-password"
#define BACKEND_BASE_URL "https://replace-with-backend-name.onrender.com"
#define WAREHOUSE_DEVICE_KEY "replace-with-IOT_INGEST_KEY-from-Render"
#define WAREHOUSE_DEVICE_CODE "ESP_ENV_01"
// HTTPS is required unless local HTTP is deliberately enabled for LAN development.
#define WAREHOUSE_ALLOW_LOCAL_HTTP 0
// Paste the issuing root CA PEM for your deployed hostname, not its short-lived leaf cert.
#define WAREHOUSE_ROOT_CA ""
