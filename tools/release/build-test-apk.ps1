param(
    [string]$SigningDirectory = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.jigeum/signing'),
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [Parameter(Mandatory = $true)][string]$Destination
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome -or -not $AndroidSdk) { throw 'Set JAVA_HOME and ANDROID_HOME or pass their paths.' }
if (Test-Path -LiteralPath $Destination) { throw 'Destination exists; preserve the old artifact.' }
if ([IO.Path]::GetExtension($Destination) -ne '.apk') { throw 'Test distribution is APK-only, never a Play bundle.' }
$keystore = Join-Path $SigningDirectory 'jigeum-upload.p12'
$secretFile = Join-Path $SigningDirectory 'password.dpapi'
if (-not (Test-Path -LiteralPath $keystore) -or -not (Test-Path -LiteralPath $secretFile)) { throw 'Restore the existing upload key outside Git first.' }
$variables = @('JAVA_HOME','ANDROID_HOME','ANDROID_SDK_ROOT','PATH','MOA_UPLOAD_KEYSTORE','MOA_UPLOAD_KEY_ALIAS','MOA_UPLOAD_STORE_PASSWORD','MOA_UPLOAD_KEY_PASSWORD')
$original = @{}
foreach ($name in $variables) { $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$securePassword = Get-Content -LiteralPath $secretFile -Raw | ConvertTo-SecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../outputs/MoaAndroid'))
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
        & ./gradlew.bat :app:assemblePreview --max-workers=1 --console=plain --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Preview build failed.' }
    } finally { Pop-Location }
    $apk = Join-Path $project 'app/build/outputs/apk/preview/app-preview.apk'
    $final = [IO.Path]::GetFullPath($Destination)
    New-Item -ItemType Directory -Path (Split-Path -Parent $final) -Force | Out-Null
    Copy-Item -LiteralPath $apk -Destination $final
    [pscustomobject]@{
        purpose = 'full-access-sideload-test'
        playSubmission = $false
        artifact = $final
        bytes = (Get-Item -LiteralPath $final).Length
        sha256 = (Get-FileHash -LiteralPath $final -Algorithm SHA256).Hash.ToLowerInvariant()
    } | ConvertTo-Json
} finally {
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $original[$name], 'Process') }
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    $securePassword.Dispose()
}
