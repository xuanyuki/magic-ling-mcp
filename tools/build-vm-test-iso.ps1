param(
    [string]$VmWorkspace = 'D:\vmpc\win',
    [string]$IsoDirectory = 'D:\iso'
)

$ErrorActionPreference = 'Stop'
$pluginRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$inputs = Join-Path $VmWorkspace 'MyriadCodexInputs'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$staging = Join-Path $VmWorkspace ('MyriadCodexTesting\media-' + $stamp)
$isoPath = Join-Path $IsoDirectory ('MyriadCodexTests-' + $stamp + '.iso')
$runtimeClasses = Join-Path $pluginRoot 'out\runtime-ui-classes'
$agent = Join-Path $pluginRoot 'dist\myriad-codex-agent-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath (Join-Path $runtimeClasses 'RuntimeUiIntegration.class'))) {
    throw 'Compile and run test-runtime-ui.ps1 before creating the VM test ISO'
}
if (Test-Path -LiteralPath $isoPath) { throw "ISO already exists: $isoPath" }
New-Item -ItemType Directory -Force -Path $staging, $IsoDirectory | Out-Null
Copy-Item -LiteralPath (Join-Path $inputs 'jdk18') -Destination $staging -Recurse
Copy-Item -LiteralPath (Join-Path $pluginRoot '..\魔绫地牢-V0.9.6.5.jar') `
    -Destination (Join-Path $staging 'game.jar')
Copy-Item -LiteralPath $agent -Destination (Join-Path $staging 'agent.jar')
& (Join-Path $inputs 'jdk18\bin\jar.exe') --create `
    --file (Join-Path $staging 'runtime-tests.jar') -C $runtimeClasses .
if ($LASTEXITCODE -ne 0) { throw 'Packaging the runtime fixture failed' }
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
& $compiler /nologo /target:winexe `
    /reference:System.Windows.Forms.dll /reference:System.Management.dll `
    /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll `
    ("/out:" + (Join-Path $staging 'StartVmTests.exe')) `
    (Join-Path $PSScriptRoot 'StartVmTests.cs')
if ($LASTEXITCODE -ne 0) { throw 'Building the VM launcher failed' }
@'
万象之杖插件虚拟机测试

在 MYRIAD-VM 内双击 StartVmTests.exe 即可自动测试。
不需要输入命令、不需要管理员权限、不更改密码。
测试使用独立临时数据，原始游戏包只读。

测试完成会提示结果。日志和截图保存在：
%LOCALAPPDATA%\MyriadVmTest\Runs

如果挂载了专用空白结果软盘，结果同时保存到 A:\RESULT.ZIP。
如果出现 GLFW/OpenGL 错误，保留失败日志；不能算作插件界面测试通过。
'@ | Set-Content -LiteralPath (Join-Path $staging 'README.txt') -Encoding utf8BOM
$checksums = foreach ($name in @('game.jar', 'agent.jar', 'runtime-tests.jar', 'StartVmTests.exe')) {
    $hash = Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $staging $name)
    $hash.Hash + '  ' + $name
}
$checksums | Set-Content -LiteralPath (Join-Path $staging 'SHA256SUMS.txt') -Encoding ascii
Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.ComTypes;
public static class MyriadTestIsoWriter {
    public static void Save(object source, string output) {
        IStream stream = (IStream)source;
        IntPtr read = Marshal.AllocHGlobal(sizeof(int));
        try {
            using (FileStream file = new FileStream(output, FileMode.CreateNew, FileAccess.Write)) {
                byte[] buffer = new byte[65536];
                for (;;) {
                    Marshal.WriteInt32(read, 0);
                    stream.Read(buffer, buffer.Length, read);
                    int count = Marshal.ReadInt32(read);
                    if (count == 0) break;
                    file.Write(buffer, 0, count);
                }
            }
        } finally { Marshal.FreeHGlobal(read); }
    }
}
'@
$image = New-Object -ComObject IMAPI2FS.MsftFileSystemImage
$image.FileSystemsToCreate = 3
$image.VolumeName = 'MYRIAD_TESTS'
$image.Root.AddTree($staging, $false)
$result = $image.CreateResultImage()
[MyriadTestIsoWriter]::Save($result.ImageStream, $isoPath)
Get-Item -LiteralPath $isoPath | Select-Object FullName, Length
Write-Output "Media staging: $staging"
