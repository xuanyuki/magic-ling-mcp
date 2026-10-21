param(
    [Parameter(Mandatory=$true)][int]$ProcessId,
    [string]$Jdk = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
if ($Jdk) {
    $javac = Join-Path $Jdk 'bin\javac.exe'
    $jar = Join-Path $Jdk 'bin\jar.exe'
    $java = Join-Path $Jdk 'bin\java.exe'
    foreach ($tool in @($javac, $jar, $java)) {
        if (-not (Test-Path -LiteralPath $tool)) { throw "Java tool not found: $tool" }
    }
} else {
    foreach ($name in @('javac', 'jar', 'java')) {
        $command = Get-Command "$name.exe" -ErrorAction SilentlyContinue
        if (-not $command) { $command = Get-Command $name -ErrorAction Stop }
        Set-Variable -Name $name -Value $command.Source
    }
}
$archive = Join-Path $PSScriptRoot 'dist\game-text-bridge.jar'
$fingerprint = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
$loadedDirectory = Join-Path $PSScriptRoot 'out\loaded'
New-Item -ItemType Directory -Force -Path $loadedDirectory | Out-Null
$loaded = Join-Path $loadedDirectory "bridge-$fingerprint.jar"
if (-not (Test-Path -LiteralPath $loaded)) { Copy-Item -LiteralPath $archive -Destination $loaded }
$helperName = 'UpdateBridge_' + $fingerprint.Substring(0,12)
$helperJar = Join-Path $loadedDirectory "$helperName.jar"
$manifest = Join-Path $PSScriptRoot "out\$helperName.mf"
$helperSource = Join-Path $PSScriptRoot "out\$helperName.java"
$helperText = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'tools\RecoverBridge.java') -Raw).Replace('public final class RecoverBridge', "public final class $helperName")
Set-Content -LiteralPath $helperSource -Value $helperText -Encoding utf8
& $javac --release 11 -encoding UTF-8 -d (Join-Path $PSScriptRoot 'out') $helperSource
if ($LASTEXITCODE -ne 0) { throw 'Update helper compilation failed' }
Set-Content -LiteralPath $manifest -Encoding ascii -Value "Manifest-Version: 1.0`nAgent-Class: $helperName`nCan-Redefine-Classes: true`n`n"
& $jar cfm $helperJar $manifest -C (Join-Path $PSScriptRoot 'out') "$helperName.class"
if ($LASTEXITCODE -ne 0) { throw 'Update helper packaging failed' }
& $java --add-modules jdk.attach -cp (Join-Path $PSScriptRoot 'out') Attach $ProcessId $helperJar $loaded
if ($LASTEXITCODE -ne 0) { throw 'Update failed; no game restart has been requested' }
