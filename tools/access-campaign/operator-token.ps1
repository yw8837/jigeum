param([Parameter(Mandatory=$true)][string]$GcloudPath)
$ErrorActionPreference = 'Stop'
$tokenOutput = & $GcloudPath auth print-access-token --account=studioyeolmae.com@gmail.com --quiet 2>$null
if ($LASTEXITCODE -ne 0) { throw 'Existing operator authentication unavailable' }
Write-Output $tokenOutput
