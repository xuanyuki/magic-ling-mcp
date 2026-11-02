$ErrorActionPreference = 'Stop'

$rootPath = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$gameJar = Join-Path $rootPath '..\魔绫地牢-V0.9.6.5.jar'
$agentJar = Join-Path $rootPath 'dist\myriad-codex-agent-0.1.0-SNAPSHOT.jar'
$fixtureSources = Join-Path $rootPath 'src\test\agent'
$targetSource = Join-Path $rootPath 'src\test\attach-fixture\AttachSmokeTarget.java'
$fixtureClasses = Join-Path $rootPath 'out\attach-fixture-classes'
$attachClasses = Join-Path $rootPath 'out\attach-classes'
$stdoutPath = Join-Path $rootPath 'out\attach-smoke.stdout.log'
$stderrPath = Join-Path $rootPath 'out\attach-smoke.stderr.log'

foreach ($path in @($gameJar, $agentJar)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required file not found: $path"
    }
}

New-Item -ItemType Directory -Force -Path $fixtureClasses, $attachClasses | Out-Null
$fixtureJava = @(Get-ChildItem $fixtureSources -Recurse -Filter '*.java') + @(
    Get-Item -LiteralPath $targetSource
)
& javac -encoding UTF-8 -d $fixtureClasses $fixtureJava.FullName
if ($LASTEXITCODE -ne 0) {
    throw "Attach fixture compilation failed with exit code $LASTEXITCODE"
}

$attachSource = Join-Path $rootPath 'tools\AttachMyriadAgent.java'
& javac --add-modules jdk.attach -d $attachClasses $attachSource
if ($LASTEXITCODE -ne 0) {
    throw "Attach helper compilation failed with exit code $LASTEXITCODE"
}

Remove-Item -LiteralPath $stdoutPath, $stderrPath -Force -ErrorAction SilentlyContinue
$fixtureClassPath = $fixtureClasses + [IO.Path]::PathSeparator + $gameJar
$target = Start-Process -FilePath 'java' `
    -ArgumentList @('-Dmyriad.agent.debug=true', '-cp', ('"' + $fixtureClassPath + '"'), 'AttachSmokeTarget') `
    -PassThru `
    -WindowStyle Hidden `
    -RedirectStandardOutput $stdoutPath `
    -RedirectStandardError $stderrPath

try {
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    do {
        if ($target.HasExited) {
            $failure = Get-Content -LiteralPath $stderrPath -Raw -ErrorAction SilentlyContinue
            throw "Fixture process exited before attach. $failure"
        }
        $stdout = Get-Content -LiteralPath $stdoutPath -Raw -ErrorAction SilentlyContinue
        if ($stdout -match 'ATTACH_READY') { break }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $deadline)

    if ($stdout -notmatch 'ATTACH_READY') {
        throw "Fixture process did not become ready. Output: $stdout"
    }

    $targetPid = [int]([regex]::Match($stdout, 'ATTACH_READY\s+(\d+)').Groups[1].Value)

    & java --add-modules jdk.attach -cp $attachClasses AttachMyriadAgent `
        $targetPid `
        $agentJar
    if ($LASTEXITCODE -ne 0) {
        throw "External attach helper failed with exit code $LASTEXITCODE"
    }

    Wait-Process -Id $targetPid -Timeout 15

    $stdout = Get-Content -LiteralPath $stdoutPath -Raw -ErrorAction SilentlyContinue
    $stderr = Get-Content -LiteralPath $stderrPath -Raw -ErrorAction SilentlyContinue
    if ($stdout -notmatch 'Intercepted WndUseItem construction') {
        throw "The retransformed constructor was not intercepted. Output: $stdout`n$stderr"
    }
    Write-Output $stdout
    Write-Output 'External live attach smoke test: OK'
} finally {
    if (Get-Process -Id $targetPid -ErrorAction SilentlyContinue) {
        Stop-Process -Id $targetPid -Force -ErrorAction SilentlyContinue
    }
    if (-not $target.HasExited) {
        Stop-Process -Id $target.Id -Force -ErrorAction SilentlyContinue
    }
}
