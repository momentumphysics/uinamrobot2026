# Intellink.py — proyek Android

Aplikasi Android native (Kotlin + Jetpack Compose) untuk robot ESP32:
menyambung ke `ws://<ip>:81` dan mengirim paket kontrol 8 byte — protokol
yang sama persis dengan `controller.html`. Firmware `vehicle_control_ws.ino`
dan `controller.html` tidak diubah.

## Prasyarat

- **Android Studio** (versi mesin pengembang: 2026.2.1).
- **Gradle JDK 17–23 khusus untuk IDE.** JBR bawaan Android Studio
  (Java 25) ditolak saat sinkronisasi: *"Current JDK version 25 … is not
  fully supported. Kotlin 2.2.10 supports version 23 and lower."*
  Unduh JDK 17 sekali saja:

  `File` → `Settings` → `Build, Execution, Deployment` → `Build Tools` →
  `Gradle` → **Gradle JDK** → **`Download JDK…`** → pilih **versi 17**
  (vendor bebas, mis. JetBrains Runtime / Adoptium Temurin) → `OK`,
  lalu **Sync** ulang.

- SDK Platform 35 (diunduh otomatis saat sync pertama bila belum ada).

> Build dari terminal tidak terpengaruh — CLI tetap memakai JBR 25 dan
> sudah terbukti (lihat bagian Terminal).

## Membuka proyek

Buka **folder `android/`** ini di Android Studio — bukan folder akar
repo — karena akar proyek Gradle (`settings.gradle.kts`) ada di sini.
Konfigurasi run/test di folder `.run/` akan muncul otomatis di toolbar
sebagai pilihan konfigurasi.

## Menjalankan aplikasi

Siapkan target di sebelah tombol Run:

- **Emulator:** pilih AVD yang sudah ada (mis. `Medium_Phone`), atau buat
  baru lewat Device Manager.
- **HP fisik:** aktifkan Opsi Pengembang + USB debugging. Khusus MIUI
  (Poco X3 Pro): nyalakan juga **"Install via USB"** dan
  **"USB debugging (Security settings)"** bila `adb install` ditolak.

Pilih konfigurasi **"Jalankan di perangkat (app)"** → **Run ▶**.
Android Studio akan build, memasang, dan membuka aplikasi.

> Bila konfigurasi itu mengeluh modul tidak ditemukan: `Run` →
> `Edit Configurations…` → pilih modul **`app`** pada dropdown **Module**
> (sekali saja; hanya terjadi bila penamaan modul berbeda antar versi AS).

## Menjalankan unit test

Dua cara:

1. **Per kelas/fungsi** (paling cepat): buka
   `app/src/test/java/id/uinam/intellink/…`, klik ikon ▶ di samping nama
   kelas/fungsi lalu Run/Debug.
2. **Semua sekaligus:** pilih konfigurasi **"Uji unit (semua tes)"** →
   **Run ▶** (menjalankan `:app:testDebugUnitTest`).

Saat ini **42 tes** hijau: encoder paket (golden frame), RobotClient
(MockWebServer sungguhan), ViewModel (ticker/stop/background),
matematika joystick, dan gerak servo (defleksi + laju). Hasil XML:
`app/build/test-results/testDebugUnitTest/`.

## Merakit APK

Konfigurasi **"Rakit APK debug"** → **Run ▶**, atau lewat menu
`Build` → `Build App Bundle(s) / APK(s)` → `Build APK(s)`.
Hasil: `app/build/outputs/apk/debug/app-debug.apk` (± 10,4 MB).

## Terminal (opsional)

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest     # semua tes
.\gradlew.bat :app:assembleDebug         # rakit APK debug
.\gradlew.bat :app:installDebug          # pasang ke perangkat adb aktif
```

## Uji lapangan (checklist singkat)

HP dan ESP32 harus berada di jaringan yang sama (HP menjadi hotspot;
IP ESP32 dari Serial Monitor 115200 saat boot):

1. Isi IP → selesai → status **Terhubung** (hijau) dalam ~2 detik.
2. Lingkaran **CAPIT** (BUKA/TUTUP) dan **LENGAN** (NAIK/TURUN)
   menggerakkan servo yang benar; lepas kenop → sudut berhenti di posisi itu.
3. Joystick: atas = maju, bawah = mundur, kanan/kiri = belok.
4. Lepas joystick → robot berhenti seketika; kenop kembali ke tengah.
5. STOP saat robot jalan → drive berhenti seketika, gerak servo batal (+ getaran).
6. Matikan ESP32 → status merah; hidupkan → tersambung otomatis (~2–3 detik).
7. Pindah app / kunci layar saat robot jalan → robot berhenti.
8. Layar tidak mati selama aplikasi di depan.
9. Jauhkan HP (sinyal lemah) → failsafe firmware menghentikan robot;
   setelah sinyal kembali robot **tidak melompat** (perintah drive
   dinolkan otomatis saat koneksi putus).
