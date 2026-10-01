[CmdletBinding()]
param(
    [string]$PlanPath,
    [string]$GcloudPath = 'gcloud',
    [string]$NodePath = 'node',
    [string]$SigningKeyPath,
    [string]$ReviewHashPath,
    [string]$BillingAccountIdFile,
    [string]$BudgetAccount = 'bsfire1237@gmail.com',
    [switch]$Execute,
    [switch]$OperationalPreparationConfirmed,
    [switch]$BillingCurrencyKRWConfirmed,
    [switch]$ReuseExistingSecretVersionsConfirmed
)
$ErrorActionPreference = 'Stop'
if (-not $PlanPath) { $PlanPath = Join-Path $PSScriptRoot 'plan.json' }
$plan = Get-Content -LiteralPath $PlanPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($plan.projectId -ne 'jigeum-play-20261001' -or $plan.projectNumber -ne '795224750373' -or $plan.region -ne 'asia-northeast3') { throw 'Unexpected bootstrap target' }
if ($plan.signingSecretVersion -ne '1' -or $plan.reviewHashSecretVersion -ne '1') { throw 'This bootstrap provisions only immutable version 1' }
$projectId = $plan.projectId
$runtimeEmail = "$($plan.runtimeServiceAccountName)@$projectId.iam.gserviceaccount.com"
$buildName = 'jigeum-play-build'
$buildEmail = "$buildName@$projectId.iam.gserviceaccount.com"
$sourceBucket = "$projectId-build-source"
$bucketUrl = "gs://$sourceBucket"
$budgetName = 'jigeum-monthly-krw-10000'
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$supportApis = @('iam.googleapis.com', 'storage.googleapis.com', 'logging.googleapis.com', 'billingbudgets.googleapis.com')
$allApis = @($plan.apis) + $supportApis

if (-not $Execute) {
    [ordered]@{
        mode='dry-run'; project=$projectId; account=$plan.account; region=$plan.region; gatesActuallyChecked=$false;
        billingLinkMutation=$false; deploymentMutation=$false; publicInvokerMutation=$false; exportedServiceAccountKeys=$false;
        coreApis=$plan.apis; bootstrapSupportApis=$supportApis; runtimeIdentity=$runtimeEmail; buildIdentity=$buildEmail;
        artifactRepository=$plan.artifactRepository; sourceBucket=$bucketUrl; sourceObjectLifecycleDays=7;
        sourceBucketPublicAccessPrevention='enforced'; sourceBucketUniformIam=$true;
        secrets=@($plan.signingSecret,$plan.reviewHashSecret); replicationLocations=@($plan.region);
        runtimeRoles=@('secretAccessor on each of the two named secrets','single getAccessToken custom role on runtime service account itself');
        buildRoles=@('objectViewer on the named source bucket','artifactregistry.writer on the named repository','logging.logWriter on this project');
        budget=@{ displayName=$budgetName; amount=10000; currency='KRW'; period='MONTH'; projectFilter="projects/$($plan.projectNumber)";
          thresholds=@('50% actual','80% actual','100% actual','100% forecast'); recipients='Default billing administrators/users'; hardSpendingCap=$false };
        sensitiveInputs='Three existing files outside the repository: P-256 signing key, exact SHA256 review hash, billing account ID. Never emitted.';
        manualGates=@('Latest user cost/IAM preparation approval','KRW billing currency confirmed in account','Secret version reuse explicitly confirmed if version1 exists',
            'Play app access, publication, live purchase and deployment remain separate');
        preparationConfirmed=[bool]$OperationalPreparationConfirmed; currencyConfirmed=[bool]$BillingCurrencyKRWConfirmed
    } | ConvertTo-Json -Depth 6
    exit 0
}
if (-not $OperationalPreparationConfirmed -or -not $BillingCurrencyKRWConfirmed) { throw 'Execution requires explicit infrastructure preparation and KRW billing currency confirmation' }
function Outside-RepositoryFile([string]$Path, [string]$Label) {
    if (-not $Path -or -not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "$Label input file is required" }
    $absolute = [System.IO.Path]::GetFullPath((Resolve-Path -LiteralPath $Path).Path)
    if ($absolute.StartsWith($repositoryRoot.TrimEnd('\','/') + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label must remain outside the repository"
    }
    if ((Get-Item -LiteralPath $absolute).Length -gt 16384) { throw "$Label input is too large" }
    return $absolute
}
$SigningKeyPath = Outside-RepositoryFile $SigningKeyPath 'Signing key'
$ReviewHashPath = Outside-RepositoryFile $ReviewHashPath 'Review hash'
$BillingAccountIdFile = Outside-RepositoryFile $BillingAccountIdFile 'Billing account'
$reviewHash = [System.IO.File]::ReadAllText($ReviewHashPath)
if ($reviewHash -notmatch '^[0-9a-f]{64}$') { throw 'Review hash file must contain exactly 64 lowercase hex characters with no newline' }
$billingId = [System.IO.File]::ReadAllText($BillingAccountIdFile).Trim()
if ($billingId -notmatch '^[0-9A-F]{6}-[0-9A-F]{6}-[0-9A-F]{6}$') { throw 'Invalid private billing account ID file' }
& $NodePath (Join-Path $PSScriptRoot 'validate-signing-key.mjs') $SigningKeyPath
if ($LASTEXITCODE -ne 0) { throw 'Signing file must be a valid EC P-256 private key; no key is created by bootstrap' }

function Call-Gcloud([string[]]$Arguments, [string]$Stage, [string]$Account = $plan.account, [bool]$BillingAccountScope = $false) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        [string[]]$scopeArguments = @()
        if ($BillingAccountScope) { $scopeArguments = @("--billing-project=$projectId") }
        else { $scopeArguments = @("--project=$projectId") }
        $captured = @(& $GcloudPath @Arguments @scopeArguments "--account=$Account" '--quiet' '--format=json' '--verbosity=error' '--no-log-http' 2>&1)
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference=$previousPreference }
    if ($code -ne 0) { throw "$Stage failed; stop before dependent operations. CLI diagnostics are suppressed to protect financial/secret inputs." }
    $output = @($captured | Where-Object { $_ -isnot [System.Management.Automation.ErrorRecord] }) -join "`n"
    if (-not $output.Trim()) { return $null }
    try { $parsed = $output | ConvertFrom-Json } catch { throw "$Stage returned invalid JSON" }
    # Enumerate JSON arrays, including empty inventories, normally. On PowerShell 7,
    # -NoEnumerate wraps a single PSCustomObject in List[object]; member enumeration
    # then flattens its singleton permission array to a string (index 0 becomes 'i').
    foreach ($record in $parsed) { Write-Output $record }
}
function Done([string]$Stage) { Write-Output ("PASS: " + $Stage) }
function Verify-ResourceBinding([string[]]$ReadArguments, [string]$Role, [string]$Member, [string]$Stage) {
    $policy = Call-Gcloud $ReadArguments $Stage
    $matches = @($policy.bindings | Where-Object { $_.role -eq $Role -and $_.members -contains $Member -and -not $_.condition })
    if ($matches.Count -lt 1) { throw "$Stage resource-specific binding verification failed" }
}

$projectState = Call-Gcloud @('projects','describe',$projectId) 'Project identity'
if ($projectState.projectNumber -ne $plan.projectNumber -or $projectState.lifecycleState -ne 'ACTIVE') { throw 'Bootstrap project identity/state mismatch' }
$billingState = Call-Gcloud @('billing','projects','describe',$projectId) 'Billing state'
if ($billingState.billingEnabled -ne $true -or $billingState.billingAccountName -ne "billingAccounts/$billingId") { throw 'Project billing is absent or does not match the private input file' }
Done 'project identity and existing billing link verified'

$null = Call-Gcloud (@('services','enable') + $allApis) 'API enablement'
$enabled = @(Call-Gcloud @('services','list','--enabled') 'API inventory')
foreach ($api in $allApis) { if (@($enabled | ForEach-Object {$_.config.name}) -notcontains $api) { throw "Required API missing after enablement: $api" } }
Done 'six core APIs and four preparation APIs enabled'

$accounts = @(Call-Gcloud @('iam','service-accounts','list') 'Identity inventory')
foreach ($entry in @(@{ Name=$plan.runtimeServiceAccountName; Email=$runtimeEmail }, @{ Name=$buildName; Email=$buildEmail })) {
    if (@($accounts | Where-Object { $_.email -eq $entry.Email }).Count -eq 0) {
        $null = Call-Gcloud @('iam','service-accounts','create',$entry.Name,"--display-name=$($entry.Name)") 'Dedicated identity creation'
    }
    $identity = Call-Gcloud @('iam','service-accounts','describe',$entry.Email) 'Dedicated identity verification'
    if ($identity.email -ne $entry.Email -or $identity.disabled -eq $true) { throw 'Dedicated service account is mismatched or disabled' }
}
Done 'dedicated runtime and build identities verified; no account keys exported'

$repositories = @(Call-Gcloud @('artifacts','repositories','list',"--location=$($plan.region)") 'Repository inventory')
if (@($repositories | Where-Object { $_.name -like "*/repositories/$($plan.artifactRepository)" }).Count -eq 0) {
    $null = Call-Gcloud @('artifacts','repositories','create',$plan.artifactRepository,'--repository-format=docker',"--location=$($plan.region)") 'Seoul repository creation'
}
$repository = Call-Gcloud @('artifacts','repositories','describe',$plan.artifactRepository,"--location=$($plan.region)") 'Repository verification'
if ($repository.format -ne 'DOCKER' -or $repository.name -notlike "*/locations/$($plan.region)/repositories/$($plan.artifactRepository)") { throw 'Repository location or format mismatch' }

$bucketInventory = @(Call-Gcloud @('storage','buckets','list','--raw') 'Source bucket inventory')
if (@($bucketInventory | Where-Object { $_.name -eq $sourceBucket }).Count -eq 0) {
    $null = Call-Gcloud @('storage','buckets','create',$bucketUrl,"--location=$($plan.region)",'--uniform-bucket-level-access','--public-access-prevention',
        "--lifecycle-file=$(Join-Path $PSScriptRoot 'build-source-lifecycle.json')",'--soft-delete-duration=0') 'Dedicated source bucket creation'
}
$bucket = Call-Gcloud @('storage','buckets','describe',$bucketUrl,'--raw') 'Source bucket verification'
if ($bucket.projectNumber -ne $plan.projectNumber -or $bucket.location -ne $plan.region -or
    $bucket.iamConfiguration.uniformBucketLevelAccess.enabled -ne $true -or $bucket.iamConfiguration.publicAccessPrevention -ne 'enforced') {
    throw 'Dedicated source bucket ownership, location or privacy configuration mismatch'
}
$sourceLifecycle = @($bucket.lifecycle.rule)
if ($sourceLifecycle.Count -ne 1 -or $sourceLifecycle[0].action.type -ne 'Delete' -or
    $sourceLifecycle[0].condition.age -ne 7 -or @($sourceLifecycle[0].condition.PSObject.Properties).Count -ne 1) {
    throw 'Dedicated source bucket must retain the exact reviewed Delete age7 lifecycle; existing bucket is not modified'
}
if ($bucket.softDeletePolicy -and ($null -eq $bucket.softDeletePolicy.retentionDurationSeconds -or $bucket.softDeletePolicy.retentionDurationSeconds -ne '0')) {
    throw 'Dedicated source bucket soft delete must be disabled or exactly zero; existing bucket is not modified'
}
$null = Call-Gcloud @('storage','buckets','add-iam-policy-binding',$bucketUrl,"--member=serviceAccount:$buildEmail",'--role=roles/storage.objectViewer','--condition=None') 'Source reader binding'
Verify-ResourceBinding @('storage','buckets','get-iam-policy',$bucketUrl) 'roles/storage.objectViewer' "serviceAccount:$buildEmail" 'Source reader'
$null = Call-Gcloud @('artifacts','repositories','add-iam-policy-binding',$plan.artifactRepository,"--location=$($plan.region)","--member=serviceAccount:$buildEmail",'--role=roles/artifactregistry.writer','--condition=None') 'Repository writer binding'
Verify-ResourceBinding @('artifacts','repositories','get-iam-policy',$plan.artifactRepository,"--location=$($plan.region)") 'roles/artifactregistry.writer' "serviceAccount:$buildEmail" 'Repository writer'
$null = Call-Gcloud @('projects','add-iam-policy-binding',$projectId,"--member=serviceAccount:$buildEmail",'--role=roles/logging.logWriter','--condition=None') 'Build log writer binding'
Verify-ResourceBinding @('projects','get-iam-policy',$projectId) 'roles/logging.logWriter' "serviceAccount:$buildEmail" 'Build log writer'
Done 'source bucket and resource-specific build permissions verified'

$secretInventory = @(Call-Gcloud @('secrets','list') 'Secret metadata inventory')
foreach ($entry in @(@{ Name=$plan.signingSecret; Path=$SigningKeyPath }, @{ Name=$plan.reviewHashSecret; Path=$ReviewHashPath })) {
    if (@($secretInventory | Where-Object { $_.name -like "*/secrets/$($entry.Name)" }).Count -eq 0) {
        $null = Call-Gcloud @('secrets','create',$entry.Name,'--replication-policy=user-managed',"--locations=$($plan.region)") 'Seoul secret metadata creation'
    }
    $secret = Call-Gcloud @('secrets','describe',$entry.Name) 'Secret location verification'
    $replicas = @($secret.replication.userManaged.replicas)
    if ($replicas.Count -ne 1 -or $replicas[0].location -ne $plan.region) { throw 'Secret replication must be user-managed in Seoul only' }
    $versions = @(Call-Gcloud @('secrets','versions','list',$entry.Name) 'Secret version metadata inventory')
    if ($versions.Count -eq 0) {
        $createdVersion = Call-Gcloud @('secrets','versions','add',$entry.Name,"--data-file=$($entry.Path)") 'Immutable secret version upload'
        if ($createdVersion.name -notlike '*/versions/1') { throw 'Initial secret version did not receive version 1; stop and review deployment plan' }
    } elseif (-not $ReuseExistingSecretVersionsConfirmed) { throw 'Existing immutable secret data requires explicit known-version reuse confirmation; payload is never read or replaced' }
    $version = Call-Gcloud @('secrets','versions','describe','1',"--secret=$($entry.Name)") 'Secret version verification'
    if ($version.state -ne 'ENABLED') { throw 'Required secret version 1 is not ENABLED' }
    $null = Call-Gcloud @('secrets','add-iam-policy-binding',$entry.Name,"--member=serviceAccount:$runtimeEmail",'--role=roles/secretmanager.secretAccessor','--condition=None') 'Individual secret accessor binding'
    Verify-ResourceBinding @('secrets','get-iam-policy',$entry.Name) 'roles/secretmanager.secretAccessor' "serviceAccount:$runtimeEmail" 'Individual secret accessor'
}
Done 'two immutable Seoul secrets and individual runtime access verified; no payload printed'

$roles = @(Call-Gcloud @('iam','roles','list','--show-deleted') 'Custom role inventory')
$customRole = "projects/$projectId/roles/$($plan.scopedTokenRoleId)"
if (@($roles | Where-Object { $_.name -eq $customRole }).Count -eq 0) {
    $null = Call-Gcloud @('iam','roles','create',$plan.scopedTokenRoleId,'--title=Jigeum scoped Publisher token','--description=Runtime identity may mint only its own scoped access token',
        '--permissions=iam.serviceAccounts.getAccessToken','--stage=GA') 'Single-permission role creation'
}
$role = Call-Gcloud @('iam','roles','describe',$plan.scopedTokenRoleId) 'Custom role verification'
if (@($role.includedPermissions).Count -ne 1 -or $role.includedPermissions[0] -ne 'iam.serviceAccounts.getAccessToken' -or $role.deleted -eq $true -or $role.stage -eq 'DISABLED') { throw 'Scoped token custom role contains unexpected permissions or is disabled' }
$null = Call-Gcloud @('iam','service-accounts','add-iam-policy-binding',$runtimeEmail,"--member=serviceAccount:$runtimeEmail","--role=$customRole",'--condition=None') 'Runtime self token binding'
Verify-ResourceBinding @('iam','service-accounts','get-iam-policy',$runtimeEmail) $customRole "serviceAccount:$runtimeEmail" 'Runtime self token'
Done 'single-permission token role bound only on the runtime identity to itself'

$null = Call-Gcloud @('logging','buckets','update','_Default','--location=global','--retention-days=30') 'Default request metadata retention'
$loggingBucket = Call-Gcloud @('logging','buckets','describe','_Default','--location=global') 'Request metadata retention verification'
if ($loggingBucket.retentionDays -ne 30) { throw 'Default logging retention is not 30 days' }
Done 'default request metadata retention verified at 30 days'

$budgets = @(Call-Gcloud @('billing','budgets','list',"--billing-account=$billingId") 'Budget inventory' $BudgetAccount $true)
$existingBudgets = @($budgets | Where-Object { $_.displayName -eq $budgetName })
if ($existingBudgets.Count -gt 1) { throw 'Duplicate named budgets exist; review manually before changing billing rules' }
if ($existingBudgets.Count -eq 0) {
    $budget = Call-Gcloud @('billing','budgets','create',"--billing-account=$billingId","--display-name=$budgetName",'--budget-amount=10000KRW','--calendar-period=month',
        "--filter-projects=projects/$($plan.projectNumber)",'--threshold-rule=percent=0.5,basis=current-spend','--threshold-rule=percent=0.8,basis=current-spend',
        '--threshold-rule=percent=1.0,basis=current-spend','--threshold-rule=percent=1.0,basis=forecasted-spend','--ownership-scope=billing-account') 'Monthly project-only budget creation' $BudgetAccount $true
} else { $budget = $existingBudgets[0] }
if ($budget.amount.specifiedAmount.currencyCode -ne 'KRW' -or $budget.amount.specifiedAmount.units -ne '10000' -or $budget.amount.specifiedAmount.nanos -gt 0 -or
    $budget.budgetFilter.calendarPeriod -ne 'MONTH' -or @($budget.budgetFilter.projects).Count -ne 1 -or $budget.budgetFilter.projects[0] -ne "projects/$($plan.projectNumber)" -or
    $budget.allUpdatesRule.disableDefaultIamRecipients -eq $true) { throw 'Budget amount/currency/project/period/default alert recipients differ from the reviewed plan' }
$actualRules = @($budget.thresholdRules | ForEach-Object { "$($_.thresholdPercent):$($_.spendBasis)" } | Sort-Object)
$requiredRules = @('0.5:CURRENT_SPEND','0.8:CURRENT_SPEND','1:CURRENT_SPEND','1:FORECASTED_SPEND') | Sort-Object
if (($actualRules -join '|') -ne ($requiredRules -join '|')) { throw 'Budget alert thresholds differ from the reviewed plan' }
Done 'monthly KRW10000 project-only alerts verified; alerts do not cap spending'
[ordered]@{ mode='bootstrap-verified'; project=$projectId; region=$plan.region; buildIdentity=$buildEmail; runtimeIdentity=$runtimeEmail;
    sourceStagingDirectory="$bucketUrl/source"; budgetAmountKRW=10000; hardSpendingCap=$false; inheritedAndUnexpectedIamAudited=$false;
    exportedServiceAccountKeys=$false; billingIdOrSecretPayloadPrinted=$false; playPermissionConfigured=$false; deployed=$false } | ConvertTo-Json
