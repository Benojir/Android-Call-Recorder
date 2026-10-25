param(
    [Parameter(Mandatory=$true)][string]$ApkPath,
    [string]$OutputDirectory = "$PSScriptRoot\..\build\distributions",
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [switch]$DebugBuild
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath "$PSScriptRoot\..").Path
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
if (!$AndroidSdk) { $AndroidSdk = "$env:LOCALAPPDATA\Android\Sdk" }
$buildTools = Get-ChildItem -LiteralPath "$AndroidSdk\build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1
if (!$buildTools) { throw 'Android SDK build-tools are required to validate the APK.' }
$badging = & "$($buildTools.FullName)\aapt.exe" dump badging $apk
if ($LASTEXITCODE -ne 0 -or $badging[0] -notmatch "name='zoro\.benojir\.callrecorder'.*versionCode='6'") {
    throw 'Expected Call Recorder v2.0 (versionCode 6). Refusing to package an old or unrelated APK.'
}
& "$($buildTools.FullName)\apksigner.bat" verify $apk
if ($LASTEXITCODE -ne 0) { throw 'APK must be signed before it can be installed as a privileged application.' }
$isDebuggable = [bool]($badging | Select-String '^application-debuggable')
if ($isDebuggable -and !$DebugBuild) { throw 'Debug APK detected. Pass -DebugBuild to label the test package explicitly.' }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$outputRoot = (Resolve-Path -LiteralPath $OutputDirectory).Path
$suffix = if ($DebugBuild) { '-debug' } else { '' }
$zipPath = Join-Path $outputRoot "Call-Recorder-v2.0-universal$suffix.zip"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$stream = [IO.File]::Open($zipPath, [IO.FileMode]::Create)
$archive = New-Object IO.Compression.ZipArchive($stream, [IO.Compression.ZipArchiveMode]::Create)
try {
    $moduleRoot = Join-Path $projectRoot 'magisk-module'
    foreach ($file in Get-ChildItem -LiteralPath $moduleRoot -File -Recurse) {
        $entryName = $file.FullName.Substring($moduleRoot.Length + 1).Replace('\', '/')
        if ($entryName.EndsWith('.apk')) { continue } # Never ship the historical checked-in APK.
        $entry = $archive.CreateEntry($entryName, [IO.Compression.CompressionLevel]::Optimal)
        $target = $entry.Open()
        try {
            if ($entryName.EndsWith('.sh') -or $entryName.EndsWith('.prop') -or $entryName.EndsWith('update-binary') -or $entryName.EndsWith('updater-script')) {
                $bytes = [Text.Encoding]::UTF8.GetBytes([IO.File]::ReadAllText($file.FullName).Replace("`r`n", "`n"))
            } else { $bytes = [IO.File]::ReadAllBytes($file.FullName) }
            $target.Write($bytes, 0, $bytes.Length)
        } finally { $target.Dispose() }
    }
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $apk,
        'system/priv-app/zoro.benojir.callrecorder/app-release.apk', [IO.Compression.CompressionLevel]::Optimal) | Out-Null
} finally { $archive.Dispose(); $stream.Dispose() }
Copy-Item -LiteralPath $apk -Destination (Join-Path $outputRoot "Call-Recorder-v2.0$suffix.apk") -Force
Write-Output "Created $zipPath"
