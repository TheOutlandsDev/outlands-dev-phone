#Requires -Version 5.1
<#
Build the plain-Java Android launcher with official SDK tools, without Gradle.
Produces an aligned UNSIGNED APK. Signing and installation are separate steps.
Example:
  .\scripts\Build-Launcher.ps1 -SdkRoot C:\Android\Sdk -JavaHome C:\Java\jdk-17
#>
[CmdletBinding()]
param(
    [string]$SdkRoot = $env:ANDROID_SDK_ROOT,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$BuildToolsVersion = '36.1.0',
    [ValidateSet(36)][int]$ApiLevel = 36,
    [ValidateRange(26,36)][int]$MinSdk = 26,
    [ValidateRange(1,2147483647)][int]$VersionCode = 2,
    [ValidatePattern('^[0-9]+\.[0-9]+\.[0-9]+([.-][a-zA-Z0-9]+)*$')]
    [string]$VersionName = '0.2.0',
    [ValidateSet('dev.outlands.launcher')]
    [string]$ApplicationId = 'dev.outlands.launcher',
    [string]$SourceRoot = (Join-Path $PSScriptRoot '..\android\app\src\main'),
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\artifacts')
)
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($SdkRoot) -or [string]::IsNullOrWhiteSpace($JavaHome)) {
    throw 'Supply -SdkRoot and -JavaHome, or set ANDROID_SDK_ROOT and JAVA_HOME for this process.'
}
if ($MinSdk -gt $ApiLevel) { throw 'MinSdk cannot exceed ApiLevel.' }
$SdkRoot = [IO.Path]::GetFullPath($SdkRoot)
$JavaHome = [IO.Path]::GetFullPath($JavaHome)
$SourceRoot = [IO.Path]::GetFullPath($SourceRoot)
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$tools = Join-Path $SdkRoot ('build-tools\' + $BuildToolsVersion)
$androidJar = Join-Path $SdkRoot ('platforms\android-' + $ApiLevel + '\android.jar')
$javaExe = Join-Path $JavaHome 'bin\java.exe'
$javacExe = Join-Path $JavaHome 'bin\javac.exe'
$aapt2 = Join-Path $tools 'aapt2.exe'
$d8Jar = Join-Path $tools 'lib\d8.jar'
$zipalign = Join-Path $tools 'zipalign.exe'
$manifest = Join-Path $SourceRoot 'AndroidManifest.xml'
foreach ($path in @($javaExe,$javacExe,$aapt2,$d8Jar,$zipalign,$androidJar,$manifest)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw ('Required build file is missing: ' + $path) }
}
$res = Join-Path $SourceRoot 'res'
$javaSource = Join-Path $SourceRoot 'java'
if (-not (Test-Path -LiteralPath $res -PathType Container) -or
    -not (Test-Path -LiteralPath $javaSource -PathType Container)) {
    throw 'SourceRoot must contain AndroidManifest.xml, res and java.'
}
$runName = 'build-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8)
$run = Join-Path $OutputDirectory $runName
$classes = Join-Path $run 'classes'
$generated = Join-Path $run 'generated'
$dex = Join-Path $run 'dex'
foreach ($directory in @($run,$classes,$generated,$dex)) { [IO.Directory]::CreateDirectory($directory) | Out-Null }
$script:buildLog = Join-Path $run 'build.log'
$script:utf8 = New-Object Text.UTF8Encoding($false)
function Invoke-BuildTool {
    param([string]$FilePath, [string[]]$Arguments)
    $oldPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $lines = & $FilePath @Arguments 2>&1
        $toolExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $oldPreference }
    $text = ($lines | Out-String)
    [IO.File]::AppendAllText($script:buildLog, $text, $script:utf8)
    if (-not [string]::IsNullOrWhiteSpace($text)) { Write-Host $text.TrimEnd() }
    if ($toolExitCode -ne 0) { throw ('Build tool failed, exit ' + $toolExitCode + ': ' + [IO.Path]::GetFileName($FilePath)) }
}

# AAPT2 requires a manifest package. Add it to a build copy only; the source can
# retain its modern Android Gradle Plugin namespace configuration.
$manifestDocument = New-Object Xml.XmlDocument
$manifestDocument.Load($manifest)
$manifestDocument.DocumentElement.SetAttribute('package', $ApplicationId)
$buildManifest = Join-Path $run 'AndroidManifest.xml'
$manifestDocument.Save($buildManifest)
$compiledResources = Join-Path $run 'compiled-resources.zip'
$resourceApk = Join-Path $run 'resources.apk'
Invoke-BuildTool $aapt2 @('compile','--dir',$res,'-o',$compiledResources)
$linkArgs = @('link','-I',$androidJar,'--manifest',$buildManifest,'--java',$generated,
    '--min-sdk-version',[string]$MinSdk,'--target-sdk-version',[string]$ApiLevel,
    '--version-code',[string]$VersionCode,'--version-name',$VersionName,'--replace-version',
    '--auto-add-overlay','-R',$compiledResources,'-o',$resourceApk)
$assets = Join-Path $SourceRoot 'assets'
if (Test-Path -LiteralPath $assets -PathType Container) { $linkArgs += @('-A',$assets) }
Invoke-BuildTool $aapt2 $linkArgs
$sources = @((Get-ChildItem -LiteralPath $javaSource -Recurse -Filter '*.java' -File).FullName) +
    @((Get-ChildItem -LiteralPath $generated -Recurse -Filter '*.java' -File).FullName)
if ($sources.Count -eq 0) { throw 'No Java source files were found.' }
$sourceList = Join-Path $run 'java-sources.txt'
$sourceLines = @($sources | Sort-Object | ForEach-Object { '"' + $_.Replace('\','/') + '"' })
[IO.File]::WriteAllLines($sourceList, [string[]]$sourceLines, $script:utf8)
# JDK's Java 8 platform stubs supply LambdaMetafactory for javac; Android's
# stripped framework stubs do not. D8 handles the resulting Java 8 bytecode.
Invoke-BuildTool $javacExe @('-encoding','UTF-8','--release','8',
    '-classpath',$androidJar,'-d',$classes,('@' + $sourceList))
Add-Type -AssemblyName System.IO.Compression.FileSystem
$classesZip = Join-Path $run 'classes.zip'
[IO.Compression.ZipFile]::CreateFromDirectory($classes, $classesZip, [IO.Compression.CompressionLevel]::Optimal, $false)
Invoke-BuildTool $javaExe @('-cp',$d8Jar,'com.android.tools.r8.D8','--release','--min-api',
    [string]$MinSdk,'--lib',$androidJar,'--output',$dex,$classesZip)
$dexFiles = @(Get-ChildItem -LiteralPath $dex -Filter 'classes*.dex' -File)
if ($dexFiles.Count -eq 0) { throw 'D8 did not produce a dex file.' }
$unaligned = Join-Path $run 'launcher-unaligned.apk'
Copy-Item -LiteralPath $resourceApk -Destination $unaligned
$apk = [IO.Compression.ZipFile]::Open($unaligned, [IO.Compression.ZipArchiveMode]::Update)
try {
    foreach ($dexFile in $dexFiles) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apk, $dexFile.FullName,
            $dexFile.Name, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $apk.Dispose() }
$unsignedApk = Join-Path $run 'outlands-dev-phone-unsigned.apk'
Invoke-BuildTool $zipalign @('-P','16','4',$unaligned,$unsignedApk)
Invoke-BuildTool $zipalign @('-c','-P','16','4',$unsignedApk)
Invoke-BuildTool $aapt2 @('dump','badging',$unsignedApk)
$receipt = [ordered]@{
    status = 'ALIGNED_UNSIGNED'
    applicationId = $ApplicationId
    apiLevel = $ApiLevel
    minSdk = $MinSdk
    versionCode = $VersionCode
    versionName = $VersionName
    buildToolsVersion = $BuildToolsVersion
    javaSourceCount = $sources.Count
    unsignedApk = $unsignedApk
    sha256 = (Get-FileHash -LiteralPath $unsignedApk -Algorithm SHA256).Hash.ToLowerInvariant()
    builtAtUtc = [DateTime]::UtcNow.ToString('o')
    signingPerformed = $false
    installationPerformed = $false
}
$receiptJson = $receipt | ConvertTo-Json -Depth 5
[IO.File]::WriteAllText((Join-Path $run 'build-result.json'), $receiptJson, $script:utf8)
$receiptJson
