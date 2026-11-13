#Requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$UnsignedApk,
    [Parameter(Mandatory=$true)][string]$SignedApk,
    [Parameter(Mandatory=$true)][string]$SdkRoot,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$KeyStore,
    [Parameter(Mandatory=$true)][string]$PasswordFile,
    [string]$KeyAlias = 'outlands-home',
    [string]$BuildToolsVersion = '36.1.0'
)
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$java = Join-Path $JavaHome 'bin\java.exe'
$signer = Join-Path $SdkRoot ('build-tools\' + $BuildToolsVersion + '\lib\apksigner.jar')
foreach ($path in @($java,$signer,$UnsignedApk,$KeyStore,$PasswordFile)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw ('Missing required file: ' + $path) }
}
if (Test-Path -LiteralPath $SignedApk) { throw 'Choose a new signed APK path; existing output was retained.' }
$priorPassword = [Environment]::GetEnvironmentVariable('OUTLANDS_APK_SIGN_PASSWORD','Process')
try {
    $password = [IO.File]::ReadAllText([IO.Path]::GetFullPath($PasswordFile)).TrimEnd([char[]]"`r`n")
    if ([string]::IsNullOrWhiteSpace($password)) { throw 'The private signing password file is empty.' }
    [Environment]::SetEnvironmentVariable('OUTLANDS_APK_SIGN_PASSWORD',$password,'Process')
    $password = $null
    & $java -jar $signer sign --ks $KeyStore --ks-key-alias $KeyAlias `
        --ks-pass env:OUTLANDS_APK_SIGN_PASSWORD --key-pass env:OUTLANDS_APK_SIGN_PASSWORD `
        --out $SignedApk $UnsignedApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
    & $java -jar $signer verify --verbose --print-certs $SignedApk
    if ($LASTEXITCODE -ne 0) { throw 'Signed APK verification failed.' }
    Get-FileHash -LiteralPath $SignedApk -Algorithm SHA256
} finally {
    [Environment]::SetEnvironmentVariable('OUTLANDS_APK_SIGN_PASSWORD',$priorPassword,'Process')
    $password = $null
}
