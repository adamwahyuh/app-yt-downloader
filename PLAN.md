# Rencana: Aplikasi Android YT Downloader

Tujuan: aplikasi Android ringan (.apk). Pengguna menempel link YouTube, memilih **Video** atau **Audio** beserta kualitasnya, lalu download.

---

## 1. Keputusan yang perlu dipilih

### Mesin pengambil video

| | **A. NewPipeExtractor** (disarankan) | **B. yt-dlp** (lewat youtubedl-android) |
|---|---|---|
| Biaya | Gratis, GPL-3.0 | Gratis. yt-dlp: Unlicense, youtubedl-android: GPL-3.0 |
| Ukuran APK | ± 5–8 MB | ± 40–70 MB (membawa Python + FFmpeg) |
| Kecepatan buka app | Cepat | Lebih lambat (Python harus disiapkan dulu) |
| Format audio | **.m4a** (AAC asli dari YouTube, tanpa konversi) | **.mp3** asli (dikonversi FFmpeg) |
| Gabung video+audio ≥720p | `MediaMuxer` bawaan Android | FFmpeg |
| Kalau YouTube berubah | Update library, lalu build ulang APK | Bisa update yt-dlp dari dalam app |

Untuk .mp3 asli di opsi A: decode dengan `MediaCodec` lalu encode dengan LAME (JNI). APK bertambah ± 1–2 MB dan prosesnya lebih lambat.

**Pilihan:** [ ] A  [x] B — yt-dlp

Format audio: **.mp3** (dikonversi FFmpeg bawaan youtubedl-android).
Ukuran APK bisa ditekan dengan build per-ABI (`arm64-v8a` saja) menjadi ± 30–40 MB.

---

## 2. Teknologi

- **Bahasa:** Kotlin
- **UI:** XML Views, 1 Activity (lebih ringan daripada Jetpack Compose)
- **Download:** OkHttp + Foreground Service (progress muncul di notifikasi)
- **Simpan file:** MediaStore ke `Download/` (video) dan `Music/` (audio). Di Android 10+ tidak perlu izin storage.
- **minSdk** 24, **targetSdk** 35

---

## 3. Fitur MVP

1. Kolom link. Saat app dibuka, link dari clipboard otomatis ditempel.
2. App muncul di menu **Share** YouTube (Share → pilih app ini, tanpa perlu copy link).
3. Tombol **Cek** untuk menampilkan judul, thumbnail, dan durasi.
4. Pilihan **Video / Audio**.
5. Dropdown kualitas sesuai yang tersedia:
   - Video: 144p – 1080p (atau lebih)
   - Audio: 48 / 128 / 160 kbps
6. Tombol **Download**, progress di notifikasi, file langsung muncul di galeri atau pemutar musik.

**Tidak termasuk (sengaja dilewati):** riwayat download, playlist, antrian banyak file, login, tema gelap/terang.

---

## 4. Struktur project

```
app/src/main/
├─ AndroidManifest.xml          # INTERNET, notifikasi, foreground service, intent-filter SEND text/plain
├─ java/.../MainActivity.kt     # UI: input link, pilih tipe & kualitas
├─ java/.../Extractor.kt        # ambil info video & daftar stream
├─ java/.../DownloadService.kt  # download + notifikasi progress
├─ java/.../Muxer.kt            # gabung video+audio (khusus opsi A)
└─ res/layout/activity_main.xml
```

---

## 5. Alur kerja

```
Link
 └─ ambil info stream
     ├─ Audio → pilih bitrate → download → simpan ke Music/
     └─ Video
         ├─ ≤360p (progressive, video+audio jadi satu) → download langsung
         └─ ≥720p (DASH, video & audio terpisah) → download keduanya → gabung → .mp4
```

---

## 6. Tahapan pengerjaan

1. **Setup:** buat project Gradle (Kotlin), tambahkan dependency sesuai pilihan A/B.
2. **Ekstraksi:** tempel link, lalu tampilkan judul dan daftar kualitas. Uji dengan link biasa, `youtu.be`, dan Shorts.
3. **Download audio:** simpan ke `Music/`, lengkap dengan notifikasi progress.
4. **Download video:** progressive dulu, lalu DASH + muxing.
5. **Menu Share + auto-paste clipboard.**
6. **Build APK:** jalankan `./gradlew assembleRelease` (R8/minify aktif, ditandatangani keystore sendiri).
   Hasil: `app/build/outputs/apk/release/app-release.apk`

---

## 7. Kebutuhan di komputer

- JDK 17
- Android SDK (paling mudah lewat Android Studio)
- HP Android untuk uji coba (aktifkan *Install dari sumber tidak dikenal*)

---

## 8. Risiko

- **Download bisa berhenti berfungsi** saat YouTube mengubah sistemnya, dan perlu update library. Ini terjadi pada semua aplikasi downloader YouTube.
- **Melanggar Ketentuan Layanan YouTube.** App ini tidak bisa masuk Play Store, hanya untuk sideload. Gunakan untuk konten milik sendiri atau yang memang boleh diunduh.
- **Lisensi GPL-3.0:** tidak masalah untuk dipakai sendiri. Kalau APK dibagikan ke orang lain, kode sumber wajib dibuka.
