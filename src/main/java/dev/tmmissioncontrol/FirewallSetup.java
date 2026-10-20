package dev.tmmissioncontrol;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class FirewallSetup {
    static final String APP_RULE = "Terraforming Mars Telemetry app";

    private FirewallSetup() {
    }

    static Path javaExe() {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        if (!Files.isRegularFile(java)) {
            java = Path.of(System.getProperty("java.home"), "bin", "java");
        }
        return java;
    }

    /**
     * The executable Windows sees listening: "Terraforming Mars Telemetry.exe" when installed (its bundled runtime has no
     * java.exe), or java.exe / javaw.exe for a development run.
     */
    static Path appExe() {
        return ProcessHandle.current().info().command()
                .map(Path::of)
                .filter(Files::isRegularFile)
                .orElseGet(FirewallSetup::javaExe);
    }

    static void rememberJavaExe() {
        try {
            Files.writeString(LocalCert.dir().resolve("java-exe.txt"), appExe().toString());
        } catch (Exception ignored) {
        }
    }

    /**
     * Make sure phones can reach this app. Explains itself before the Windows admin prompt; "Not now" is
     * remembered so later launches don't ask, and the page's Allow phones button can still run it.
     */
    static boolean ensure() {
        rememberJavaExe();
        Path exe = appExe();
        if (programAllowed(exe)) {
            clearNotNow();
            return true;
        }
        if (notNowMarked() || !askToAllow()) {
            markNotNow();
            return false;
        }
        return openElevated(exe);
    }

    /** Run the admin setup now, from the page's Allow phones button. */
    static boolean fix() {
        clearNotNow();
        Path exe = appExe();
        return programAllowed(exe) || openElevated(exe);
    }

    private static boolean askToAllow() {
        if (GraphicsEnvironment.isHeadless()) {
            return true;
        }
        String message = """
                Let phones on your Wi-Fi open Terraforming Mars Telemetry?

                Scanning the QR code needs Windows Firewall to let this app accept
                connections. Windows will ask for administrator permission next.

                If you choose Not now, you can allow it later from the Terraforming Mars Telemetry page.""";
        Object[] options = {"Allow", "Not now"};
        JOptionPane pane = new JOptionPane(message, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION,
                null, options, options[0]);
        try {
            SwingUtilities.invokeAndWait(() -> {
                JDialog dialog = pane.createDialog(null, "Terraforming Mars Telemetry");
                dialog.setAlwaysOnTop(true);
                dialog.setVisible(true);
                dialog.dispose();
            });
        } catch (Exception ignored) {
            return false;
        }
        return options[0].equals(pane.getValue());
    }

    private static Path notNowFile() throws IOException {
        return LocalCert.dir().resolve("firewall-not-now.txt");
    }

    private static boolean notNowMarked() {
        try {
            return Files.exists(notNowFile());
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void markNotNow() {
        try {
            Files.writeString(notNowFile(), "Phones stay blocked until Allow phones is clicked on the page.");
        } catch (Exception ignored) {
        }
    }

    private static void clearNotNow() {
        try {
            Files.deleteIfExists(notNowFile());
        } catch (Exception ignored) {
        }
    }

    private static boolean openElevated(Path exe) {
        try {
            Path bat = LocalCert.dir().resolve("open-firewall.bat");
            Files.writeString(bat, script(exe));
            Process elevate = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "Start-Process -FilePath '" + bat.toString().replace("'", "''") + "' -Verb RunAs -Wait")
                    .redirectErrorStream(true)
                    .start();
            elevate.waitFor();
        } catch (Exception ignored) {
        }
        return programAllowed(exe);
    }

    /**
     * True when our all-profile allow rule names this program and no block rule does. Windows adds block
     * rules for a program when its "allow access?" prompt is cancelled, and a block beats every allow, so
     * phones time out even though the port rules exist. Windows' own allow rules often cover only Private
     * networks, so they don't count.
     */
    static boolean programAllowed(Path exe) {
        String ps = "$exe='" + exe.toString().replace("'", "''") + "';"
                + "$r=@(Get-NetFirewallApplicationFilter -ErrorAction SilentlyContinue"
                + " | Where-Object { [Environment]::ExpandEnvironmentVariables($_.Program) -ieq $exe }"
                + " | Get-NetFirewallRule -ErrorAction SilentlyContinue"
                + " | Where-Object { $_.Direction -eq 'Inbound' -and $_.Enabled -eq 'True' });"
                + "'allow=' + @($r | Where-Object { $_.Action -eq 'Allow' -and $_.DisplayName -eq '" + APP_RULE + "' }).Count"
                + " + ' block=' + @($r | Where-Object { $_.Action -eq 'Block' }).Count";
        try {
            Process proc = new ProcessBuilder("powershell", "-NoProfile", "-Command", ps)
                    .redirectErrorStream(true)
                    .start();
            String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            proc.waitFor();
            return !out.contains("allow=0") && out.contains("block=0");
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * The installed app and a development run (javaw.exe) each need their own program rule under the same name,
     * so only this program's old rules are replaced; deleting by name would remove the other one's and make it ask again.
     */
    private static String script(Path program) {
        String exe = program.toString();
        String psExe = exe.replace("'", "''");
        return """
                @echo off
                netsh advfirewall firewall delete rule name="TM Companion" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Companion HTTPS" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Companion HTTPS probe" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Companion 8080" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Companion LAN" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Companion Java" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Companion Java UDP" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Companion mDNS" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control HTTPS" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control HTTPS probe" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control 8080" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control LAN" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control Java" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control Java UDP" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control mDNS" >nul 2>nul
                netsh advfirewall firewall delete rule name="TM Mission Control app" >nul 2>nul
                netsh advfirewall firewall delete rule name="Terraforming Mars Telemetry" >nul 2>nul
                netsh advfirewall firewall delete rule name="Terraforming Mars Telemetry HTTPS" >nul 2>nul
                netsh advfirewall firewall delete rule name="Terraforming Mars Telemetry 8080" >nul 2>nul
                netsh advfirewall firewall delete rule name="Terraforming Mars Telemetry mDNS" >nul 2>nul
                powershell -NoProfile -Command "$exe='%3$s'; Get-NetFirewallApplicationFilter | Where-Object { [Environment]::ExpandEnvironmentVariables($_.Program) -ieq $exe } | Get-NetFirewallRule | Where-Object { $_.Direction -eq 'Inbound' -and ($_.Action -eq 'Block' -or $_.DisplayName -eq '%1$s') } | Remove-NetFirewallRule"
                netsh advfirewall firewall add rule name="Terraforming Mars Telemetry" dir=in action=allow protocol=TCP localport=443,8080,8765 profile=any enable=yes
                netsh advfirewall firewall add rule name="Terraforming Mars Telemetry HTTPS" dir=in action=allow protocol=TCP localport=443 profile=any enable=yes
                netsh advfirewall firewall add rule name="Terraforming Mars Telemetry mDNS" dir=in action=allow protocol=UDP localport=5353 profile=any enable=yes
                netsh advfirewall firewall add rule name="%1$s" dir=in action=allow program="%2$s" protocol=TCP profile=any enable=yes
                netsh advfirewall firewall add rule name="%1$s" dir=in action=allow program="%2$s" protocol=UDP profile=any enable=yes
                """.formatted(APP_RULE, exe, psExe);
    }
}
