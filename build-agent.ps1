$ErrorActionPreference = 'Stop'

$rootPath = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$gameJar = Join-Path $rootPath '..\魔绫地牢-V0.9.6.5.jar'
$dependencyDirectory = Join-Path $rootPath 'deps'
$byteBuddy = Join-Path $dependencyDirectory 'byte-buddy-1.17.7.jar'
$classesDirectory = Join-Path $rootPath 'out\agent-classes'
$outputDirectory = Join-Path $rootPath 'dist'
$agentJar = Join-Path $outputDirectory 'myriad-codex-agent-0.1.0-SNAPSHOT.jar'

if (-not (Test-Path -LiteralPath $gameJar -PathType Leaf)) {
    throw "Expected the unchanged desktop game JAR at: $gameJar"
}

New-Item -ItemType Directory -Force -Path $dependencyDirectory | Out-Null
if (-not (Test-Path -LiteralPath $byteBuddy -PathType Leaf)) {
    Invoke-WebRequest -UseBasicParsing `
        -Uri 'https://repo.maven.apache.org/maven2/net/bytebuddy/byte-buddy/1.17.7/byte-buddy-1.17.7.jar' `
        -OutFile $byteBuddy
}

if (Test-Path -LiteralPath $classesDirectory) {
    $resolvedClasses = (Resolve-Path -LiteralPath $classesDirectory).Path
    if (-not $resolvedClasses.StartsWith($rootPath + [IO.Path]::DirectorySeparatorChar)) {
        throw "Refusing to clean a path outside the plugin project: $resolvedClasses"
    }
    Remove-Item -LiteralPath $resolvedClasses -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $classesDirectory, $outputDirectory | Out-Null

$sourceFiles = @(
    Get-ChildItem (Join-Path $rootPath 'src\main\java') -Recurse -Filter '*.java'
    Get-ChildItem (Join-Path $rootPath 'src\host\java') -Recurse -Filter '*.java'
    Get-ChildItem (Join-Path $rootPath 'src\agent\java') -Recurse -Filter '*.java'
)
$classPath = $gameJar + [IO.Path]::PathSeparator + $byteBuddy
& javac --release 11 -encoding UTF-8 -cp $classPath -d $classesDirectory $sourceFiles.FullName
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

& python (Join-Path $rootPath 'tools\package_agent.py') `
    --classes $classesDirectory `
    --dependency $byteBuddy `
    --output $agentJar
if ($LASTEXITCODE -ne 0) {
    throw "Agent packaging failed with exit code $LASTEXITCODE"
}

Write-Host "Built external agent: $agentJar"
