package dev.tmmissioncontrol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The game's own final scores for solo games. When a solo game is left and saved to History, the game writes
 * its scoreboard to {@code Saves/LocalSoloGameSave}: TR, each greenery and city with its VP, card VP and the
 * final score. That includes city points from the neutral greeneries solo setup places, which the log never
 * mentions, so a matched game shows the game's numbers instead of our estimate.
 */
final class SoloHistory {
    /** Games saved before seeds were kept: a History record saved this long after a game started can be it. */
    private static final Duration LONGEST_GAME = Duration.ofHours(6);

    private static final Object LOCK = new Object();
    private static long loadedModified = -1;
    private static List<JsonObject> entries = List.of();

    private SoloHistory() {
    }

    static Path file() {
        return Path.of(System.getProperty("user.home"), "AppData", "LocalLow", "LuckyHammers", "Terraforming Mars",
                "Saves", "LocalSoloGameSave", "LocalSoloGameSave");
    }

    /** Every History record, re-read whenever the game rewrites the file. */
    private static List<JsonObject> entries() {
        synchronized (LOCK) {
            try {
                Path file = file();
                long modified = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : 0;
                if (modified != loadedModified) {
                    loadedModified = modified;
                    List<JsonObject> out = new ArrayList<>();
                    if (modified > 0) {
                        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                                .getAsJsonObject();
                        for (JsonElement el : root.getAsJsonArray("localSoloGameDatas")) {
                            out.add(el.getAsJsonObject());
                        }
                    }
                    entries = out;
                }
            } catch (Exception ex) {
                System.err.println("Could not read the solo History: " + ex.getMessage());
                entries = List.of();
            }
            return entries;
        }
    }

    /**
     * For a solo game (live snapshot or saved copy): put the game's own final score in its place when History
     * has it, and say whether it does ("official" or "pending").
     */
    static void apply(JsonObject game) {
        apply(game, List.of());
    }

    /** As {@link #apply(JsonObject)}, with the other saved games so an unseeded game can't take a later one's record. */
    static void apply(JsonObject game, List<JsonObject> others) {
        if (!solo(game)) {
            return;
        }
        JsonObject player = game.getAsJsonArray("players").get(0).getAsJsonObject();
        JsonObject record = find(game, player, others);
        JsonObject score = game.has("score") && game.get("score").isJsonObject() ? game.getAsJsonObject("score") : null;
        if (record == null || score == null) {
            if (score != null) {
                score.addProperty("official", false);
            }
            return;
        }
        JsonObject data = record.getAsJsonObject("ScoreData");
        int tr = num(data, "TerraformingScore");
        int greeneries = sumVp(data, "GreeneriesScore");
        int cities = sumVp(data, "CitiesScore");
        int milestones = sumVp(data, "MilestonesScore");
        int awards = sumVp(data, "AwardsScore");
        int cards = num(data, "VictoryPoints");
        int total = num(data, "FinalScore");
        String pid = String.valueOf(player.get("id").getAsInt());
        if (score.has("byId") && score.getAsJsonObject("byId").has(pid)) {
            JsonObject b = score.getAsJsonObject("byId").getAsJsonObject(pid);
            b.addProperty("tr", tr);
            b.addProperty("greeneries", greeneries);
            b.addProperty("cities", cities);
            b.addProperty("milestones", milestones);
            b.addProperty("awards", awards);
            b.addProperty("cards", cards);
            b.addProperty("total", total);
            if (data.has("Credits")) {
                b.addProperty("mc", num(data, "Credits"));
            }
        }
        score.addProperty("official", true);
        JsonObject official = new JsonObject();
        official.addProperty("total", total);
        official.addProperty("winner", data.has("IsWinner") && data.get("IsWinner").getAsBoolean());
        game.add("official", official);
    }

    /**
     * The History record for this game: same corporation and the same seed. Games saved before seeds were kept
     * match the first record with that corporation saved after the game started (History is written when a
     * finished game is left), so only finished games are matched that way.
     */
    static boolean solo(JsonObject game) {
        return game != null && game.has("players") && game.getAsJsonArray("players").size() == 1;
    }

    private static boolean finished(JsonObject game) {
        return (game.has("finished") && game.get("finished").getAsBoolean())
                || str(game, "phase").toLowerCase(Locale.ROOT).contains("endgame");
    }

    private static String corp(JsonObject game) {
        return norm(str(game.getAsJsonArray("players").get(0).getAsJsonObject(), "corporation"));
    }

    private static JsonObject find(JsonObject game, JsonObject player, List<JsonObject> others) {
        String corp = norm(str(player, "corporation"));
        if (corp.isEmpty()) {
            return null;
        }
        String seed = str(game, "gameSeed");
        boolean finished = finished(game);
        Instant started = instant(str(game, "startedAt"));
        JsonObject best = null;
        Duration bestGap = null;
        for (JsonObject entry : entries()) {
            JsonObject data = entry.getAsJsonObject("ScoreData");
            JsonObject save = entry.getAsJsonObject("LocalGameSave");
            if (data == null || save == null || !corp.equals(norm(str(data, "PlayerName")))) {
                continue;
            }
            if (!seed.isEmpty()) {
                if (seed.equals(str(save, "GameSeed"))) {
                    return entry;
                }
                continue;
            }
            Instant saved = instant(str(save, "DatePlayed"));
            if (!finished || started == null || saved == null || saved.isBefore(started)) {
                continue;
            }
            Duration gap = Duration.between(started, saved);
            if (gap.compareTo(LONGEST_GAME) <= 0 && (bestGap == null || gap.compareTo(bestGap) < 0)
                    && !laterGame(game, corp, started, saved, others)) {
                best = entry;
                bestGap = gap;
            }
        }
        return best;
    }

    /** Another finished solo game with this corporation started after this one and before the record was saved. */
    private static boolean laterGame(JsonObject game, String corp, Instant started, Instant saved,
            List<JsonObject> others) {
        for (JsonObject other : others) {
            if (other == game || !solo(other) || !finished(other) || !corp.equals(corp(other))
                    || str(other, "gameId").equals(str(game, "gameId"))) {
                continue;
            }
            Instant at = instant(str(other, "startedAt"));
            if (at != null && at.isAfter(started) && at.isBefore(saved)) {
                return true;
            }
        }
        return false;
    }

    /** "CrediCor" and "Credicor", "PhoboLog" and "Phobolog": compare letters only. */
    private static String norm(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static int sumVp(JsonObject data, String key) {
        int sum = 0;
        if (data.has(key) && data.get(key).isJsonArray()) {
            JsonArray items = data.getAsJsonArray(key);
            for (JsonElement el : items) {
                JsonObject item = el.getAsJsonObject();
                sum += item.has("Item2") ? item.get("Item2").getAsInt() : 0;
            }
        }
        return sum;
    }

    private static int num(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : 0;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static Instant instant(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(iso).toInstant();
        } catch (Exception ex) {
            try {
                return Instant.parse(iso);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
