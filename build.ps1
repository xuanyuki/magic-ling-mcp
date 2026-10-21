param(
    [string]$Jdk = $env:JAVA_HOME,
    [string]$GameJar = (Join-Path $PSScriptRoot '..\魔绫地牢-V0.9.6.5.jar'),
    [string]$ByteBuddyJar = (Join-Path $PSScriptRoot 'deps\byte-buddy-1.17.7.jar')
)
$ErrorActionPreference = 'Stop'
function Resolve-JavaTool([string]$Name, [string]$JdkPath) {
    if ($JdkPath) {
        $tool = Join-Path $JdkPath "bin\$Name.exe"
        if (-not (Test-Path -LiteralPath $tool)) { throw "Java tool not found: $tool" }
        return $tool
    }
    $command = Get-Command "$Name.exe" -ErrorAction SilentlyContinue
    if (-not $command) { $command = Get-Command $Name -ErrorAction SilentlyContinue }
    if (-not $command) { throw "$Name was not found. Set JAVA_HOME to a JDK or add the JDK bin directory to PATH." }
    return $command.Source
}
$javac = Resolve-JavaTool 'javac' $Jdk
$game = (Resolve-Path -LiteralPath $GameJar -ErrorAction Stop).Path
$buddy = (Resolve-Path -LiteralPath $ByteBuddyJar -ErrorAction Stop).Path
$classes = Join-Path $PSScriptRoot 'out\classes'
$dist = Join-Path $PSScriptRoot 'dist'
New-Item -ItemType Directory -Force -Path $classes,$dist | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java')
& $javac -J-Xmx512m -J-XX:+UseSerialGC --release 11 -encoding UTF-8 -cp "$game;$buddy" -d $classes $sources.FullName
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
& python (Join-Path $PSScriptRoot 'tools\package.py') $classes $buddy (Join-Path $dist 'game-text-bridge.jar')
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed' }
& $javac -J-Xmx256m -J-XX:+UseSerialGC --release 11 --add-modules jdk.attach -d (Join-Path $PSScriptRoot 'out') (Join-Path $PSScriptRoot 'tools\Attach.java')
if ($LASTEXITCODE -ne 0) { throw 'Attach helper compilation failed' }
