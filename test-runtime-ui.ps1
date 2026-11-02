param(
    [ValidateSet('landscape', 'portrait')]
    [string]$Orientation = 'landscape',
    [ValidateSet('catalog', 'combat', 'evidence')]
    [string]$Scenario = 'catalog',
    [string]$JavaHome,
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$pluginRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$gameJar = (Resolve-Path -LiteralPath (Join-Path $pluginRoot '..\魔绫地牢-V0.9.6.5.jar')).Path
$agentJar = Join-Path $pluginRoot 'dist\myriad-codex-agent-0.1.0-SNAPSHOT.jar'
$classes = Join-Path $pluginRoot 'out\runtime-ui-classes'
if (-not $OutputDirectory) {
    $outputName = switch ($Scenario) {
        'combat' { 'runtime-combat-' }
        'evidence' { 'runtime-evidence-' }
        default { 'runtime-ui-' }
    }
    $OutputDirectory = Join-Path $pluginRoot ('out\' + $outputName + $Orientation)
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $classes, $OutputDirectory | Out-Null
$java = if ($JavaHome) { Join-Path $JavaHome 'bin\java.exe' } else { (Get-Command java).Source }
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin\javac.exe' } else { (Get-Command javac).Source }
$classPath = $gameJar + [IO.Path]::PathSeparator + $agentJar
& $javac --release 11 -encoding UTF-8 -cp $classPath -d $classes `
    (Join-Path $pluginRoot 'src\test\runtime\RuntimeUiIntegration.java')
if ($LASTEXITCODE -ne 0) { throw 'Runtime integration test compilation failed' }
$testClassPath = $classes + [IO.Path]::PathSeparator + $classPath
$arguments = @(
    ('"-javaagent:' + $agentJar + '"'),
    ('"-Duser.home=' + (Join-Path $OutputDirectory 'home') + '"'),
    '-Djava.awt.headless=true',
    '-cp', ('"' + $testClassPath + '"'),
    'RuntimeUiIntegration',
    ('"' + $OutputDirectory + '"'),
    $Orientation,
    $Scenario
)
$testProcess = Start-Process -FilePath $java -ArgumentList $arguments `
    -WorkingDirectory $OutputDirectory -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $OutputDirectory 'stdout.log') `
    -RedirectStandardError (Join-Path $OutputDirectory 'stderr.log')
if (-not $testProcess.WaitForExit(90000)) {
    $testProcess.Kill()
    throw "Runtime integration test timed out; logs: $OutputDirectory"
}
$resultFile = Join-Path $OutputDirectory 'result.txt'
if (Test-Path -LiteralPath $resultFile) {
    Get-Content -LiteralPath $resultFile -Encoding UTF8
}
if ($testProcess.ExitCode -ne 0) {
    Get-Content -LiteralPath (Join-Path $OutputDirectory 'stderr.log') -Tail 60
    throw "Runtime integration test failed ($($testProcess.ExitCode)); logs: $OutputDirectory"
}
Write-Output "Screenshots and logs: $OutputDirectory"
