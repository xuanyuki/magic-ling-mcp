param(
    [ValidateSet('landscape', 'portrait')]
    [string]$Orientation = 'landscape',
    [string]$JavaHome,
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
function Resolve-JavaTool([string]$Name, [string]$JdkPath) {
    if ($JdkPath) {
        $tool = Join-Path $JdkPath "bin\$Name.exe"
        if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
            throw "Java tool not found: $tool"
        }
        return (Resolve-Path -LiteralPath $tool).Path
    }
    $command = Get-Command "$Name.exe" -CommandType Application -ErrorAction SilentlyContinue
    if (-not $command) {
        $command = Get-Command $Name -CommandType Application -ErrorAction SilentlyContinue
    }
    if (-not $command) {
        throw "$Name was not found. Pass -JavaHome, set JAVA_HOME to a JDK, or add the JDK bin directory to PATH."
    }
    return $command.Source
}
$selectedJavaHome = if ($JavaHome) { $JavaHome } else { $env:JAVA_HOME }
$java = Resolve-JavaTool 'java' $selectedJavaHome
$javac = Resolve-JavaTool 'javac' $selectedJavaHome
$pluginRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$gameJar = (Resolve-Path -LiteralPath (Join-Path $pluginRoot '..\魔绫地牢-V0.9.6.5.jar')).Path
$agentJar = Join-Path $pluginRoot 'dist\myriad-codex-agent-0.1.0-SNAPSHOT.jar'
$classes = Join-Path $pluginRoot 'out\full-run-warrior-classes'
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $pluginRoot ('out\full-run-warrior-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + $Orientation)
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $classes, $OutputDirectory | Out-Null
$classPath = $gameJar + [IO.Path]::PathSeparator + $agentJar
& $javac --release 11 -encoding UTF-8 -cp $classPath -d $classes `
    (Join-Path $pluginRoot 'src\test\runtime\FullRunWarriorIntegration.java')
if ($LASTEXITCODE -ne 0) { throw 'Full Warrior integration test compilation failed' }
$testClassPath = $classes + [IO.Path]::PathSeparator + $classPath

function Invoke-Run([string]$Mode) {
    $stdout = Join-Path $OutputDirectory ($Mode + '-stdout.log')
    $stderr = Join-Path $OutputDirectory ($Mode + '-stderr.log')
    $arguments = @(
        ('"-javaagent:' + $agentJar + '"'),
        ('"-Duser.home=' + (Join-Path $OutputDirectory ('home-' + $Mode)) + '"'),
        '-Djava.awt.headless=true',
        '-cp', ('"' + $testClassPath + '"'),
        'FullRunWarriorIntegration',
        ('"' + $OutputDirectory + '"'),
        $Orientation,
        $Mode
    )
    $process = Start-Process -FilePath $java -ArgumentList $arguments `
        -WorkingDirectory $OutputDirectory -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    if (-not $process.WaitForExit(120000)) {
        $process.Kill()
        throw "Full Warrior $Mode run timed out; logs: $OutputDirectory"
    }
    if ($process.ExitCode -ne 0) {
        Get-Content -LiteralPath $stderr -Tail 80
        throw "Full Warrior $Mode run failed ($($process.ExitCode)); logs: $OutputDirectory"
    }
}

Invoke-Run 'success'
Invoke-Run 'failure'

$resultFile = Join-Path $OutputDirectory 'result.txt'
if (Test-Path -LiteralPath $resultFile) {
    Get-Content -LiteralPath $resultFile -Encoding UTF8
}
Write-Output "Screenshots and logs: $OutputDirectory"
