# Builds the EngQuest Teacher native app: runs the unit tests, then builds the APK.
#   pwsh -File native-teacher/build.ps1            -> tests + debug APK
#   pwsh -File native-teacher/build.ps1 -Release   -> tests + signed release APK
param([switch]$Release)

$ErrorActionPreference = 'Stop'

$env:JAVA_HOME = 'D:\android-dev\jdk'
$env:ANDROID_HOME = 'D:\android-dev\android-sdk'
$env:ANDROID_SDK_ROOT = 'D:\android-dev\android-sdk'
$env:GRADLE_USER_HOME = 'D:\android-dev\.gradle'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Build output lives on D:. The build/ folders here are junctions pointing there.
$outRoot = 'D:\android-dev\build\native-teacher'
foreach ($name in @('root', 'app')) {
    $link = if ($name -eq 'root') { Join-Path $PSScriptRoot 'build' } else { Join-Path $PSScriptRoot 'app\build' }
    $target = Join-Path $outRoot $name
    New-Item -ItemType Directory -Force $target | Out-Null
    $item = Get-Item $link -Force -ErrorAction SilentlyContinue
    if ($item -and $item.LinkType -ne 'Junction') { Remove-Item -Recurse -Force $link; $item = $null }
    if (-not $item) { New-Item -ItemType Junction -Path $link -Target $target | Out-Null }
}

$gradlew = Join-Path $PSScriptRoot 'gradlew.bat'
$assemble = if ($Release) { 'assembleRelease' } else { 'assembleDebug' }

& $gradlew -p $PSScriptRoot testDebugUnitTest $assemble
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$variant = if ($Release) { 'release' } else { 'debug' }
Write-Host "APK folder: $outRoot\app\outputs\apk\$variant"
