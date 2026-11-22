<#
.SYNOPSIS
    棱账 (KairoLedger) 构建、安装并自动重启小米 14 应用的开发脚本。
.EXAMPLE
    .\scripts\dev-run.ps1
    .\scripts\dev-run.ps1 -Watch
    .\scripts\dev-run.ps1 -Device d05c6533 -SkipTests -EnsureAccessibility
#>
param(
    [switch]$Watch,
    [string]$Device = '',
    [switch]$SkipTests,
    [switch]$EnsureAccessibility
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$PackageName = 'com.kairoledger.app.debug'
$MainActivity = 'com.kairoledger.app.MainActivity'
$A11yClass = 'com.kairoledger.app.capture.screen.KairoAccessibilityService'
$A11yShortClass = '.capture.screen.KairoAccessibilityService'
$A11yComponent = "$PackageName/$A11yClass"
$ApkPath = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'

if (-not $env:JAVA_HOME -and (Test-Path 'D:\DevTools\jdk-21\bin\java.exe')) {
    $env:JAVA_HOME = 'D:\DevTools\jdk-21'
}
if (-not $env:ANDROID_HOME -and (Test-Path 'D:\DevTools\android-sdk')) {
    $env:ANDROID_HOME = 'D:\DevTools\android-sdk'
    $env:ANDROID_SDK_ROOT = 'D:\DevTools\android-sdk'
}
if (-not $env:GRADLE_USER_HOME -and (Test-Path 'D:\DevTools\.gradle')) {
    $env:GRADLE_USER_HOME = 'D:\DevTools\.gradle'
}

$AdbExe = if ($env:ANDROID_HOME -and (Test-Path "$env:ANDROID_HOME\platform-tools\adb.exe")) {
    "$env:ANDROID_HOME\platform-tools\adb.exe"
} else {
    (Get-Command adb -ErrorAction SilentlyContinue)?.Source
}

function Get-TargetDevice {
    if (-not $AdbExe) { return $null }
    $lines = & $AdbExe devices | Select-String '\tdevice$'
    if (-not $lines) { return $null }
    if ($Device) { return $Device }
    return ($lines[0].Line -split '\s+')[0]
}

function Get-A11yEntries([string]$raw) {
    if ([string]::IsNullOrWhiteSpace($raw) -or $raw.Trim() -eq 'null') { return @() }
    return @($raw.Trim() -split ':' | ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -ne 'null' })
}

function Test-HasKairoA11y([string[]]$entries) {
    foreach ($e in $entries) {
        if ($e -eq $A11yComponent -or $e -eq "$PackageName/$A11yShortClass") {
            return $true
        }
    }
    return $false
}

function Restore-KairoAccessibility([string]$serial) {
    $currentRaw = (& $AdbExe -s $serial shell settings get secure enabled_accessibility_services 2>$null) -join ''
    $existing = Get-A11yEntries $currentRaw
    $merged = [System.Collections.Generic.List[string]]::new()
    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($entry in $existing) {
        $normalized = if ($entry -eq "$PackageName/$A11yShortClass") { $A11yComponent } else { $entry }
        if ($seen.Add($normalized)) {
            $merged.Add($normalized)
        }
    }
    if ($seen.Add($A11yComponent)) {
        $merged.Add($A11yComponent)
    }
    $joined = $merged -join ':'
    & $AdbExe -s $serial shell settings put secure enabled_accessibility_services "$joined" 2>$null | Out-Null
    & $AdbExe -s $serial shell settings put secure accessibility_enabled 1 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        & $AdbExe -s $serial shell "su -c 'settings put secure enabled_accessibility_services $joined && settings put secure accessibility_enabled 1'" | Out-Null
    }
    Write-Host "[KairoLedger] 已无损保持并启用无障碍页面监听服务。" -ForegroundColor Green
}

function Invoke-BuildAndRestart {
    Push-Location $ProjectRoot
    try {
        [string[]]$tasks = if ($SkipTests) { @('assembleDebug') } else { @('testDebugUnitTest', 'assembleDebug') }
        Write-Host "[KairoLedger] 正在编译: $($tasks -join ' ') ..." -ForegroundColor Cyan
        & (Join-Path $ProjectRoot 'gradlew.bat') $tasks
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[KairoLedger] 构建失败 (exit=$LASTEXITCODE)" -ForegroundColor Red
            return $false
        }
        if (-not (Test-Path $ApkPath)) {
            Write-Host "[KairoLedger] 未找到输出 APK: $ApkPath" -ForegroundColor Red
            return $false
        }

        $target = Get-TargetDevice
        if (-not $target) {
            Write-Host "[KairoLedger] 构建成功！未检测到在线 ADB 设备（请连接小米 14 并开启 USB/无线调试）。" -ForegroundColor Yellow
            Write-Host "[KairoLedger] APK 路径: $ApkPath" -ForegroundColor Green
            return $true
        }

        $beforeRaw = (& $AdbExe -s $target shell settings get secure enabled_accessibility_services 2>$null) -join ''
        $wasA11yEnabled = Test-HasKairoA11y (Get-A11yEntries $beforeRaw)

        Write-Host "[KairoLedger] 正在安装到设备 $target ..." -ForegroundColor Cyan
        & $AdbExe -s $target install -r -d $ApkPath
        if ($LASTEXITCODE -ne 0) { throw "adb install 失败" }

        Write-Host "[KairoLedger] 正在强停并自动重启 $PackageName ..." -ForegroundColor Cyan
        & $AdbExe -s $target shell am force-stop $PackageName | Out-Null
        & $AdbExe -s $target shell am start -n "$PackageName/$MainActivity" | Out-Null

        if ($wasA11yEnabled -or $EnsureAccessibility) {
            Restore-KairoAccessibility $target
        }

        Write-Host "[KairoLedger] 已在设备上重启应用完成！" -ForegroundColor Green
        return $true
    } finally {
        Pop-Location
    }
}

$ok = Invoke-BuildAndRestart
if (-not $Watch) {
    if (-not $ok) { exit 1 }
    exit 0
}

Write-Host "`n[KairoLedger] 已进入文件监听自动热重启模式（监听 app/src 与 gradle 文件变化，Ctrl+C 退出）..." -ForegroundColor Magenta
$watchRoot = Join-Path $ProjectRoot 'app\src'
$lastSignature = ''
while ($true) {
    Start-Sleep -Seconds 2
    $files = Get-ChildItem -Path $watchRoot -Recurse -File -Include *.kt,*.xml,*.kts -ErrorAction SilentlyContinue
    $sig = ($files | Sort-Object FullName | ForEach-Object { "$($_.FullName):$($_.LastWriteTimeUtc.Ticks)" }) -join '|'
    if ($lastSignature -and $sig -ne $lastSignature) {
        Write-Host "`n[KairoLedger] 检测到源码修改，触发自动构建与重启..." -ForegroundColor Yellow
        Invoke-BuildAndRestart | Out-Null
    }
    $lastSignature = $sig
}
