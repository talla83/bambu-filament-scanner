# Baut die APK ohne Gradle/Android Studio: aapt2 -> javac -> d8 -> zipalign -> apksigner
# Aufruf:  pwsh -File build.ps1   (oder in PowerShell: .\build.ps1)
param(
    [int]$VersionCode = 1,
    [string]$VersionName = "1.0",
    [string]$PackageName = "",          # optional: anderer Paketname -> eigene App neben der normalen
    [string]$OutFile = "",
    [string]$Keystore = "",             # optional: anderer Keystore
    [string]$KeyDName = "CN=Bambu Filament Scanner",
    [switch]$Debuggable,                # android:debuggable=true
    [switch]$V2Only                     # nur APK-Signatur v2 (wie Gradle-Debug-Builds)
)
$ErrorActionPreference = 'Stop'

$Root = $PSScriptRoot
$T    = Join-Path $env:USERPROFILE '.bambu-toolchain'
$env:JAVA_HOME = (Get-ChildItem $T -Directory -Filter 'jdk-17*' | Select-Object -First 1).FullName
$BT   = "$T\sdk\build-tools\34.0.0"
$AJ   = "$T\sdk\platforms\android-34\android.jar"
$B    = "$Root\build"
$Out  = if ($OutFile) { $OutFile } else { "$Root\BambuFilamentScanner.apk" }
$rename = if ($PackageName) { @('--rename-manifest-package', $PackageName) } else { @() }
$Ks   = if ($Keystore) { $Keystore } else { "$Root\keystore\bambu-scanner.jks" }
if ($Debuggable) { $rename += '--debug-mode' }
$sigOpts = if ($V2Only) { @('--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'false') } else { @() }

function Run([string]$exe) {
    & $exe @args
    if ($LASTEXITCODE -ne 0) { throw "Fehlgeschlagen: $exe" }
}

if (Test-Path $B) { Remove-Item -Recurse -Force $B }
New-Item -ItemType Directory -Force "$B\gen", "$B\classes" | Out-Null

Write-Host "1/6 Ressourcen kompilieren" -ForegroundColor Green
Run "$BT\aapt2.exe" compile --dir "$Root\res" -o "$B\res.zip"
Run "$BT\aapt2.exe" link -I $AJ --manifest "$Root\AndroidManifest.xml" -o "$B\base.apk" "$B\res.zip" `
    --java "$B\gen" --min-sdk-version 26 --target-sdk-version 34 `
    --version-code $VersionCode --version-name $VersionName @rename

Write-Host "2/6 Java kompilieren" -ForegroundColor Green
$srcs = Get-ChildItem "$Root\src", "$B\gen" -Recurse -Filter *.java | ForEach-Object FullName
Run "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 '-Xlint:-options' `
    -bootclasspath "$AJ;$BT\core-lambda-stubs.jar" -d "$B\classes" @srcs

Write-Host "3/6 DEX erzeugen" -ForegroundColor Green
$classes = Get-ChildItem "$B\classes" -Recurse -Filter *.class | ForEach-Object FullName
Run "$BT\d8.bat" --release --lib $AJ --min-api 26 --output $B @classes

Write-Host "4/6 APK packen" -ForegroundColor Green
Copy-Item "$B\base.apk" "$B\unsigned.apk"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open("$B\unsigned.apk", 'Update')
[void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$B\classes.dex", 'classes.dex')
$zip.Dispose()
Run "$BT\zipalign.exe" -p -f 4 "$B\unsigned.apk" "$B\aligned.apk"

Write-Host "5/6 Signieren" -ForegroundColor Green
if (-not (Test-Path $Ks)) {
    New-Item -ItemType Directory -Force (Split-Path $Ks) | Out-Null
    Run "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore $Ks -storepass android -keypass android `
        -alias app -keyalg RSA -keysize 2048 -validity 10000 -dname $KeyDName
}
Run "$BT\apksigner.bat" sign @sigOpts --ks $Ks --ks-pass pass:android --key-pass pass:android --out $Out "$B\aligned.apk"

Write-Host "6/6 Prüfen" -ForegroundColor Green
Run "$BT\apksigner.bat" verify $Out
Write-Host ("Fertig: {0}  ({1:N0} KB)" -f $Out, ((Get-Item $Out).Length / 1KB)) -ForegroundColor Green
