param(
    [ValidateSet('internal', 'paid-candidate', 'closed-test')][string]$Purpose = 'internal',
    [string]$SigningDirectory = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.jigeum/signing'),
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [Parameter(Mandatory = $true)][string]$Destination,
    [switch]$AstBabbleSupport,
    [switch]$InfantPresenceSupport,
    [switch]$InfantCaptionSupport,
    [string]$InfantArmProof,
    [string]$ProofPython
)
$ErrorActionPreference = 'Stop'
if ($AstBabbleSupport -and $Purpose -ne 'closed-test') {
    throw 'AST babble support is valid only for closed-test beta candidates.'
}
if ($InfantCaptionSupport -and (-not $InfantPresenceSupport -or -not $AstBabbleSupport -or $Purpose -ne 'closed-test')) {
    throw 'Caption support requires closed-test, AstBabbleSupport and InfantPresenceSupport.'
}
if ($InfantPresenceSupport) {
    if ($Purpose -ne 'closed-test' -or -not $AstBabbleSupport) {
        throw 'Infant presence requires closed-test and AstBabbleSupport.'
    }
    if (-not $InfantArmProof -or -not $ProofPython) { throw 'Provide actual ARM proof and ProofPython.' }
    $InfantArmProof = [IO.Path]::GetFullPath($InfantArmProof)
    $ProofPython = [IO.Path]::GetFullPath($ProofPython)
    $proofArguments = @((Join-Path $PSScriptRoot 'verify_infant_presence_arm.py'), $InfantArmProof)
    if ($InfantCaptionSupport) { $proofArguments += '--infant-caption-candidate' }
    & $ProofPython @proofArguments
    if ($LASTEXITCODE -ne 0) { throw 'Infant presence ARM proof rejected; no signing or build started.' }
} elseif ($InfantArmProof -or $ProofPython) {
    throw 'ARM proof arguments require InfantPresenceSupport.'
}
if (-not $JavaHome -or -not $AndroidSdk) { throw 'Set JAVA_HOME and ANDROID_HOME or pass their paths.' }
if (Test-Path -LiteralPath $Destination) { throw 'Destination already exists; preserve the old artifact.' }
if ($Purpose -eq 'closed-test' -and [IO.Path]::GetFileName($Destination) -notmatch '-beta-code\d+\.aab$') {
    throw 'Closed-test artifacts must be visibly named *-beta-codeN.aab, never a production release.'
}
if ($Purpose -eq 'paid-candidate') {
    if (-not $env:JIGEUM_PLAY_VERIFY_URL -or -not $env:JIGEUM_PLAY_ENTITLEMENT_PUBLIC_KEY) {
        throw 'Paid candidates require the actual HTTPS verification URL and public key.'
    }
    if (-not $env:JIGEUM_PLAY_VERIFY_URL.StartsWith('https://', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Verifier URL must use HTTPS.'
    }
} elseif ($Purpose -eq 'internal' -and ($env:JIGEUM_PLAY_VERIFY_URL -or $env:JIGEUM_PLAY_ENTITLEMENT_PUBLIC_KEY)) {
    throw 'Internal bootstrap is explicitly billing-disabled. Use paid-candidate for configured billing.'
}
$keystore = Join-Path $SigningDirectory 'jigeum-upload.p12'
$secretFile = Join-Path $SigningDirectory 'password.dpapi'
if (-not (Test-Path -LiteralPath $keystore) -or -not (Test-Path -LiteralPath $secretFile)) {
    throw 'Prepare or restore the real upload key outside Git first.'
}
$variables = @('JAVA_HOME','ANDROID_HOME','ANDROID_SDK_ROOT','PATH', 'MOA_UPLOAD_KEYSTORE', 'MOA_UPLOAD_KEY_ALIAS', 'MOA_UPLOAD_STORE_PASSWORD', 'MOA_UPLOAD_KEY_PASSWORD')
$original = @{}
foreach ($name in $variables) { $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$securePassword = Get-Content -LiteralPath $secretFile -Raw | ConvertTo-SecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../outputs/MoaAndroid'))
$final = [IO.Path]::GetFullPath($Destination)
try {
    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_HOME = $AndroidSdk
    $env:ANDROID_SDK_ROOT = $AndroidSdk
    $env:PATH = "$JavaHome\bin;$env:PATH"
    $env:MOA_UPLOAD_KEYSTORE = [IO.Path]::GetFullPath($keystore)
    $env:MOA_UPLOAD_KEY_ALIAS = 'jigeum-upload'
    $env:MOA_UPLOAD_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $env:MOA_UPLOAD_KEY_PASSWORD = $env:MOA_UPLOAD_STORE_PASSWORD
    Push-Location $project
    try {
        $variant = if ($Purpose -eq 'closed-test') { 'Beta' } else { 'Release' }
        $gradleArguments = @(":app:bundle$variant", ":app:assemble$variant", '--max-workers=1', '--console=plain', '--no-daemon')
        if ($AstBabbleSupport) { $gradleArguments += '-PagattokAstBeta=true' }
        if ($InfantPresenceSupport) {
            $gradleArguments += @('-PagattokInfantPresenceBeta=true', "-PagattokInfantArmProof=$InfantArmProof", "-PagattokProofPython=$ProofPython")
        }
        if ($InfantCaptionSupport) { $gradleArguments += '-PagattokInfantCaptionBeta=true' }
        & ./gradlew.bat @gradleArguments
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    } finally { Pop-Location }
    $variantDirectory = $variant.ToLowerInvariant()
    $aab = Join-Path $project "app/build/outputs/bundle/$variantDirectory/app-$variantDirectory.aab"
    New-Item -ItemType Directory -Path (Split-Path -Parent $final) -Force | Out-Null
    # Use the JDK's documented AAB signer as the final writer. AGP places its
    # manifest at the end of this archive; JarInputStream then reports it missing.
    # jarsigner writes the signature metadata first, preserving payload bytes.
    & (Join-Path $JavaHome 'bin/jarsigner.exe') '-J-Duser.language=en' `
        -keystore $keystore -storepass:env MOA_UPLOAD_STORE_PASSWORD `
        -keypass:env MOA_UPLOAD_KEY_PASSWORD -sigalg SHA384withRSA -digestalg SHA-256 `
        -signedjar $final $aab jigeum-upload
    if ($LASTEXITCODE -ne 0) { throw 'Final bundle signing failed.' }
    $verification = & (Join-Path $JavaHome 'bin/jarsigner.exe') '-J-Duser.language=en' -verify $final 2>&1
    $verificationExit = $LASTEXITCODE
    [IO.File]::WriteAllText($final + '.signature.txt', ($verification -join [Environment]::NewLine))
    Write-Output $verification
    if ($verificationExit -ne 0 -or ($verification -join "`n") -notmatch 'jar verified\.') {
        throw 'Bundle signature verification failed or no signed JAR was verified.'
    }
    # Android uses self-signed upload certificates; trust-chain warnings are expected.
    [pscustomobject]@{
        purpose = $Purpose
        playTrack = if ($Purpose -eq 'closed-test') { 'closed-test-only' } else { 'release-candidate' }
        billingConfigured = ($Purpose -eq 'paid-candidate')
        artifact = $final
        bytes = (Get-Item -LiteralPath $final).Length
        sha256 = (Get-FileHash -LiteralPath $final -Algorithm SHA256).Hash.ToLowerInvariant()
    } | ConvertTo-Json
} finally {
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $original[$name], 'Process') }
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    $securePassword.Dispose()
}
