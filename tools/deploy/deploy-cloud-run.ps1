[CmdletBinding()]
param(
    [string]$PlanPath,
    [string]$GcloudPath = 'gcloud',
    [string]$NodePath = 'node',
    [string]$BuildServiceAccountEmail,
    [switch]$Execute,
    [switch]$PublishPublicService,
    [switch]$ReuseExistingPublicService,
    [switch]$OperationalGatesConfirmed
)
$ErrorActionPreference = 'Stop'
if (-not $PlanPath) { $PlanPath = Join-Path $PSScriptRoot 'plan.json' }
$plan = Get-Content -LiteralPath $PlanPath -Raw | ConvertFrom-Json
if ($plan.schemaVersion -ne 1 -or $plan.projectId -notmatch '^[a-z][a-z0-9-]{4,28}[a-z0-9]$') { throw 'Invalid deployment plan' }
if ($plan.region -ne 'asia-northeast3' -or $plan.service -ne 'jigeum-verifier') { throw 'Unexpected deployment target' }
if ($plan.runtime.maxInstances -ne 1 -or $plan.runtime.minInstances -ne 0 -or $plan.runtime.concurrency -ne 24) { throw 'Unexpected approved resource envelope' }
if ($plan.buildSourceBucket -ne "$($plan.projectId)-build-source") { throw 'A dedicated source staging bucket is required' }
if ($plan.signingSecretVersion -notmatch '^[1-9]\d*$' -or $plan.reviewHashSecretVersion -notmatch '^[1-9]\d*$') { throw 'Use immutable positive numeric secret versions, not latest' }
$projectId = $plan.projectId
$runtimeEmail = "$($plan.runtimeServiceAccountName)@$projectId.iam.gserviceaccount.com"
if ($BuildServiceAccountEmail -and $BuildServiceAccountEmail -notmatch ('^[a-z][a-z0-9-]{4,28}[a-z0-9]@' + [regex]::Escape($projectId) + '\.iam\.gserviceaccount\.com$')) { throw 'Build identity must be a named service account in this project' }
if ($Execute -and -not $BuildServiceAccountEmail) { throw 'Execution requires a dedicated, least-privilege build identity' }
if ($Execute -and -not $OperationalGatesConfirmed) { throw 'Execution requires explicit confirmation of the manual IAM, Play, policy, retention, budget and QA gates' }
if ($BuildServiceAccountEmail -eq $runtimeEmail) { throw 'Build and runtime identities must be distinct' }
$sourceDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../outputs/PlayVerifier'))
$image = "$($plan.region)-docker.pkg.dev/$projectId/$($plan.artifactRepository)/$($plan.imageName):$($plan.imageTag)"
$common = @("--project=$projectId", "--account=$($plan.account)", '--quiet')
$environment = @(
    'HOST=0.0.0.0', 'PLAY_CREDENTIALS_MODE=workload', "PLAY_RUNTIME_SERVICE_ACCOUNT_EMAIL=$runtimeEmail",
    'PLAY_ENTITLEMENT_PRIVATE_KEY_PATH=/secrets/entitlement.pem', 'RATE_LIMIT_MODE=installation',
    "ORIGIN_REQUESTS_PER_MINUTE=$($plan.runtime.perInstallationRequestsPerMinute)",
    "GLOBAL_REQUESTS_PER_MINUTE=$($plan.runtime.globalRequestsPerMinute)",
    "MAX_CONCURRENT_VERIFICATIONS=$($plan.runtime.concurrency)", "MAX_RATE_KEYS=$($plan.runtime.maxRateKeys)",
    "BODY_TIMEOUT_MS=$($plan.runtime.bodyTimeoutMs)", "UPSTREAM_TIMEOUT_MS=$($plan.runtime.upstreamTimeoutMs)",
    "VERIFICATION_TIMEOUT_MS=$($plan.runtime.verificationTimeoutMs)", "PLAY_REVIEW_ACCESS_ID=$($plan.reviewAccessId)"
) -join ','
$secretBindings = "/secrets/entitlement.pem=$($plan.signingSecret):$($plan.signingSecretVersion),PLAY_REVIEW_CODE_SHA256=$($plan.reviewHashSecret):$($plan.reviewHashSecretVersion)"
if ($plan.accessCodes) {
    if ($plan.accessCodes.manifestSecret -ne 'jigeum-access-code-manifest' -or $plan.accessCodes.version -notmatch '^[1-9]\d*$' -or
        $plan.accessCodes.bucket -ne "$projectId-code-redemptions") { throw 'Invalid dedicated access code resources' }
    $environment += ",ACCESS_CODE_MANIFEST_PATH=/secrets/access-codes/manifest.json,CODE_REDEMPTION_BUCKET=$($plan.accessCodes.bucket)"
    $secretBindings += ",/secrets/access-codes/manifest.json=$($plan.accessCodes.manifestSecret):$($plan.accessCodes.version)"
}
$deploymentArgs = @('run', 'deploy', $plan.service, "--region=$($plan.region)", "--service-account=$runtimeEmail",
    '--cpu=1', '--memory=512Mi', '--concurrency=24', '--min=0', '--max=1', '--min-instances=0', '--max-instances=1',
    '--cpu-throttling', '--timeout=30s', '--port=8080', '--ingress=all', '--execution-environment=gen2',
    "--set-env-vars=$environment",
    "--set-secrets=$secretBindings")
if ($PublishPublicService) { $deploymentArgs += '--allow-unauthenticated' }
$deploymentArgs += $common

if (-not $Execute) {
    [ordered]@{
        mode = 'dry-run'; project = $projectId; account = $plan.account; source = $sourceDirectory;
        image = $image; buildIdentity = $BuildServiceAccountEmail; runtimeIdentity = $runtimeEmail;
        sourceStagingDirectory = "gs://$($plan.buildSourceBucket)/source";
        publicInvokerMutation = [bool]$PublishPublicService;
        reuseExistingPublicServiceConsent = [bool]$ReuseExistingPublicService;
        manualOperationalGatesConfirmed = [bool]$OperationalGatesConfirmed;
        billingMutation = $false; resourcePreparationMutation = $false;
        gatesActuallyChecked = $false;
        deployArguments = $deploymentArgs;
        gates = @('Billing/API/repository/secret versions ready', 'Scoped-token self permission and Play app permission verified',
          'Runtime grants only on named secrets', 'Dedicated build identity approved', 'Published policy text approved',
          'Request metadata 30-day retention and budget alerts agreed', 'Node tests and image/context validation passed')
    } | ConvertTo-Json -Depth 6
    exit 0
}

function Get-GcloudResult([string[]]$Arguments) {
    $previousErrorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $combined = @(& $GcloudPath @Arguments 2>&1)
        $code = $LASTEXITCODE
        # Native stderr is an ErrorRecord in Windows PowerShell. Reading its
        # message preserves the exact diagnostic without formatting decoration.
        $stdout = @($combined | Where-Object { $_ -isnot [System.Management.Automation.ErrorRecord] })
        $stderr = @($combined | Where-Object { $_ -is [System.Management.Automation.ErrorRecord] } | ForEach-Object { $_.ToString() })
        return [pscustomobject]@{ Code = $code; Output = ($stdout -join "`n"); ErrorText = ($stderr -join "`n").Trim() }
    } finally { $ErrorActionPreference = $previousErrorPreference }
}
function Get-GcloudJson([string[]]$Arguments, [string]$Stage) {
    $result = Get-GcloudResult ($Arguments + '--format=json' + $common)
    if ($result.Code -ne 0) { throw "$Stage preflight failed; no dependent mutation is permitted" }
    try { return $result.Output | ConvertFrom-Json } catch { throw "$Stage preflight returned invalid JSON" }
}
function Invoke-Gcloud([string[]]$Arguments, [string]$Stage) {
    $result = Get-GcloudResult $Arguments
    if ($result.Code -ne 0) { throw "$Stage failed; stop before dependent mutations" }
    return $result.Output
}

# Every Cloud-state and current-source check runs before source upload/build/push.
$billing = Get-GcloudJson @('billing', 'projects', 'describe', $projectId) 'Billing'
if ($billing.billingEnabled -ne $true) { throw 'Billing is not enabled for this project' }
$services = @(Get-GcloudJson @('services', 'list', '--enabled') 'APIs')
$enabledApis = @($services | ForEach-Object { $_.config.name })
foreach ($api in $plan.apis) {
    if ($enabledApis -notcontains $api) { throw "Required API is not enabled: $api" }
}
foreach ($secretVersion in @(
    @{ Name = $plan.signingSecret; Version = $plan.signingSecretVersion },
    @{ Name = $plan.reviewHashSecret; Version = $plan.reviewHashSecretVersion }
)) {
    $secret = Get-GcloudJson @('secrets', 'versions', 'describe', $secretVersion.Version, "--secret=$($secretVersion.Name)") 'Secret version'
    if ($secret.state -ne 'ENABLED') { throw 'A required immutable secret version is not enabled' }
}
if ($plan.accessCodes) {
    $codeSecret = Get-GcloudJson @('secrets', 'versions', 'describe', $plan.accessCodes.version, "--secret=$($plan.accessCodes.manifestSecret)") 'Access code secret'
    if ($codeSecret.state -ne 'ENABLED') { throw 'Access code manifest version is not enabled' }
    $codeBucket = Get-GcloudJson @('storage', 'buckets', 'describe', "gs://$($plan.accessCodes.bucket)") 'Code bucket'
    if ($codeBucket.public_access_prevention -ne 'enforced' -or $codeBucket.uniform_bucket_level_access -ne $true) { throw 'Code bucket must be private with uniform access' }
}
$repository = Get-GcloudJson @('artifacts', 'repositories', 'describe', $plan.artifactRepository, "--location=$($plan.region)") 'Artifact repository'
if ($repository.format -ne 'DOCKER') { throw 'Artifact repository must exist with DOCKER format' }
foreach ($identity in @($runtimeEmail, $BuildServiceAccountEmail)) {
    $accountState = Get-GcloudJson @('iam', 'service-accounts', 'describe', $identity) 'Service account'
    if ($accountState.email -ne $identity -or $accountState.disabled -eq $true) { throw 'Required service account is absent, disabled or mismatched' }
}
$runResult = Get-GcloudResult (@('run', 'services', 'describe', $plan.service, "--region=$($plan.region)", '--format=json') + $common)
$existingPublic = $false
if ($runResult.Code -ne 0) {
    # Only the exact target-specific NOT_FOUND diagnostic is a create path.
    $notFoundDiagnostic = '^ERROR:\s+\(gcloud\.run\.services\.describe\)\s+Cannot find service \[' + [regex]::Escape($plan.service) + '\]\.?(?:\r?\n)?$'
    if ($runResult.ErrorText -notmatch $notFoundDiagnostic) { throw 'Existing Cloud Run service state could not be verified' }
    if (-not $PublishPublicService) { $deploymentArgs += '--no-allow-unauthenticated' }
} else {
    try { $runService = $runResult.Output | ConvertFrom-Json } catch { throw 'Cloud Run service returned invalid JSON' }
    $serviceIdentity = if ($runService.metadata.name) { $runService.metadata.name } else { $runService.name }
    $expectedServiceResource = "projects/$projectId/locations/$($plan.region)/services/$($plan.service)"
    $expectedNumericResource = "projects/$($plan.projectNumber)/locations/$($plan.region)/services/$($plan.service)"
    if ($serviceIdentity -notin @($plan.service, $expectedServiceResource, $expectedNumericResource)) { throw 'Cloud Run service identity is missing or mismatched' }
    $existingPublic = $runService.metadata.annotations.'run.googleapis.com/invoker-iam-disabled' -eq 'true' -or $runService.invokerIamDisabled -eq $true
    $policy = Get-GcloudJson @('run', 'services', 'get-iam-policy', $plan.service, "--region=$($plan.region)") 'Cloud Run IAM'
    foreach ($binding in $policy.bindings) {
        if ($binding.role -eq 'roles/run.invoker' -and $binding.members -contains 'allUsers') { $existingPublic = $true }
    }
    if ($existingPublic -and -not ($PublishPublicService -or $ReuseExistingPublicService)) {
        throw 'Existing service is public; explicit public publication or reuse consent is required'
    }
}
$nodeCommand = Get-Command $NodePath -ErrorAction Stop
Push-Location -LiteralPath $sourceDirectory
try {
    & $nodeCommand.Source '--test'
    if ($LASTEXITCODE -ne 0) { throw 'Current source Node tests failed; build/push/deploy is prohibited' }
} finally { Pop-Location }
[ordered]@{
    mode = 'preflight-passed'; billingAndApisChecked = $true; secretVersionsAndResourcesChecked = $true;
    currentSourceNodeTestsPassed = $true; manualOperationalGatesConfirmed = $true;
    existingServicePublic = $existingPublic;
    existingPublicServiceConsent = [bool]($PublishPublicService -or $ReuseExistingPublicService)
} | ConvertTo-Json
$buildConfigPath = Join-Path ([System.IO.Path]::GetTempPath()) ("jigeum-cloudbuild-$([guid]::NewGuid().ToString('N')).json")
try {
    $config = @{
        steps = @(@{ name = 'gcr.io/cloud-builders/docker'; args = @('build', '-t', $image, '.') });
        images = @($image); options = @{ logging = 'CLOUD_LOGGING_ONLY' }; timeout = '600s'
    }
    [System.IO.File]::WriteAllText($buildConfigPath, ($config | ConvertTo-Json -Depth 8), [System.Text.UTF8Encoding]::new($false))
    Invoke-Gcloud (@('builds', 'submit', $sourceDirectory, "--config=$buildConfigPath", "--region=$($plan.region)",
        "--service-account=projects/$projectId/serviceAccounts/$BuildServiceAccountEmail", "--gcs-source-staging-dir=gs://$($plan.buildSourceBucket)/source") + $common) 'Build/push'
    $digest = (Invoke-Gcloud (@('artifacts', 'docker', 'images', 'describe', $image, '--format=value(image_summary.digest)') + $common) 'Image digest').Trim()
    if ($digest -notmatch '^sha256:[0-9a-f]{64}$') { throw 'Invalid immutable image digest' }
    $immutableImage = ($image -replace ':[^/:]+$', '') + "@$digest"
    Invoke-Gcloud ($deploymentArgs + "--image=$immutableImage") 'Cloud Run deploy'
    Invoke-Gcloud (@('run', 'services', 'describe', $plan.service, "--region=$($plan.region)", '--format=value(status.url)') + $common) 'Deployed URL'
} finally {
    if (Test-Path -LiteralPath $buildConfigPath) { Remove-Item -LiteralPath $buildConfigPath -Force }
}
