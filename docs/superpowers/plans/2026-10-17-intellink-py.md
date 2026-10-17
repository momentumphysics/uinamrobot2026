# Rencana Implementasi: Intellink.py — Aplikasi Android Kontrol Robot

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aplikasi Android native **"Intellink.py"** (Kotlin + Jetpack Compose) sebagai pengganti `controller.html` — protokol WebSocket 8 byte yang sama persis, plus kenyamanan: joystick, haptik, layar selalu menyala, portrait terkunci, stop otomatis saat app ke background. Hasil akhir: file APK yang bisa dipasang (sideload) di HP Android 11.

**Architecture:** Satu modul Gradle `app` di `android/`. UI Compose mengamati `ControlViewModel` (state + ticker 33 ms); `ControlViewModel` memakai antarmuka `RobotConnection` yang diimplementasikan `RobotClient` (OkHttp WebSocket, auto-reconnect 1,5 dtk); encoder murni `ControlPacket`; IP terakhir di `SettingsStore` (SharedPreferences). Pengujian: JUnit4 + MockWebServer (server tiruan in-JVM) + kotlinx-coroutines-test (waktu virtual).

**Tech Stack (semua versi terverifikasi ada di repositori resmi per 17 Okt 2026):**

| Komponen | Versi | Catatan |
|---|---|---|
| Gradle (wrapper) | 9.8.1 | JBR mesin ini Java 25 → wajib Gradle 9.x |
| AGP | 9.4.1 | built-in Kotlin (tanpa plugin `kotlin-android`), DSL baru, min Gradle 9.6 |
| Kotlin | 2.2.10 (bawaan AGP) | plugin Compose harus sama: `org.jetbrains.kotlin.plugin.compose` 2.2.10 |
| Compose BOM | 2025.06.01 | era pertengahan 2025, cukup untuk semua yang dipakai |
| activity-compose | 1.10.1 | |
| lifecycle (viewmodel-compose, runtime-compose) | 2.9.2 | |
| OkHttp + MockWebServer | 4.12.0 | API klasik yang stabil |
| kotlinx-coroutines (android, test) | 1.10.2 | |
| JUnit | 4.13.2 | |

**Spec:** `docs/superpowers/specs/2026-10-17-android-kotlin-app-design.md`

## Global Constraints

- Bahasa komunikasi dengan pengguna dan teks UI: **Indonesia**. Nama aplikasi persis: **`Intellink.py`** (termasuk `.py`). Package: `id.uinam.intellink`.
- **Jangan mengubah** `controller.html` dan `vehicle_control_ws.ino` di root repo.
- Semua perintah Gradle dijalankan dari folder `android/`, diawali:
  `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'`
  Build pertama lama (unduh Gradle ~140 MB + dependensi ~400 MB + platform android-35) — beri timeout ≥ 15 menit.
- Versi build: `minSdk 26`, `compileSdk 35`, `targetSdk 34` (perangkat target: Android 11 ke atas; sengaja bukan level terbaru). **Jangan menaikkan versi apa pun tanpa alasan yang diuji** — semua pin sudah terverifikasi saling kompatibel.
- Protokol (tidak boleh menyimpang): `ws://<ip>:81`, paket 8 byte LE `A5 | servo2 | servo3 | int16 throttle | int16 steering | XOR(0..6)`, kirim tiap 33 ms, auto-reconnect 1500 ms, frame dibuang bila socket belum ONLINE atau `queueSize() > 64`.
- Kunci SharedPreferences IP terakhir: `last_ip`, default `192.168.1.100`.
- AndroidManifest: `INTERNET`, `VIBRATE`, `android:usesCleartextTraffic="true"` (wajib untuk `ws://`).
- Warna mengikuti web: latar `#121212`, kartu `#1E1E1E`, aksen `#40C4FF`, hijau `#00E676` (online), merah `#FF1744` (offline/STOP), kuning `#FFC107` (connecting).
- Commit kecil per task (gaya repo: bahasa Indonesia, awalan `feat:`/`chore:`/`docs:`/`test:`).
- TDD: tulis test dulu (harus GAGAL), lalu implementasi (test HIJAU), baru commit.

## Review Focus (titik rawan yang harus ditinjau di tiap review task)

1. **Nilai joystick ekstrem/NaN** (drag ke luar lingkaran, multi-touch) → byte tetap dalam rentang, tanpa crash. Diuji di Task 2 (clamp encoder) & Task 5 (JoystickMath: clamp, NaN, radius 0).
2. **IP kosong/berisi spasi** → tidak crash, status OFFLINE, tidak mencoba connect dengan string kosong. Diuji di Task 4 (`onIpChanged("   ")`).
3. **Kirim saat socket belum tersambung/menutup** (race) → `send()` mengembalikan false, tanpa exception. Diuji di Task 3 (`send` sebelum connect).
4. **Koneksi gagal berulang** (ESP32 mati) → percobaan ulang berjarak ≥ 1,5 dtk, hanya satu timer reconnect aktif (tidak storm/leak). Diuji di Task 3 (jeda antar koneksi) dan persyaratan `generation`/`autoReconnect` di `RobotClient`.
5. **`onBackground()` dipanggil sebelum pernah tersambung** → aman (tidak error), tetap memutus koneksi secara idempoten. Diuji di Task 4.

---

## Task 1: Bootstrap proyek Android + APK kosong "Intellink.py"

**Files:**
- Create (digenerate): `android/gradlew`, `android/gradlew.bat`, `android/gradle/wrapper/gradle-wrapper.jar`, `android/gradle/wrapper/gradle-wrapper.properties`
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/app/build.gradle.kts`, `android/app/proguard-rules.pro`, `android/.gitignore`, `android/local.properties` (TIDAK di-commit)
- Create: `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values/themes.xml`
- Create: `android/app/src/main/java/id/uinam/intellink/MainActivity.kt`

**Interfaces (dipakai task berikutnya):**
- Aktivitas masuk: `id.uinam.intellink.MainActivity` (di-replace di Task 5).
- Label aplikasi: `@string/app_name` = `Intellink.py`.

- [ ] **Langkah 1: Bootstrap Gradle wrapper** (dari root repo; wrapper dibuat SEBELUM file build lengkap ditulis karena Gradle 9 menolak menjalankan task di folder tanpa file build):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$tmp = "$env:TEMP\opencode"
curl.exe -L -o "$tmp\gradle-9.8.1-bin.zip" "https://services.gradle.org/distributions/gradle-9.8.1-bin.zip"
Expand-Archive "$tmp\gradle-9.8.1-bin.zip" -DestinationPath "$tmp\gradle-dist" -Force
New-Item -ItemType Directory -Force -Path "D:\app\iot\uinamrobot2026\android" | Out-Null
Set-Content -Path "D:\app\iot\uinamrobot2026\android\settings.gradle.kts" -Value 'rootProject.name = "Intellink"' -Encoding utf8
Push-Location "D:\app\iot\uinamrobot2026\android"
& "$tmp\gradle-dist\gradle-9.8.1\bin\gradle.bat" wrapper --gradle-version 9.8.1 --distribution-type bin
Pop-Location
```

Hasil yang diharapkan: `BUILD SUCCESSFUL`; muncul `android/gradlew.bat`, `android/gradle/wrapper/gradle-wrapper.jar`, `android/gradle/wrapper/gradle-wrapper.properties` (distributionUrl `...gradle-9.8.1-bin.zip`). `settings.gradle.kts` minimal itu akan ditimpa di Langkah 2.

- [ ] **Langkah 2: Tulis file konfigurasi Gradle.**

`android/settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Intellink"
include(":app")
```

`android/build.gradle.kts` (root):
```kotlin
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
```
Catatan: TIDAK ada plugin `org.jetbrains.kotlin.android` — AGP 9 memakai built-in Kotlin. Jika build error menyebut mismatch versi Compose Compiler vs Kotlin, sesuaikan versi `org.jetbrains.kotlin.plugin.compose` dengan versi Kotlin yang dilaporkan error tersebut dan samakan `2.2.10` → versi itu di `app/build.gradle.kts` (laporan versinya di komentar commit).

`android/gradle.properties`:
```
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`android/local.properties` (jangan di-commit):
```
sdk.dir=C:/Users/dhiwa/AppData/Local/Android/Sdk
```

`android/.gitignore`:
```
.gradle/
build/
local.properties
.idea/
*.iml
captures/
.kotlin/
```

- [ ] **Langkah 3: Tulis modul `app`.**

`android/app/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "id.uinam.intellink"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.uinam.intellink"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
```

`android/app/proguard-rules.pro`:
```
# Tidak ada aturan khusus (minify dimatikan).
```

`android/app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <application
        android:label="@string/app_name"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.Intellink">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```
(Ikon launcher dibiarkan default sistem — di luar cakupan.)

`android/app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">Intellink.py</string>
</resources>
```

`android/app/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.Intellink" parent="@android:style/Theme.Black.NoTitleBar" />
</resources>
```

`android/app/src/main/java/id/uinam/intellink/MainActivity.kt` (stub; diganti di Task 5):
```kotlin
package id.uinam.intellink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Intellink.py") }
    }
}
```

- [ ] **Langkah 4: Build pertama.**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug --console=plain
```
(workdir: `android/`; jalankan dengan timeout besar. Build ini juga mengunduh Gradle 9.8.1 + AGP + dependensi + Android SDK Platform 35.)

Hasil yang diharapkan: `BUILD SUCCESSFUL`, dan file `android/app/build/outputs/apk/debug/app-debug.apk` ada.
Jika gagal karena lisensi/platform SDK: buka Android Studio → **SDK Manager** → pasang **Android SDK Platform 35** → ulangi build.

- [ ] **Langkah 5: Commit.**

```powershell
git add android
git commit -m "chore(android): bootstrap proyek Intellink.py yang bisa di-build (APK kosong)"
```
(Rekam `git status` akhir: `local.properties`, `.gradle/`, `build/` tidak ikut ter-commit.)

---

## Task 2: Encoder paket kontrol `ControlPacket` (TDD)

**Files:**
- Create: `android/app/src/main/java/id/uinam/intellink/control/ControlPacket.kt`
- Create: `android/app/src/test/java/id/uinam/intellink/control/ControlPacketTest.kt`

**Interfaces:**
```kotlin
object ControlPacket {
    fun encode(servo2: Int, servo3: Int, throttle: Int, steering: Int): ByteArray
}
```
- Servo diklem ke 0..180, throttle/steering ke -255..255 (pertahanan terhadap nilai liar dari UI).
- Layout byte: `[0]=0xA5`, `[1]=servo2`, `[2]=servo3`, `[3..4]=int16 LE throttle`, `[5..6]=int16 LE steering`, `[7]=XOR byte 0..6`.

- [ ] **Langkah 1: Tulis test yang GAGAL.**

`android/app/src/test/java/id/uinam/intellink/control/ControlPacketTest.kt`:
```kotlin
package id.uinam.intellink.control

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ControlPacketTest {

    @Test
    fun `golden - encode(90, 90, 0, 0)`() {
        val expected = byteArrayOf(
            0xA5.toByte(), 0x5A, 0x5A, 0x00, 0x00, 0x00, 0x00, 0xA5.toByte(),
        )
        assertArrayEquals(expected, ControlPacket.encode(90, 90, 0, 0))
    }

    @Test
    fun `int16 little-endian dengan nilai negatif`() {
        val p = ControlPacket.encode(90, 90, -255, 0)
        assertEquals(0x01, p[3].toInt() and 0xFF)
        assertEquals(0xFF, p[4].toInt() and 0xFF)

        val q = ControlPacket.encode(90, 90, 255, -255)
        assertEquals(0xFF, q[3].toInt() and 0xFF)
        assertEquals(0x00, q[4].toInt() and 0xFF)
        assertEquals(0x01, q[5].toInt() and 0xFF)
        assertEquals(0xFF, q[6].toInt() and 0xFF)
        assertEquals(0xA4, q[7].toInt() and 0xFF)
    }

    @Test
    fun `nilai di luar rentang diklem`() {
        val expected = byteArrayOf(
            0xA5.toByte(), 0xB4.toByte(), 0x00, 0xFF.toByte(), 0x00, 0x01, 0xFF.toByte(), 0x10,
        )
        assertArrayEquals(expected, ControlPacket.encode(999, -999, 999, -999))
    }

    @Test
    fun `checksum adalah XOR byte 0 sampai 6`() {
        val cases = listOf(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(180, 180, 255, -255),
            intArrayOf(45, 135, -100, 200),
            intArrayOf(90, 90, 1, -1),
        )
        for (c in cases) {
            val p = ControlPacket.encode(c[0], c[1], c[2], c[3])
            var xor = 0
            for (i in 0..6) xor = xor xor (p[i].toInt() and 0xFF)
            assertEquals("kasus ${c.toList()}", xor, p[7].toInt() and 0xFF)
        }
    }
}
```

- [ ] **Langkah 2: Jalankan test — harus GAGAL** (belum ada `ControlPacket`):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --tests "id.uinam.intellink.control.ControlPacketTest" --console=plain
```

- [ ] **Langkah 3: Implementasi minimal.**

`android/app/src/main/java/id/uinam/intellink/control/ControlPacket.kt`:
```kotlin
package id.uinam.intellink.control

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encoder paket kontrol 8 byte little-endian — harus identik dengan controller.html
 * dan vehicle_control_ws.ino.
 *
 * Layout: [0]=0xA5, [1]=servo2 (0..180), [2]=servo3 (0..180),
 *         [3..4]=int16 throttle (-255..255), [5..6]=int16 steering (-255..255),
 *         [7]=XOR byte 0..6.
 */
object ControlPacket {

    fun encode(servo2: Int, servo3: Int, throttle: Int, steering: Int): ByteArray {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0xA5.toByte())
        buffer.put(servo2.coerceIn(0, 180).toByte())
        buffer.put(servo3.coerceIn(0, 180).toByte())
        buffer.putShort(throttle.coerceIn(-255, 255).toShort())
        buffer.putShort(steering.coerceIn(-255, 255).toShort())
        var checksum = 0
        for (i in 0 until 7) checksum = checksum xor (buffer.get(i).toInt() and 0xFF)
        buffer.put(checksum.toByte())
        return buffer.array()
    }
}
```

- [ ] **Langkah 4: Jalankan test — harus HIJAU** (perintah sama seperti Langkah 2). Lalu **commit**:

```powershell
git add android/app/src
git commit -m "feat(android): encoder paket kontrol 8 byte + unit test (golden, LE negatif, clamp, checksum)"
```

---

## Task 3: `RobotConnection` + `RobotClient` (OkHttp WebSocket, TDD dengan MockWebServer)

**Files:**
- Create: `android/app/src/main/java/id/uinam/intellink/control/RobotConnection.kt`
- Create: `android/app/src/main/java/id/uinam/intellink/control/RobotClient.kt`
- Create: `android/app/src/test/java/id/uinam/intellink/control/RobotClientTest.kt`

**Interfaces:**
```kotlin
enum class ConnectionState { CONNECTING, ONLINE, OFFLINE }

interface RobotConnection {
    val state: StateFlow<ConnectionState>
    fun connect(ip: String)
    fun disconnect()
    /** true bila paket benar-benar masuk antrean kirim. */
    fun send(packet: ByteArray): Boolean
}

class RobotClient(
    private val port: Int = 81,            // pengujian memakai port server tiruan
    private val reconnectDelayMs: Long = 1500L,
) : RobotConnection {
    fun shutdown()   // menutup semuanya; hanya untuk pengujian/keluar total
}
```

- [ ] **Langkah 1: Tulis test yang GAGAL** (MockWebServer = server WebSocket tiruan di JVM; `ws://127.0.0.1` tidak terkena aturan cleartext karena tes ini bukan Android).

`android/app/src/test/java/id/uinam/intellink/control/RobotClientTest.kt`:
```kotlin
package id.uinam.intellink.control

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RobotClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: RobotClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = RobotClient(port = server.port)
    }

    @After
    fun tearDown() {
        client.shutdown()
        server.shutdown()
    }

    private fun enqueueUpgrade(listener: WebSocketListener) {
        server.enqueue(MockResponse().withWebSocketUpgrade(listener))
    }

    private fun awaitUntil(timeoutMs: Long = 5000, message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        fail("Tidak tercapai dalam ${timeoutMs} ms: $message")
    }

    @Test
    fun `send sebelum tersambung mengembalikan false`() {
        assertFalse(client.send(ControlPacket.encode(90, 90, 0, 0)))
        assertEquals(ConnectionState.OFFLINE, client.state.value)
    }

    @Test
    fun `connect lalu send sampai ke server`() {
        val received = CopyOnWriteArrayList<ByteArray>()
        val opened = CountDownLatch(1)
        enqueueUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = opened.countDown()
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                received.add(bytes.toByteArray())
            }
        })

        client.connect("127.0.0.1")
        assertTrue("socket server tidak terbuka", opened.await(5, TimeUnit.SECONDS))
        awaitUntil(message = "state ONLINE") { client.state.value == ConnectionState.ONLINE }

        val packet = ControlPacket.encode(90, 90, 0, 0)
        assertTrue(client.send(packet))
        awaitUntil(message = "paket diterima") { received.isNotEmpty() }
        assertArrayEquals(packet, received[0])
    }

    @Test
    fun `server memutus koneksi maka client reconnect dengan jeda minimal 1,5 detik`() {
        val openTimes = CopyOnWriteArrayList<Long>()
        val sockets = CopyOnWriteArrayList<WebSocket>()
        val opened = CountDownLatch(2)
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                openTimes.add(System.currentTimeMillis())
                sockets.add(webSocket)
                opened.countDown()
            }
        }
        enqueueUpgrade(listener)
        enqueueUpgrade(listener)

        client.connect("127.0.0.1")
        assertTrue("koneksi pertama tidak terbuka", opened.await(10, TimeUnit.SECONDS))
        sockets[0].close(1000, "tes putus")
        assertTrue("tidak ada koneksi ulang", opened.await(10, TimeUnit.SECONDS))

        val gap = openTimes[1] - openTimes[0]
        assertTrue("jeda reconnect hanya $gap ms", gap >= 1400)
    }

    @Test
    fun `disconnect manual tidak memicu koneksi ulang`() {
        enqueueUpgrade(object : WebSocketListener() {})
        client.connect("127.0.0.1")
        awaitUntil(message = "state ONLINE") { client.state.value == ConnectionState.ONLINE }

        client.disconnect()
        assertEquals(ConnectionState.OFFLINE, client.state.value)
        Thread.sleep(2200)
        assertEquals("tidak boleh ada koneksi baru", 1, server.requestCount)
    }
}
```

- [ ] **Langkah 2: Jalankan — harus GAGAL** (kelas belum ada):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --tests "id.uinam.intellink.control.RobotClientTest" --console=plain
```

- [ ] **Langkah 3: Implementasi.**

`android/app/src/main/java/id/uinam/intellink/control/RobotConnection.kt`:
```kotlin
package id.uinam.intellink.control

import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState { CONNECTING, ONLINE, OFFLINE }

/** Abstraksi transport — memudahkan unit test ViewModel dengan fake. */
interface RobotConnection {
    val state: StateFlow<ConnectionState>
    fun connect(ip: String)
    fun disconnect()
    /** @return true bila paket benar-benar dimasukkan ke antrean pengiriman. */
    fun send(packet: ByteArray): Boolean
}
```

`android/app/src/main/java/id/uinam/intellink/control/RobotClient.kt`:
```kotlin
package id.uinam.intellink.control

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.ByteString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Klien WebSocket ke ESP32 (ws://<ip>:<port>, default port 81).
 * - Auto-reconnect tiap [reconnectDelayMs] setelah koneksi putus, kecuali [disconnect] dipanggil manual.
 * - [send] mengembalikan false bila belum ONLINE atau antrean kirim membengkak (> 64) — frame dibuang, bukan mengantre lag.
 */
class RobotClient(
    private val port: Int = 81,
    private val reconnectDelayMs: Long = 1500L,
) : RobotConnection {

    private val httpClient = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ConnectionState.OFFLINE)
    override val state: StateFlow<ConnectionState> = _state

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var currentIp: String? = null
    private var autoReconnect = false
    private var generation = 0

    @Synchronized
    override fun connect(ip: String) {
        currentIp = ip
        autoReconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        val gen = ++generation
        socket?.close(1000, null)
        _state.value = ConnectionState.CONNECTING
        open(ip, gen)
    }

    @Synchronized
    override fun disconnect() {
        autoReconnect = false
        generation++
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, null)
        socket = null
        _state.value = ConnectionState.OFFLINE
    }

    @Synchronized
    override fun send(packet: ByteArray): Boolean {
        val ws = socket ?: return false
        if (_state.value != ConnectionState.ONLINE) return false
        if (ws.queueSize() > 64) return false
        return ws.send(ByteString.of(*packet))
    }

    /** Menutup totale — dipakai pengujian; app normal cukup memakai [disconnect]. */
    @Synchronized
    fun shutdown() {
        disconnect()
        scope.cancel()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    private fun open(ip: String, gen: Int) {
        val request = Request.Builder().url("ws://$ip:$port").build()
        socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (gen != generation) return
                _state.value = ConnectionState.ONLINE
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (gen != generation) return
                _state.value = ConnectionState.OFFLINE
                scheduleReconnect(gen)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (gen != generation) return
                _state.value = ConnectionState.OFFLINE
                scheduleReconnect(gen)
            }
        })
    }

    private fun scheduleReconnect(gen: Int) {
        if (!autoReconnect || gen != generation) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(reconnectDelayMs)
            if (autoReconnect && gen == generation) {
                currentIp?.let { connect(it) }
            }
        }
    }
}
```
Catatan penting (jangan disederhanakan): `generation` mencegah callback socket lama (mis. saat `connect` menutup socket sebelumnya) memicu reconnect ganda; `autoReconnect` mencegah reconnect setelah `disconnect()` manual.

- [ ] **Langkah 4: Jalankan — harus HIJAU.** Uji ketahanan: ulangi sekali lagi (test jaringan lokal, harus stabil). Lalu **commit**:

```powershell
git add android/app/src
git commit -m "feat(android): klien WebSocket RobotClient + auto-reconnect 1,5 dtk (uji MockWebServer)"
```

---

## Task 4: `ControlViewModel` (state, ticker 33 ms, keselamatan, lifecycle) — TDD

**Files:**
- Create: `android/app/src/main/java/id/uinam/intellink/control/ControlViewModel.kt`
- Create: `android/app/src/test/java/id/uinam/intellink/control/MainDispatcherRule.kt`
- Create: `android/app/src/test/java/id/uinam/intellink/control/FakeConnection.kt`
- Create: `android/app/src/test/java/id/uinam/intellink/control/ControlViewModelTest.kt`

**Interfaces:**
```kotlin
class ControlViewModel(connection: RobotConnection, initialIp: String) : ViewModel() {
    val state: StateFlow<ConnectionState>
    val ip: StateFlow<String>
    val servo2: StateFlow<Int>; val servo3: StateFlow<Int>
    val throttle: StateFlow<Int>; val steering: StateFlow<Int>
    fun setServo2(value: Int)              // klem 0..180
    fun setServo3(value: Int)
    fun setDrive(throttle: Int, steering: Int)   // klem -255..255
    fun stopDrive()                        // nol + kirim seketika
    fun onIpChanged(newIp: String)         // commit IP baru → reconnect; kosong → disconnect
    fun onForeground()                     // connect bila OFFLINE dan IP tidak kosong
    fun onBackground()                     // kirim stop + disconnect
}
```
Semantik penting: `onIpChanged` dipanggil saat pengguna **selesai** mengetik (IME Done), bukan tiap ketikan. Ticker selalu jalan; hanya benar-benar mengirim saat ONLINE.

- [ ] **Langkah 1: Tulis test yang GAGAL.**

`android/app/src/test/java/id/uinam/intellink/control/MainDispatcherRule.kt`:
```kotlin
package id.uinam.intellink.control

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

`android/app/src/test/java/id/uinam/intellink/control/FakeConnection.kt`:
```kotlin
package id.uinam.intellink.control

import kotlinx.coroutines.flow.MutableStateFlow

/** Fake RobotConnection; waktu diambil dari scheduler test lewat [now]. */
class FakeConnection(private val now: () -> Long) : RobotConnection {
    override val state = MutableStateFlow(ConnectionState.OFFLINE)
    val sent = mutableListOf<Pair<Long, ByteArray>>()
    val connectCalls = mutableListOf<String>()
    var disconnectCalls = 0
        private set

    override fun connect(ip: String) {
        connectCalls += ip
        state.value = ConnectionState.ONLINE
    }

    override fun disconnect() {
        disconnectCalls++
        state.value = ConnectionState.OFFLINE
    }

    override fun send(packet: ByteArray): Boolean {
        sent += now() to packet
        return true
    }
}
```

`android/app/src/test/java/id/uinam/intellink/control/ControlViewModelTest.kt`:
```kotlin
package id.uinam.intellink.control

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ControlViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun decodeInt16LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or (bytes[offset + 1].toInt() shl 8)

    @Test
    fun `connect otomatis saat dibuat dan ticker mengirim tiap 33 ms`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fake = FakeConnection(now = { testScheduler.currentTime })
            ControlViewModel(fake, initialIp = "192.168.1.100")

            assertEquals(listOf("192.168.1.100"), fake.connectCalls)

            advanceTimeBy(100)
            assertEquals(listOf(0L, 33L, 66L, 99L), fake.sent.map { it.first })
        }

    @Test
    fun `tidak mengirim paket saat offline`() = runTest(mainDispatcherRule.testDispatcher) {
        val fake = FakeConnection(now = { testScheduler.currentTime })
        ControlViewModel(fake, initialIp = "")

        advanceTimeBy(100)
        assertTrue(fake.sent.isEmpty())
    }

    @Test
    fun `nilai servo dan drive masuk ke paket berikutnya`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fake = FakeConnection(now = { testScheduler.currentTime })
            val vm = ControlViewModel(fake, initialIp = "192.168.1.100")
            advanceTimeBy(40)

            vm.setServo2(120)
            vm.setDrive(throttle = 100, steering = -50)
            advanceTimeBy(40)

            val packet = fake.sent.last().second
            assertEquals(120, packet[1].toInt() and 0xFF)
            assertEquals(100, decodeInt16LE(packet, 3))
            assertEquals(-50, decodeInt16LE(packet, 5))
        }

    @Test
    fun `stopDrive mengirim nol seketika tanpa menunggu tick`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fake = FakeConnection(now = { testScheduler.currentTime })
            val vm = ControlViewModel(fake, initialIp = "192.168.1.100")
            advanceTimeBy(100)
            val before = fake.sent.size

            vm.stopDrive()

            assertEquals(before + 1, fake.sent.size)
            val packet = fake.sent.last().second
            assertEquals(0, decodeInt16LE(packet, 3))
            assertEquals(0, decodeInt16LE(packet, 5))
        }

    @Test
    fun `onBackground mengirim stop lalu memutus koneksi dan berhenti mengirim`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fake = FakeConnection(now = { testScheduler.currentTime })
            val vm = ControlViewModel(fake, initialIp = "192.168.1.100")
            advanceTimeBy(100)
            val before = fake.sent.size

            vm.onBackground()

            assertEquals(before + 1, fake.sent.size)
            val packet = fake.sent.last().second
            assertEquals(0, decodeInt16LE(packet, 3))
            assertEquals(0, decodeInt16LE(packet, 5))
            assertEquals(1, fake.disconnectCalls)

            advanceTimeBy(200)
            assertEquals(before + 1, fake.sent.size)
        }

    @Test
    fun `onBackground sebelum pernah tersambung tidak error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fake = FakeConnection(now = { testScheduler.currentTime })
            val vm = ControlViewModel(fake, initialIp = "")

            vm.onBackground()

            assertEquals(1, fake.disconnectCalls)
            assertTrue(fake.sent.isEmpty())
        }

    @Test
    fun `onForeground saat offline mengconnect ulang`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fake = FakeConnection(now = { testScheduler.currentTime })
            val vm = ControlViewModel(fake, initialIp = "192.168.1.100")

            vm.onBackground()
            vm.onForeground()

            assertEquals(listOf("192.168.1.100", "192.168.1.100"), fake.connectCalls)
        }

    @Test
    fun `onForeground saat masih online tidak mengconnect ulang`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fake = FakeConnection(now = { testScheduler.currentTime })
            val vm = ControlViewModel(fake, initialIp = "192.168.1.100")

            vm.onForeground()

            assertEquals(1, fake.connectCalls.size)
        }

    @Test
    fun `onIpChanged kosong memutus koneksi tanpa connect ke string kosong`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fake = FakeConnection(now = { testScheduler.currentTime })
            val vm = ControlViewModel(fake, initialIp = "192.168.1.100")

            vm.onIpChanged("   ")

            assertEquals(1, fake.disconnectCalls)
            assertEquals(listOf("192.168.1.100"), fake.connectCalls)
            assertEquals(ConnectionState.OFFLINE, vm.state.value)
        }

    @Test
    fun `onIpChanged baru mengconnect ke IP baru`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fake = FakeConnection(now = { testScheduler.currentTime })
            val vm = ControlViewModel(fake, initialIp = "192.168.1.100")

            vm.onIpChanged("10.0.0.5")

            assertEquals(listOf("192.168.1.100", "10.0.0.5"), fake.connectCalls)
            assertEquals("10.0.0.5", vm.ip.value)
        }
}
```

- [ ] **Langkah 2: Jalankan — harus GAGAL:**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --tests "id.uinam.intellink.control.ControlViewModelTest" --console=plain
```

- [ ] **Langkah 3: Implementasi.**

`android/app/src/main/java/id/uinam/intellink/control/ControlViewModel.kt`:
```kotlin
package id.uinam.intellink.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * State kontrol + ticker 33 ms (~30 Hz, sekaligus heartbeat failsafe ESP32).
 * Ticker selalu berjalan; paket hanya dikirim saat ONLINE.
 */
class ControlViewModel(
    private val connection: RobotConnection,
    initialIp: String,
) : ViewModel() {

    private val _ip = MutableStateFlow(initialIp)
    val ip: StateFlow<String> = _ip

    val state: StateFlow<ConnectionState> = connection.state

    private val _servo2 = MutableStateFlow(90)
    private val _servo3 = MutableStateFlow(90)
    private val _throttle = MutableStateFlow(0)
    private val _steering = MutableStateFlow(0)
    val servo2: StateFlow<Int> = _servo2
    val servo3: StateFlow<Int> = _servo3
    val throttle: StateFlow<Int> = _throttle
    val steering: StateFlow<Int> = _steering

    init {
        if (initialIp.isNotBlank()) connection.connect(initialIp)
        viewModelScope.launch {
            while (true) {
                sendCurrentValues()
                delay(33)
            }
        }
    }

    fun setServo2(value: Int) {
        _servo2.value = value.coerceIn(0, 180)
    }

    fun setServo3(value: Int) {
        _servo3.value = value.coerceIn(0, 180)
    }

    fun setDrive(throttle: Int, steering: Int) {
        _throttle.value = throttle.coerceIn(-255, 255)
        _steering.value = steering.coerceIn(-255, 255)
    }

    /** Tombol STOP dan lepas joystick: nol + kirim seketika (tidak menunggu tick). */
    fun stopDrive() {
        setDrive(0, 0)
        sendCurrentValues()
    }

    /** Dipanggil saat pengguna selesai mengubah IP (IME Done). */
    fun onIpChanged(newIp: String) {
        val trimmed = newIp.trim()
        _ip.value = trimmed
        if (trimmed.isBlank()) {
            connection.disconnect()
        } else {
            connection.connect(trimmed)
        }
    }

    fun onForeground() {
        val current = _ip.value
        if (current.isNotBlank() && connection.state.value == ConnectionState.OFFLINE) {
            connection.connect(current)
        }
    }

    fun onBackground() {
        setDrive(0, 0)
        sendCurrentValues()
        connection.disconnect()
    }

    private fun sendCurrentValues() {
        if (connection.state.value != ConnectionState.ONLINE) return
        connection.send(
            ControlPacket.encode(_servo2.value, _servo3.value, _throttle.value, _steering.value)
        )
    }
}
```

- [ ] **Langkah 4: Jalankan — harus HIJAU.** Lalu **commit**:

```powershell
git add android/app/src
git commit -m "feat(android): ViewModel kontrol (ticker 30 Hz, stop seketika, kebijakan background)"
```

---

## Task 5: UI Compose — dashboard, joystick, haptik, keep-screen-on, portrait (TDD untuk matematika)

**Files:**
- Create: `android/app/src/main/java/id/uinam/intellink/ui/JoystickMath.kt`
- Create: `android/app/src/main/java/id/uinam/intellink/ui/Joystick.kt`
- Create: `android/app/src/main/java/id/uinam/intellink/ui/DashboardScreen.kt`
- Create: `android/app/src/main/java/id/uinam/intellink/ui/Haptics.kt`
- Create: `android/app/src/main/java/id/uinam/intellink/data/SettingsStore.kt`
- Replace: `android/app/src/main/java/id/uinam/intellink/MainActivity.kt`
- Modify: `android/app/src/main/AndroidManifest.xml` (tambah `android:screenOrientation="portrait"`)
- Create: `android/app/src/test/java/id/uinam/intellink/ui/JoystickMathTest.kt`

**Interfaces:**
```kotlin
data class DriveCommand(val throttle: Int, val steering: Int)     // -255..255
fun joystickToDrive(dx: Float, dy: Float, radius: Float): DriveCommand
fun clampToCircle(offset: Offset, radius: Float): Offset

@Composable fun Joystick(modifier, enabled, onMove: (DriveCommand) -> Unit, onRelease: () -> Unit)
@Composable fun DashboardScreen(viewModel: ControlViewModel, modifier: Modifier = Modifier)

class Haptics(context: Context) { fun connectPattern(); fun disconnectPattern() }
class SettingsStore(context: Context) {
    fun loadIp(): String; fun saveIp(ip: String)
    companion object { const val DEFAULT_IP = "192.168.1.100" }
}
```
Tanda arah (kalibrasi sudah diputuskan): dorong **atas** → `throttle` **positif** (maju); geser **kanan** → `steering` **positif**.

- [ ] **Langkah 1: Tulis test matematika joystick yang GAGAL.**

`android/app/src/test/java/id/uinam/intellink/ui/JoystickMathTest.kt`:
```kotlin
package id.uinam.intellink.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class JoystickMathTest {

    @Test
    fun `titik tengah menghasilkan nol`() {
        assertEquals(DriveCommand(0, 0), joystickToDrive(0f, 0f, 100f))
    }

    @Test
    fun `dorong atas penuh menghasilkan throttle maksimal`() {
        assertEquals(DriveCommand(255, 0), joystickToDrive(0f, -100f, 100f))
    }

    @Test
    fun `dorong bawah penuh menghasilkan throttle negatif maksimal`() {
        assertEquals(DriveCommand(-255, 0), joystickToDrive(0f, 100f, 100f))
    }

    @Test
    fun `geser kanan penuh menghasilkan steering maksimal`() {
        assertEquals(DriveCommand(0, 255), joystickToDrive(100f, 0f, 100f))
    }

    @Test
    fun `geser kiri penuh menghasilkan steering negatif maksimal`() {
        assertEquals(DriveCommand(0, -255), joystickToDrive(-100f, 0f, 100f))
    }

    @Test
    fun `nilai setengah diskalakan proporsional`() {
        assertEquals(DriveCommand(64, 128), joystickToDrive(50f, -25f, 100f))
    }

    @Test
    fun `di luar radius tetap dibatasi 255`() {
        assertEquals(DriveCommand(-255, 255), joystickToDrive(500f, 500f, 100f))
    }

    @Test
    fun `NaN atau radius nol menghasilkan nol`() {
        assertEquals(DriveCommand(0, 0), joystickToDrive(Float.NaN, 0f, 100f))
        assertEquals(DriveCommand(0, 0), joystickToDrive(0f, Float.NaN, 100f))
        assertEquals(DriveCommand(0, 0), joystickToDrive(10f, 10f, 0f))
    }

    @Test
    fun `clampToCircle membatasi panjang vektor`() {
        val clamped = clampToCircle(Offset(300f, 0f), 100f)
        assertEquals(100f, clamped.x, 0.001f)
        assertEquals(0f, clamped.y, 0.001f)

        assertEquals(Offset.Zero, clampToCircle(Offset.Zero, 100f))

        val inside = Offset(50f, 20f)
        assertEquals(inside, clampToCircle(inside, 100f))
    }
}
```

- [ ] **Langkah 2: Jalankan — harus GAGAL**, lalu **implementasi**:

`android/app/src/main/java/id/uinam/intellink/ui/JoystickMath.kt`:
```kotlin
package id.uinam.intellink.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

/** Perintah drive yang sudah diskalakan ke rentang protokol. */
data class DriveCommand(val throttle: Int, val steering: Int)

/**
 * Konversi offset kenop joystick (px; dy positif = ke bawah mengikuti konvensi layar)
 * ke perintah drive: dorong atas => throttle positif, geser kanan => steering positif.
 * Nilai selalu valid (-255..255) walau input ekstrem/NaN.
 */
fun joystickToDrive(dx: Float, dy: Float, radius: Float): DriveCommand {
    if (radius <= 0f || dx.isNaN() || dy.isNaN()) return DriveCommand(0, 0)
    val steering = (dx / radius * 255f).roundToInt().coerceIn(-255, 255)
    val throttle = (-dy / radius * 255f).roundToInt().coerceIn(-255, 255)
    return DriveCommand(throttle, steering)
}

/** Batasi offset kenop supaya tidak keluar lingkaran joystick. */
fun clampToCircle(offset: Offset, radius: Float): Offset {
    val length = offset.getDistance()
    if (length <= radius || length == 0f) return offset
    return offset * (radius / length)
}
```

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --tests "id.uinam.intellink.ui.JoystickMathTest" --console=plain
```
Harus HIJAU sebelum lanjut.

- [ ] **Langkah 3: Tulis komponen UI** (styling mengikuti warna web; teks Indonesia).

`android/app/src/main/java/id/uinam/intellink/ui/Haptics.kt`:
```kotlin
package id.uinam.intellink.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Pola getaran singkat untuk event koneksi. */
class Haptics(context: Context) {

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    fun connectPattern() {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 80, 40), -1))
    }

    fun disconnectPattern() {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 150), -1))
    }
}
```

`android/app/src/main/java/id/uinam/intellink/data/SettingsStore.kt`:
```kotlin
package id.uinam.intellink.data

import android.content.Context

/** Menyimpan IP ESP32 terakhir (mengikuti perilaku web). */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("intellink", Context.MODE_PRIVATE)

    fun loadIp(): String = prefs.getString(KEY_LAST_IP, DEFAULT_IP) ?: DEFAULT_IP

    fun saveIp(ip: String) {
        prefs.edit().putString(KEY_LAST_IP, ip).apply()
    }

    companion object {
        const val DEFAULT_IP = "192.168.1.100"
        private const val KEY_LAST_IP = "last_ip"
    }
}
```

`android/app/src/main/java/id/uinam/intellink/ui/Joystick.kt`:
```kotlin
package id.uinam.intellink.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

private const val BASE_RADIUS_FACTOR = 0.6f      // radius area gerak = 60% setengah sisi
private const val KNOB_RADIUS_FACTOR = 0.12f     // radius kenop
private val ACCENT = Color(0xFF40C4FF)
private val ACTIVE = Color(0xFF00E676)
private val DISABLED = Color(0xFF555555)

/**
 * Joystick lingkaran: dorong ke arah mana pun (dibatasi lingkaran), lepas => kembali ke tengah
 * dan memanggil [onRelease] (UI akan memanggil stop seketika).
 */
@Composable
fun Joystick(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onMove: (DriveCommand) -> Unit,
    onRelease: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var knob by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val radius = minOf(size.width, size.height) / 2f * BASE_RADIUS_FACTOR
                detectDragGestures(
                    onDragStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        knob = Offset.Zero
                        onRelease()
                    },
                    onDragCancel = {
                        knob = Offset.Zero
                        onRelease()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        knob = clampToCircle(knob + dragAmount, radius)
                        onMove(joystickToDrive(knob.x, knob.y, radius))
                    },
                )
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f * BASE_RADIUS_FACTOR
        val accent = if (enabled) ACCENT else DISABLED
        val knobColor = if (enabled) ACTIVE else DISABLED

        drawCircle(color = accent.copy(alpha = 0.12f), radius = radius, center = center)
        drawCircle(color = accent.copy(alpha = 0.6f), radius = radius, center = center, style = Stroke(width = 4f))
        drawCircle(
            color = knobColor,
            radius = minOf(size.width, size.height) * KNOB_RADIUS_FACTOR,
            center = center + knob,
        )
    }
}
```

`android/app/src/main/java/id/uinam/intellink/ui/DashboardScreen.kt`:
```kotlin
package id.uinam.intellink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.uinam.intellink.control.ConnectionState
import id.uinam.intellink.control.ControlViewModel
import kotlin.math.roundToInt

private val ACCENT = Color(0xFF40C4FF)
private val ONLINE = Color(0xFF00E676)
private val OFFLINE = Color(0xFFFF1744)
private val PENDING = Color(0xFFFFC107)

@Composable
fun DashboardScreen(viewModel: ControlViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ip by viewModel.ip.collectAsStateWithLifecycle()
    val servo2 by viewModel.servo2.collectAsStateWithLifecycle()
    val servo3 by viewModel.servo3.collectAsStateWithLifecycle()
    val online = state == ConnectionState.ONLINE
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Intellink.py", color = ACCENT, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        val (statusText, statusColor) = when (state) {
            ConnectionState.CONNECTING -> "Menghubungkan…" to PENDING
            ConnectionState.ONLINE -> "Terhubung" to ONLINE
            ConnectionState.OFFLINE -> "Tidak terhubung" to OFFLINE
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(statusColor, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(statusText, color = statusColor, fontSize = 18.sp)
        }

        var ipText by remember(ip) { mutableStateOf(ip) }
        OutlinedTextField(
            value = ipText,
            onValueChange = { ipText = it },
            label = { Text("IP Address ESP32") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                viewModel.onIpChanged(ipText)
            }),
            modifier = Modifier.fillMaxWidth(),
        )

        ServoSlider(
            label = "Servo 2 (GPIO 4)",
            value = servo2,
            enabled = online,
            onValueChange = viewModel::setServo2,
        )
        ServoSlider(
            label = "Servo 3 (GPIO 16)",
            value = servo3,
            enabled = online,
            onValueChange = viewModel::setServo3,
        )

        Joystick(
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = online,
            onMove = { viewModel.setDrive(it.throttle, it.steering) },
            onRelease = { viewModel.stopDrive() },
        )

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.stopDrive()
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OFFLINE),
        ) {
            Text("STOP", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ServoSlider(label: String, value: Int, enabled: Boolean, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("$label: $value°", color = Color.LightGray, fontSize = 14.sp)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..180f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = ONLINE,
                activeTrackColor = ONLINE,
            ),
        )
    }
}
```

- [ ] **Langkah 4: Ganti `MainActivity.kt`** (keep screen on, lifecycle, simpan IP, haptik koneksi, tema):

`android/app/src/main/java/id/uinam/intellink/MainActivity.kt`:
```kotlin
package id.uinam.intellink

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.uinam.intellink.control.ConnectionState
import id.uinam.intellink.control.ControlViewModel
import id.uinam.intellink.control.RobotClient
import id.uinam.intellink.data.SettingsStore
import id.uinam.intellink.ui.DashboardScreen
import id.uinam.intellink.ui.Haptics

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val store = SettingsStore(this)

        setContent {
            val viewModel: ControlViewModel = viewModel {
                ControlViewModel(RobotClient(), store.loadIp())
            }
            val haptics = remember { Haptics(applicationContext) }
            val state by viewModel.state.collectAsStateWithLifecycle()
            val ip by viewModel.ip.collectAsStateWithLifecycle()

            LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
            LaunchedEffect(ip) { store.saveIp(ip) }

            var previousState by remember { mutableStateOf<ConnectionState?>(null) }
            LaunchedEffect(state) {
                when {
                    previousState == ConnectionState.ONLINE && state != ConnectionState.ONLINE ->
                        haptics.disconnectPattern()
                    previousState != null && state == ConnectionState.ONLINE ->
                        haptics.connectPattern()
                }
                previousState = state
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    primary = Color(0xFF40C4FF),
                ),
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DashboardScreen(viewModel)
                }
            }
        }
    }
}
```

- [ ] **Langkah 5: Kunci portrait** — ubah `android/app/src/main/AndroidManifest.xml`, tambahkan atribut pada `<activity ...>`:
```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:screenOrientation="portrait">
```

- [ ] **Langkah 6: Verifikasi** (test semua + build):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
```
Uji asap di HP (bila tersambung adb): `adb install -r app\build\outputs\apk\debug\app-debug.apk`, lalu buka app: label **Intellink.py**, portrait, layar tidak mati, joystick bergerak, STOP bergetar. (Tanpa robot, status akan "Tidak terhubung" + retry — itu benar.)

- [ ] **Langkah 7: Commit.**

```powershell
git add android
git commit -m "feat(android): UI dashboard + joystick, haptik, keep-screen-on, portrait, simpan IP"
```

---

## Task 6: Verifikasi akhir + APK siap pasang

**Files:** (tidak ada file baru; hanya perbaikan bila verifikasi menemukan masalah)

- [ ] **Langkah 1: Seluruh test + build bersih.**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
```
Harapan: semua test HIJAU; `BUILD SUCCESSFUL`; APK di `android/app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Langkah 2: Pasang di HP Android 11** (USB atau transfer file):
```powershell
adb devices
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```
Catatan MIUI (Poco X3 Pro): aktifkan "Install via USB" + "USB debugging (Security settings)" bila `adb install` ditolak.

- [ ] **Langkah 3: Checklist uji meja & lapangan (manual, pengguna).** [Uji meja protokol sudah tercakup otomatis oleh MockWebServer (Task 3) + ticker 33 ms (Task 4) — tidak perlu server tiruan terpisah.]

Uji lapangan (HP di hotspot yang sama dengan ESP32 — hotspot bernama "Poco X3 Pro"; IP ESP32 dibaca dari Serial Monitor 115200 saat boot):
- [ ] Isi IP ESP32 → IME Done → status menjadi **Terhubung** (hijau) ≤ ~2 detik.
- [ ] Slider Servo 2 dan Servo 3 menggerakkan servo yang benar.
- [ ] Joystick: dorong atas → robot maju; bawah → mundur; kanan/kiri → belok kanan/kiri. **Bila arah terbalik**, kalibrasi satu titik: balik tanda di `joystickToDrive` (tukar `dx`/`-dy`) — lalu ulangi test Task 5 dan build ulang.
- [ ] Lepas joystick → robot berhenti seketika (paket nol terkirim); kenop kembali ke tengah.
- [ ] Tekan STOP saat robot jalan → berhenti seketika + getaran.
- [ ] Matikan ESP32 saat app terbuka → status merah; hidupkan lagi → tersambung otomatis (≤ ~3 detik).
- [ ] Pindah ke app lain / kunci layar saat robot jalan → robot berhenti (disconnect + failsafe 500 ms firmware).
- [ ] Daya HP: layar tidak mati selama app di depan.
- [ ] Jauhkan HP (lemah sinyal) → robot berhenti (failsafe), app menampilkan status offline, lalu reconnect saat sinyal kuat.

- [ ] **Langkah 4: Commit perbaikan (bila ada), dan laporkan hasil.** Selesai — APK siap dipasang ulang kapan saja dari path di atas.

---

## Catatan akhir untuk pelaksana

- Urutan task **wajib** (2 → 3 → 4 → 5): tiap task memakai hasil task sebelumnya (encoder dipakai klien/VM; VM dipakai UI).
- Jangan menambah fitur di luar spec (tanpa kamera, tanpa telemetri, tanpa pengaturan tambahan).
- Bila muncul galat versi saat sync pertama (mis. Compose Compiler ↔ Kotlin), pakai pesan galat sebagai sumber kebenaran: samakan versi plugin Compose dengan versi Kotlin bawaan AGP yang disebut, lalu ulangi test.
