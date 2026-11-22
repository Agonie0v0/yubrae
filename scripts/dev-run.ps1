<#
.SYNOPSIS
    棱账 (KairoLedger) 构建、安装并自动重启小米 14 应用的开发脚本（默认构建 R8 优化性能包）。
.EXAMPLE
    .\scripts\dev-run.ps1
    .\scripts\dev-run.ps1 -Debug
    .\scripts\dev-run.ps1 -Watch
    .\scripts\dev-run.ps1 -Device d05c6533 -SkipTests -EnsureAccessibility
#>
param(
    [switch]$Watch,
    [switch]$Debug,
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
$VariantName = if ($Debug) { 'debug' } else { 'perf' }
$AssembleTask = if ($Debug) { 'assembleDebug' } else { 'assemblePerf' }
$ApkPath = Join-Path $ProjectRoot "app\build\outputs\apk\$VariantName\app-$VariantName.apk"

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
    $hasRoot = ((& $AdbExe -s $serial shell "su -c id 2>/dev/null" 2>$null) -join '') -match 'uid=0'
    for ($attempt = 1; $attempt -le 2; $attempt++) {
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

        if ($hasRoot) {
            $escaped = $joined -replace "'", "'\\''"
            $putSh = (@"
settings put secure enabled_accessibility_services '$escaped'
settings put secure accessibility_enabled 1
"@) -replace "`r`n", "`n"
            $putSh | & $AdbExe -s $serial shell "su -c sh" 2>$null | Out-Null
        } else {
            & $AdbExe -s $serial shell settings put secure enabled_accessibility_services "$joined" 2>$null | Out-Null
            & $AdbExe -s $serial shell settings put secure accessibility_enabled 1 2>$null | Out-Null
        }

        Start-Sleep -Milliseconds 1000
        $verifyRaw = (& $AdbExe -s $serial shell settings get secure enabled_accessibility_services 2>$null) -join ''
        if (Test-HasKairoA11y (Get-A11yEntries $verifyRaw)) {
            Write-Host "[KairoLedger] 已无损保持并启用无障碍页面监听服务。" -ForegroundColor Green
            return
        }
        if ($attempt -eq 1) {
            Write-Host "[KairoLedger] 检测到无障碍列表被系统清除，正在自动重试恢复..." -ForegroundColor Yellow
        }
    }
    Write-Host "[KairoLedger] 警告：重试后 enabled_accessibility_services 仍未保留 $A11yComponent。" -ForegroundColor Yellow
}

function Ensure-HyperOsKeepAlive([string]$serial) {
    $suCheck = (& $AdbExe -s $serial shell "su -c id 2>/dev/null" 2>$null) -join ''
    if ($suCheck -notmatch 'uid=0') {
        Write-Host "[KairoLedger] 设备未授予 ADB Shell Root，跳过 HyperOS 防冻结与 AppOps 自动放行。" -ForegroundColor Yellow
        return
    }

    $rawSh = @'
PKG='__PKG__'
dumpsys deviceidle whitelist +"$PKG" >/dev/null 2>&1 || true
am set-standby-bucket "$PKG" active >/dev/null 2>&1 || true

# 1. HyperOS 3 PowerKeeper 省电策略设为无限制 (noRestrict)
PK_OUT=$(content query --uri content://com.miui.powerkeeper.configure/userTable --where "pkgName='${PKG}' AND userId=0" 2>&1 || true)
NOW_S=$(date +%s 2>/dev/null || echo 0)
case "$NOW_S" in ''|*[!0-9]*) NOW_MS=0 ;; *) NOW_MS="${NOW_S}000" ;; esac
case "$PK_OUT" in
    *Error*|*Exception*|*"Could not find provider"*) ;;
    *"Row:"*)
        case "$PK_OUT" in
            *"bgControl=noRestrict"*) ;;
            *) content update --uri content://com.miui.powerkeeper.configure/userTable --where "pkgName='${PKG}' AND userId=0" --bind bgControl:s:noRestrict --bind lastConfigured:l:"$NOW_MS" >/dev/null 2>&1 || true ;;
        esac
        ;;
    *"No result found"*)
        content insert --uri content://com.miui.powerkeeper.configure/userTable --bind userId:i:0 --bind pkgName:s:"$PKG" --bind bgControl:s:noRestrict --bind lastConfigured:l:"$NOW_MS" >/dev/null 2>&1 || true
        ;;
esac

# 2. 无损将应用包名加入 immobulus_mode_switch_restrict 的 _allowlist_
IMMOB_CUR=$(settings get secure immobulus_mode_switch_restrict 2>/dev/null | tr -d '\r\n')
case "$IMMOB_CUR" in
    *_allowlist_*)
        case "$IMMOB_CUR" in
            *_wakeuplist_*) ALLOW_PART=${IMMOB_CUR%%_wakeuplist_*} ;;
            *) ALLOW_PART=$IMMOB_CUR ;;
        esac
        ALLOW_SEG=${ALLOW_PART#*_allowlist_}
        NEED_ADD=1
        case "$ALLOW_SEG" in
            "$PKG"|"${PKG}_"*|*"_${PKG}"|*"_${PKG}_"*) NEED_ADD=0 ;;
        esac
        if [ "$NEED_ADD" = "1" ]; then
            HEAD_PART=${IMMOB_CUR%%_allowlist_*}
            TAIL_PART=${IMMOB_CUR#*_allowlist_}
            if [ -z "$TAIL_PART" ]; then
                IMMOB_NEW="${HEAD_PART}_allowlist_${PKG}"
            else
                case "$TAIL_PART" in
                    _*) IMMOB_NEW="${HEAD_PART}_allowlist_${PKG}${TAIL_PART}" ;;
                    *) IMMOB_NEW="${HEAD_PART}_allowlist_${PKG}_${TAIL_PART}" ;;
                esac
            fi
            settings put secure immobulus_mode_switch_restrict "$IMMOB_NEW" >/dev/null 2>&1 || true
        fi
        ;;
esac

# 3. 批量放行后台保活 / 悬浮窗 / MIUI 后台弹出与锁屏展示 AppOps
for OP in SYSTEM_ALERT_WINDOW RUN_IN_BACKGROUND RUN_ANY_IN_BACKGROUND ACCESS_RESTRICTED_SETTINGS RECEIVE_SENSITIVE_NOTIFICATIONS 10008 10021; do
    appops set "$PKG" "$OP" allow >/dev/null 2>&1 || true
done

# 4. 查询当前 UID 并立即通过 Greezer 与 cgroup 解冻兜底
KAIRO_UID=$(cmd package list packages -U "$PKG" 2>/dev/null | sed -n 's/.*uid:\([0-9][0-9]*\).*/\1/p' | head -n 1)
if [ -n "$KAIRO_UID" ] && [ "$KAIRO_UID" -gt 0 ] 2>/dev/null; then
    cmd greezer thuid "$KAIRO_UID" 1000 >/dev/null 2>&1 || true
    for CF in "/sys/fs/cgroup/apps/uid_${KAIRO_UID}/cgroup.freeze" /sys/fs/cgroup/apps/uid_${KAIRO_UID}/pid_*/cgroup.freeze; do
        [ -w "$CF" ] && printf '0\n' > "$CF" 2>/dev/null || true
    done
fi
echo "__HYPEROS_KEEPALIVE_DONE__"
'@
    $lfSh = ($rawSh -replace '__PKG__', $PackageName) -replace "`r`n", "`n"
    $out = ($lfSh | & $AdbExe -s $serial shell "su -c sh" 2>&1) -join "`n"
    if ($out -match '__HYPEROS_KEEPALIVE_DONE__') {
        Write-Host "[KairoLedger] 已施加 HyperOS 3 防冻结保护 (noRestrict + immobulus + AppOps + 解冻)。" -ForegroundColor Green
    } else {
        Write-Host "[KairoLedger] HyperOS 3 防冻结脚本未确认完成: $out" -ForegroundColor Yellow
    }
}

function Invoke-BuildAndRestart {
    Push-Location $ProjectRoot
    try {
        [string[]]$tasks = if ($SkipTests) { @($AssembleTask) } else { @('testDebugUnitTest', $AssembleTask) }
        Write-Host "[KairoLedger] 正在编译 ($VariantName): $($tasks -join ' ') ..." -ForegroundColor Cyan
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
            Ensure-HyperOsKeepAlive $target
            Start-Sleep -Milliseconds 1500
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
