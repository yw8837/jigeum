param(
    [switch]$OpenBrowser,
    [string]$NodePath = 'C:/Program Files/nodejs/node.exe',
    [string]$GcloudPath = (Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Google/Cloud SDK/google-cloud-sdk/bin/gcloud.ps1')
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $NodePath) -or -not (Test-Path -LiteralPath $GcloudPath)) { throw 'Node or existing operator gcloud was not found.' }
$dashboardScript = Join-Path $PSScriptRoot 'dashboard.mjs'
$existingDashboard = $null
try { $existingDashboard = Invoke-RestMethod -Uri 'http://127.0.0.1:8765/api/health' -TimeoutSec 2 } catch { }
if ($existingDashboard -and $existingDashboard.app -ne 'agattok-campaign-local') { throw 'Another service uses port 8765.' }
if (-not $existingDashboard) {
    $previousGcloud = $env:AGATTOK_GCLOUD_PATH
    try {
        $env:AGATTOK_GCLOUD_PATH = $GcloudPath
        Start-Process -FilePath $NodePath -ArgumentList @('"' + $dashboardScript + '"') -WindowStyle Hidden
    } finally { $env:AGATTOK_GCLOUD_PATH = $previousGcloud }
}
if ($OpenBrowser) { Start-Process 'http://127.0.0.1:8765/' }
Write-Output 'http://127.0.0.1:8765/'
