# Bambu Filament Scanner

Android-App (ab Android 8), die die NFC-Tags (MIFARE Classic 1K) von Bambu-Lab-Spulen liest und anzeigt.

## Bauen

Benötigt nur die Toolchain unter `%USERPROFILE%\.bambu-toolchain` (JDK 17, Android build-tools 34, Plattform android-34), kein Gradle und kein Android Studio.

```powershell
.\build.ps1                       # -> BambuFilamentScanner.apk
.\build.ps1 -VersionCode 2 -VersionName 1.1
```

`keystore\bambu-scanner.jks` nicht löschen: Updates müssen mit demselben Schlüssel signiert sein, sonst muss die App vorher deinstalliert werden.

## Aufbau

| Datei | Inhalt |
|---|---|
| `src/.../BambuTag.java` | HKDF-Schlüsselableitung aus der UID, Parsing der Blöcke |
| `src/.../TagReader.java` | MIFARE-Classic-Auslesen (alle 16 Sektoren) |
| `src/.../MainActivity.java` | UI (Scannen / Verlauf / Details) |
| `src/.../SpoolView.java` | Spulen-Grafik |
| `src/.../History.java` | Lokaler Verlauf |
| `src/.../ColorNames.java` | Farbnamen-Tabelle |

Tag-Format: https://github.com/Bambu-Research-Group/RFID-Tag-Guide
