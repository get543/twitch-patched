Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Ensure JDK 21+ is in PATH
if (Test-Path "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin") {
    $env:PATH = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin;$env:PATH"
}

$projectRoot = [IO.Path]::GetFullPath($PSScriptRoot)

Write-Host "Building patches..." -ForegroundColor Yellow

# 1. Check source
if (Test-Path "$projectRoot/scripts/check-source.ps1") {
    & "$projectRoot/scripts/check-source.ps1"
}

# 2. Build the patch bundle (.mpp) and generate patch metadata
& "$projectRoot/scripts/build.ps1"

# 3. Locate generated .mpp file
$mppFiles = Get-ChildItem -Path "$projectRoot/patches/build/libs/patches-*.mpp"
if (-not $mppFiles) {
    throw "No .mpp patch file found in patches/build/libs/"
}
$sourceMpp = (Resolve-Path $mppFiles[0].FullName).Path

# Copy patch bundle to root directory
Copy-Item -Path $sourceMpp -Destination "$projectRoot/patches.mpp" -Force
Write-Host "Patch bundle copied to .\patches.mpp" -ForegroundColor Green

# 4. Check for twitch input APK/APKM
$twitchApkFile = Get-ChildItem -Path $projectRoot -Filter "twitch.apkm" | Select-Object -First 1
if (-not $twitchApkFile) {
    $twitchApkFile = Get-ChildItem -Path $projectRoot -Filter "twitch.apk" | Select-Object -First 1
}

if (-not $twitchApkFile) {
    Write-Host "`nNo twitch input file (e.g. twitch.apkm) found in root directory." -ForegroundColor Yellow
    Write-Host "Patch bundle built successfully at .\patches.mpp!" -ForegroundColor Green
    exit 0
}

$inputApkPath = (Resolve-Path $twitchApkFile.FullName).Path

Write-Host "`nApplying patches on $($twitchApkFile.Name)..." -ForegroundColor Yellow

$patchNames = @(
    "Landscape edge-to-edge playback",
    "Clip-style chat animation",
    "Auto-claim bonus channel points",
    "BTTV, FFZ and 7TV emotes",
    "Block client-requested ads",
    "Block stream ads",
    "Hide Turbo promotions",
    "Hide feed and display ads",
    "Hide subscription discount banners",
    "Reload stream"
)

& "$projectRoot/scripts/patch.ps1" -InputApk $inputApkPath -Patch $patchNames

# Locate generated patched APK from latest run
$latestRun = Get-ChildItem -Path "$projectRoot/.local/runs" -Directory | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($latestRun) {
    $patchedApk = Get-ChildItem -Path $latestRun.FullName -Filter "*patched*.apk" | Select-Object -First 1
    if ($patchedApk) {
        Copy-Item -Path $patchedApk.FullName -Destination "$projectRoot/twitch_patched.apk" -Force
        Write-Host "`nPatched APK created successfully at .\twitch_patched.apk!" -ForegroundColor Green
    }
}
