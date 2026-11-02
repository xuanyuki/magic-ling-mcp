param(
    [Parameter(Mandatory = $true)]
    [int]$ProcessId
)

$ErrorActionPreference = 'Stop'
$rootPath = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$agentJar = Join-Path $rootPath 'dist\myriad-codex-agent-0.1.0-SNAPSHOT.jar'
$source = Join-Path $rootPath 'tools\AttachMyriadAgent.java'
$classesDirectory = Join-Path $rootPath 'out\attach-classes'

if (-not (Test-Path -LiteralPath $agentJar -PathType Leaf)) {
    throw "Agent JAR not found: $agentJar. Build it first with build-agent.ps1."
}

New-Item -ItemType Directory -Force -Path $classesDirectory | Out-Null
& javac --add-modules jdk.attach -d $classesDirectory $source
if ($LASTEXITCODE -ne 0) {
    throw "Attach helper compilation failed with exit code $LASTEXITCODE"
}

& java --add-modules jdk.attach -cp $classesDirectory AttachMyriadAgent `
    $ProcessId `
    $agentJar
if ($LASTEXITCODE -ne 0) {
    throw "Attach failed with exit code $LASTEXITCODE"
}
