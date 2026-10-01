[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$harnessRoot = Join-Path $repositoryRoot ('work/deploy-preflight-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $harnessRoot -Force | Out-Null
$fakeScript = Join-Path $harnessRoot 'fake-gcloud.ps1'
$fakeCommand = Join-Path $harnessRoot 'fake-gcloud.cmd'
$fakeNode = Join-Path $harnessRoot 'failing-node.cmd'
$logPath = Join-Path $harnessRoot 'calls.jsonl'
$utf8 = [System.Text.UTF8Encoding]::new($false)
$fakeSource = @'
$arguments = @($args)
$scenario = $env:JIGEUM_DEPLOY_QA_SCENARIO
[System.IO.File]::AppendAllText($env:JIGEUM_DEPLOY_QA_LOG_PATH, (ConvertTo-Json -InputObject $arguments -Compress) + "`n")
function Json($value) { ConvertTo-Json -InputObject $value -Depth 8 -Compress; exit 0 }
function Fail([string]$message) { [Console]::Error.WriteLine($message); exit 1 }
$prefix = ($arguments | Select-Object -First 3) -join ' '
if ($arguments[0] -eq 'builds' -and $arguments[1] -eq 'submit') {
    if ($scenario -eq 'build-fails') { Fail 'Build failed' }
    'synthetic-build-complete'; exit 0
}
switch ($prefix) {
    'billing projects describe' { Json @{ billingEnabled = ($scenario -ne 'billing-disabled') } }
    'services list --enabled' {
        $names = @('run.googleapis.com','cloudbuild.googleapis.com','artifactregistry.googleapis.com','secretmanager.googleapis.com','iamcredentials.googleapis.com','androidpublisher.googleapis.com')
        if ($scenario -eq 'api-missing') { $names = $names | Where-Object { $_ -ne 'androidpublisher.googleapis.com' } }
        Json @($names | ForEach-Object { @{ config = @{ name = $_ } } })
    }
    'secrets versions describe' { Json @{ state = $(if ($scenario -eq 'secret-disabled') { 'DISABLED' } else { 'ENABLED' }) } }
    'artifacts repositories describe' { Json @{ format = $(if ($scenario -eq 'repository-wrong') { 'MAVEN' } else { 'DOCKER' }) } }
    'iam service-accounts describe' {
        if ($scenario -eq 'build-missing' -and $arguments[3] -like 'jigeum-play-build@*') { Fail 'PERMISSION_DENIED: build account cannot be read' }
        Json @{ email = $arguments[3]; disabled = ($scenario -eq 'runtime-disabled' -and $arguments[3] -like 'jigeum-play-runtime@*') }
    }
    'run services describe' {
        if ($arguments -contains '--format=value(status.url)') { 'https://qa-only.invalid'; exit 0 }
        if ($scenario -eq 'run-permission') { Fail 'ERROR: (gcloud.run.services.describe) PERMISSION_DENIED: not found or not authorized' }
        if ($scenario -eq 'wrong-not-found') { Fail 'ERROR: (gcloud.run.services.describe) Cannot find service [other-service].' }
        if ($scenario -eq 'new-private') { Fail 'ERROR: (gcloud.run.services.describe) Cannot find service [jigeum-verifier].' }
        Json @{ metadata = @{ name = 'jigeum-verifier'; annotations = @{ 'run.googleapis.com/invoker-iam-disabled' = $(if ($scenario -eq 'invoker-disabled') { 'true' } else { 'false' }) } } }
    }
    'run services get-iam-policy' {
        if ($scenario -eq 'iam-permission') { Fail 'PERMISSION_DENIED: IAM policy cannot be read' }
        Json @{ bindings = @(@{ role = 'roles/run.invoker'; members = @($(if ($scenario -in @('public-denied','public-reuse','public-publish')) { 'allUsers' } else { 'serviceAccount:private@example.invalid' })) }) }
    }
    'artifacts docker images' { if ($scenario -eq 'digest-invalid') { 'mutable-not-digest'; exit 0 }; 'sha256:' + ('a' * 64); exit 0 }
    'run deploy jigeum-verifier' { if ($scenario -eq 'deploy-fails') { Fail 'Deploy failed' }; 'synthetic-deploy-complete'; exit 0 }
    default { Fail ('Unexpected mocked command: ' + $prefix) }
}
'@
[System.IO.File]::WriteAllText($fakeScript, $fakeSource, $utf8)
[System.IO.File]::WriteAllText($fakeCommand, '@echo off' + "`r`n" + 'powershell.exe -NoProfile -File "%~dp0fake-gcloud.ps1" %*' + "`r`n", $utf8)
[System.IO.File]::WriteAllText($fakeNode, "@echo off`r`nexit /b 7`r`n", $utf8)
$cases = @(
    @{ Name='dry-run'; Dry=$true; Manual=$false; Success=$true; Build=$false; Deploy=$false; Calls=0 },
    @{ Name='manual-missing'; Manual=$false; Success=$false; Build=$false; Deploy=$false; Calls=0 },
    @{ Name='billing-disabled'; Manual=$true; Success=$false; Build=$false; Deploy=$false },
    @{ Name='api-missing'; Manual=$true; Success=$false; Build=$false; Deploy=$false },
    @{ Name='secret-disabled'; Manual=$true; Success=$false; Build=$false; Deploy=$false },
    @{ Name='repository-wrong'; Manual=$true; Success=$false; Build=$false; Deploy=$false },
    @{ Name='runtime-disabled'; Manual=$true; Success=$false; Build=$false; Deploy=$false },
    @{ Name='build-missing'; Manual=$true; Success=$false; Build=$false; Deploy=$false },
    @{ Name='run-permission'; Manual=$true; Success=$false; Build=$false; Deploy=$false },
    @{ Name='wrong-not-found'; Manual=$true; Success=$false; Build=$false; Deploy=$false },
    @{ Name='iam-permission'; Manual=$true; Success=$false; Build=$false; Deploy=$false },
    @{ Name='public-denied'; Manual=$true; Success=$false; Build=$false; Deploy=$false },
    @{ Name='invoker-disabled'; Manual=$true; Success=$false; Build=$false; Deploy=$false },
    @{ Name='node-fails'; Manual=$true; NodeFails=$true; Success=$false; Build=$false; Deploy=$false },
    @{ Name='build-fails'; Manual=$true; Success=$false; Build=$true; Deploy=$false },
    @{ Name='digest-invalid'; Manual=$true; Success=$false; Build=$true; Deploy=$false },
    @{ Name='deploy-fails'; Manual=$true; Success=$false; Build=$true; Deploy=$true; FinalUrl=$false },
    @{ Name='private-existing'; Manual=$true; Success=$true; Build=$true; Deploy=$true },
    @{ Name='new-private'; Manual=$true; Success=$true; Build=$true; Deploy=$true; PrivateCreate=$true },
    @{ Name='public-reuse'; Manual=$true; Reuse=$true; Success=$true; Build=$true; Deploy=$true },
    @{ Name='public-publish'; Manual=$true; Publish=$true; Success=$true; Build=$true; Deploy=$true }
)
$previousScenario = $env:JIGEUM_DEPLOY_QA_SCENARIO
$previousLog = $env:JIGEUM_DEPLOY_QA_LOG_PATH
$expectedCallCounts = @{
    'dry-run'=0; 'manual-missing'=0; 'billing-disabled'=1; 'api-missing'=2; 'secret-disabled'=3;
    'repository-wrong'=5; 'runtime-disabled'=6; 'build-missing'=7; 'run-permission'=8; 'wrong-not-found'=8;
    'iam-permission'=9; 'public-denied'=9; 'invoker-disabled'=9; 'node-fails'=9; 'build-fails'=10;
    'digest-invalid'=11; 'deploy-fails'=12; 'private-existing'=13; 'new-private'=12; 'public-reuse'=13; 'public-publish'=13
}
$expectedFailures = @{
    'manual-missing'='Execution requires explicit confirmation'; 'billing-disabled'='Billing is not enabled';
    'api-missing'='Required API is not enabled'; 'secret-disabled'='required immutable secret version is not enabled';
    'repository-wrong'='Artifact repository must exist'; 'runtime-disabled'='service account is absent, disabled or mismatched';
    'build-missing'='Service account preflight failed'; 'run-permission'='service state could not be verified';
    'wrong-not-found'='service state could not be verified'; 'iam-permission'='Cloud Run IAM preflight failed';
    'public-denied'='Existing service is public'; 'invoker-disabled'='Existing service is public';
    'node-fails'='Current source Node tests failed'; 'build-fails'='Build/push failed';
    'digest-invalid'='Invalid immutable image digest'; 'deploy-fails'='Cloud Run deploy failed'
}
$results = @()
try {
    foreach ($case in $cases) {
        $env:JIGEUM_DEPLOY_QA_SCENARIO = $case.Name
        $env:JIGEUM_DEPLOY_QA_LOG_PATH = $logPath
        [System.IO.File]::WriteAllText($logPath, '', $utf8)
        $arguments = @('-NoProfile', '-File', (Join-Path $PSScriptRoot 'deploy-cloud-run.ps1'), '-GcloudPath', $fakeCommand,
            '-BuildServiceAccountEmail', 'jigeum-play-build@jigeum-play-20261001.iam.gserviceaccount.com')
        if (-not $case.Dry) { $arguments += '-Execute' }
        if ($case.Manual) { $arguments += '-OperationalGatesConfirmed' }
        if ($case.Reuse) { $arguments += '-ReuseExistingPublicService' }
        if ($case.Publish) { $arguments += '-PublishPublicService' }
        if ($case.NodeFails) { $arguments += @('-NodePath', $fakeNode) }
        $stderrPath = Join-Path $harnessRoot ($case.Name + '.stderr.txt')
        $previousPreference = $ErrorActionPreference
        try { $ErrorActionPreference='Continue'; $output = & powershell.exe @arguments 2> $stderrPath; $code = $LASTEXITCODE }
        finally { $ErrorActionPreference=$previousPreference }
        [System.IO.File]::WriteAllText((Join-Path $harnessRoot ($case.Name + '.stdout.txt')), ($output -join "`n"), $utf8)
        $calls = @(Get-Content -LiteralPath $logPath -Encoding UTF8 | ForEach-Object { ,(ConvertFrom-Json $_) })
        $buildCalls = @($calls | Where-Object { $_[0] -eq 'builds' -and $_[1] -eq 'submit' })
        $deployCalls = @($calls | Where-Object { $_[0] -eq 'run' -and $_[1] -eq 'deploy' })
        $urlCalls = @($calls | Where-Object { $_ -contains '--format=value(status.url)' })
        $passed = (($code -eq 0) -eq $case.Success) -and (($buildCalls.Count -gt 0) -eq $case.Build) -and (($deployCalls.Count -gt 0) -eq $case.Deploy)
        $passed = $passed -and ($calls.Count -eq $expectedCallCounts[$case.Name])
        if (-not $case.Success) {
            $failureText = [System.IO.File]::ReadAllText($stderrPath)
            $passed = $passed -and ($failureText.Contains($expectedFailures[$case.Name]))
        }
        if ($case.ContainsKey('Calls')) { $passed = $passed -and $calls.Count -eq $case.Calls }
        if ($case.ContainsKey('FinalUrl')) { $passed = $passed -and (($urlCalls.Count -gt 0) -eq $case.FinalUrl) }
        if ($buildCalls.Count -gt 0) {
            $passed = $passed -and ($buildCalls[0] -contains '--gcs-source-staging-dir=gs://jigeum-play-20261001-build-source/source')
        }
        if ($deployCalls.Count -gt 0) {
            $passed = $passed -and (($deployCalls[0] -contains '--allow-unauthenticated') -eq [bool]$case.Publish)
            $passed = $passed -and (($deployCalls[0] -contains '--no-allow-unauthenticated') -eq [bool]$case.PrivateCreate)
            $passed = $passed -and (($deployCalls[0] | Where-Object { $_ -like '--image=*@sha256:*' }).Count -eq 1)
        }
        if (-not $case.Success -and -not $case.Build) {
            $passed = $passed -and (($output -join "`n") -notmatch 'preflight-passed')
        }
        $results += [pscustomobject]@{ case=$case.Name; pass=$passed; fakeCloudCalls=$calls.Count; fakeBuildCalls=$buildCalls.Count; fakeDeployCalls=$deployCalls.Count }
        Write-Output ("{0}: {1}" -f $case.Name, $(if ($passed) { 'PASS' } else { 'FAIL' }))
    }
} finally {
    $env:JIGEUM_DEPLOY_QA_SCENARIO=$previousScenario
    $env:JIGEUM_DEPLOY_QA_LOG_PATH=$previousLog
}
$report = [ordered]@{ tests=$results.Count; passed=@($results | Where-Object pass).Count;
    externalCloudOperations=0; resultDirectory=$harnessRoot; results=$results }
$json = $report | ConvertTo-Json -Depth 5
[System.IO.File]::WriteAllText((Join-Path $harnessRoot 'summary.json'), $json, $utf8)
Write-Output $json
if ($report.passed -ne $report.tests) { exit 1 }
