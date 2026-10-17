param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$secretDir = Join-Path $root '.local/signing'
$properties = Join-Path $root 'signing.properties'
$keyPath = Join-Path $secretDir 'brnv-upload.jks'
if ((Test-Path $properties) -or (Test-Path $keyPath)) {
    throw 'Signing material already exists. Refusing to overwrite it.'
}
$keytool = Join-Path $JavaHome 'bin/keytool.exe'
if (!(Test-Path $keytool)) { throw 'Set JAVA_HOME to a JDK before running this script.' }
New-Item -ItemType Directory -Force -Path $secretDir | Out-Null
$owner = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$acl = [Security.AccessControl.DirectorySecurity]::new()
$acl.SetAccessRuleProtection($true, $false)
$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($owner, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
Set-Acl -LiteralPath $secretDir -AclObject $acl
$bytes = [byte[]]::new(32)
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$env:BRNV_UPLOAD_PASSWORD = [Convert]::ToBase64String($bytes)
try {
    & $keytool -genkeypair -keystore $keyPath -storetype JKS -alias brnv-upload -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=BRNV Currency Converter' -storepass:env BRNV_UPLOAD_PASSWORD -keypass:env BRNV_UPLOAD_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
    $text = "storeFile=.local/signing/brnv-upload.jks`nkeyAlias=brnv-upload`nstorePassword=$env:BRNV_UPLOAD_PASSWORD`nkeyPassword=$env:BRNV_UPLOAD_PASSWORD`n"
    [IO.File]::WriteAllText($properties, $text)
    $fileAcl = [Security.AccessControl.FileSecurity]::new()
    $fileAcl.SetAccessRuleProtection($true, $false)
    $fileAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($owner, 'FullControl', 'Allow'))
    Set-Acl -LiteralPath $properties -AclObject $fileAcl
    Write-Output 'Upload key created. Back up .local/signing and signing.properties securely; never commit them.'
} finally {
    Remove-Item Env:BRNV_UPLOAD_PASSWORD -ErrorAction SilentlyContinue
}
