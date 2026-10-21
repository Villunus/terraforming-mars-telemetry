package dev.tmmissioncontrol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Past games, one file each in %LOCALAPPDATA%\Terraforming Mars Telemetry\games\&lt;game id&gt;.json. A file holds the same
 * snapshot the page gets for a live game, so a past game renders with the same code (boards, charts, log).
 */
final class GameArchive {
    /** Off for --once dumps; on for the running app. */
    static volatile boolean enabled;

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    /** Only meaningful while the app runs; a saved game shouldn't carry them. */
    private static final List<String> LIVE_ONLY = List.of(
            "url", "urls", "firewallOpen", "updateAvailable", "activePlay");

    private GameArchive() {
    }

    static Path dir() throws IOException {
        Path dir = LocalCert.dir().resolve("games");
        Files.createDirectories(dir);
        return dir;
    }

    /** Game ids come from the log; keep them to a safe file name. */
    static String fileId(String gameId) {
        return gameId == null ? "" : gameId.trim().replaceAll("[^A-Za-z0-9_-]", "");
    }

    /** Called with the state lock held (generation end, game end, before a new game resets the state). */
    static void save(GameState state) {
        if (!enabled || !state.autoSave) {
            return;
        }
        write(state, null, false);
    }

    @SuppressWarnings("unchecked")
    private static void write(GameState state, String playedAt, boolean imported) {
        String id = fileId(state.gameId);
        if (id.isEmpty() || state.playLog.isEmpty()) {
            return;
        }
        try {
            Path file = dir().resolve(id + ".json");
            Map<String, Object> snap = new LinkedHashMap<>((Map<String, Object>) state.snapshot());
            LIVE_ONLY.forEach(snap::remove);
            snap.put("live", false);
            snap.put("finished", state.phase != null && state.phase.toLowerCase().contains("endgame"));
            // The real start time when the log gave one; otherwise the import's estimate, or the first save.
            boolean known = state.startedAtMs > 0;
            snap.put("playedAt", known ? Instant.ofEpochMilli(state.startedAtMs).toString()
                    : playedAt != null ? playedAt : firstPlayedAt(file));
            snap.put("savedAt", Instant.now().toString());
            if (imported) {
                snap.put("imported", true);
            }
            if (!known && playedAt != null) {
                snap.put("approxDate", true);
            }
            Path tmp = file.resolveSibling(id + ".json.tmp");
            Files.writeString(tmp, GSON.toJson(snap), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            System.err.println("Could not save game " + id + ": " + ex.getMessage());
        }
    }

    private static String firstPlayedAt(Path file) {
        try {
            if (Files.exists(file)) {
                JsonObject old = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (old.has("playedAt") && !old.get("playedAt").isJsonNull()) {
                    return old.get("playedAt").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return Instant.now().toString();
    }

    /**
     * Save games from the other logs in the game's log folder: Player-prev.log (the game before a relaunch,
     * which is lost if this app wasn't running) and any copies. Player.log itself is the live tailer's.
     * Each file is replayed once per size and timestamp; games already saved are left alone.
     */
    static void importLogs(Path logDir, Path liveLog, CardDatabase cards) {
        if (logDir == null || !Files.isDirectory(logDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(logDir)) {
            // v2: logs are re-read once so games imported with estimated dates get their real start times.
            Path marker = dir().resolve("imported-logs-v2.txt");
            List<String> done = Files.exists(marker) ? Files.readAllLines(marker, StandardCharsets.UTF_8) : new ArrayList<>();
            for (Path file : files.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".log")).toList()) {
                if (file.equals(liveLog)) {
                    continue;
                }
                String stamp = file.getFileName() + "|" + Files.size(file) + "|" + Files.getLastModifiedTime(file).toMillis();
                if (done.contains(stamp)) {
                    continue;
                }
                int saved = importLog(file, cards);
                System.out.println("Imported " + saved + " past game(s) from " + file.getFileName());
                done.add(stamp);
                Files.write(marker, done, StandardCharsets.UTF_8);
            }
        } catch (Exception ex) {
            System.err.println("Past-game import stopped: " + ex.getMessage());
        }
    }

    /** Replays each game in `file` on its own and saves the ones not already saved. Returns how many. */
    static int importLog(Path file, CardDatabase cards) throws IOException {
        List<GameState> games = new ArrayList<>();
        GameState game = null;
        LogParser parser = null;
        long launch = -1; // chat clock lines before a game give its start time
        // InputStreamReader replaces bad bytes instead of failing like Files.newBufferedReader would.
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                long seen = LogParser.launchFrom(line);
                if (seen > 0) {
                    launch = seen;
                }
                if (line.contains("Created new Game")) {
                    game = new GameState();
                    game.autoSave = false;
                    parser = new LogParser(cards, game);
                    parser.launchEpochMs = launch;
                    games.add(game);
                }
                if (parser != null) {
                    parser.consume(line);
                }
            }
        }
        // Without a chat clock line, estimate: order games by position, ending at the file's timestamp.
        Instant end = Files.getLastModifiedTime(file).toInstant();
        int saved = 0;
        for (int i = 0; i < games.size(); i++) {
            GameState g = games.get(i);
            String id = fileId(g.gameId);
            if (id.isEmpty() || g.playLog.isEmpty()) {
                continue;
            }
            Path existing = dir().resolve(id + ".json");
            // Leave saved games alone, except imports with an estimated date that we can now date for real.
            if (Files.exists(existing) && !(g.startedAtMs > 0 && estimatedImport(existing))) {
                continue;
            }
            synchronized (g.lock()) {
                write(g, end.minusSeconds(60L * (games.size() - 1 - i)).toString(), true);
            }
            saved++;
        }
        return saved;
    }

    /** An imported game whose date was estimated (saved before start times came from the log). */
    private static boolean estimatedImport(Path file) {
        try {
            JsonObject old = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean imported = old.has("imported") && old.get("imported").getAsBoolean();
            boolean dated = old.has("startedAt") && !old.get("startedAt").isJsonNull();
            return imported && !dated;
        } catch (Exception ex) {
            return false;
        }
    }

    /** Every saved game, parsed; unreadable files are skipped. */
    private static List<JsonObject> all() {
        List<JsonObject> games = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir())) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                try {
                    games.add(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
                } catch (Exception ex) {
                    System.err.println("Skipping unreadable saved game " + file.getFileName() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ignored) {
        }
        return games;
    }

    /** One line per saved game for the picker, newest first; solo games carry the game's own final score. */
    static List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        List<JsonObject> games = all();
        for (JsonObject game : games) {
            SoloHistory.apply(game, games);
            out.add(summary(game));
        }
        out.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("playedAt"))).reversed());
        return out;
    }

    private static Map<String, Object> summary(JsonObject game) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("gameId", str(game, "gameId"));
        s.put("playedAt", str(game, "playedAt"));
        s.put("board", str(game, "board"));
        s.put("generation", game.has("generation") ? game.get("generation").getAsInt() : 0);
        s.put("finished", game.has("finished") && game.get("finished").getAsBoolean());
        s.put("imported", game.has("imported") && game.get("imported").getAsBoolean());
        // Imports without a real start time (the log had no chat clock line) carry an estimated date.
        boolean started = game.has("startedAt") && !game.get("startedAt").isJsonNull();
        boolean imported = game.has("imported") && game.get("imported").getAsBoolean();
        s.put("approxDate", (game.has("approxDate") && game.get("approxDate").getAsBoolean()) || (imported && !started));
        JsonObject byId = game.has("score") && game.getAsJsonObject("score").has("byId")
                ? game.getAsJsonObject("score").getAsJsonObject("byId") : new JsonObject();
        List<Map<String, Object>> players = new ArrayList<>();
        if (game.has("players")) {
            for (JsonElement el : game.getAsJsonArray("players")) {
                JsonObject p = el.getAsJsonObject();
                String pid = String.valueOf(p.get("id").getAsInt());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", p.get("id").getAsInt());
                row.put("corporation", str(p, "corporation"));
                row.put("name", str(p, "name"));
                row.put("color", str(p, "color"));
                row.put("human", p.has("human") && p.get("human").getAsBoolean());
                row.put("total", byId.has(pid) && byId.getAsJsonObject(pid).has("total")
                        ? byId.getAsJsonObject(pid).get("total").getAsInt() : 0);
                players.add(row);
            }
        }
        s.put("players", players);
        return s;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    /** The saved snapshot as JSON, or null; a solo game carries the game's own final score when History has it. */
    static byte[] load(String gameId) {
        String id = fileId(gameId);
        if (id.isEmpty()) {
            return null;
        }
        try {
            Path file = dir().resolve(id + ".json");
            if (!Files.exists(file)) {
                return null;
            }
            JsonObject game = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (SoloHistory.solo(game)) {
                SoloHistory.apply(game, all());
            }
            return GSON.toJson(game).getBytes(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }
}
