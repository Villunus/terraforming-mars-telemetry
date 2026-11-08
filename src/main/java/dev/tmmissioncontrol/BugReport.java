package dev.tmmissioncontrol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The Report a bug link and the diagnostics zip users attach to it. The zip holds the current game's slice of
 * Player.log and the dashboard state, with player names and the Windows user folder replaced.
 */
final class BugReport {
    static final String NEW_ISSUE = "https://github.com/Villunus/terraforming-mars-telemetry/issues/new";
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting().create();

    /** Set by {@link App} once the log and state exist. */
    static volatile GameState state;
    static volatile Path logFile;

    private BugReport() {
    }

    /** The GitHub bug form with the version and game details filled in. */
    static String issueUrl() {
        return NEW_ISSUE + "?template=bug_report.yml"
                + "&version=" + enc(GithubMsiUpdater.currentVersion())
                + "&game=" + enc(gameSummary());
    }

    static String fileName() {
        return "telemetry-diagnostics-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip";
    }

    /** Downloads\Terraforming Mars Telemetry, so the zips don't get lost among other downloads. */
    static Path folder() {
        return Path.of(System.getProperty("user.home"), "Downloads", "Terraforming Mars Telemetry");
    }

    static Path saveToDownloads() throws IOException {
        Path dir = folder();
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName());
        try (OutputStream out = Files.newOutputStream(file)) {
            write(out);
        }
        return file;
    }

    static void write(OutputStream raw) throws IOException {
        GameState s = state;
        Map<String, String> names = names(s);
        try (ZipOutputStream zip = new ZipOutputStream(raw)) {
            put(zip, "info.txt", anonymize(info(), names));
            if (s != null) {
                Object snapshot;
                synchronized (s.lock()) {
                    snapshot = s.snapshot();
                }
                if (snapshot instanceof Map<?, ?> map) {
                    // LAN addresses don't help reproduce a parsing bug.
                    map.remove("url");
                    map.remove("urls");
                }
                put(zip, "state.json", anonymize(GSON.toJson(snapshot), names));
            }
            Path log = logFile;
            if (log != null && Files.exists(log)) {
                zip.putNextEntry(new ZipEntry("Player.log"));
                writeGameSlice(log, zip, names);
                zip.closeEntry();
            }
        }
    }

    /**
     * Player.log runs to hundreds of MB across sessions. Keep what a replay of the current game reads: the last
     * launch-time line, then everything from the "Begin Game" before the last "Created new Game".
     */
    private static void writeGameSlice(Path log, OutputStream out, Map<String, String> names) throws IOException {
        int created = 0;
        int begin = 1;
        int clock = 0;
        int lastBegin = 1;
        int lastClock = 0;
        int lineNo = 0;
        try (var reader = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.contains("[INFO] Begin Game")) {
                    lastBegin = lineNo;
                } else if (line.contains("Created new Game")) {
                    created = lineNo;
                    begin = lastBegin;
                    clock = lastClock;
                } else if (LogParser.launchFrom(line) > 0) {
                    lastClock = lineNo;
                }
            }
        }
        if (created == 0) {
            begin = Math.max(1, lineNo - 5000); // no game yet: the tail shows what the client was doing
            clock = 0;
        }
        lineNo = 0;
        try (var reader = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (lineNo >= begin || lineNo == clock) {
                    out.write((anonymize(line, names) + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    /** Players' account names, longest first so one name inside another is replaced whole. */
    private static Map<String, String> names(GameState s) {
        Map<String, String> out = new LinkedHashMap<>();
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            out.put(home.replace("\\", "\\\\"), "%USERPROFILE%");
            out.put(home, "%USERPROFILE%");
            out.put(home.replace('\\', '/'), "%USERPROFILE%");
        }
        if (s == null) {
            return out;
        }
        List<PlayerState> players;
        synchronized (s.lock()) {
            players = new ArrayList<>(s.players.values());
        }
        players.sort(Comparator.comparingInt((PlayerState p) -> p.name == null ? 0 : -p.name.length()));
        for (PlayerState p : players) {
            String name = p.name == null ? "" : p.name.trim();
            if (name.length() < 2 || "You".equals(name) || name.startsWith("AI - ") || name.matches("Player \\d+")) {
                continue;
            }
            out.putIfAbsent(name, "Player " + p.id);
        }
        return out;
    }

    private static String anonymize(String text, Map<String, String> names) {
        for (Map.Entry<String, String> e : names.entrySet()) {
            text = text.replace(e.getKey(), e.getValue());
        }
        return text;
    }

    private static String info() {
        return "Terraforming Mars Telemetry " + GithubMsiUpdater.currentVersion() + "\n"
                + "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + "\n"
                + "Java: " + System.getProperty("java.version") + "\n"
                + "Log: " + logFile + "\n"
                + "Game: " + gameSummary() + "\n";
    }

    /** One line about the game in progress, e.g. "Tharsis, gen 7, 3 players (2 AI), Prelude + Venus". */
    static String gameSummary() {
        GameState s = state;
        if (s == null) {
            return "";
        }
        synchronized (s.lock()) {
            if (s.gameId == null || s.gameId.isEmpty()) {
                return "No game loaded";
            }
            List<PlayerState> seated = s.seatedPlayers();
            long ai = seated.stream().filter(p -> p.name != null && p.name.startsWith("AI - ")).count();
            List<String> modes = new ArrayList<>();
            if (s.prelude) {
                modes.add("Prelude");
            }
            if (s.venus) {
                modes.add("Venus");
            }
            if (s.colonies) {
                modes.add("Colonies");
            }
            return s.board + ", gen " + s.generation + ", " + (seated.size() == 1 ? "solo" : seated.size() + " players")
                    + (ai > 0 ? " (" + ai + " AI)" : "")
                    + (modes.isEmpty() ? "" : ", " + String.join(" + ", modes))
                    + ", " + s.phase.toLowerCase(Locale.ROOT);
        }
    }

    private static void put(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
