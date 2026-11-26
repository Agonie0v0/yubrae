[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$SourceZip,

    [string]$Version = (Get-Date -Format 'yyyy.MM.dd.1'),

    [string]$Changelog = '融合 AutoAccounting 短信/通知/屏幕识别规则与智能分类词库',

    [string]$RepoOwner = 'Agonie0v0',

    [string]$RepoName = 'yubrae',

    [string]$Branch = 'main',

    [bool]$SyncAssets = $true
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$resolvedSourceZip = (Resolve-Path -LiteralPath $SourceZip).Path
if (-not (Test-Path -LiteralPath $resolvedSourceZip -PathType Leaf)) {
    throw "Source zip not found: $SourceZip"
}

$repoRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$rulesDir = Join-Path $repoRoot 'rules'
$rulesZipPath = Join-Path $rulesDir 'rules.zip'
$manifestPath = Join-Path $rulesDir 'manifest.json'
$assetsRulesDir = Join-Path $repoRoot 'app\src\main\assets\rules'

New-Item -ItemType Directory -Path $rulesDir -Force | Out-Null

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$tempZipPath = Join-Path $rulesDir ("rules.{0}.tmp.zip" -f [System.Guid]::NewGuid().ToString('N'))

$sourceArchive = [System.IO.Compression.ZipFile]::OpenRead($resolvedSourceZip)
try {
    $entryMap = [System.Collections.Generic.Dictionary[string, System.IO.Compression.ZipArchiveEntry]]::new([System.StringComparer]::Ordinal)
    foreach ($entry in $sourceArchive.Entries) {
        if ([string]::IsNullOrEmpty($entry.Name)) {
            continue
        }
        $normalizedName = ($entry.FullName -replace '\\', '/').TrimStart('/')
        $entryMap[$normalizedName] = $entry
    }

    $requiredEntries = @(
        'rule/rules.json',
        'rule/common.js',
        'rule/category.js',
        'rule/category.json'
    )
    foreach ($req in $requiredEntries) {
        if (-not $entryMap.ContainsKey($req)) {
            throw "Required entry missing in source zip: $req"
        }
    }

    $rulesEntry = $entryMap['rule/rules.json']
    $rulesReader = [System.IO.StreamReader]::new($rulesEntry.Open(), [System.Text.Encoding]::UTF8, $true)
    try {
        $rawRulesJson = $rulesReader.ReadToEnd()
    } finally {
        $rulesReader.Dispose()
    }

    $allRules = @($rawRulesJson | ConvertFrom-Json)
    $filteredRules = @(
        $allRules | Where-Object {
            ($_.ruleType -eq 'app' -and $_.ruleApp -eq 'com.android.phone') -or
            $_.ruleType -eq 'notice' -or
            $_.ruleType -eq 'ocr'
        }
    )

    if ($filteredRules.Count -eq 0) {
        throw 'Filtered rule set is empty; aborting.'
    }

    $smsCount = @($filteredRules | Where-Object { $_.ruleType -eq 'app' -and $_.ruleApp -eq 'com.android.phone' }).Count
    $noticeCount = @($filteredRules | Where-Object { $_.ruleType -eq 'notice' }).Count
    $ocrCount = @($filteredRules | Where-Object { $_.ruleType -eq 'ocr' }).Count
    $ruleCount = $filteredRules.Count

    $filteredRulesJson = (@($filteredRules) | ConvertTo-Json -AsArray -Depth 10) + "`n"

    $destArchive = [System.IO.Compression.ZipFile]::Open(
        $tempZipPath,
        [System.IO.Compression.ZipArchiveMode]::Create,
        $utf8NoBom
    )
    try {
        $writtenEntries = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)

        # 1. Write filtered rule/rules.json
        $rulesOutEntry = $destArchive.CreateEntry('rule/rules.json', [System.IO.Compression.CompressionLevel]::Optimal)
        $rulesOutStream = $rulesOutEntry.Open()
        try {
            $rulesBytes = $utf8NoBom.GetBytes($filteredRulesJson)
            $rulesOutStream.Write($rulesBytes, 0, $rulesBytes.Length)
        } finally {
            $rulesOutStream.Dispose()
        }
        [void]$writtenEntries.Add('rule/rules.json')

        # 2. Copy shared runtime scripts & category dictionary
        foreach ($sharedPath in @('rule/common.js', 'rule/category.js', 'rule/category.json')) {
            $srcEntry = $entryMap[$sharedPath]
            $dstEntry = $destArchive.CreateEntry($sharedPath, [System.IO.Compression.CompressionLevel]::Optimal)
            $srcStream = $srcEntry.Open()
            $dstStream = $dstEntry.Open()
            try {
                $srcStream.CopyTo($dstStream)
            } finally {
                $dstStream.Dispose()
                $srcStream.Dispose()
            }
            [void]$writtenEntries.Add($sharedPath)
        }

        # 3. Copy each matched rule/<path> script
        foreach ($rule in $filteredRules) {
            $relPath = ($rule.path -replace '\\', '/').TrimStart('/')
            if ([string]::IsNullOrWhiteSpace($relPath)) {
                throw "Rule '$($rule.ruleName)' has an empty path."
            }
            $entryPath = $relPath.StartsWith('rule/') ? $relPath : "rule/$relPath"
            if (-not $entryMap.ContainsKey($entryPath)) {
                throw "Referenced rule script missing in source zip: $entryPath"
            }
            if ($writtenEntries.Add($entryPath)) {
                $srcEntry = $entryMap[$entryPath]
                $dstEntry = $destArchive.CreateEntry($entryPath, [System.IO.Compression.CompressionLevel]::Optimal)
                $srcStream = $srcEntry.Open()
                $dstStream = $dstEntry.Open()
                try {
                    $srcStream.CopyTo($dstStream)
                } finally {
                    $dstStream.Dispose()
                    $srcStream.Dispose()
                }
            }
        }
    } finally {
        $destArchive.Dispose()
    }
} finally {
    $sourceArchive.Dispose()
}

Move-Item -LiteralPath $tempZipPath -Destination $rulesZipPath -Force

$sha256 = (Get-FileHash -LiteralPath $rulesZipPath -Algorithm SHA256).Hash.ToLowerInvariant()
$sizeBytes = [long](Get-Item -LiteralPath $rulesZipPath).Length
$updatedAt = [DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
$rulesUrl = "https://raw.githubusercontent.com/$RepoOwner/$RepoName/$Branch/rules/rules.zip"

$manifest = [ordered]@{
    schemaVersion = 1
    version       = $Version
    updatedAt     = $updatedAt
    minAppVersion = '1.2.2'
    rulesUrl      = $rulesUrl
    sha256        = $sha256
    sizeBytes     = $sizeBytes
    ruleCount     = $ruleCount
    smsCount      = $smsCount
    noticeCount   = $noticeCount
    ocrCount      = $ocrCount
    changelog     = $Changelog
}

$manifestJson = ($manifest | ConvertTo-Json -Depth 4) + "`n"
[System.IO.File]::WriteAllText($manifestPath, $manifestJson, $utf8NoBom)

if ($SyncAssets) {
    New-Item -ItemType Directory -Path $assetsRulesDir -Force | Out-Null
    Copy-Item -LiteralPath $rulesZipPath -Destination (Join-Path $assetsRulesDir 'builtin_rules.zip') -Force
    Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $assetsRulesDir 'builtin_manifest.json') -Force
}

Write-Output "Packed rules successfully:"
Write-Output "  rules.zip    : $rulesZipPath ($sizeBytes bytes, sha256=$sha256)"
Write-Output "  manifest.json: $manifestPath"
Write-Output "  counts       : total=$ruleCount (sms=$smsCount, notice=$noticeCount, ocr=$ocrCount)"
if ($SyncAssets) {
    Write-Output "  assets synced: $assetsRulesDir"
}
