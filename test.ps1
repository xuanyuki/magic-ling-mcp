param(
    [string]$Jdk = $env:JAVA_HOME,
    [string]$GameJar = (Join-Path $PSScriptRoot '..\魔绫地牢-V0.9.6.5.jar')
)
$ErrorActionPreference = 'Stop'
if ($Jdk) {
    $java = Join-Path $Jdk 'bin\java.exe'
    $javac = Join-Path $Jdk 'bin\javac.exe'
    if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath $javac)) {
        throw "JDK tools were not found under: $Jdk"
    }
} else {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if (-not $javaCommand) { $javaCommand = Get-Command java -ErrorAction Stop }
    $javacCommand = Get-Command javac.exe -ErrorAction SilentlyContinue
    if (-not $javacCommand) { $javacCommand = Get-Command javac -ErrorAction Stop }
    $java = $javaCommand.Source
    $javac = $javacCommand.Source
}
$game = (Resolve-Path -LiteralPath $GameJar -ErrorAction Stop).Path
$classes = Join-Path $PSScriptRoot 'out\test-classes'
$observer = Join-Path $PSScriptRoot ('out\test-observer-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Force -Path $classes | Out-Null
New-Item -ItemType Directory -Force -Path $observer | Out-Null
Set-Content -LiteralPath (Join-Path $observer 'bridge.properties') -Encoding utf8 -Value "port=0`nintervalMs=250"
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests') -Recurse -Filter '*.java')
& $javac -J-Xmx512m -J-XX:+UseSerialGC --release 11 -encoding UTF-8 -cp "$game;$(Join-Path $PSScriptRoot 'out\classes')" -d $classes $sources.FullName
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& $java -Xmx512m -XX:+UseSerialGC '-Dfile.encoding=UTF-8' "-javaagent:$(Join-Path $PSScriptRoot 'dist\game-text-bridge.jar')=$observer" -cp "$classes;$game" ml.bridge.BoundaryTest (Join-Path $observer 'state') $observer
if ($LASTEXITCODE -ne 0) { throw 'Boundary tests failed' }
& python -X utf8 (Join-Path $PSScriptRoot 'tools\test_attach.py') $java $PSScriptRoot $game
if ($LASTEXITCODE -ne 0) { throw 'Dynamic attach tests failed' }
& python -X utf8 (Join-Path $PSScriptRoot 'tests\test_client.py')
if ($LASTEXITCODE -ne 0) { throw 'Text client tests failed' }
& python -X utf8 (Join-Path $PSScriptRoot 'tests\test_package.py')
if ($LASTEXITCODE -ne 0) { throw 'Package license tests failed' }
& python -X utf8 (Join-Path $PSScriptRoot 'tests\test_ci_release.py')
if ($LASTEXITCODE -ne 0) { throw 'CI release tests failed' }
& uv run --project $PSScriptRoot --frozen python (Join-Path $PSScriptRoot 'tests\test_mcp.py')
if ($LASTEXITCODE -ne 0) { throw 'MCP stdio tests failed' }
