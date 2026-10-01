import { mkdtempSync, writeFileSync, readFileSync, unlinkSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { generateKeyPairSync, randomBytes } from 'node:crypto';
import { spawnSync } from 'node:child_process';

const directory = mkdtempSync(join(tmpdir(), 'jigeum-bootstrap-qa-'));
const root = resolve(import.meta.dirname, '../..');
const script = join(root, 'tools/deploy/bootstrap-cloud.ps1');
const mock = join(directory, 'fake-gcloud.ps1');
const key = join(directory, 'synthetic-signing.pem');
const wrongKey = join(directory, 'synthetic-rsa.pem');
const hash = join(directory, 'synthetic-review-hash.txt');
const billing = join(directory, 'synthetic-billing.id');
const statePath = join(directory, 'state.json');
const callPath = join(directory, 'calls.jsonl');
writeFileSync(key, generateKeyPairSync('ec', { namedCurve: 'prime256v1' }).privateKey.export({ type: 'pkcs8', format: 'pem' }), { mode: 0o600 });
writeFileSync(wrongKey, generateKeyPairSync('rsa', { modulusLength: 2048 }).privateKey.export({ type: 'pkcs8', format: 'pem' }), { mode: 0o600 });
writeFileSync(hash, randomBytes(32).toString('hex'), { mode: 0o600 });
writeFileSync(billing, Array.from({ length: 3 }, () => randomBytes(3).toString('hex').toUpperCase()).join('-'), { mode: 0o600 });
writeFileSync(mock, String.raw`
$a=@($args); $scenario=$env:JIGEUM_BOOTSTRAP_QA_SCENARIO
$s=Get-Content -LiteralPath $env:JIGEUM_BOOTSTRAP_QA_STATE -Raw -Encoding UTF8 | ConvertFrom-Json
$logArgs=@($a | ForEach-Object { if ($_ -like '--billing-account=*') { '--billing-account=[synthetic-redacted]' } else { $_ } })
[System.IO.File]::AppendAllText($env:JIGEUM_BOOTSTRAP_QA_LOG, (ConvertTo-Json -InputObject $logArgs -Compress) + [Environment]::NewLine)
$global:LASTEXITCODE=0
function Json($v){ConvertTo-Json -InputObject $v -Depth 10 -Compress}
function Save{[System.IO.File]::WriteAllText($env:JIGEUM_BOOTSTRAP_QA_STATE,($s|ConvertTo-Json -Depth 8))}
function Fail{ $global:LASTEXITCODE=1; Write-Error 'Synthetic denied operation' -ErrorAction Continue }
function Policy($role,$member){Json @{bindings=@(@{role=$role;members=@($member)})}}
$runtime='jigeum-play-runtime@jigeum-play-20261001.iam.gserviceaccount.com'; $build='jigeum-play-build@jigeum-play-20261001.iam.gserviceaccount.com'
$p=($a|Select-Object -First 3)-join ' '
if($a[0] -eq 'services' -and $a[1] -eq 'enable'){if($scenario -eq 'api-fails'){Fail;return};$s.apis=$true;Save;Json @{};return}
if($p -eq 'projects describe jigeum-play-20261001'){Json @{projectNumber='795224750373';lifecycleState='ACTIVE'};return}
if($p -eq 'billing projects describe'){Json @{billingEnabled=($scenario -ne 'billing-disabled');billingAccountName=('billingAccounts/'+[System.IO.File]::ReadAllText($env:JIGEUM_BOOTSTRAP_QA_BILLING).Trim())};return}
if($p -eq 'services list --enabled'){Json @(@('run','cloudbuild','artifactregistry','secretmanager','iamcredentials','androidpublisher','iam','storage','logging','billingbudgets') | ForEach-Object {@{config=@{name=($_+'.googleapis.com')}}});return}
if($p -eq 'iam service-accounts list'){if($s.identities){Json @(@{email=$runtime},@{email=$build})}else{'[]'};return}
if($p -eq 'iam service-accounts create'){$s.identities=$true;Save;Json @{};return}
if($p -eq 'iam service-accounts describe'){Json @{email=$a[3];disabled=$false};return}
if($p -eq 'artifacts repositories list'){if($s.repository){Json @(@{name='projects/jigeum-play-20261001/locations/asia-northeast3/repositories/jigeum'})}else{'[]'};return}
if($p -eq 'artifacts repositories create'){$s.repository=$true;Save;Json @{};return}
if($p -eq 'artifacts repositories describe'){Json @{name='projects/jigeum-play-20261001/locations/asia-northeast3/repositories/jigeum';format='DOCKER'};return}
if($p -eq 'storage buckets list'){if($s.bucket){Json @(@{name='jigeum-play-20261001-build-source'})}else{'[]'};return}
if($p -eq 'storage buckets create'){$s.bucket=$true;Save;Json @{};return}
if($p -eq 'storage buckets describe'){Json @{projectNumber='795224750373';location='ASIA-NORTHEAST3';iamConfiguration=@{uniformBucketLevelAccess=@{enabled=($scenario -ne 'bucket-unsafe')};publicAccessPrevention='enforced'};lifecycle=@{rule=@(@{action=@{type='Delete'};condition=@{age=$(if($scenario -eq 'bucket-lifecycle-drift'){30}else{7})}})};softDeletePolicy=@{retentionDurationSeconds=$(if($scenario -eq 'bucket-soft-delete-drift'){'604800'}else{'0'})}};return}
if($p -eq 'storage buckets add-iam-policy-binding'){Json @{};return}
if($p -eq 'storage buckets get-iam-policy'){Policy 'roles/storage.objectViewer' ('serviceAccount:'+$build);return}
if($p -eq 'artifacts repositories add-iam-policy-binding'){Json @{};return}
if($p -eq 'artifacts repositories get-iam-policy'){Policy 'roles/artifactregistry.writer' ('serviceAccount:'+$build);return}
if($p -eq 'projects add-iam-policy-binding jigeum-play-20261001'){Json @{};return}
if($p -eq 'projects get-iam-policy jigeum-play-20261001'){Policy 'roles/logging.logWriter' ('serviceAccount:'+$build);return}
if($a[0] -eq 'secrets' -and $a[1] -eq 'list'){if($s.secrets){Json @(@{name='projects/795224750373/secrets/jigeum-entitlement-signing'},@{name='projects/795224750373/secrets/jigeum-review-code-sha256'})}else{'[]'};return}
if($a[0] -eq 'secrets' -and $a[1] -eq 'create'){$s.secrets=$true;Save;Json @{};return}
if($a[0] -eq 'secrets' -and $a[1] -eq 'describe'){Json @{replication=@{userManaged=@{replicas=@(@{location='asia-northeast3'})}}};return}
if($p -eq 'secrets versions list'){if($s.versions){Json @(@{name='projects/795224750373/secrets/synthetic/versions/1';state='ENABLED'})}else{'[]'};return}
if($p -eq 'secrets versions add'){Json @{name=('projects/795224750373/secrets/'+$a[3]+'/versions/1')};return}
if($p -eq 'secrets versions describe'){Json @{state=$(if($scenario -eq 'version-disabled'){'DISABLED'}else{'ENABLED'})};return}
if($a[0] -eq 'secrets' -and $a[1] -eq 'add-iam-policy-binding'){Json @{};return}
if($a[0] -eq 'secrets' -and $a[1] -eq 'get-iam-policy'){Policy 'roles/secretmanager.secretAccessor' ('serviceAccount:'+$runtime);return}
if($p -eq 'iam roles list'){if($s.role){Json @(@{name='projects/jigeum-play-20261001/roles/JigeumScopedTokenMinter'})}else{'[]'};return}
if($p -eq 'iam roles create'){$s.role=$true;Save;Json @{};return}
if($p -eq 'iam roles describe'){Json @{includedPermissions=@('iam.serviceAccounts.getAccessToken') + $(if($scenario -eq 'role-too-wide'){@('iam.serviceAccounts.signJwt')}else{@()});stage='GA';deleted=$false};return}
if($p -eq 'iam service-accounts add-iam-policy-binding'){Json @{};return}
if($p -eq 'iam service-accounts get-iam-policy'){Policy 'projects/jigeum-play-20261001/roles/JigeumScopedTokenMinter' ('serviceAccount:'+$runtime);return}
if($p -eq 'logging buckets update'){Json @{};return}
if($p -eq 'logging buckets describe'){Json @{retentionDays=30};return}
function Budget{Json @{displayName='jigeum-monthly-krw-10000';amount=@{specifiedAmount=@{currencyCode='KRW';units=$(if($scenario -eq 'budget-mismatch'){'20000'}else{'10000'})}};budgetFilter=@{calendarPeriod='MONTH';projects=@('projects/795224750373')};allUpdatesRule=@{disableDefaultIamRecipients=$false};thresholdRules=@(@{thresholdPercent=0.5;spendBasis='CURRENT_SPEND'},@{thresholdPercent=0.8;spendBasis='CURRENT_SPEND'},@{thresholdPercent=1;spendBasis='CURRENT_SPEND'},@{thresholdPercent=1;spendBasis='FORECASTED_SPEND'})}}
if($p -eq 'billing budgets list'){if($s.budget){Budget}else{'[]'};return}
if($p -eq 'billing budgets create'){$s.budget=$true;Save;Budget;return}
Fail
`, 'utf8');

const cases = [
  { name: 'dry-run', dry: true, pass: true, noCalls: true },
  { name: 'confirmation-missing', manual: false, pass: false, noCalls: true, error: 'explicit infrastructure preparation' },
  { name: 'wrong-signing-curve', wrongKey: true, pass: false, noCalls: true, error: 'valid EC P-256' },
  { name: 'billing-disabled', pass: false, error: 'Project billing is absent', noEnable: true },
  { name: 'api-fails', pass: false, error: 'API enablement failed', noIdentityCreate: true },
  { name: 'bucket-unsafe', pass: false, error: 'bucket ownership, location or privacy', noVersionAdd: true },
  { name: 'bucket-lifecycle-drift', existing: true, reuse: true, pass: false, error: 'exact reviewed Delete age7 lifecycle', noVersionAdd: true },
  { name: 'bucket-soft-delete-drift', existing: true, reuse: true, pass: false, error: 'soft delete must be disabled', noVersionAdd: true },
  { name: 'secret-reuse-unconfirmed', existing: true, reuse: false, pass: false, error: 'explicit known-version reuse', noVersionAdd: true },
  { name: 'version-disabled', existing: true, reuse: true, pass: false, error: 'version 1 is not ENABLED', noVersionAdd: true },
  { name: 'role-too-wide', existing: true, reuse: true, pass: false, error: 'unexpected permissions', noBudgetCreate: true },
  { name: 'budget-mismatch', existing: true, reuse: true, pass: false, error: 'Budget amount/currency/project', noBudgetCreate: true },
  { name: 'new-resource-success', pass: true },
  { name: 'known-resource-reuse', existing: true, reuse: true, pass: true, noVersionAdd: true, noIdentityCreate: true, noBudgetCreate: true }
];
const results = [];
try {
  const shells = process.env.JIGEUM_BOOTSTRAP_QA_SHELL ? [process.env.JIGEUM_BOOTSTRAP_QA_SHELL] : ['powershell.exe', 'pwsh.exe'];
  for (const shell of shells) for (const item of cases) {
    writeFileSync(statePath, JSON.stringify({ apis: !!item.existing, identities: !!item.existing, repository: !!item.existing, bucket: !!item.existing,
      secrets: !!item.existing, versions: !!item.existing, role: !!item.existing, budget: !!item.existing }));
    writeFileSync(callPath, '');
    const args = ['-NoProfile', '-File', script, '-GcloudPath', mock];
    if (!item.dry) args.push('-Execute', '-SigningKeyPath', item.wrongKey ? wrongKey : key, '-ReviewHashPath', hash, '-BillingAccountIdFile', billing);
    if (item.manual !== false) args.push('-OperationalPreparationConfirmed', '-BillingCurrencyKRWConfirmed');
    if (item.reuse) args.push('-ReuseExistingSecretVersionsConfirmed');
    const run = spawnSync(shell, args, { encoding: 'utf8', timeout: 30_000,
      env: { ...process.env, JIGEUM_BOOTSTRAP_QA_SCENARIO: item.name, JIGEUM_BOOTSTRAP_QA_STATE: statePath,
        JIGEUM_BOOTSTRAP_QA_LOG: callPath, JIGEUM_BOOTSTRAP_QA_BILLING: billing } });
    const calls = readFileSync(callPath, 'utf8').trim().split('\n').filter(Boolean).map(line => JSON.parse(line));
    const has = prefix => calls.some(call => call.slice(0, prefix.length).join(' ') === prefix.join(' '));
    let pass = (run.status === 0) === item.pass && (!item.error || run.stderr.includes(item.error));
    if (item.noCalls) pass &&= calls.length === 0;
    if (item.noEnable) pass &&= !has(['services', 'enable']);
    if (item.noIdentityCreate) pass &&= !has(['iam', 'service-accounts', 'create']);
    if (item.noVersionAdd) pass &&= !has(['secrets', 'versions', 'add']);
    if (item.noBudgetCreate) pass &&= !has(['billing', 'budgets', 'create']);
    if (item.pass && !item.dry) pass &&= run.stdout.includes('bootstrap-verified');
    pass &&= !has(['iam', 'service-accounts', 'keys']) && !has(['run', 'deploy']) && !has(['billing', 'projects', 'link']);
    pass &&= calls.filter(c => c[0] === 'projects' && c[1] === 'add-iam-policy-binding')
      .every(c => c.includes('--member=serviceAccount:jigeum-play-build@jigeum-play-20261001.iam.gserviceaccount.com') && c.includes('--role=roles/logging.logWriter'));
    pass &&= calls.every(call => call.every(argument => typeof argument === 'string'));
    pass &&= calls.every(call => call.includes('--no-log-http') && call.includes('--verbosity=error'));
    pass &&= calls.filter(c => c[0] === 'billing' && c[1] === 'budgets').every(c =>
      c.includes('--account=bsfire1237@gmail.com') && c.includes('--billing-project=jigeum-play-20261001') && !c.some(a => a.startsWith('--project=')));
    pass &&= calls.filter(c => !(c[0] === 'billing' && c[1] === 'budgets')).every(c => c.includes('--account=studioyeolmae.com@gmail.com'));
    results.push({ shell, case: item.name, pass, fakeCalls: calls.length });
    console.log(`${shell}/${item.name}: ${pass ? 'PASS' : 'FAIL'}`);
    if (!pass) console.log(JSON.stringify({ exit: run.status, reason: run.stderr.slice(-1200) }));
  }
  const report = { tests: results.length, passed: results.filter(r => r.pass).length, externalCloudOperations: 0,
    operationalSecretsRead: false, keys: 'Ephemeral synthetic signing fixtures only; no account JSON', results };
  writeFileSync(join(root, 'tools/deploy/QA-bootstrap-20261001.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
  if (report.passed !== report.tests) process.exitCode = 1;
} finally {
  for (const file of [mock, key, wrongKey, hash, billing, statePath, callPath]) { try { unlinkSync(file); } catch {} }
  try { rmdirSync(directory); } catch {}
}
