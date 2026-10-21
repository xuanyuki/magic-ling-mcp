param(
    [Parameter(Mandatory=$true)][int]$ProcessId,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'output'),
    [string]$Jdk = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
if ($Jdk) {
    $java = Join-Path $Jdk 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $java)) { throw "Java tool not found: $java" }
} else {
    $command = Get-Command java.exe -ErrorAction SilentlyContinue
    if (-not $command) { $command = Get-Command java -ErrorAction Stop }
    $java = $command.Source
}
$agent = Join-Path $PSScriptRoot 'dist\game-text-bridge.jar'
if (-not (Test-Path -LiteralPath $agent)) { throw 'Run build.ps1 first' }
$loaded = Join-Path $PSScriptRoot 'out\loaded'
New-Item -ItemType Directory -Force -Path $loaded | Out-Null
$fingerprint = (Get-FileHash -LiteralPath $agent -Algorithm SHA256).Hash.ToLowerInvariant()
$immutableAgent = Join-Path $loaded ("bridge-$fingerprint.jar")
if (-not (Test-Path -LiteralPath $immutableAgent)) { Copy-Item -LiteralPath $agent -Destination $immutableAgent }
$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
& $java --add-modules jdk.attach -cp (Join-Path $PSScriptRoot 'out') Attach $ProcessId $immutableAgent $outputPath
if ($LASTEXITCODE -ne 0) { throw 'Attach failed; inspect game stderr and port configuration' }
