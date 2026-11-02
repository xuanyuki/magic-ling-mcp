$ErrorActionPreference = 'Stop'

$rootPath = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$gameJar = Join-Path $rootPath '..\魔绫地牢-V0.9.6.5.jar'
$agentJar = Join-Path $rootPath 'dist\myriad-codex-agent-0.1.0-SNAPSHOT.jar'
$testClasses = Join-Path $rootPath 'out\agent-smoke-classes'
$hostTestClasses = Join-Path $rootPath 'out\host-transform-smoke-classes'
$testSources = Join-Path $rootPath 'src\test\agent'

foreach ($path in @($gameJar, $agentJar)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required file not found: $path"
    }
}
if (-not (Test-Path -LiteralPath $testSources -PathType Container)) {
    throw "Required test source directory not found: $testSources"
}

New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$sourceFiles = Get-ChildItem $testSources -Recurse -Filter '*.java'
& javac -encoding UTF-8 -d $testClasses $sourceFiles.Where({ $_.Name -ne 'ActualHostTransformSmoke.java' }).FullName
if ($LASTEXITCODE -ne 0) {
    throw "Smoke-test compilation failed with exit code $LASTEXITCODE"
}

$classPath = $testClasses + [IO.Path]::PathSeparator + $gameJar
$smokeOutput = @(& java '-Dmyriad.agent.debug=true' "-javaagent:$agentJar" -cp $classPath MyriadAgentLoadSmoke 2>&1)
$smokeExitCode = $LASTEXITCODE
$smokeOutput | ForEach-Object { Write-Output $_ }
if ($smokeExitCode -ne 0) {
    throw "Agent smoke test failed with exit code $smokeExitCode"
}
if (($smokeOutput -join "`n") -notmatch '\[Myriad Codex\] Intercepted WndUseItem construction\.') {
    throw 'Agent smoke test failed: the constructor advice did not execute.'
}

New-Item -ItemType Directory -Force -Path $hostTestClasses | Out-Null
$actualHostSource = Join-Path $testSources 'ActualHostTransformSmoke.java'
& javac -encoding UTF-8 -cp $gameJar -d $hostTestClasses $actualHostSource
if ($LASTEXITCODE -ne 0) {
    throw "Original game class smoke-test compilation failed with exit code $LASTEXITCODE"
}

$gameClassPath = $hostTestClasses + [IO.Path]::PathSeparator + $gameJar
$hostOutput = @(& java "-javaagent:$agentJar" -cp $gameClassPath ActualHostTransformSmoke 2>&1)
$hostExitCode = $LASTEXITCODE
$hostOutput | ForEach-Object { Write-Output $_ }
if ($hostExitCode -ne 0) {
    throw "Original game class transform smoke test failed with exit code $hostExitCode"
}
if (($hostOutput -join "`n") -notmatch '\[Myriad Codex\] Hooked com\.shatteredpixel\.shatteredpixeldungeon\.windows\.WndUseItem') {
    throw 'Original game class transform smoke test failed: the host class was not transformed.'
}
