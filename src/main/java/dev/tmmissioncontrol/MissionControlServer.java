package dev.tmmissioncontrol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public final class MissionControlServer {
    private final GameState state;
    private static final String SERVER_STARTED = Long.toString(System.currentTimeMillis());
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final int port;
    private final String forcedHost;
    private HttpServer server;
    private HttpServer lanHttp;
    private final List<HttpsServer> httpsServers = new ArrayList<>();
    private final List<JmDNS> mdns = new ArrayList<>();
    private byte[] qrSvg = new byte[0];

    public MissionControlServer(GameState state, int port) {
        this(state, port, null);
    }

    public MissionControlServer(GameState state, int port, String forcedHost) {
        this.state = state;
        this.port = port;
        this.forcedHost = forcedHost;
    }

    public URI start() throws IOException {
        rememberJavaExe();
        boolean firewallOpen = FirewallSetup.ensure();
        List<String> ips = lanAddresses();
        List<String> wifi = wifiAddresses();
        List<String> certHosts = LanNames.certHosts(ips);
        SSLContext tls = tlsContext(certHosts);
        boolean https443 = tls != null && bindHttps(443, tls);
        boolean https8080 = tls != null && bindHttps(8080, tls);
        List<String> urls = new ArrayList<>();
        for (String host : LanNames.phoneHosts(forcedHost)) {
            if (https443) {
                addUrl(urls, httpsUrl(host, 443));
            } else if (https8080) {
                addUrl(urls, httpsUrl(host, 8080));
            } else {
                // No HTTPS at all: plain HTTP on the desktop port still reaches phones on the same Wi-Fi.
                addUrl(urls, httpUrl(host, port));
            }
        }
        int phonePort = https443 ? 443 : https8080 ? 8080 : port;
        addUrl(urls, httpUrl("127.0.0.1", port));
        state.url = urls.get(0);
        state.urls = List.copyOf(urls);
        allowInboundNamed("Terraforming Mars Telemetry HTTPS", 443);
        allowInboundNamed("Terraforming Mars Telemetry 8080", 8080);
        allowInboundUdp("Terraforming Mars Telemetry mDNS", 5353);
        allowJavaProgram();
        allowInbound(port);
        // Port rules alone don't prove phones get through: a block rule on the program overrides them.
        state.firewallOpen = firewallOpen;
        qrSvg = QrCodes.svg(state.url).getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        mount(server);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        advertiseMdns(phonePort, wifi.isEmpty() ? ips : wifi);
        return URI.create("http://127.0.0.1:" + port + "/");
    }

    private void mount(HttpServer http) {
        http.createContext("/api/state", this::state);
        http.createContext("/api/shutdown", this::shutdown);
        http.createContext("/api/firewall/fix", this::fixFirewall);
        http.createContext("/api/games", this::games);
        http.createContext("/qr.svg", this::qr);
        http.createContext("/", this::staticFile);
    }

    /** Stop any copy of this app already bound to {@code port} so this process can take over. */
    static void takeOver(int port) throws IOException {
        if (!portInUse(port)) {
            return;
        }
        System.out.println("Another copy of Terraforming Mars Telemetry is already running — stopping it.");
        askToStop(port);
        if (waitUntilFree(port, 2500)) {
            return;
        }
        long pid = listenerPid(port);
        long self = ProcessHandle.current().pid();
        if (pid > 0 && pid != self) {
            forceStopApp(pid);
        }
        if (!waitUntilFree(port, 4000)) {
            throw new IOException("Could not take over port " + port);
        }
    }

    private static void askToStop(int port) {
        try {
            URL url = URI.create("http://127.0.0.1:" + port + "/api/shutdown").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(400);
            conn.setReadTimeout(800);
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception ignored) {
        }
    }

    private static boolean portInUse(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress("127.0.0.1", port));
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private static boolean waitUntilFree(int port, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!portInUse(port)) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !portInUse(port);
    }

    /** The installed app is a windowed launcher, so a console close does not stop it. */
    private static void forceStopApp(long pid) {
        long root = pid;
        ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
        if (handle != null) {
            ProcessHandle parent = handle.parent().orElse(null);
            if (parent != null && parent.pid() != ProcessHandle.current().pid() && isMissionControl(parent)) {
                root = parent.pid();
            }
        }
        try {
            Process kill = new ProcessBuilder("taskkill", "/F", "/T", "/PID", Long.toString(root))
                    .redirectErrorStream(true)
                    .start();
            String output = new String(kill.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            kill.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (output.toLowerCase(Locale.ROOT).contains("access is denied")) {
                elevateKill(root);
            }
        } catch (Exception ignored) {
        }
    }

    private static void elevateKill(long pid) {
        try {
            new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "Start-Process -FilePath taskkill -ArgumentList '/F','/T','/PID','" + pid + "' -Verb RunAs -Wait")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    /** This app's names, old and new: an older copy may be the one holding the port. */
    private static boolean isOurApp(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("tmmissioncontrol") || t.contains("terraforming mars telemetry")
                || t.contains("terraforming-mars-telemetry");
    }

    private static boolean isMissionControl(ProcessHandle handle) {
        if (isOurApp(handle.info().command().orElse(""))) {
            return true;
        }
        try {
            Process proc = new ProcessBuilder("tasklist", "/FI", "PID eq " + handle.pid(), "/FO", "CSV", "/NH")
                    .redirectErrorStream(true)
                    .start();
            String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            return isOurApp(out);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static long listenerPid(int port) {
        String needle = ":" + port;
        try {
            Process proc = new ProcessBuilder("cmd", "/c", "netstat -ano -p tcp")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.contains("LISTENING") || !line.contains(needle)) {
                        continue;
                    }
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length < 5) {
                        continue;
                    }
                    String local = parts[1];
                    if (!local.endsWith(needle) && !local.contains(needle + " ")) {
                        continue;
                    }
                    return Long.parseLong(parts[parts.length - 1]);
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static String httpsUrl(String host, int phonePort) {
        return phonePort == 443 ? "https://" + host + "/" : "https://" + host + ":" + phonePort + "/";
    }

    private static String httpUrl(String host, int phonePort) {
        return phonePort == 80 ? "http://" + host + "/" : "http://" + host + ":" + phonePort + "/";
    }

    private static void addUrl(List<String> urls, String url) {
        if (!urls.contains(url)) {
            urls.add(url);
        }
    }

    /**
     * Built before any HTTPS port is opened: a failure here used to leave 443 and 8080 bound with no server
     * behind them, so phones following the QR code timed out.
     */
    private static SSLContext tlsContext(List<String> certHosts) {
        try {
            return LocalCert.sslContext(certHosts);
        } catch (Exception ex) {
            System.err.println("HTTPS certificate unavailable, serving phones over HTTP: " + ex.getMessage());
            return null;
        }
    }

    private boolean bindHttps(int httpsPort, SSLContext tls) {
        try {
            HttpsServer https = HttpsServer.create(new InetSocketAddress("0.0.0.0", httpsPort), 0);
            https.setHttpsConfigurator(new HttpsConfigurator(tls) {
                @Override
                public void configure(HttpsParameters params) {
                    SSLParameters sp = getSSLContext().getDefaultSSLParameters();
                    sp.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
                    params.setSSLParameters(sp);
                }
            });
            mount(https);
            https.setExecutor(Executors.newCachedThreadPool());
            https.start();
            httpsServers.add(https);
            return true;
        } catch (Exception ex) {
            System.err.println("HTTPS :" + httpsPort + " not bound: " + ex.getMessage());
            return false;
        }
    }

    private void advertiseMdns(int phonePort, List<String> ips) {
        for (String ip : ips) {
            if (ip.startsWith("192.168.137.")) {
                continue;
            }
            try {
                JmDNS dns = JmDNS.create(InetAddress.getByName(ip), LanNames.SHORT);
                dns.registerService(ServiceInfo.create("_https._tcp.local.", "Terraforming Mars Telemetry", phonePort, "path=/"));
                dns.registerService(ServiceInfo.create("_http._tcp.local.", "Terraforming Mars Telemetry", phonePort, "path=/"));
                mdns.add(dns);
            } catch (Exception ex) {
                System.err.println("mDNS not advertised on " + ip + ": " + ex.getMessage());
            }
        }
    }

    public void stop() {
        stopListening();
        for (JmDNS dns : mdns) {
            try {
                dns.unregisterAllServices();
                dns.close();
            } catch (Exception ignored) {
            }
        }
        mdns.clear();
    }

    /**
     * Release the listen sockets before slower teardown, so a restart can bind the ports. The desktop port goes
     * last: a new instance waits for it, then binds 443 and 8080 straight away.
     */
    private void stopListening() {
        for (HttpsServer https : httpsServers) {
            https.stop(0);
        }
        httpsServers.clear();
        if (lanHttp != null) {
            lanHttp.stop(0);
            lanHttp = null;
        }
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void shutdown(HttpExchange exchange) throws IOException {
        InetAddress remote = exchange.getRemoteAddress() == null ? null : exchange.getRemoteAddress().getAddress();
        if (remote == null || !remote.isLoopbackAddress()) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        byte[] body = "stopping".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
        stopListening();
        Thread stopper = new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            App.exit(0);
        }, "mission-control-handoff");
        stopper.setDaemon(false);
        stopper.start();
    }

    /** The page's Allow phones button. Local only: it raises a Windows admin prompt on this PC. */
    private void fixFirewall(HttpExchange exchange) throws IOException {
        InetAddress remote = exchange.getRemoteAddress() == null ? null : exchange.getRemoteAddress().getAddress();
        if (remote == null || !remote.isLoopbackAddress()) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        boolean open = FirewallSetup.fix();
        state.firewallOpen = open;
        byte[] body = ("{\"open\":" + open + "}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /** GET /api/games lists saved games; GET /api/games/{id} returns one, in the same shape as /api/state. */
    private void games(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String id = path.length() > "/api/games/".length() ? path.substring("/api/games/".length()) : "";
        byte[] body = id.isEmpty()
                ? gson.toJson(GameArchive.list()).getBytes(StandardCharsets.UTF_8)
                : GameArchive.load(id);
        if (body == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void state(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        com.google.gson.JsonElement snapshot = gson.toJsonTree(state.snapshot());
        if (snapshot.isJsonObject()) {
            SoloHistory.apply(snapshot.getAsJsonObject());
        }
        byte[] body = gson.toJson(snapshot).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        // Open pages reload when this changes, so they pick up a new build's page code.
        exchange.getResponseHeaders().add("X-Server-Started", SERVER_STARTED);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void qr(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "image/svg+xml; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, qrSvg.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(qrSvg);
        }
    }

    private void staticFile(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        String resource = "/web" + path;
        try (InputStream in = MissionControlServer.class.getResourceAsStream(resource)) {
            if (in == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] body = in.readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", contentType(path));
            exchange.getResponseHeaders().add("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    static String lanHost() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("1.1.1.1"), 80);
            InetAddress local = socket.getLocalAddress();
            if (local instanceof Inet4Address && !local.isLoopbackAddress() && !local.isAnyLocalAddress()) {
                return local.getHostAddress();
            }
        } catch (Exception ignored) {
        }
        List<String> candidates = lanAddresses();
        return candidates.isEmpty() ? "127.0.0.1" : candidates.get(0);
    }

    static List<String> lanAddresses() {
        List<String> wifi = new ArrayList<>();
        List<String> wired = new ArrayList<>();
        collectLanAddresses(wifi, wired);
        List<String> out = new ArrayList<>(wifi);
        out.addAll(wired);
        return out;
    }

    static List<String> wifiAddresses() {
        List<String> wifi = new ArrayList<>();
        collectLanAddresses(wifi, new ArrayList<>());
        return wifi;
    }

    private static void collectLanAddresses(List<String> wifi, List<String> wired) {
        Set<String> seen = new LinkedHashSet<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual() || skipNic(nic)) {
                    continue;
                }
                boolean wireless = isWifi(nic);
                for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
                    if (addr instanceof Inet4Address v4 && v4.isSiteLocalAddress()) {
                        String ip = v4.getHostAddress();
                        if (ip.startsWith("192.168.137.") || !seen.add(ip)) {
                            continue;
                        }
                        (wireless ? wifi : wired).add(ip);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isWifi(NetworkInterface nic) {
        String name = (nic.getDisplayName() + " " + nic.getName()).toLowerCase(Locale.ROOT);
        return name.contains("wi-fi")
                || name.contains("wifi")
                || name.contains("wlan")
                || name.contains("wireless");
    }

    static void stopHotspot() {
        try {
            Process proc = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "$p=[Windows.Networking.Connectivity.NetworkInformation,Windows.Networking.Connectivity,ContentType=WindowsRuntime]::GetInternetConnectionProfile();"
                            + "$m=[Windows.Networking.NetworkOperators.NetworkOperatorTetheringManager,Windows.Networking.NetworkOperators,ContentType=WindowsRuntime]::CreateFromConnectionProfile($p);"
                            + "if($m.TetheringOperationalState.ToString() -eq 'On'){$null=$m.StopTetheringAsync()}")
                    .redirectErrorStream(true)
                    .start();
            proc.waitFor();
        } catch (Exception ignored) {
        }
    }

    private static boolean skipNic(NetworkInterface nic) {
        String name = (nic.getDisplayName() + " " + nic.getName()).toLowerCase(Locale.ROOT);
        return name.contains("bluetooth")
                || name.contains("virtual")
                || name.contains("vethernet")
                || name.contains("hyper-v")
                || name.contains("wsl")
                || name.contains("loopback")
                || name.contains("local area connection");
    }

    private static void rememberJavaExe() {
        FirewallSetup.rememberJavaExe();
    }

    private static boolean allowJavaProgram() {
        return allowProgram(FirewallSetup.APP_RULE, FirewallSetup.appExe(), "TCP");
    }

    static boolean allowInbound(int port) {
        return allowInboundNamed("Terraforming Mars Telemetry", port);
    }

    static boolean allowInboundNamed(String name, int port) {
        return allowInboundNamed(name, "TCP", port);
    }

    static boolean allowInboundUdp(String name, int port) {
        return allowInboundNamed(name, "UDP", port);
    }

    private static boolean allowProgram(String name, Path program, String protocol) {
        if (firewallRulePresent(name)) {
            return true;
        }
        try {
            Process add = new ProcessBuilder(
                    "netsh", "advfirewall", "firewall", "add", "rule",
                    "name=" + name,
                    "dir=in",
                    "action=allow",
                    "program=" + program.toString(),
                    "protocol=" + protocol,
                    "profile=any",
                    "enable=yes")
                    .redirectErrorStream(true)
                    .start();
            add.waitFor();
        } catch (Exception ignored) {
        }
        return firewallRulePresent(name);
    }

    private static boolean allowInboundNamed(String name, String protocol, int port) {
        if (firewallRulePresent(name)) {
            return true;
        }
        try {
            Process add = new ProcessBuilder(
                    "netsh", "advfirewall", "firewall", "add", "rule",
                    "name=" + name,
                    "dir=in",
                    "action=allow",
                    "protocol=" + protocol,
                    "localport=" + Integer.toString(port),
                    "profile=any",
                    "enable=yes")
                    .redirectErrorStream(true)
                    .start();
            add.waitFor();
        } catch (Exception ignored) {
        }
        return firewallRulePresent(name);
    }

    private static boolean firewallRulePresent(String name) {
        try {
            Process show = new ProcessBuilder(
                    "netsh", "advfirewall", "firewall", "show", "rule",
                    "name=" + name)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(show.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            show.waitFor();
            return output.contains(name) && !output.toLowerCase(Locale.ROOT).contains("no rules match");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (path.endsWith(".png")) {
            return "image/png";
        }
        if (path.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        return "text/html; charset=utf-8";
    }
}
