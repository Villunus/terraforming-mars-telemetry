package dev.tmmissioncontrol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GameState {
    private final Object lock = new Object();
    public String gameId = "";
    public String board = "Tharsis";
    public int generation = 1;
    public String phase = "Waiting for a match";
    public String url = "";
    public List<String> urls = List.of();
    public boolean firewallOpen;
    public boolean live;
    public boolean prelude;
    public boolean venus;
    public boolean colonies;
    /** Global parameters as the log reports them: °C, oxygen %, oceans placed, Venus %. */
    public int temperature = -30;
    public int oxygen;
    public int oceanLevel;
    public int venusScale;
    /**
     * False while players are still choosing corporations and starting cards. The game shows no opponent
     * corporation until then, so neither do we (the AI picks first and its choice is already in the log).
     */
    public boolean corpsRevealed = true;
    public int humanId = 1;
    public final List<Integer> playerOrder = new ArrayList<>();
    public final Map<Integer, PlayerState> players = new LinkedHashMap<>();
    public final Map<Integer, PlacedTile> tiles = new LinkedHashMap<>();
    public final List<Map<String, Object>> scoreHistory = new ArrayList<>();
    public final List<ScoreEvent> scoreEvents = new ArrayList<>();
    public final List<LogEntry> playLog = new ArrayList<>();
    public final Set<String> listedMilestones = new LinkedHashSet<>();
    /** Colony tiles in play, in the order setup lists them. */
    public final Map<String, ColonyTile> colonyTiles = new LinkedHashMap<>();
    public ActivePlay activePlay;
    /** Save this game to Past games as it goes. Off for games replayed only to import them. */
    public boolean autoSave = true;
    /** Seconds after the game client launched at which this game was created (the log's [elapsed] stamp). */
    public double createdElapsed = -1;
    /** When the game started, in epoch millis, once the log's launch time is known; -1 until then. */
    public long startedAtMs = -1;

    public ColonyTile colonyTile(String name) {
        return colonyTiles.computeIfAbsent(name, ColonyTile::new);
    }

    public PlayerState player(int id) {
        return players.computeIfAbsent(id, PlayerState::new);
    }

    public void reset() {
        synchronized (lock) {
            // A game quit partway has no game-end save; keep what we have before it's wiped.
            GameArchive.save(this);
            gameId = "";
            board = "Tharsis";
            generation = 1;
            phase = "Setup";
            live = true;
            prelude = false;
            venus = false;
            colonies = false;
            temperature = -30;
            oxygen = 0;
            oceanLevel = 0;
            venusScale = 0;
            corpsRevealed = false;
            humanId = 1;
            playerOrder.clear();
            players.clear();
            tiles.clear();
            scoreHistory.clear();
            scoreEvents.clear();
            playLog.clear();
            listedMilestones.clear();
            colonyTiles.clear();
            createdElapsed = -1;
            startedAtMs = -1;
            activePlay = null;
            player(1);
            player(2);
        }
    }

    public Object snapshot() {
        synchronized (lock) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("url", url);
            out.put("urls", urls);
            out.put("firewallOpen", firewallOpen);
            out.put("gameId", gameId);
            out.put("board", board);
            out.put("colonies", colonies);
            out.put("colonyTiles", colonyTiles.values().stream().map(ColonyTile::toMap).toList());
            out.put("generation", generation);
            out.put("phase", phase);
            Map<String, Object> globals = new LinkedHashMap<>();
            globals.put("temperature", temperature);
            globals.put("oxygen", oxygen);
            globals.put("oceans", oceanLevel);
            globals.put("venus", venusScale);
            out.put("globals", globals);
            out.put("live", live);
            out.put("activePlay", activePlay);
            out.put("log", new ArrayList<>(playLog));
            out.put("startedAt", startedAtMs > 0 ? java.time.Instant.ofEpochMilli(startedAtMs).toString() : null);
            List<PlayerState> seated = seatedPlayers();
            PlayerState you = seated.stream().filter(p -> p.human).findFirst()
                    .orElse(players.getOrDefault(humanId, player(humanId)));
            List<PlayerState> table = tableOrder(seated, you);
            if (!corpsRevealed) {
                table = table.stream().map(p -> p.id == you.id ? p : p.concealed()).toList();
            }
            List<PlayerState> opponents = table.stream().filter(p -> p.id != you.id).toList();
            out.put("you", you);
            out.put("opponents", opponents);
            out.put("players", table);
            out.put("tiles", new ArrayList<>(tiles.values()));
            out.put("score", ScoreCalculator.estimate(this, table, you));
            out.put("milestones", MilestoneAdvisor.snapshot(this, you, table));
            out.put("fundedAwards", ScoreCalculator.fundedAwards(this, table));
            out.put("updateAvailable", GithubMsiUpdater.availableVersion());
            return out;
        }
    }

    public List<PlayerState> seatedPlayers() {
        List<PlayerState> seated = players.values().stream().filter(p -> p.seated).toList();
        if (!seated.isEmpty()) {
            return seated;
        }
        return new ArrayList<>(players.values());
    }

    /** You first, then the rest of Steam {@code GenerationPlayerOrder}. */
    List<PlayerState> tableOrder(List<PlayerState> seated, PlayerState you) {
        List<PlayerState> circle = new ArrayList<>();
        for (int id : playerOrder) {
            PlayerState player = players.get(id);
            if (player != null && seated.contains(player) && !circle.contains(player)) {
                circle.add(player);
            }
        }
        for (PlayerState player : seated) {
            if (!circle.contains(player)) {
                circle.add(player);
            }
        }
        if (you == null || circle.isEmpty()) {
            return circle;
        }
        int start = circle.indexOf(you);
        if (start <= 0) {
            return circle;
        }
        List<PlayerState> rotated = new ArrayList<>();
        rotated.addAll(circle.subList(start, circle.size()));
        rotated.addAll(circle.subList(0, start));
        return rotated;
    }

    public Object lock() {
        return lock;
    }

    public PlayedCard cardByNumber(int number) {
        for (PlayerState player : players.values()) {
            PlayedCard card = player.cardByNumber(number);
            if (card != null) {
                return card;
            }
        }
        return null;
    }

    public PlayerState ownerOfCard(int number) {
        for (PlayerState player : players.values()) {
            if (player.cardByNumber(number) != null) {
                return player;
            }
        }
        return null;
    }

    public ScoreEvent addScoreEvent(int playerId, String kind, String label, int delta) {
        if (playerId <= 0 || label == null || label.isBlank()) {
            return null;
        }
        ScoreEvent event = new ScoreEvent(generation, playerId, kind, label, delta);
        scoreEvents.add(event);
        return event;
    }

    public LogEntry addLog(int playerId, String kind, String name, String color) {
        if (playerId <= 0 || name == null || name.isBlank()) {
            return null;
        }
        LogEntry entry = new LogEntry(generation, playerId, kind, name.trim(), color);
        playLog.add(entry);
        return entry;
    }

    public void recordGenerationEnd() {
        List<PlayerState> seated = seatedPlayers();
        if (seated.stream().noneMatch(p -> p.seated)) {
            return;
        }
        Map<String, Object> point = compactPoint(generation, seated, false);
        Object lastGen = scoreHistory.isEmpty() ? null : scoreHistory.get(scoreHistory.size() - 1).get("generation");
        if (Integer.valueOf(generation).equals(lastGen)) {
            scoreHistory.set(scoreHistory.size() - 1, point);
        } else {
            scoreHistory.add(point);
        }
        // Each generation end (and game end, which comes through here) updates the saved copy of this game.
        GameArchive.save(this);
    }

    List<Map<String, Object>> chartHistory(Map<String, Map<String, Integer>> liveById) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> point : scoreHistory) {
            out.add(new LinkedHashMap<>(point));
        }
        boolean ended = phase != null && phase.toLowerCase().contains("endgame");
        Map<String, Object> live = new LinkedHashMap<>();
        live.put("generation", generation);
        live.put("now", !ended);
        live.put("byId", liveById);
        if (ended) {
            if (out.isEmpty() || !Integer.valueOf(generation).equals(out.get(out.size() - 1).get("generation"))) {
                live.put("now", false);
                out.add(live);
            } else {
                live.put("now", false);
                out.set(out.size() - 1, live);
            }
        } else {
            out.add(live);
        }
        return out;
    }

    private Map<String, Object> compactPoint(int gen, List<PlayerState> seated, boolean now) {
        Map<Integer, ScoreCalculator.Breakdown> scored = ScoreCalculator.compute(this, seated);
        Map<String, Map<String, Integer>> byId = new LinkedHashMap<>();
        for (PlayerState player : seated) {
            ScoreCalculator.Breakdown b = scored.get(player.id);
            if (b != null) {
                byId.put(String.valueOf(player.id), chartValues(b, player));
            }
        }
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("generation", gen);
        point.put("now", now);
        point.put("byId", byId);
        return point;
    }

    /** Score breakdown plus production and resources on hand, one chart point for one player. */
    static Map<String, Integer> chartValues(ScoreCalculator.Breakdown b, PlayerState p) {
        Map<String, Integer> out = b.toCompact();
        out.put("prod-mc", p.megaCreditProd);
        out.put("prod-steel", p.steelProd);
        out.put("prod-ti", p.titaniumProd);
        out.put("prod-plant", p.plantProd);
        out.put("prod-energy", p.energyProd);
        out.put("prod-heat", p.heatProd);
        out.put("qty-mc", p.megaCredits);
        out.put("qty-steel", p.steel);
        out.put("qty-ti", p.titanium);
        out.put("qty-plant", p.plants);
        out.put("qty-energy", p.energy);
        out.put("qty-heat", p.heat);
        return out;
    }
}
