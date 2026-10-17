# Spesifikasi Desain: Aplikasi Android Kotlin — UINAM Robot 2026

- **Tanggal:** 2026-10-17
- **Status:** Desain disetujui; menunggu review dokumen
- **Repo:** `uinamrobot2026`

## 1. Latar belakang

Repo ini berisi kontroler robot dalam dua komponen:

- `controller.html` — dashboard web. Terhubung ke ESP32 via WebSocket `ws://<ip>:81`, mengirim paket biner 8 byte dengan laju ~30 Hz; kontrol 2 servo (GPIO 4 & 16) + throttle + steering, tombol STOP, auto-reconnect 1,5 detik, spring-back saat dilepas, dan auto-stop saat tab disembunyikan.
- `vehicle_control_ws.ino` — firmware ESP32: WiFi STA, WebSocket server port 81, failsafe 500 ms, OTA.

**Masalah:** kontrol berbasis browser kurang nyaman saat mengoperasikan robot dari HP.

**Tujuan:** aplikasi Android native (Kotlin) yang dapat dipasang sebagai APK, menyalin seluruh fungsi web, dengan peningkatan kenyamanan kontrol.

## 2. Keputusan yang disetujui

| Keputusan | Pilihan | Alternatif yang ditolak |
|---|---|---|
| Arah produk | APK native Android (Kotlin) | PWA (kurang "aplikasi asli") dan WebView shell (joystick/haptic/keep-screen-on jadi ribet) |
| Teknologi UI | Jetpack Compose | XML Views (lebih banyak kode, pola lama) |
| WebSocket | OkHttp | — |
| Firmware ESP32 | Tidak diubah | Protokol 8 byte sudah berjalan baik |
| Web lama | Dipertahankan di root repo | Menghapus web lama |

## 3. Ruang lingkup

**Termasuk:**

- Project Android baru di subfolder `android/`; `controller.html` dan `.ino` tetap di root dan tidak disentuh.
- Menyalin semua fungsi web: koneksi + auto-reconnect, 2 slider servo, throttle/steering, tombol STOP, indikator status, pengiriman ~30 Hz, failsafe.
- Peningkatan kenyamanan: joystick, haptic (getaran), layar selalu menyala, portrait terkunci, stop-on-background.
- Distribusi berupa file APK (sideload), bukan Play Store.

**Tidak termasuk:**

- Perubahan firmware ESP32.
- Fitur baru di luar web (kamera, telemetri, dll).
- iOS.
- Menghapus atau mengubah `controller.html`.

## 4. Arsitektur

Struktur folder project Android:

```
android/
├─ app/src/main/java/id/uinam/robot2026/
│  ├─ MainActivity.kt            # entry, keep-screen-on, lifecycle
│  ├─ ui/DashboardScreen.kt      # layar utama (Compose)
│  ├─ ui/Joystick.kt             # komponen joystick (Canvas + pointerInput)
│  ├─ control/ControlViewModel.kt  # state + ticker 30 Hz
│  ├─ control/RobotClient.kt     # OkHttp WebSocket + auto-reconnect
│  ├─ control/ControlPacket.kt   # encoder paket (fungsi murni)
│  └─ data/SettingsStore.kt      # IP terakhir (SharedPreferences)
└─ (Gradle wrapper + build files)
```

Tanggung jawab komponen:

- **MainActivity** — single activity; menyalakan `FLAG_KEEP_SCREEN_ON`; saat `onStop` memanggil stop + tutup socket; saat kembali ke depan/`onStart` reconnect ke IP terakhir.
- **ControlViewModel** — menyimpan state: `ConnectionState` (CONNECTING/ONLINE/OFFLINE), `servo2`, `servo3`, `throttle`, `steering`; coroutine ticker mengirim paket tiap 33 ms.
- **RobotClient** — membungkus OkHttp WebSocket; connect/disconnect; auto-reconnect 1,5 detik; buang frame bila socket belum terbuka atau antrian kirim membengkak (setara `bufferedAmount > 64` di web; di OkHttp dicek via `queueSize()`).
- **ControlPacket** — fungsi murni `encode(s2, s3, throttle, steering): ByteArray`; diuji unit.
- **SettingsStore** — baca/tulis IP terakhir.
- **DashboardScreen + Joystick** — seluruh UI.

Aliran data:

```
UI event → ControlViewModel (state) → ticker 33 ms → RobotClient.send(ControlPacket.encode(...)) → ESP32
WS callback (open/close/fail) → ControlViewModel → UI status + enable/disable kontrol
```

## 5. Protokol komunikasi (tetap sama dengan web)

- Alamat: `ws://<ip>:81` (tanpa TLS).
- Paket 8 byte little-endian:

| Offset | Isi | Tipe |
|---|---|---|
| 0 | magic `0xA5` | uint8 |
| 1 | servo 2 (0–180) | uint8 |
| 2 | servo 3 (0–180) | uint8 |
| 3–4 | throttle (-255..255) | int16 little-endian |
| 5–6 | steering (-255..255) | int16 little-endian |
| 7 | XOR byte 0..6 | uint8 |

- Laju kirim: tiap 33 ms (~30 Hz), sekaligus heartbeat untuk failsafe ESP32.
- Frame dibuang bila tidak bisa dikirim tepat waktu (jangan mengantre lag).

## 6. Keselamatan & lifecycle

1. Lepas joystick → throttle/steering = 0 + kirim paket stop seketika (tidak menunggu tick berikutnya).
2. Tombol STOP → semua nilai drive = 0 + kirim seketika.
3. App ke background / layar terkunci → kirim paket stop lalu tutup socket; ESP32 memanggil `stopVehicle()` pada event disconnect. Kembali ke depan → reconnect otomatis. App juga langsung connect saat pertama dibuka (seperti web).
4. Layar selalu menyala selama app di depan, agar tidak mati saat mengoperasikan robot.
5. Failsafe firmware (500 ms tanpa paket → stop) tetap menjadi lapisan terakhir dan tidak diubah.
6. Kontrol dinonaktifkan saat status bukan ONLINE (sama seperti web).
7. Tidak ada kredensial di protokol, sehingga koneksi `ws://` tanpa TLS tidak membawa data sensitif.

## 7. UI/UX

- Orientasi: portrait terkunci.
- Susunan dari atas ke bawah:
  1. Baris status: dot indikator + teks `CONNECTING` / `ONLINE` / `OFFLINE`.
  2. Kolom IP Address ESP32 — terisi otomatis dari simpanan terakhir (default `192.168.1.100`, mengikuti web); ubah IP → reconnect.
  3. Slider Servo 2 (GPIO 4), 0–180° + label derajat.
  4. Slider Servo 3 (GPIO 16), 0–180° + label derajat.
  5. Joystick besar: dorong ke atas = maju (throttle positif), ke bawah = mundur; geser kanan = kanan (steering positif), kiri = kiri (skala -255..255); kembali ke tengah saat dilepas. Tanda arah dikalibrasi saat uji lapangan (satu titik perubahan bila terbalik).
  6. Tombol STOP besar, lebar penuh.
- Haptic: getaran singkat saat memegang/melepas joystick dan menekan STOP; pola berbeda saat koneksi putus vs tersambung.
- Bahasa antarmuka: Indonesia (mengikuti web).

## 8. Izin & konfigurasi Android

- `INTERNET` — WebSocket.
- `VIBRATE` — haptic.
- `android:usesCleartextTraffic="true"` — wajib untuk `ws://` tanpa TLS.
- minSdk 26 (Android 8.0), compileSdk/targetSdk 36.
- Nama app: "UINAM Robot 2026"; package `id.uinam.robot2026`.

## 9. Pengujian

**Unit test (encoder):**

- Byte golden: `encode(90, 90, 0, 0)` → `A5 5A 5A 00 00 00 00 A5`.
- int16 little-endian negatif: throttle = -255 → byte `01 FF` pada offset 3–4.
- Checksum dihitung benar untuk kombinasi nilai acak (dibandingkan rumus XOR 0..6).

**Uji meja (tanpa robot):**

- Server WebSocket tiruan kecil di laptop: mencetak paket yang diterima + frekuensi kedatangan.
- Verifikasi: laju ~30 Hz; paket stop terkirim saat joystick dilepas, saat tombol STOP, dan saat app di-background; reconnect otomatis setelah server dimatikan lalu dinyalakan.

**Uji lapangan (HP + robot):**

- Servo bergerak sesuai slider; arah maju/mundur/kiri/kanan benar (jika terbalik cukup flip tanda — satu titik kalibrasi).
- Failsafe: tutup app → motor berhenti ≤ 0,5 detik.
- Layar terkunci / app di-background → motor berhenti.
- Jauhkan HP (WiFi lemah) → robot berhenti karena failsafe, app menampilkan status offline.
- Reboot ESP32 → app reconnect otomatis.

**Build:** `gradlew assembleDebug` → APK di-sideload ke HP.

## 10. Risiko & catatan

- ESP32 hanya mendukung WiFi 2,4 GHz; firmware konek ke hotspot bernama "Poco X3 Pro" → app harus dijalankan di HP yang hotspot-nya dipakai ESP32. IP ESP32 dapat dilihat dari Serial Monitor 115200 saat boot.
- HP Xiaomi/MIUI: selain USB debugging, biasanya perlu menyalakan "Install via USB" dan "USB debugging (Security settings)" agar bisa dipasang/diuji dari Android Studio.
- Platform android-36 mungkin diunduh otomatis oleh Gradle saat build pertama (komputer ini sudah punya Android SDK + build-tools).
- Versi Compose/AGP/Kotlin: pakai rilis stabil terbaru yang kompatibel satu sama lain.
