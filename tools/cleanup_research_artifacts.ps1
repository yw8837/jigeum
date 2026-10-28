param(
    [Parameter(Mandatory = $true)][string]$PlanPath,
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent)).TrimEnd('\')
$primaryRoot = 'C:\Users\최용우\Documents\Codex\2026-09-29\plugin-browser-openai-bundled-mention-tab'
$planItem = Get-Item -LiteralPath $PlanPath
$plan = Get-Content -LiteralPath $planItem.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
$runDir = Split-Path $planItem.FullName -Parent
if (-not $runDir.StartsWith($repoRoot + '\work\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The cleanup plan must be inside this checkout work directory.'
}
$roots = @($repoRoot, $primaryRoot) | Select-Object -Unique
$allowedModels = @(
    'work\ondevice-av\model\gemma-4-E2B-it.litertlm',
    'work\grounded-text-probe\qwen17-model\Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm',
    'work\speech-maturity-reconstruction\bootstrap\checkpoint_best.pt',
    'work\speech-maturity-checkpoint-audit\weights\wav2vec_asr.ckpt',
    'work\speech-maturity-checkpoint-audit\weights\CKPT+2025-02-08+21-43-25+00\wav2vec2.ckpt',
    'work\ast-portability\run-01\ast-fp32.onnx',
    'work\ondevice-av\ast-model\model.safetensors'
)
$allowedBuilds = @('outputs\MoaAndroid\app\build', 'outputs\MoaAndroid\build', 'tools\research\gemma_android_probe\build')
$checked = @()
foreach ($target in $plan.targets) {
    $path = [System.IO.Path]::GetFullPath($target.path).TrimEnd('\')
    $owner = @($roots | Where-Object { $path.StartsWith($_ + '\', [StringComparison]::OrdinalIgnoreCase) })
    if ($owner.Count -ne 1) { throw "Target outside named checkouts: $path" }
    $owner = $owner[0]
    $relative = $path.Substring($owner.Length + 1)
    $item = Get-Item -LiteralPath $path -Force
    $cursor = $item
    while ($cursor.FullName.Length -gt $owner.Length) {
        if ($cursor.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Reparse path rejected: $path" }
        $cursor = Get-Item -LiteralPath (Split-Path $cursor.FullName -Parent) -Force
    }
    if ($target.kind -eq 'file') {
        if ($relative -match '(?:^|[^0-9])0[._-](?:2[._-]1|3[._-]0)(?:[^0-9]|$)') { throw "Historical QA baseline is protected: $path" }
        if ($item.PSIsContainer -or $item.Length -ne [long]$target.bytes) { throw "Changed file: $path" }
        $oldPackage = ($owner -eq $repoRoot -and $relative -match '^work\\.+\.(apk|aab)$')
        $researchWeight = ($owner -eq $repoRoot -and $allowedModels -contains $relative)
        if (-not ($oldPackage -or $researchWeight)) { throw "Unsupported file target: $path" }
        foreach ($keep in $plan.preserved_release_roots.PSObject.Properties.Name) {
            if ($path.StartsWith($keep + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "Preserved package: $path" }
        }
    } elseif ($target.kind -eq 'directory') {
        if (-not $item.PSIsContainer -or $allowedBuilds -notcontains $relative) { throw "Unsupported directory: $path" }
        $links = @(Get-ChildItem -LiteralPath $path -Recurse -Force | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint })
        if ($links.Count -ne 0) { throw "Directory contains a reparse point: $path" }
    } else { throw 'Unsupported target kind.' }
    $tracked = @(git -C $owner ls-files -- $relative.Replace('\', '/'))
    if ($LASTEXITCODE -ne 0 -or $tracked.Count -ne 0) { throw "Tracked or unverified target: $path" }
    $checked += [PSCustomObject]@{ Path = $path; Owner = $owner; Target = $target }
}

$activeBuilders = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^(java|javaw|python|pythonw|emulator|qemu.*)\.exe$' -and
    $_.CommandLine -match '(GradleDaemon|gradle-wrapper|gemma_android_probe|ast_device_benchmark|MoaAndroid|AgattokNavigation30|AgattokExpression29)'
})
if ($activeBuilders.Count -ne 0) { throw 'A potentially related build/probe/emulator is still running.' }
if (-not $Apply) {
    [PSCustomObject]@{ Mode = 'preview'; Targets = $checked.Count; LogicalBytes = ($plan.targets | Measure-Object bytes -Sum).Sum } | ConvertTo-Json
    exit 0
}

$preserved = @()
foreach ($root in $plan.preserved_release_roots.PSObject.Properties.Name) {
    foreach ($file in Get-ChildItem -LiteralPath $root -File) {
        $preserved += [PSCustomObject]@{ Path = $file.FullName; Bytes = $file.Length; SHA256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash }
    }
}
$beforeFree = ([IO.DriveInfo]::new('C:\')).AvailableFreeSpace
$report = [ordered]@{ Status = 'running'; BeforeFreeBytes = $beforeFree; Removed = @(); PreservedPackages = $preserved; PreservedReports = @(); Errors = @() }
try {
    foreach ($entry in $checked) {
        $target = $entry.Target
        if ($target.kind -eq 'directory') {
            $index = $report.PreservedReports.Count
            foreach ($source in $target.reports_to_preserve) {
                $sourcePath = [IO.Path]::GetFullPath($source)
                if (-not $sourcePath.StartsWith($entry.Path + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Report path outside build target.' }
                $dest = Join-Path $runDir ('preserved-build-reports\{0}' -f $index)
                $null = New-Item -ItemType Directory -Path (Split-Path $dest -Parent) -Force
                Copy-Item -LiteralPath $sourcePath -Destination $dest -Recurse
                $sourceFiles = @(Get-ChildItem -LiteralPath $sourcePath -File -Recurse -Force)
                foreach ($file in $sourceFiles) {
                    $relative = $file.FullName.Substring($sourcePath.Length + 1)
                    $copy = Join-Path $dest $relative
                    if ((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash) { throw 'Report copy hash mismatch.' }
                }
                $report.PreservedReports += [PSCustomObject]@{ Source = $sourcePath; Copy = $dest; Files = $sourceFiles.Count; Verified = $true }
                $index++
            }
        }
        if ($target.kind -eq 'directory') { Remove-Item -LiteralPath $entry.Path -Recurse -Force }
        else { Remove-Item -LiteralPath $entry.Path -Force }
        if (Test-Path -LiteralPath $entry.Path) { throw "Target remains: $($entry.Path)" }
        $report.Removed += [PSCustomObject]@{ Path = $entry.Path; Bytes = [long]$target.bytes; Reason = $target.reason }
    }
    foreach ($file in $preserved) {
        if ((Get-Item -LiteralPath $file.Path).Length -ne $file.Bytes -or (Get-FileHash -LiteralPath $file.Path -Algorithm SHA256).Hash -ne $file.SHA256) { throw 'Preserved package changed.' }
    }
    $report.Status = 'pass'
} catch {
    $report.Status = 'failed'
    $report.Errors += $_.Exception.Message
    throw
} finally {
    $report.AfterFreeBytes = ([IO.DriveInfo]::new('C:\')).AvailableFreeSpace
    $report.RecoveredVolumeBytes = $report.AfterFreeBytes - $beforeFree
    $report.CompletedAt = [DateTimeOffset]::Now.ToString('o')
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $runDir 'cleanup-result.json') -Encoding UTF8
    [PSCustomObject]@{ Status = $report.Status; Removed = $report.Removed.Count; RecoveredGiB = [Math]::Round($report.RecoveredVolumeBytes / 1GB, 2); FreeGiB = [Math]::Round($report.AfterFreeBytes / 1GB, 2); PreservedReports = $report.PreservedReports.Count } | ConvertTo-Json
}
