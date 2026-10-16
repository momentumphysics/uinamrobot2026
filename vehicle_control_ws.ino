// Requires library: "WebSockets" by Markus Sattler (Links2004) - install via Library Manager
#include <WiFi.h>
#include <WebSocketsServer.h>
#include <ArduinoOTA.h>

// ---------------- WIFI / OTA ----------------
const char* WIFI_SSID    = "Poco X3 Pro";
const char* WIFI_PASS    = "ulalaaaa";
const char* OTA_PASSWORD = "1234";

// ---------------- WEBSOCKET ----------------
WebSocketsServer ws(81);   // browser connects to ws://<esp-ip>:81

// ---------------- PINS ----------------
const int PIN_SERVO_2 = 4;
const int PIN_SERVO_3 = 16;

const int PIN_ENA = 14;
const int PIN_IN1 = 27;
const int PIN_IN2 = 26;
const int PIN_IN3 = 25;
const int PIN_IN4 = 33;
const int PIN_ENB = 32;

// ---------------- PWM ----------------
const int MOTOR_FREQ = 5000;
const int MOTOR_RES  = 8;
const int SERVO_FREQ = 50;
const int SERVO_RES  = 16;

// ---------------- FAILSAFE ----------------
const uint32_t FAILSAFE_MS = 500;  // stop if no packet for this long

// ---------------- PACKET (8 bytes, little-endian) ----------------
//  [0] 0xA5  [1] servo2  [2] servo3  [3-4] throttle int16  [5-6] steering int16
//  [7] XOR checksum of bytes 0..6
struct __attribute__((packed)) ControlPacket {
  uint8_t magic;
  uint8_t s2;
  uint8_t s3;
  int16_t throttle;
  int16_t steering;
  uint8_t checksum;
};
const uint8_t PACKET_MAGIC = 0xA5;

static bool packetValid(const ControlPacket& p) {
  if (p.magic != PACKET_MAGIC) return false;
  const uint8_t* b = reinterpret_cast<const uint8_t*>(&p);
  uint8_t x = 0;
  for (size_t i = 0; i < sizeof(p) - 1; i++) x ^= b[i];
  return x == p.checksum;
}

// ---------------- SERVO ----------------
int lastS2 = -1, lastS3 = -1;

void writeServo(int pin, int angle) {
  angle = constrain(angle, 0, 180);
  uint32_t us   = map(angle, 0, 180, 500, 2400);
  uint32_t duty = (uint32_t)(((uint64_t)us * 65535) / 20000);
  ledcWrite(pin, duty);
}

// ---------------- MOTORS ----------------
static inline void setMotor(int pinA, int pinB, int pinPwm, int speed) {
  if (speed > 0) {
    digitalWrite(pinA, HIGH);
    digitalWrite(pinB, LOW);
  } else if (speed < 0) {
    digitalWrite(pinA, LOW);
    digitalWrite(pinB, HIGH);
    speed = -speed;
  } else {
    digitalWrite(pinA, LOW);
    digitalWrite(pinB, LOW);
  }
  ledcWrite(pinPwm, speed);
}

void driveVehicle(int throttle, int steering) {
  int left  = constrain(throttle + steering, -255, 255);
  int right = constrain(throttle - steering, -255, 255);
  setMotor(PIN_IN1, PIN_IN2, PIN_ENA, left);
  setMotor(PIN_IN3, PIN_IN4, PIN_ENB, right);
}

void stopVehicle() { driveVehicle(0, 0); }

// ---------------- STATE ----------------
uint32_t lastPacketMs = 0;
bool failsafeActive = true;

void applyPacket(const ControlPacket& p) {
  lastPacketMs = millis();
  failsafeActive = false;

  if (p.s2 != lastS2) { writeServo(PIN_SERVO_2, p.s2); lastS2 = p.s2; }
  if (p.s3 != lastS3) { writeServo(PIN_SERVO_3, p.s3); lastS3 = p.s3; }
  driveVehicle(p.throttle, p.steering);
}

// ---------------- WEBSOCKET EVENTS ----------------
void onWsEvent(uint8_t num, WStype_t type, uint8_t* payload, size_t length) {
  switch (type) {
    case WStype_BIN:
      if (length == sizeof(ControlPacket)) {
        ControlPacket p;
        memcpy(&p, payload, sizeof(p));
        if (packetValid(p)) applyPacket(p);
      }
      break;

    case WStype_DISCONNECTED:
      stopVehicle();             // browser closed/lost -> stop immediately
      failsafeActive = true;
      break;

    case WStype_CONNECTED:
      Serial.printf("WS client %u connected\n", num);
      break;

    default:
      break;
  }
}

// ---------------- OTA ----------------
void setupOTA() {
  ArduinoOTA.setHostname("ESP32-Vehicle-Control");
  ArduinoOTA.setPassword(OTA_PASSWORD);
  ArduinoOTA.onStart([]() { stopVehicle(); Serial.println("OTA start"); });
  ArduinoOTA.onEnd([]() { Serial.println("\nOTA done"); });
  ArduinoOTA.onError([](ota_error_t e) { Serial.printf("OTA error %u\n", e); });
  ArduinoOTA.begin();
}

// ---------------- SETUP / LOOP ----------------
void setup() {
  Serial.begin(115200);

  pinMode(PIN_IN1, OUTPUT);
  pinMode(PIN_IN2, OUTPUT);
  pinMode(PIN_IN3, OUTPUT);
  pinMode(PIN_IN4, OUTPUT);

  ledcAttachChannel(PIN_ENA,     MOTOR_FREQ, MOTOR_RES, 0);
  ledcAttachChannel(PIN_ENB,     MOTOR_FREQ, MOTOR_RES, 1);
  ledcAttachChannel(PIN_SERVO_2, SERVO_FREQ, SERVO_RES, 2);
  ledcAttachChannel(PIN_SERVO_3, SERVO_FREQ, SERVO_RES, 3);

  writeServo(PIN_SERVO_2, 90);
  writeServo(PIN_SERVO_3, 90);
  stopVehicle();

  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);          // no modem power-save = much lower latency
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(250);
    Serial.print(".");
  }
  Serial.print("\nIP: ");
  Serial.println(WiFi.localIP());

  setupOTA();

  ws.begin();
  ws.onEvent(onWsEvent);
  Serial.println("WebSocket server on port 81");
}

void loop() {
  ArduinoOTA.handle();
  ws.loop();

  if (!failsafeActive && millis() - lastPacketMs > FAILSAFE_MS) {
    stopVehicle();
    failsafeActive = true;
    Serial.println("Failsafe: no packets, motors stopped");
  }
}
