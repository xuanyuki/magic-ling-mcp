using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Management;
using System.Reflection;
using System.Text;
using System.Threading;
using System.Windows.Forms;

// Guest-only launcher for the precompiled integration fixture. No elevation,
// installation, desktop input, or account changes are involved.
internal static class StartVmTests
{
    [STAThread]
    private static int Main()
    {
        try
        {
            if (!IsTestGuest())
            {
                MessageBox.Show("此启动器仅用于 MYRIAD-VM 测试虚拟机。", "未启动测试");
                return 3;
            }
            string media = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
            string output = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "MyriadVmTest", "Runs", DateTime.Now.ToString("yyyyMMdd-HHmmss"));
            Directory.CreateDirectory(output);
            File.WriteAllText(Path.Combine(output, "environment.txt"),
                "Machine: " + Environment.MachineName + Environment.NewLine
                + "OS: " + Environment.OSVersion + Environment.NewLine
                + "Source: " + media + Environment.NewLine
                + "Started: " + DateTimeOffset.Now.ToString("o"), Encoding.UTF8);
            int code = RunTest(media, output, "landscape");
            if (code == 0) code = RunTest(media, output, "portrait");
            string summary = code == 0
                ? "虚拟机横屏、竖屏测试均通过。"
                : "虚拟机测试未通过，退出码：" + code;
            File.WriteAllText(Path.Combine(output, "summary.txt"), summary, Encoding.UTF8);
            bool exported = ExportResults(output);
            MessageBox.Show(summary + "\r\n\r\n日志和截图：\r\n" + output
                + (exported ? "\r\n\r\n结果已写入测试软盘 A:\\RESULT.ZIP。" : ""),
                "万象之杖插件测试", MessageBoxButtons.OK,
                code == 0 ? MessageBoxIcon.Information : MessageBoxIcon.Warning);
            return code;
        }
        catch (Exception error)
        {
            MessageBox.Show(error.ToString(), "测试启动失败");
            return 1;
        }
    }

    private static int RunTest(string media, string root, string orientation)
    {
        string output = Path.Combine(root, orientation);
        Directory.CreateDirectory(output);
        string java = Path.Combine(media, @"jdk18\bin\java.exe");
        string agent = Path.Combine(media, "agent.jar");
        string classPath = Path.Combine(media, "runtime-tests.jar") + ";"
            + Path.Combine(media, "game.jar") + ";" + agent;
        ProcessStartInfo start = new ProcessStartInfo(java);
        start.Arguments = Quote("-javaagent:" + agent)
            + " " + Quote("-Duser.home=" + Path.Combine(output, "home"))
            + " -Djava.awt.headless=true -cp " + Quote(classPath)
            + " RuntimeUiIntegration " + Quote(output) + " " + orientation;
        start.WorkingDirectory = output;
        start.UseShellExecute = false;
        start.CreateNoWindow = true;
        start.WindowStyle = ProcessWindowStyle.Hidden;
        start.RedirectStandardOutput = true;
        start.RedirectStandardError = true;
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        using (Process process = new Process())
        {
            process.StartInfo = start;
            process.OutputDataReceived += delegate(object sender, DataReceivedEventArgs data) {
                if (data.Data != null) lock (stdout) stdout.AppendLine(data.Data);
            };
            process.ErrorDataReceived += delegate(object sender, DataReceivedEventArgs data) {
                if (data.Data != null) lock (stderr) stderr.AppendLine(data.Data);
            };
            process.Start();
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
            int code;
            if (!process.WaitForExit(60000))
            {
                process.Kill();
                process.WaitForExit();
                stderr.AppendLine("TIMEOUT: runtime integration exceeded 60 seconds");
                code = 124;
            }
            else
            {
                process.WaitForExit();
                code = process.ExitCode;
            }
            File.WriteAllText(Path.Combine(output, "stdout.log"), stdout.ToString(), Encoding.UTF8);
            File.WriteAllText(Path.Combine(output, "stderr.log"), stderr.ToString(), Encoding.UTF8);
            return code;
        }
    }

    private static bool ExportResults(string output)
    {
        // The optional blank floppy is dedicated to this test. Existing results
        // are preserved; a ZIP is copied only when RESULT.ZIP does not exist.
        try
        {
            if (!Directory.Exists(@"A:\") || File.Exists(@"A:\RESULT.ZIP")) return false;
            string zip = output + ".zip";
            ZipFile.CreateFromDirectory(output, zip, CompressionLevel.Optimal, false);
            if (new FileInfo(zip).Length > 1300000) return false;
            File.Copy(zip, @"A:\RESULT.ZIP", false);
            return true;
        }
        catch { return false; }
    }

    private static string Quote(string value)
    {
        if (value.IndexOf('"') >= 0) throw new ArgumentException("Invalid quote in path");
        return "\"" + value + "\"";
    }

    private static bool IsTestGuest()
    {
        if (!String.Equals(Environment.MachineName, "MYRIAD-VM",
            StringComparison.OrdinalIgnoreCase)) return false;
        using (ManagementObjectSearcher searcher = new ManagementObjectSearcher(
            "SELECT Manufacturer, Model FROM Win32_ComputerSystem"))
        using (ManagementObjectCollection systems = searcher.Get())
        {
            foreach (ManagementObject system in systems)
            using (system)
            {
                if (Convert.ToString(system["Manufacturer"]).IndexOf(
                    "VMware", StringComparison.OrdinalIgnoreCase) >= 0) return true;
            }
        }
        return false;
    }
}
