param(
    [string]$SigningDirectory = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.jigeum/signing'),
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) { throw 'Set JAVA_HOME or pass -JavaHome.' }
$keytool = Join-Path $JavaHome 'bin/keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { throw 'keytool.exe not found.' }
$directory = [IO.Path]::GetFullPath($SigningDirectory)
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($directory.StartsWith($repository.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase) -or $directory -eq $repository) {
    throw 'Signing material must be outside the repository.'
}
$keystore = Join-Path $directory 'jigeum-upload.p12'
$secretFile = Join-Path $directory 'password.dpapi'
$certificate = Join-Path $directory 'upload-certificate.pem'
if ((Test-Path -LiteralPath $keystore) -or (Test-Path -LiteralPath $secretFile)) {
    throw 'Existing signing material found. Reuse it; do not generate a replacement.'
}
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
$acl = New-Object Security.AccessControl.DirectorySecurity
$acl.SetOwner($identity)
$acl.SetAccessRuleProtection($true, $false)
foreach ($sid in @($identity, (New-Object Security.Principal.SecurityIdentifier('S-1-5-18')))) {
    $rule = New-Object Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
    $acl.AddAccessRule($rule)
}
Set-Acl -LiteralPath $directory -AclObject $acl
$randomBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
$password = [Convert]::ToBase64String($randomBytes)
$securePassword = ConvertTo-SecureString $password -AsPlainText -Force
try {
    $env:JIGEUM_LOCAL_SIGNING_PASSWORD = $password
    & $keytool -genkeypair -keystore $keystore -storetype PKCS12 -alias jigeum-upload `
        -keyalg RSA -keysize 3072 -validity 10000 `
        -dname 'CN=Choi Yongwoo, O=Jigeum, C=KR' `
        -storepass:env JIGEUM_LOCAL_SIGNING_PASSWORD -keypass:env JIGEUM_LOCAL_SIGNING_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Upload key generation failed. Inspect local partial files before retrying.' }
    [IO.File]::WriteAllText($secretFile, ($securePassword | ConvertFrom-SecureString))
    & $keytool -exportcert -rfc -keystore $keystore -alias jigeum-upload `
        -storepass:env JIGEUM_LOCAL_SIGNING_PASSWORD -file $certificate
    if ($LASTEXITCODE -ne 0) { throw 'Certificate export failed.' }
    & $keytool -list -v -keystore $keystore -alias jigeum-upload -storepass:env JIGEUM_LOCAL_SIGNING_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Keystore verification failed.' }
    Write-Output "Local upload keystore created outside Git: $keystore"
    Write-Output 'Password is Windows DPAPI protected for this Windows user. No Play key binding has occurred.'
    Write-Output 'A separate portable, encrypted backup of BOTH keystore and password is required before public release.'
} finally {
    Remove-Item Env:JIGEUM_LOCAL_SIGNING_PASSWORD -ErrorAction SilentlyContinue
    $password = $null
    $securePassword.Dispose()
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
}
