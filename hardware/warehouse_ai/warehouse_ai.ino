#include <WiFi.h>
#include <HTTPClient.h>
#include <DHT.h>
#include <ArduinoJson.h>
#include <math.h>

#define DHTPIN 4
#define DHTTYPE DHT22
#define MQ2_PIN 34
#define LED_RED 18
#define LED_GREEN 5
#define FAN 22
#define BUZZER 23

const char* ssid = "TP-LINK_EDA4";
const char* password = "12341234";
const char* serverUrl = "http://192.10.3.104:8080/api/sensor/readings";
const char* deviceKey = "esp32-warehouse-01";
const char* deviceCode = "ESP_ENV_01";

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

void sendSensorData(float temperature, float humidity, int gasAdc) {
  if (WiFi.status() != WL_CONNECTED) connectWifi();
  if (WiFi.status() != WL_CONNECTED) {
    indicateFault();
    return;
  }

  JsonDocument request;
  request["deviceCode"] = deviceCode;
  request["temperature"] = temperature;
  request["humidity"] = humidity;
  request["gas"] = gasAdc;
  String body;
  serializeJson(request, body);

  WiFiClient client;
  HTTPClient http;
  http.setConnectTimeout(5000);
  http.setTimeout(10000);
  http.setReuse(false);
  if (!http.begin(client, serverUrl)) {
    indicateFault();
    return;
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
    indicateFault();
    return;
  }

  String bodyResponse = http.getString();
  http.end();
  JsonDocument response;
  if (deserializeJson(response, bodyResponse)) {
    Serial.println("Phan hoi JSON khong hop le");
    indicateFault();
    return;
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
    indicateFault();
    return;
  }
  int level = response["riskLevel"].as<int>();
  const char* expected[] = {"GREEN_LED", "RED_LED", "RED_LED_FAN", "RED_LED_FAN_BUZZER"};
  if (level < 0 || level > 3 || strcmp(action, expected[level]) != 0) {
    indicateFault();
    return;
  }
  applyRiskLevel(level);
  Serial.print("Do tin cay AI nhiet/am: ");
  Serial.println(response["confidence"].as<float>(), 4);
}

void setup() {
  Serial.begin(115200);
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
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.setAutoReconnect(true);
  Serial.println("ESP32 MQ-2 AI DA KHOI DONG");
  delay(2000);
  lastReading = millis() - READ_INTERVAL_MS;
}

void loop() {
  if (aiFault && millis() - lastBlink >= 500UL) {
    lastBlink = millis();
    redState = !redState;
    digitalWrite(LED_RED, redState ? HIGH : LOW);
  }
  if (millis() - lastReading < READ_INTERVAL_MS) {
    delay(10);
    return;
  }
  lastReading = millis();
  float temperature = dht.readTemperature();
  float humidity = dht.readHumidity();
  int gasAdc = analogRead(MQ2_PIN);
  if (!isfinite(temperature) || !isfinite(humidity)) {
    Serial.println("Khong doc duoc DHT22");
    indicateFault();
    return;
  }
  JsonDocument sample;
  sample["deviceCode"] = deviceCode;
  sample["temperature"] = temperature;
  sample["humidity"] = humidity;
  sample["gas"] = gasAdc;
  sample["uptimeMs"] = millis();
  Serial.print("MQ2_SAMPLE:");
  serializeJson(sample, Serial);
  Serial.println();
  sendSensorData(temperature, humidity, gasAdc);
}
