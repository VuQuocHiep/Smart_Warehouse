#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <time.h>
#include <DHT.h>
#include <ArduinoJson.h>
#include <HX711.h>
#include <Preferences.h>
#include <math.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <freertos/queue.h>

#define DHTPIN 4
#define DHTTYPE DHT22
#define MQ2_PIN 34
#define LED_RED 18
#define LED_GREEN 5
#define FAN 22
#define BUZZER 23
#define HX711_DT 16
#define HX711_SCK 21

#if __has_include("config.local.h")
#include "config.local.h"
#else
#include "config.example.h"
#endif
const char* ssid = WAREHOUSE_WIFI_SSID;
const char* password = WAREHOUSE_WIFI_PASSWORD;
const char* serverUrl = BACKEND_BASE_URL "/api/sensor/readings";
const char* scaleServerUrl = BACKEND_BASE_URL "/api/scale/readings";
const char* deviceKey = WAREHOUSE_DEVICE_KEY;
const char* deviceCode = WAREHOUSE_DEVICE_CODE;

// Called only by network tasks. TLS/NTP must never block HX711 acquisition in loop().
bool beginBackendRequest(HTTPClient& http, WiFiClient& plain, WiFiClientSecure& secure, const char* url) {
  http.setReuse(false);
  http.setConnectTimeout(5000);
  http.setTimeout(15000);
  http.setFollowRedirects(HTTPC_DISABLE_FOLLOW_REDIRECTS);
  if (strncmp(url, "https://", 8) == 0) {
    if (!strlen(WAREHOUSE_ROOT_CA)) {
      Serial.println("HTTPS: cau hinh WAREHOUSE_ROOT_CA trong config.local.h");
      return false;
    }
    if (time(nullptr) < 1700000000) {
      configTime(0, 0, "pool.ntp.org", "time.google.com");
      unsigned long started = millis();
      while (time(nullptr) < 1700000000 && millis() - started < 10000UL) vTaskDelay(pdMS_TO_TICKS(200));
      if (time(nullptr) < 1700000000) { Serial.println("HTTPS: chua dong bo duoc gio NTP"); return false; }
    }
    secure.setCACert(WAREHOUSE_ROOT_CA);
    secure.setHandshakeTimeout(12);
    return http.begin(secure, url);
  }
  if (WAREHOUSE_ALLOW_LOCAL_HTTP && strncmp(url, "http://", 7) == 0) return http.begin(plain, url);
  Serial.println("Backend phai dung HTTPS");
  return false;
}

constexpr float SCALE_PLATFORM_GRAMS = 0.0f; // Enter mass of the mica/fixtures borne by the cell.
constexpr float SCALE_CAPACITY_GRAMS = 5000.0f;
const float SCALE_STABLE_RANGE_GRAMS = 2.0f;
const float ZERO_THRESHOLD = 0.5f;
static_assert(SCALE_PLATFORM_GRAMS >= 0 && SCALE_PLATFORM_GRAMS < SCALE_CAPACITY_GRAMS, "Invalid platform mass");

HX711 scale;
Preferences prefs;
float calibration_factor = 0;
bool daTruBi = false;
long emptyOffset = 0;
bool bootTarePending = true;
long lastRaw = 0;
long lastSpan = 0;
long stableMinRaw = 0, stableMaxRaw = 0;
bool havePreviousRaw = false;
bool readSignalFault = false;
bool readSaturated = false;
unsigned long stableSince = 0;
unsigned long lastScaleSampleAt = 0, lastScaleLogAt = 0;

struct ScaleCommandMessage { char id[48]; bool calibrate; float reference; };
struct ScaleSnapshot {
  float weight = 0, gross = 0;
  bool valid = false, stable = false, calibrated = false, tared = false, overloaded = false;
  unsigned long sampledAtMs = 0;
  char status[32] = "NOT_READY";
  char commandId[48] = "";
  char commandResult[64] = "";
};
ScaleSnapshot scaleState; // Only setup()/loop() write acquisition state.
static ScaleSnapshot scaleSnapshot; // Network receives a copy, never accesses HX711.
static portMUX_TYPE scaleMux = portMUX_INITIALIZER_UNLOCKED;
static QueueHandle_t scaleCommands;
struct EnvironmentSample { float temperature, humidity; int gas; };
static QueueHandle_t environmentSamples;
static QueueHandle_t environmentResults;

void publishScale() {
  portENTER_CRITICAL(&scaleMux);
  scaleSnapshot = scaleState;
  portEXIT_CRITICAL(&scaleMux);
}

// Read sequence from the working standalone sketch. Only Arduino setup/loop calls this.
bool docRAW(long &ketQua, int n) {
  long a[15];
  int count = 0, stuckZeroCount = 0;
  readSignalFault = false;
  readSaturated = false;
  if (n < 1 || n > 15) return false;
  unsigned long started = millis();
  for (int i = 0; i < n; ++i) {
    if (millis() - started >= 2500UL) break;
    if (!scale.wait_ready_timeout(1000)) continue;
    long raw = scale.read();
    if (raw == 0 && digitalRead(HX711_DT) == LOW) ++stuckZeroCount;
    else stuckZeroCount = 0;
    if (stuckZeroCount >= 3) readSignalFault = true;
    if (raw >= 8300000L || raw <= -8300000L) { readSaturated = true; continue; }
    a[count++] = raw;
  }
  if (count < (n + 1) / 2 || readSignalFault) return false;
  for (int i = 1; i < count; ++i) {
    long x = a[i];
    int j = i - 1;
    while (j >= 0 && a[j] > x) { a[j + 1] = a[j]; --j; }
    a[j + 1] = x;
  }
  ketQua = a[count / 2];
  int trim = count >= 5 ? 1 : 0;
  lastSpan = a[count - 1 - trim] - a[trim];
  return true;
}

void markScaleReadError() {
  stableSince = 0;
  havePreviousRaw = false;
  scaleState.valid = false;
  scaleState.stable = false;
  scaleState.overloaded = readSaturated;
  scaleState.sampledAtMs = millis();
  strlcpy(scaleState.status, readSignalFault ? "SIGNAL_FAULT" : readSaturated ? "ADC_SATURATED" : "DISCONNECTED", sizeof(scaleState.status));
  publishScale();
}

// emptyPlatform=true is used only at boot with an EMPTY platform.
// A later user tare removes packaging from net weight without changing the gross baseline.
bool truBi(bool emptyPlatform) {
  Serial.println("HX711: Dang tru bi, de mat can trong...");
  long raw;
  bool ok = docRAW(raw, 15);
  if (!ok || readSaturated) { markScaleReadError(); Serial.println("HX711: Tru bi that bai, chua co mau hop le."); return false; }
  if (!emptyPlatform && calibration_factor != 0 &&
      (raw - emptyOffset) / calibration_factor + SCALE_PLATFORM_GRAMS > SCALE_CAPACITY_GRAMS) {
    scaleState.valid = false; scaleState.stable = false; scaleState.overloaded = true;
    strlcpy(scaleState.status, "OVERLOAD", sizeof(scaleState.status));
    publishScale();
    Serial.println("HX711: Qua tai, lay bot hang truoc khi tru bi.");
    return false;
  }
  scale.set_offset(raw);
  daTruBi = true;
  if (emptyPlatform) {
    emptyOffset = raw;
    bootTarePending = false;
    prefs.putLong("empty", emptyOffset);
    prefs.putBool("baseline", true);
  }
  stableSince = 0;
  havePreviousRaw = false;
  scaleState.tared = true;
  scaleState.valid = false;
  scaleState.stable = false;
  scaleState.sampledAtMs = millis();
  strlcpy(scaleState.status, "SETTLING", sizeof(scaleState.status));
  publishScale();
  Serial.printf("HX711: Tru bi thanh cong, offset=%ld\n", raw);
  return true;
}

bool hieuChuan(float reference) {
  if (!daTruBi || !isfinite(reference) || reference < 10 || reference > SCALE_CAPACITY_GRAMS - SCALE_PLATFORM_GRAMS) {
    strlcpy(scaleState.commandResult, "FAILED_REFERENCE_OR_TARE", sizeof(scaleState.commandResult)); return false;
  }
  long raw;
  bool ok = docRAW(raw, 15);
  if (!ok || readSaturated) {
    markScaleReadError(); strlcpy(scaleState.commandResult, "FAILED_SENSOR_DATA", sizeof(scaleState.commandResult)); return false;
  }
  long delta = raw - scale.get_offset();
  if (labs(delta) < 200) {
    strlcpy(scaleState.commandResult, "FAILED_NO_REFERENCE_LOAD", sizeof(scaleState.commandResult)); return false;
  }
  float factor = (float)delta / reference;
  if (!isfinite(factor) || fabs(factor) < 0.000001f || lastSpan / fabs(factor) > SCALE_STABLE_RANGE_GRAMS) {
    strlcpy(scaleState.commandResult, "FAILED_CALIBRATION_NOISY", sizeof(scaleState.commandResult)); return false;
  }
  calibration_factor = factor;
  scale.set_scale(factor);
  emptyOffset = scale.get_offset(); // Calibration must follow an empty-platform tare.
  prefs.putFloat("factor", factor);
  prefs.putLong("empty", emptyOffset);
  prefs.putBool("baseline", true);
  scaleState.calibrated = true;
  stableSince = 0;
  havePreviousRaw = false;
  Serial.printf("HX711: Hieu chuan thanh cong, factor=%.6f\n", factor);
  return true;
}

void processScaleCommands() {
  ScaleCommandMessage command;
  if (xQueueReceive(scaleCommands, &command, 0) != pdTRUE) return;
  scaleState.valid = false;
  scaleState.stable = false;
  strlcpy(scaleState.status, "SETTLING", sizeof(scaleState.status));
  publishScale();
  bool ok;
  if (command.calibrate) {
    ok = hieuChuan(command.reference);
    if (ok) strlcpy(scaleState.commandResult, "OK_CALIBRATED", sizeof(scaleState.commandResult));
  } else {
    ok = truBi(bootTarePending);
    strlcpy(scaleState.commandResult, ok ? "OK_TARED" : "FAILED_SENSOR_DATA", sizeof(scaleState.commandResult));
  }
  strlcpy(scaleState.commandId, command.id, sizeof(scaleState.commandId));
  scaleState.valid = false;
  scaleState.stable = false;
  publishScale();
  Serial.printf("HX711 command: %s\n", scaleState.commandResult);
}

void updateScale() {
  // Consume exactly ONE fresh ADC conversion. No median/average on live weight.
  if (!scale.is_ready()) {
    if (millis() - lastScaleSampleAt > 2000UL && strcmp(scaleState.status, "DISCONNECTED") != 0) {
      readSignalFault = false; readSaturated = false;
      markScaleReadError();
      Serial.println("HX711: DISCONNECTED, khong co mau moi trong 2 giay");
    }
    return;
  }
  long raw = scale.read();
  lastScaleSampleAt = millis();
  readSaturated = raw >= 8300000L || raw <= -8300000L;
  static int stuckZeroSamples = 0;
  if (raw == 0 && digitalRead(HX711_DT) == LOW) ++stuckZeroSamples;
  else stuckZeroSamples = 0;
  if (stuckZeroSamples > 3) stuckZeroSamples = 3;
  readSignalFault = stuckZeroSamples >= 3;
  if (readSignalFault || readSaturated) {
    markScaleReadError();
    if (millis() - lastScaleLogAt >= 100UL) {
      lastScaleLogAt = millis();
      Serial.printf("HX711: %s (DT=%d, SCK=%d)\n", scaleState.status, HX711_DT, HX711_SCK);
    }
    return;
  }
  lastRaw = raw;
  float stableCounts = calibration_factor != 0 ? fabs(calibration_factor) * SCALE_STABLE_RANGE_GRAMS : 2000.0f;
  // This range only sets the stable flag; it never changes the live reading.
  if (!havePreviousRaw) {
    stableMinRaw = raw; stableMaxRaw = raw;
    stableSince = millis(); havePreviousRaw = true;
  } else {
    if (raw < stableMinRaw) stableMinRaw = raw;
    if (raw > stableMaxRaw) stableMaxRaw = raw;
    if (stableMaxRaw - stableMinRaw > stableCounts) {
      stableMinRaw = raw; stableMaxRaw = raw; stableSince = millis();
    }
  }
  bool stable = millis() - stableSince >= 1000UL;
  if (bootTarePending && stable) {
    // A failed startup tare is retried here. Keep the platform empty until success.
    truBi(true);
    return;
  }
  scaleState.calibrated = calibration_factor != 0;
  scaleState.tared = daTruBi;
  scaleState.valid = scaleState.calibrated && daTruBi;
  scaleState.stable = stable && scaleState.valid;
  scaleState.overloaded = false;
  if (scaleState.valid) {
    scaleState.weight = (raw - scale.get_offset()) / calibration_factor;
    scaleState.gross = (raw - emptyOffset) / calibration_factor + SCALE_PLATFORM_GRAMS;
    if (fabs(scaleState.weight) < ZERO_THRESHOLD) scaleState.weight = 0;
    scaleState.overloaded = scaleState.gross > SCALE_CAPACITY_GRAMS;
    scaleState.valid = isfinite(scaleState.weight) && isfinite(scaleState.gross);
  }
  scaleState.sampledAtMs = millis();
  strlcpy(scaleState.status, scaleState.overloaded ? "OVERLOAD" : bootTarePending ? "AUTO_ZERO" :
      !scaleState.calibrated ? "UNCALIBRATED" : !daTruBi ? "TARE_REQUIRED" :
      scaleState.weight < -ZERO_THRESHOLD ? "NEGATIVE" : stable ? "READY" : "UNSTABLE", sizeof(scaleState.status));
  publishScale();
  if (millis() - lastScaleLogAt >= 100UL) {
    lastScaleLogAt = millis();
    Serial.printf("HX711: %s, net=%.2f g, gross=%.2f g, raw=%ld, offset=%ld, factor=%.6f\n",
        scaleState.status, scaleState.weight, scaleState.gross, raw, scale.get_offset(), calibration_factor);
  }
}

void scaleNetworkTask(void*) {
  String lastQueuedId;
  unsigned long retryDelay = 1000;
  while (true) {
    retryDelay = 1000;
    if (WiFi.status() == WL_CONNECTED) {
      ScaleSnapshot state;
      portENTER_CRITICAL(&scaleMux);
      state = scaleSnapshot;
      portEXIT_CRITICAL(&scaleMux);
      if (state.valid && millis() - state.sampledAtMs > 3000UL) {
        state.valid = false;
        state.stable = false;
        strlcpy(state.status, "STALE", sizeof(state.status));
      }
      JsonDocument request;
      request["deviceCode"] = deviceCode;
      if (state.valid) { request["weightGrams"] = state.weight; request["grossGrams"] = state.gross; }
      request["stable"] = state.stable;
      request["calibrated"] = state.calibrated;
      request["tared"] = state.tared;
      request["overloaded"] = state.overloaded;
      request["status"] = state.status;
      request["commandId"] = state.commandId;
      request["commandResult"] = state.commandResult;
      String body; serializeJson(request, body);
      WiFiClient client; WiFiClientSecure secure; HTTPClient http;
      if (beginBackendRequest(http, client, secure, scaleServerUrl)) {
        http.addHeader("Content-Type", "application/json");
        http.addHeader("X-Device-Key", deviceKey);
        int code = http.POST(body);
        if (code >= 200 && code < 300) {
          JsonDocument response;
          if (!deserializeJson(response, http.getString()) && response["command"].is<JsonObject>()) {
            String id = response["command"]["id"].as<String>();
            if (id.length() && id != lastQueuedId && id != state.commandId) {
              ScaleCommandMessage cmd = {};
              strlcpy(cmd.id, id.c_str(), sizeof(cmd.id));
              const char* type = response["command"]["type"] | "";
              cmd.calibrate = strcmp(type, "CALIBRATE") == 0;
              cmd.reference = response["command"]["referenceGrams"] | 0.0f;
              if ((cmd.calibrate || strcmp(type, "TARE") == 0) && xQueueSend(scaleCommands, &cmd, 0) == pdTRUE) lastQueuedId = id;
            }
          }
        } else {
          retryDelay = 3000;
          Serial.printf("HX711 HTTP status: %d\n", code);
          if (code > 0) Serial.println(http.getString());
          else Serial.println(http.errorToString(code));
        }
        http.end();
      } else { retryDelay = 3000; }
    }
    vTaskDelay(pdMS_TO_TICKS(retryDelay));
  }
}


void handleScaleSerial() {
  static char buffer[32]; static size_t length = 0; static bool overflow = false;
  while (Serial.available()) {
    char ch = Serial.read();
    if (ch == '\r') continue;
    if (ch != '\n') {
      if (length < sizeof(buffer) - 1) buffer[length++] = ch; else overflow = true;
      continue;
    }
    buffer[length] = '\0';
    ScaleCommandMessage command = {};
    snprintf(command.id, sizeof(command.id), "serial-%lu", millis());
    bool valid = !overflow && strcmp(buffer, "t") == 0;
    if (!overflow && buffer[0] == 'c') {
      char* end;
      command.reference = strtof(buffer + 1, &end);
      command.calibrate = true;
      valid = end != buffer + 1 && *end == '\0' && isfinite(command.reference)
          && command.reference >= 10 && command.reference <= SCALE_CAPACITY_GRAMS;
    }
    if (!valid || !scaleCommands || xQueueSend(scaleCommands, &command, 0) != pdTRUE) Serial.println("HX711: invalid command or busy");
    length = 0; overflow = false;
  }
}


DHT dht(DHTPIN, DHTTYPE);
bool aiFault = true;
bool redState = false;
const unsigned long READ_INTERVAL_MS = 60000UL;
unsigned long lastReading = 0;
unsigned long lastBlink = 0;

void indicateFault() {
  aiFault = true;
  digitalWrite(LED_GREEN, LOW);
}

void connectWifi() {
  if (WiFi.status() == WL_CONNECTED) return;
  WiFi.begin(ssid, password);
  Serial.print("Dang ket noi WiFi");
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 10000UL) {
    delay(250);
    Serial.print(".");
  }
  Serial.println();
  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("IP ESP32: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("Chua co WiFi, van thu du lieu MQ-2 qua USB");
  }
}

void applyRiskLevel(int level) {
  aiFault = false;
  digitalWrite(LED_GREEN, level == 0 ? HIGH : LOW);
  digitalWrite(LED_RED, level > 0 ? HIGH : LOW);
  digitalWrite(FAN, level >= 2 ? HIGH : LOW);
  digitalWrite(BUZZER, level >= 3 ? HIGH : LOW);
  Serial.print("CAP CANH BAO KET HOP AI VA GAS: ");
  Serial.println(level);
}

int requestEnvironmentPrediction(float temperature, float humidity, int gasAdc) {
  if (WiFi.status() != WL_CONNECTED) connectWifi();
  if (WiFi.status() != WL_CONNECTED) {
    return -1;
  }

  JsonDocument request;
  request["deviceCode"] = deviceCode;
  request["temperature"] = temperature;
  request["humidity"] = humidity;
  request["gas"] = gasAdc;
  String body;
  serializeJson(request, body);

  WiFiClient client;
  WiFiClientSecure secure;
  HTTPClient http;
  if (!beginBackendRequest(http, client, secure, serverUrl)) {
    return -1;
  }
  http.addHeader("Content-Type", "application/json");
  http.addHeader("X-Device-Key", deviceKey);
  int code = http.POST(body);
  Serial.print("HTTP status: ");
  Serial.println(code);
  if (code < 200 || code >= 300) {
    if (code > 0) Serial.println(http.getString());
    else Serial.println(http.errorToString(code));
    if (code == 401) Serial.println("Kiem tra deviceKey va IOT_INGEST_KEY");
    if (code == 404) Serial.println("Kiem tra URL va deviceCode cua Kho 1");
    http.end();
    return -1;
  }

  String bodyResponse = http.getString();
  http.end();
  JsonDocument response;
  if (deserializeJson(response, bodyResponse)) {
    Serial.println("Phan hoi JSON khong hop le");
    return -1;
  }
  const char* status = response["predictionStatus"] | "NO_PREDICTION";
  const char* returnedDevice = response["deviceCode"] | "";
  const char* action = response["actionCode"] | "";
  bool allowed = response["controlAllowed"] | false;
  if (strcmp(status, "READY") != 0 || !allowed ||
      strcmp(returnedDevice, deviceCode) != 0 || !response["riskLevel"].is<int>()) {
    Serial.print("Trang thai AI: ");
    Serial.println(status);
    if (strcmp(status, "MQ2_MODEL_REQUIRED") == 0) {
      Serial.println("Dang thu du lieu MQ-2. Can train model MQ-2 cho backend.");
    }
    return -1;
  }
  int level = response["riskLevel"].as<int>();
  const char* expected[] = {"GREEN_LED", "RED_LED", "RED_LED_FAN", "RED_LED_FAN_BUZZER"};
  if (level < 0 || level > 3 || strcmp(action, expected[level]) != 0) {
    return -1;
  }
  // The Arduino loop applies this result through the result queue.
  Serial.print("Do tin cay AI nhiet/am: ");
  Serial.println(response["confidence"].as<float>(), 4);
  return level;
}



void environmentNetworkTask(void*) {
  EnvironmentSample sample;
  while (true) {
    if (xQueueReceive(environmentSamples, &sample, pdMS_TO_TICKS(1000)) == pdTRUE) {
      int level = requestEnvironmentPrediction(sample.temperature, sample.humidity, sample.gas);
      xQueueOverwrite(environmentResults, &level);
    }
  }
}

void setup() {
  Serial.begin(115200);
  Serial.setTimeout(100);
  delay(1000);
  Serial.println("FIRMWARE HX711: INSTANT-RAW-V3");
  Serial.println("De mat can trong khi bat nguon; tu tru bi nhu ban test.");
  scale.begin(HX711_DT, HX711_SCK);
  prefs.begin("hx711", false);
  calibration_factor = prefs.getFloat("factor", 0.0f);
  if (!isfinite(calibration_factor) || fabs(calibration_factor) < 0.000001f) calibration_factor = 0;
  scale.set_scale(calibration_factor != 0 ? calibration_factor : 1.0f);
  scaleState.calibrated = calibration_factor != 0;
  Serial.printf("HX711: DT=%d, SCK=%d, factor=%.6f\n", HX711_DT, HX711_SCK, calibration_factor);
  if (scale.wait_ready_timeout(3000)) {
    delay(1000);
    truBi(true);
  } else {
    markScaleReadError();
  }

  pinMode(MQ2_PIN, INPUT);
  pinMode(LED_RED, OUTPUT);
  pinMode(LED_GREEN, OUTPUT);
  pinMode(FAN, OUTPUT);
  pinMode(BUZZER, OUTPUT);
  digitalWrite(LED_RED, LOW);
  digitalWrite(LED_GREEN, LOW);
  digitalWrite(FAN, LOW);
  digitalWrite(BUZZER, LOW);
  analogReadResolution(12);
  dht.begin();
  scaleCommands = xQueueCreate(1, sizeof(ScaleCommandMessage));
  environmentSamples = xQueueCreate(1, sizeof(EnvironmentSample));
  environmentResults = xQueueCreate(1, sizeof(int));
  if (!scaleCommands || !environmentSamples || !environmentResults) {
    Serial.println("LOI: Khong tao duoc hang doi. Can khoi dong lai ESP.");
    while (true) delay(1000);
  }
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.setAutoReconnect(true);
  Serial.print("Backend moi truong: "); Serial.println(serverUrl);
  Serial.print("Backend can: "); Serial.println(scaleServerUrl);
  // Only these tasks do HTTP. HX711/Preferences stay entirely in setup()/loop().
  xTaskCreate(scaleNetworkTask, "hx711-http", 12288, nullptr, 1, nullptr);
  xTaskCreate(environmentNetworkTask, "environment-http", 12288, nullptr, 1, nullptr);
  lastReading = millis() - READ_INTERVAL_MS;
  lastScaleSampleAt = millis();
}

void loop() {
  handleScaleSerial();
  processScaleCommands();
  updateScale();
  int level;
  if (xQueueReceive(environmentResults, &level, 0) == pdTRUE) {
    if (level >= 0 && level <= 3) applyRiskLevel(level);
    else indicateFault();
  }
  if (aiFault && millis() - lastBlink >= 500UL) {
    lastBlink = millis();
    redState = !redState;
    digitalWrite(LED_RED, redState ? HIGH : LOW);
  }
  if (millis() - lastReading >= READ_INTERVAL_MS) {
    lastReading = millis();
    EnvironmentSample sample = {dht.readTemperature(), dht.readHumidity(), analogRead(MQ2_PIN)};
    if (isfinite(sample.temperature) && isfinite(sample.humidity)) {
      JsonDocument log;
      log["deviceCode"] = deviceCode; log["temperature"] = sample.temperature;
      log["humidity"] = sample.humidity; log["gas"] = sample.gas; log["uptimeMs"] = millis();
      Serial.print("MQ2_SAMPLE:"); serializeJson(log, Serial); Serial.println();
      xQueueOverwrite(environmentSamples, &sample);
    } else { Serial.println("Khong doc duoc DHT22"); indicateFault(); }
  }
  delay(10);
}
