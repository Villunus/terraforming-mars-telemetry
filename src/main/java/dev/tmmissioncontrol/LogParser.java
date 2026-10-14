package dev.tmmissioncontrol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogParser {
    private static final Pattern PLAYING_CARD = Pattern.compile(
            "Playing(?: action)? \\((\\d+(?:\\.\\d+)*)\\) CardPlayerAction Card: (.+)$");
    private static final Pattern CONFIRM_PLAY = Pattern.compile(
            "Do you want to play: (.+)\\??$");
    private static final Pattern PLAYER_PLAYS_ID = Pattern.compile(
            "\\[PlayerAction] Player (\\d+) plays the card (\\d+)");
    private static final Pattern PLAYER_PLAY = Pattern.compile(
            "\\[PlayerAction] Player (\\d+) Play for player (\\d+)");
    private static final Pattern TRAY = Pattern.compile("Setting tray to player (\\d+)");
    private static final Pattern CURRENT_PLAYER = Pattern.compile("CurrentPlayerLocalId: (\\d+)");
    private static final Pattern PLACE_TILE = Pattern.compile(
            "PlaceTile => Type: \\(([^)]+)\\).*Place tile (\\S+) on id (-?\\d+)");
    private static final Pattern RESOURCE = Pattern.compile(
            "\\[PlayerResources] Set (MegaCredit|Steel|Titanium|Plant|Energy|Heat) (quantity|production) from (-?\\d+) to (-?\\d+)");
    private static final Pattern TR = Pattern.compile(
            "\\[BoardDatas] Set terraforming rating for player (\\d+), from (-?\\d+) to (-?\\d+)");
    private static final Pattern GENERATION = Pattern.compile("Increment Generation to (\\d+)");
    private static final Pattern PHASE = Pattern.compile("GameSM enters (\\w+)");
    private static final Pattern GAME_ID = Pattern.compile("^GameID: (.+)$");
    private static final Pattern BOARD = Pattern.compile("^BoardType: (.+)$");
    private static final Pattern PLAYER_HEADER = Pattern.compile("^Player (\\d+)$");
    private static final Pattern AGENT = Pattern.compile("^AgentType: (.+)$");
    private static final Pattern PLAYER_NAME = Pattern.compile("^Name: (.+)$");
    private static final Pattern CORP_ID = Pattern.compile("^\\tCorporation : (\\d+)$");
    private static final Pattern MILESTONE = Pattern.compile(
            "\\[PlayerAction] Playing(?: action)? \\(\\d+\\) Milestone Action: (.+)$");
    private static final Pattern MILESTONE_LISTED = Pattern.compile(
            "\\[PlayerAction] Adding action \\(\\d+\\) Milestone Action: (.+)$");
    private static final Pattern AWARD = Pattern.compile(
            "\\[PlayerAction] Playing(?: action)? \\(\\d+\\) Award Action: (.+)$");
    private static final Pattern STANDARD = Pattern.compile(
            "Playing(?: action)? \\((\\d+(?:\\.\\d+)*)\\) Standard Project: (.+)$");
    private static final Pattern BLUE_ACTION = Pattern.compile(
            "Playing(?: action)? \\((\\d+(?:\\.\\d+)*)\\) Blue Card Action : (.+)$");
    private static final Pattern CORP_ACTION = Pattern.compile(
            "Playing(?: action)? \\((\\d+(?:\\.\\d+)*)\\) Corporation action : (.+)$");
    private static final Pattern PLACE_TILE_ACTION = Pattern.compile(
            "Playing action \\(([\\d.]+)\\) Place(\\w+?)TilePlayerAction");
    private static final Pattern CONVERSION = Pattern.compile(
            "Playing(?: action)? \\(([\\d.]+)\\) (?:Last)?(Plant|Heat)ConversionPlayerAction");
    private static final Pattern CARD_TOKEN = Pattern.compile(
            "\\[PlayerResources] (Adding|Removing) (-?\\d+) (\\w+) (?:to|from) (-?\\d+)");
    private static final Pattern CORP_CITY = Pattern.compile(
            "\\[PlayerAction] (?:Adding|Playing) action \\((\\d+)\\) PlaceCityTilePlayerAction");
    private static final Pattern ASKING_PLAYER = Pattern.compile(
            "asking for input for player (\\d+)");
    private static final Pattern TAB_PLAYER = Pattern.compile("Tab_Player_(\\d+)");
    private static final Pattern STARTING_CARD_PLAYER = Pattern.compile(
            "StartingCardEvent\\tPlayerLocalID : (\\d+)");
    private static final Pattern ADDING_CORP_ACTION = Pattern.compile(
            "\\[PlayerAction] Adding action \\(\\d+\\) Corporation action : (.+)$");
    private static final Pattern TO_PLAYER_BANK = Pattern.compile("to player (\\d+) bank");
    private static final Pattern DRAW_CARDS = Pattern.compile(
            "\\[PlayerAction] Playing(?: action)? \\([\\d.]+\\) Draw(?:AndSelect)?Cards?PlayerAction");
    private static final Pattern ADDING_HAND_CARD = Pattern.compile(
            "\\[PlayerAction] Adding action \\([\\d.]+\\) CardPlayerAction Card: (.+)$");
    private static final Pattern STAMP = Pattern.compile("^\\[([\\d.,]+)]");
    /** Start of a game-action dump; colony builds and trades are only described inside these. */
    private static final Pattern EVENT_START = Pattern.compile(
            "\\[PlayerAction] Player \\d+ Play for player (\\d+) event : GameActionEvent");
    /** Pressing Use on a card logs the planned payment just before the "Do you want to play" prompt. */
    private static final Pattern PAYMENT = Pattern.compile("] (steel|titanium|heat|microbe|floater)Cost: (\\d+)$");
    /** Setup lists each colony tile in play once per player. */
    private static final Pattern COLONY_TILE = Pattern.compile("\\[PlayerAction] Added (\\w+) actions to \\d+");
    private static final Pattern GAIN_FLEET = Pattern.compile(
            "\\[PlayerAction] Playing \\([\\d.]+\\) GainTradeFleetPlayerAction");
    /** Chat lines carry wall-clock time (local), e.g. "[6.506]... ChatEntry ... Time 9/27/2026 9:37:14 PM". */
    private static final Pattern CHAT_CLOCK = Pattern.compile(
            "^\\[([\\d,]+\\.\\d+)].*\\bTime (\\d{1,2}/\\d{1,2}/\\d{4} \\d{1,2}:\\d{2}:\\d{2} [AP]M)");
    private static final java.time.format.DateTimeFormatter CHAT_TIME =
            java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", java.util.Locale.US);

    private final CardDatabase cards;
    private final GameState state;

    private int currentPlayer = 1;
    private int resourcePlayer = 0;
    private int headerPlayer = 0;
    private boolean inHeader;
    private int pendingCorpMc = -1;
    private int pendingCorpPlayer = 0;
    private int pendingSteamCorpId = -1;
    private String pendingNamedCorp = "";
    private List<Card> pendingCorps = List.of();
    private String lastCardKey = "";
    private String lastBlueAction = "";
    private boolean collectDraws;
    private String pendingHandCard = "";
    private String lastLogKey = "";
    /** When the game client launched (epoch millis), learned from a chat clock line; the log itself has no dates. */
    long launchEpochMs = -1;
    private final java.util.Map<Integer, String> steamCorps = new java.util.HashMap<>();
    private ActionEvent pendingEvent;
    private final java.util.Map<String, Integer> pendingPayment = new java.util.LinkedHashMap<>();
    /** Each action is dumped more than once; its Time stamp tells repeats apart. */
    private final java.util.Set<String> seenEvents = new java.util.HashSet<>();

    public LogParser(CardDatabase cards, GameState state) {
        this.cards = cards;
        this.state = state;
    }

    public void replay(Path logFile) throws Exception {
        launchEpochMs = -1; // a replay is a new client session; don't carry the last one's launch time
        int startLine = lastGameLine(logFile);
        try (var reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            int lineNo = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (lineNo >= startLine) {
                    consume(line);
                } else {
                    noteClock(line); // the launch-time anchor usually comes before the game starts
                }
            }
        }
    }

    static int lastGameLine(Path logFile) throws Exception {
        int lineNo = 0;
        int last = 1;
        try (var reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.contains("Created new Game")) {
                    last = lineNo;
                }
            }
        }
        return last;
    }

    public void consume(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        synchronized (state.lock()) {
            noteClock(line);
            if (line.contains("Created new Game")) {
                state.reset();
                state.createdElapsed = elapsedOf(line);
                applyStartTime();
                currentPlayer = 1;
                resourcePlayer = 0;
                headerPlayer = 0;
                inHeader = true;
                pendingCorpMc = -1;
                pendingCorps = List.of();
                pendingCorpPlayer = 0;
                pendingSteamCorpId = -1;
                pendingNamedCorp = "";
                lastCardKey = "";
                lastBlueAction = "";
                collectDraws = false;
                pendingHandCard = "";
                lastLogKey = "";
                steamCorps.clear();
                pendingEvent = null;
                seenEvents.clear();
                return;
            }
            parseHeader(line);
            Matcher m;

            if (pendingEvent != null) {
                if (line.startsWith("[")) {
                    finishEvent(pendingEvent);
                    pendingEvent = null;
                } else {
                    pendingEvent.feed(line);
                }
            }
            m = EVENT_START.matcher(line);
            if (m.find()) {
                pendingEvent = new ActionEvent(Integer.parseInt(m.group(1)));
            }
            m = COLONY_TILE.matcher(line);
            if (m.find() && state.colonies) {
                state.colonyTile(m.group(1));
            }
            if (line.contains("Entered the Colonies Solar Phase")) {
                state.colonyTiles.values().forEach(ColonyTile::solarPhase);
            }
            if (GAIN_FLEET.matcher(line).find()) {
                state.player(currentPlayer).tradeFleets++;
            }

            m = TRAY.matcher(line);
            if (m.find()) {
                setCurrentPlayer(Integer.parseInt(m.group(1)));
            }
            m = CURRENT_PLAYER.matcher(line);
            if (m.find()) {
                setCurrentPlayer(Integer.parseInt(m.group(1)));
            }
            m = PLAYER_PLAY.matcher(line);
            if (m.find()) {
                setCurrentPlayer(Integer.parseInt(m.group(1)));
                resourcePlayer = Integer.parseInt(m.group(2));
            }
            m = PLAYER_PLAYS_ID.matcher(line);
            if (m.find()) {
                setCurrentPlayer(Integer.parseInt(m.group(1)));
            }
            m = TAB_PLAYER.matcher(line);
            if (m.find()) {
                int tabId = Integer.parseInt(m.group(1));
                rememberTurnOrder(tabId);
                if (productionPhase()) {
                    setCurrentPlayer(tabId);
                }
            }
            m = STARTING_CARD_PLAYER.matcher(line);
            if (m.find()) {
                setCurrentPlayer(Integer.parseInt(m.group(1)));
            }

            m = ADDING_CORP_ACTION.matcher(line);
            if (m.find()) {
                pendingNamedCorp = m.group(1).trim();
            } else if (line.contains("[PlayerAction] Adding action")) {
                pendingNamedCorp = "";
            }
            m = TO_PLAYER_BANK.matcher(line);
            if (m.find()) {
                int bankPlayer = Integer.parseInt(m.group(1));
                if (collectDraws && !pendingHandCard.isBlank()) {
                    if (state.player(bankPlayer).human) {
                        addDrawn(pendingHandCard);
                        logDrawn(bankPlayer, pendingHandCard);
                    }
                    pendingHandCard = "";
                }
                if (!pendingNamedCorp.isBlank()) {
                    Card named = cards.find(pendingNamedCorp);
                    if (named != null) {
                        assignCorp(state.player(bankPlayer), named);
                    }
                    pendingNamedCorp = "";
                }
            }

            m = GAME_ID.matcher(line.trim());
            if (m.matches()) {
                state.gameId = m.group(1).trim();
            }
            m = BOARD.matcher(line.trim());
            if (m.matches()) {
                state.board = m.group(1).trim();
            }
            m = GENERATION.matcher(line);
            if (m.find()) {
                int next = Integer.parseInt(m.group(1));
                if (next > state.generation) {
                    state.recordGenerationEnd();
                    lastBlueAction = "";
                    state.generation = next;
                    for (PlayerState p : state.players.values()) {
                        p.fleetsUsed = 0;
                    }
                }
            }
            m = PHASE.matcher(line);
            if (m.find() && !"Transition".equals(m.group(1))) {
                String gamePhase = m.group(1);
                if (!"InitializationPhase".equals(gamePhase) && !"PlayerSetup".equals(gamePhase)) {
                    state.corpsRevealed = true;
                }
                state.phase = humanPhase(gamePhase);
                resourcePlayer = 0;
                if (state.phase.toLowerCase().contains("endgame")) {
                    state.recordGenerationEnd();
                }
                if ("Actions".equals(state.phase)
                        && (corpTaken("Tharsis Republic") || state.generation > 1)) {
                    resolveLeftoverCorps();
                }
            }

            m = TR.matcher(line);
            if (m.find()) {
                int playerId = Integer.parseInt(m.group(1));
                int from = Integer.parseInt(m.group(2));
                int to = Integer.parseInt(m.group(3));
                PlayerState p = state.player(playerId);
                p.tr = to;
                if (from > 0 && to != from) {
                    state.addScoreEvent(playerId, "tr", causeFor(p), to - from);
                }
            }

            m = CORP_ID.matcher(line);
            if (m.matches()) {
                pendingCorpPlayer = currentPlayer;
                pendingCorpMc = -1;
                pendingCorps = List.of();
                pendingSteamCorpId = Integer.parseInt(m.group(1));
                // Resources on a corporation are logged against minus this id ("Adding 3 Floater to -23").
                state.player(pendingCorpPlayer).corpSteamId = pendingSteamCorpId;
                String known = SteamCorporations.nameFor(pendingSteamCorpId);
                if (known == null) {
                    known = steamCorps.get(pendingSteamCorpId);
                }
                if (known != null) {
                    steamCorps.put(pendingSteamCorpId, known);
                    assignCorpByName(state.player(pendingCorpPlayer), known);
                }
            }

            m = RESOURCE.matcher(line);
            if (m.find()) {
                applyResource(m.group(1), m.group(2), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
            }

            m = CARD_TOKEN.matcher(line);
            if (m.find()) {
                int amount = Integer.parseInt(m.group(2));
                if ("Removing".equals(m.group(1))) {
                    amount = -Math.abs(amount);
                }
                addCardTokens(Integer.parseInt(m.group(4)), m.group(3), amount);
                // A card holding floaters, microbes or animals wakes Titan, Enceladus or Miranda.
                String held = m.group(3).toLowerCase(java.util.Locale.ROOT).replaceAll("s$", "");
                state.colonyTiles.values().forEach(t -> t.wake(held));
            }

            m = PAYMENT.matcher(line);
            if (m.find()) {
                pendingPayment.put(m.group(1), Integer.parseInt(m.group(2)));
            }
            m = CONFIRM_PLAY.matcher(line);
            if (m.find()) {
                setActive(currentPlayer, m.group(1).trim(), null, true);
                pendingPayment.forEach((what, n) -> {
                    if (n > 0) {
                        state.activePlay.payment.put(what, n);
                    }
                });
                pendingPayment.clear();
            }

            m = PLAYING_CARD.matcher(line);
            if (m.find()) {
                collectDraws = false;
                pendingHandCard = "";
                playCard(currentPlayer, m.group(2).trim());
            }
            if (DRAW_CARDS.matcher(line).find() && !line.contains("Adding action")) {
                beginDrawCollect();
            }
            m = ADDING_HAND_CARD.matcher(line);
            if (m.find() && collectDraws) {
                pendingHandCard = m.group(1).trim();
            }
            if (line.contains("finished handling event of type:")) {
                collectDraws = false;
                pendingHandCard = "";
            }

            m = STANDARD.matcher(line);
            if (m.find()) {
                collectDraws = false;
                pendingHandCard = "";
                lastBlueAction = "";
                String project = standardProjectName(m.group(2).trim());
                setActive(currentPlayer, "Standard Project: " + project, null, false);
                logMove(line, currentPlayer, "project", project, "");
            }

            m = BLUE_ACTION.matcher(line);
            if (m.find() && !line.contains("Adding action")) {
                lastBlueAction = cards.displayName(m.group(2).trim());
                markActionUsed(m.group(2).trim());
                logMove(line, currentPlayer, "action", lastBlueAction, "");
                setActive(currentPlayer, "Using " + m.group(2).trim(), null, false);
            }
            m = CORP_ACTION.matcher(line);
            if (m.find() && !line.contains("Adding action")) {
                lastBlueAction = "";
                setActive(currentPlayer, "Using " + m.group(2).trim(), null, false);
                logMove(line, currentPlayer, "corp-action", m.group(2).trim(), "");
            }
            m = CONVERSION.matcher(line);
            if (m.find() && !line.contains("Adding action")) {
                lastBlueAction = "";
                startConversion(m.group(2));
                logMove(line, currentPlayer, "convert", m.group(2), "");
            }
            m = PLACE_TILE_ACTION.matcher(line);
            if (m.find()) {
                // Cards with their own tile (Mining Area, Mohole Area...) have their own action; call them special.
                // Flooding has its own action too, but places an ocean.
                String kind = m.group(2);
                pinPlacement("Flooding".equals(kind) ? "Ocean"
                        : kind.matches("Ocean|City|Greenery|Generic") ? kind : "Special " + kind);
            }

            m = ASKING_PLAYER.matcher(line);
            if (m.find()) {
                setCurrentPlayer(Integer.parseInt(m.group(1)));
            }
            m = CORP_CITY.matcher(line);
            if (m.find() && Integer.parseInt(m.group(1)) != 5 && state.generation <= 1) {
                assignTharsisIfPending();
            }

            m = PLACE_TILE.matcher(line);
            if (m.find()) {
                onPlace(m.group(1), Integer.parseInt(m.group(3)));
            }
            if (line.contains(":: PlaceTile") && line.contains("Callback is called")) {
                collectDraws = false;
                pendingHandCard = "";
            }

            m = MILESTONE_LISTED.matcher(line);
            if (m.find()) {
                state.listedMilestones.add(m.group(1).trim());
            }
            m = MILESTONE.matcher(line);
            if (m.find() && !line.contains("Playing action")) {
                lastBlueAction = "";
                String name = m.group(1).trim();
                PlayerState claimer = state.player(currentPlayer);
                if (!claimer.milestones.contains(name)) {
                    claimer.milestones.add(name);
                    ScoreEvent event = state.addScoreEvent(currentPlayer, "milestone", "+5 " + name, 5);
                    if (event != null) {
                        event.source = "Milestone: " + name;
                    }
                    state.addLog(currentPlayer, "milestone", name, "");
                }
            }
            m = AWARD.matcher(line);
            if (m.find() && !line.contains("Playing action")) {
                lastBlueAction = "";
                String name = m.group(1).trim();
                state.player(currentPlayer).awards.add(name);
                ScoreEvent event = state.addScoreEvent(currentPlayer, "award", "funded " + name, 0);
                if (event != null) {
                    event.source = "Funded " + name;
                }
                logMove(line, currentPlayer, "award", name, "");
            }

            if (line.contains("ShowOpponentCardPage") && state.activePlay != null) {
                state.activePlay.yours = false;
            }
        }
    }

    private void parseHeader(String line) {
        String trimmed = line.trim();
        if (trimmed.equals("----- Game Info -----")) {
            inHeader = true;
            return;
        }
        if (trimmed.equals("---------------------")) {
            inHeader = false;
            headerPlayer = 0;
            PlayerColors.apply(state);
            return;
        }
        if (!inHeader) {
            return;
        }
        Matcher m = PLAYER_HEADER.matcher(trimmed);
        if (m.matches()) {
            headerPlayer = Integer.parseInt(m.group(1));
            PlayerState seated = state.player(headerPlayer);
            seated.seated = true;
            seated.color = PlayerState.colorFor(headerPlayer);
            return;
        }
        if (trimmed.startsWith("Prelude Phase:")) {
            state.prelude = trimmed.endsWith("True");
            return;
        }
        if (trimmed.startsWith("VenusNext:")) {
            state.venus = trimmed.endsWith("True");
            return;
        }
        if (trimmed.startsWith("Colonies:")) {
            state.colonies = trimmed.endsWith("True");
            return;
        }
        if (headerPlayer == 0) {
            return;
        }
        m = PLAYER_NAME.matcher(trimmed);
        if (m.matches()) {
            String name = m.group(1).trim();
            if (name.matches("\\d+ Player")) {
                state.player(headerPlayer).name = "Player " + headerPlayer;
            } else {
                state.player(headerPlayer).name = name;
            }
            return;
        }
        if (trimmed.startsWith("AI - ")) {
            state.player(headerPlayer).name = trimmed;
            return;
        }
        m = AGENT.matcher(trimmed);
        if (m.matches()) {
            boolean human = m.group(1).equalsIgnoreCase("Human");
            state.player(headerPlayer).human = human;
            if (human) {
                state.humanId = headerPlayer;
            }
            return;
        }
        if (trimmed.startsWith("IsMe:")) {
            boolean me = trimmed.endsWith("True");
            PlayerState seated = state.player(headerPlayer);
            // Offline games also mark the AI seat IsMe. Only a human agent is the local player.
            if (me && !seated.human) {
                return;
            }
            seated.human = me;
            if (me) {
                state.humanId = headerPlayer;
            } else if (state.humanId == headerPlayer) {
                state.humanId = otherHuman(headerPlayer);
            }
            if (!me && "You".equals(seated.name)) {
                seated.name = "Player " + headerPlayer;
            }
        }
    }

    private int otherHuman(int exceptId) {
        for (PlayerState player : state.players.values()) {
            if (player.id != exceptId && player.human) {
                return player.id;
            }
        }
        return 1;
    }

    private void applyResource(String kind, String qtyOrProd, int from, int to) {
        boolean production = "production".equals(qtyOrProd);
        PlayerState target;
        if (pendingCorpPlayer > 0 && !production && "MegaCredit".equals(kind) && from == 0
                && "Unknown".equals(state.player(pendingCorpPlayer).corporation)) {
            target = state.player(pendingCorpPlayer);
        } else if (resourcePlayer > 0) {
            target = state.player(resourcePlayer);
        } else {
            target = findOwner(kind, production, from, to);
            if (target == null) {
                target = state.player(currentPlayer);
            }
        }
        setField(target, kind, production, to);
        if (!production && "MegaCredit".equals(kind) && from == 0) {
            if (target.startingMc < 0) {
                target.startingMc = to;
            }
            if ("Unknown".equals(target.corporation)) {
                identifyCorpByMc(target, to);
            }
        }
        if (!production && pendingCorps.size() > 1 && target.id == pendingCorpPlayer) {
            refineCorpByResource(target, kind, to);
        }
        if (production && pendingCorps.size() > 1 && "Unknown".equals(target.corporation)) {
            refineCorpByProduction(target, kind, to);
        }
        // Every seat starts with production set 0 → 1 before the game begins; that isn't a cause.
        boolean initialSet = from == 0 && "Setup".equals(state.phase);
        if (production && to != from && !initialSet && !productionPhase()) {
            state.addScoreEvent(target.id, "prod-" + prodKey(kind), causeFor(target), to - from);
        }
    }

    private static String prodKey(String kind) {
        return switch (kind) {
            case "MegaCredit" -> "mc";
            case "Titanium" -> "ti";
            default -> kind.toLowerCase();
        };
    }

    /** What the log is resolving right now, named for the chart detail. */
    private String causeFor(PlayerState target) {
        ActivePlay play = state.activePlay;
        if (play == null || play.cardName == null || play.cardName.isBlank()) {
            if (state.generation <= 1 && !"Unknown".equals(target.corporation)) {
                return target.corporation;
            }
            return "Other";
        }
        String name = play.cardName;
        if (name.startsWith("Using ")) {
            name = name.substring(6) + " action";
        } else if ("Action".equals(play.colorLabel)) {
            // setActive strips "Using " from card actions and marks them with the Action label.
            name = name + " action";
        } else if (name.startsWith("Standard Project: ")) {
            name = "Standard project: " + name.substring(18);
        } else if (name.startsWith("Placing ")) {
            name = name.substring(8) + " placement";
        }
        if (play.playerId != target.id) {
            name += " (" + state.player(play.playerId).displayName() + ")";
        }
        return name;
    }

    private void identifyCorpByMc(PlayerState player, int mc) {
        List<Card> matches = new ArrayList<>();
        for (Card corp : cards.corpsWithStartingMc(mc, state.prelude, state.venus, state.colonies)) {
            if (!corpTaken(corp.name)) {
                matches.add(corp);
            }
        }
        pendingCorpPlayer = player.id;
        pendingCorpMc = mc;
        pendingCorps = matches;
        player.startingMc = mc;
        if (matches.size() == 1) {
            assignCorp(player, matches.get(0));
            if (pendingSteamCorpId > 0) {
                steamCorps.put(pendingSteamCorpId, matches.get(0).name);
            }
        }
    }

    private void refineCorpByResource(PlayerState player, String kind, int to) {
        String key = switch (kind) {
            case "Steel" -> "steel";
            case "Titanium" -> "ti";
            case "Plant" -> "plant";
            default -> "";
        };
        if (key.isEmpty()) {
            return;
        }
        for (Card corp : pendingCorps) {
            if (CardDatabase.number(corp.resources.get(key)) == to) {
                assignCorp(player, corp);
                return;
            }
        }
    }

    private void refineCorpByProduction(PlayerState player, String kind, int to) {
        String key = switch (kind) {
            case "Heat" -> "heat";
            case "Energy" -> "energy";
            case "Steel" -> "steel";
            case "Titanium" -> "ti";
            case "Plant" -> "plant";
            case "MegaCredit" -> "mc";
            default -> "";
        };
        if (key.isEmpty()) {
            return;
        }
        for (Card corp : pendingCorps) {
            int extra = CardDatabase.number(corp.production.get(key));
            if (extra > 0 && to == 1 + extra) {
                assignCorp(player, corp);
                return;
            }
        }
    }

    private void rememberTurnOrder(int playerId) {
        int seated = 0;
        for (PlayerState player : state.players.values()) {
            if (player.seated) {
                seated++;
            }
        }
        if (seated == 0 || state.playerOrder.size() >= seated) {
            return;
        }
        if (!state.playerOrder.contains(playerId)) {
            state.playerOrder.add(playerId);
        }
    }

    private boolean corpTaken(String name) {
        for (PlayerState p : state.players.values()) {
            if (name.equals(p.corporation)) {
                return true;
            }
        }
        return false;
    }

    private void assignTharsisIfPending() {
        Card tharsis = cards.find("Tharsis Republic");
        if (tharsis == null || corpTaken(tharsis.name)) {
            resolveLeftoverCorps();
            return;
        }
        PlayerState player = state.player(currentPlayer);
        if (!"Unknown".equals(player.corporation)) {
            return;
        }
        if (player.startingMc != 40 && player.startingMc != -1) {
            return;
        }
        assignCorp(player, tharsis);
        resolveLeftoverCorps();
    }

    private void resolveLeftoverCorps() {
        if (!corpTaken("Tharsis Republic") && state.generation < 2) {
            return;
        }
        Card unmi = cards.find("United Nations Mars Initiative");
        if (unmi == null) {
            return;
        }
        for (PlayerState p : state.players.values()) {
            if ("Unknown".equals(p.corporation) && p.startingMc == 40) {
                assignCorp(p, unmi);
            }
        }
    }

    private void assignCorpByName(PlayerState player, String name) {
        Card corp = cards.find(name);
        if (corp != null) {
            assignCorp(player, corp);
            return;
        }
        pendingCorps = List.of();
        pendingCorpMc = -1;
        if (!"Unknown".equals(player.corporation)) {
            return;
        }
        player.corporation = name;
    }

    private void assignCorp(PlayerState player, Card corp) {
        pendingCorps = List.of();
        pendingCorpMc = -1;
        if (!"Unknown".equals(player.corporation)) {
            return;
        }
        player.corporation = corp.name;
        player.corpRules = corp.extra == null ? "" : corp.extra;
        PlayedCard holder = new PlayedCard();
        holder.name = corp.name;
        holder.color = corp.color;
        holder.extra = player.corpRules;
        holder.project = false;
        holder.tokenType = cards.tokenType(corp);
        player.corpCard = holder;
        for (String tag : corp.tags) {
            player.addTag(tag);
        }
    }

    private PlayerState findOwner(String kind, boolean production, int from, int to) {
        List<PlayerState> matches = new ArrayList<>();
        for (PlayerState p : state.players.values()) {
            if (getField(p, kind, production) == from) {
                matches.add(p);
            }
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        PlayerState byCorpStart = ownerByCorpStart(kind, production, from, to, matches);
        if (byCorpStart != null) {
            return byCorpStart;
        }
        PlayerState current = state.players.get(currentPlayer);
        if (current != null && getField(current, kind, production) == from) {
            return current;
        }
        if (productionPhase() && current != null) {
            return current;
        }
        return matches.isEmpty() ? current : matches.get(0);
    }

    private PlayerState ownerByCorpStart(String kind, boolean production, int from, int to,
            List<PlayerState> matches) {
        String key = switch (kind) {
            case "MegaCredit" -> "mc";
            case "Steel" -> "steel";
            case "Titanium" -> "ti";
            case "Plant" -> "plant";
            case "Energy" -> "energy";
            case "Heat" -> "heat";
            default -> "";
        };
        if (key.isEmpty()) {
            return null;
        }
        for (PlayerState player : matches) {
            if ("Unknown".equals(player.corporation)) {
                continue;
            }
            Card corp = cards.find(player.corporation);
            if (corp == null) {
                continue;
            }
            if (production) {
                int extra = CardDatabase.number(corp.production.get(key));
                if (extra > 0 && from + extra == to) {
                    return player;
                }
            } else {
                int start = CardDatabase.number(corp.resources.get(key));
                if (start > 0 && from == 0 && to == start) {
                    return player;
                }
            }
        }
        return null;
    }

    private boolean productionPhase() {
        String phase = state.phase;
        return "Production".equals(phase) || "FinalPlantConversion".equals(phase);
    }

    private static int getField(PlayerState p, String kind, boolean production) {
        return switch (kind) {
            case "MegaCredit" -> production ? p.megaCreditProd : p.megaCredits;
            case "Steel" -> production ? p.steelProd : p.steel;
            case "Titanium" -> production ? p.titaniumProd : p.titanium;
            case "Plant" -> production ? p.plantProd : p.plants;
            case "Energy" -> production ? p.energyProd : p.energy;
            case "Heat" -> production ? p.heatProd : p.heat;
            default -> 0;
        };
    }

    private static void setField(PlayerState p, String kind, boolean production, int to) {
        switch (kind) {
            case "MegaCredit" -> {
                if (production) p.megaCreditProd = to;
                else p.megaCredits = to;
            }
            case "Steel" -> {
                if (production) p.steelProd = to;
                else p.steel = to;
            }
            case "Titanium" -> {
                if (production) p.titaniumProd = to;
                else p.titanium = to;
            }
            case "Plant" -> {
                if (production) p.plantProd = to;
                else p.plants = to;
            }
            case "Energy" -> {
                if (production) p.energyProd = to;
                else p.energy = to;
            }
            case "Heat" -> {
                if (production) p.heatProd = to;
                else p.heat = to;
            }
            default -> {
            }
        }
    }

    private void playCard(int playerId, String rawName) {
        String key = playerId + "|" + CardDatabase.normalize(rawName);
        if (key.equals(lastCardKey)) {
            return;
        }
        lastCardKey = key;
        lastBlueAction = "";
        Card card = cards.find(rawName);
        String name = cards.displayName(rawName);
        PlayerState player = state.player(playerId);
        PlayedCard played = new PlayedCard();
        played.name = name;
        played.generation = state.generation;
        if (card != null) {
            played.color = card.color;
            played.colorLabel = card.colorLabel();
            played.tags = List.copyOf(card.tags);
            played.extra = card.extra;
            played.blue = card.isBlue();
            played.number = CardDatabase.parseCardNumber(card.number);
            played.tokenType = cards.tokenType(card);
            played.printedVp = card.vp == null ? 0 : card.vp;
            played.hasRequirement = card.hasRequirement();
            played.cost = card.cost;
            if (card.resources != null) played.resources = new java.util.LinkedHashMap<>(card.resources);
            if (card.production != null) played.production = new java.util.LinkedHashMap<>(card.production);
            if (card.req != null) played.req = new java.util.LinkedHashMap<>(card.req);
            if (card.place != null) played.place = List.copyOf(card.place);
            played.project = !"prel".equalsIgnoreCase(card.type)
                    && !"corp".equalsIgnoreCase(card.type)
                    && !card.isEvent();
        } else {
            played.color = "green";
            played.colorLabel = "";
            played.blue = false;
            played.project = true;
        }
        player.addCard(played);
        state.addLog(playerId, "card", name, played.color);
        int vp = ScoreCalculator.cardVp(played, player, citiesInPlay());
        if (vp != 0) {
            String sign = vp > 0 ? "+" : "";
            ScoreEvent event = state.addScoreEvent(playerId, "card", sign + vp + " " + name, vp);
            if (event != null) {
                event.source = name;
            }
        }
        setActive(playerId, name, null, false);
        setCurrentPlayer(playerId);
    }

    /** Seconds since the client launched, from a line's "[1,095.771]" stamp; -1 when it has none. */
    static double elapsedOf(String line) {
        Matcher ts = STAMP.matcher(line);
        if (!ts.find()) {
            return -1;
        }
        try {
            return Double.parseDouble(ts.group(1).replace(",", ""));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** The client's launch time implied by a chat line's wall-clock time, or -1 if the line has none. */
    static long launchFrom(String line) {
        if (!line.contains(" Time ")) {
            return -1;
        }
        Matcher m = CHAT_CLOCK.matcher(line);
        if (!m.find()) {
            return -1;
        }
        try {
            double elapsed = Double.parseDouble(m.group(1).replace(",", ""));
            long clock = java.time.LocalDateTime.parse(m.group(2), CHAT_TIME)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            return clock - Math.round(elapsed * 1000);
        } catch (Exception ex) {
            return -1;
        }
    }

    void noteClock(String line) {
        long launch = launchFrom(line);
        if (launch > 0) {
            launchEpochMs = launch;
            applyStartTime();
        }
    }

    private void applyStartTime() {
        if (launchEpochMs > 0 && state.createdElapsed >= 0 && state.startedAtMs < 0) {
            state.startedAtMs = launchEpochMs + Math.round(state.createdElapsed * 1000);
        }
    }

    /**
     * The game writes most moves twice, on lines with the same timestamp ("Playing action (5) ..." then
     * "Playing (5) ..."); log a move once per timestamp so a real repeat (two City projects) still counts.
     */
    private void logMove(String line, int playerId, String kind, String name, String color) {
        Matcher ts = STAMP.matcher(line);
        String key = (ts.find() ? ts.group(1) : "") + "|" + playerId + "|" + kind + "|" + name;
        if (key.equals(lastLogKey)) {
            return;
        }
        lastLogKey = key;
        state.addLog(playerId, kind, name, color);
    }

    /**
     * Put a drawn card on the player's latest move this generation: the card, project, conversion, or action
     * whose effect or tile placement drew it. Action phase only, so research-phase buys aren't pinned to the
     * previous generation's last move.
     */
    private void logDrawn(int playerId, String rawName) {
        if (!"Actions".equals(state.phase)) {
            return;
        }
        String name = cards.displayName(rawName);
        for (int i = state.playLog.size() - 1; i >= 0; i--) {
            LogEntry entry = state.playLog.get(i);
            if (entry.generation != state.generation) {
                return;
            }
            if (entry.playerId == playerId) {
                if (entry.drawn.stream().noneMatch(d -> name.equals(d.name))) {
                    entry.drawn.add(drawnCard(rawName));
                }
                return;
            }
        }
    }

    /**
     * A blue card belongs to one player, so match by name across the table. The log's number in
     * "Playing action (129) Blue Card Action : ..." is an action id, not the card number.
     */
    private void markActionUsed(String rawName) {
        String want = CardDatabase.normalize(cards.displayName(rawName));
        for (PlayerState player : state.players.values()) {
            for (PlayedCard card : player.blueCards) {
                if (CardDatabase.normalize(card.name).equals(want)) {
                    card.actionUsedGen = state.generation;
                    return;
                }
            }
        }
    }

    private void addCardTokens(int cardNumber, String rawType, int amount) {
        PlayedCard card = state.cardByNumber(cardNumber);
        PlayerState owner = state.ownerOfCard(cardNumber);
        if (card == null || owner == null) {
            return;
        }
        String type = switch (rawType.toLowerCase()) {
            case "microbe", "microbes" -> "microbe";
            case "animal", "animals" -> "animal";
            case "science" -> "science";
            case "fighter", "fighters" -> "fighter";
            case "floater", "floaters" -> "floater";
            case "asteroid", "asteroids" -> "asteroid";
            case "camp", "camps" -> "camp";
            default -> rawType.toLowerCase();
        };
        // The log names the resource; it beats the guess from card text (Stratospheric Birds mentions floaters).
        if (!type.isBlank()) {
            card.tokenType = type;
        }
        int cities = citiesInPlay();
        int before = ScoreCalculator.cardVp(card, owner, cities);
        card.tokens = Math.max(0, card.tokens + amount);
        int after = ScoreCalculator.cardVp(card, owner, cities);
        int delta = after - before;
        if (amount != 0) {
            String sign = amount > 0 ? "+" : "";
            String label = sign + amount + " " + card.name;
            if (amount < 0 && !lastBlueAction.isBlank()) {
                label += " (" + lastBlueAction + ")";
            }
            ScoreEvent event = state.addScoreEvent(owner.id, "token", label, delta);
            if (event != null) {
                event.source = card.name;
                event.tokens = amount;
                event.tokenType = card.tokenType == null ? type : card.tokenType;
                event.cause = causeFor(owner);
            }
        }
    }

    private int citiesInPlay() {
        int n = 0;
        for (PlayerState p : state.players.values()) {
            n += p.cities;
        }
        if (n == 0) {
            for (PlayerState p : state.players.values()) {
                n += p.citiesOnMars;
            }
        }
        return n;
    }

    private void setCurrentPlayer(int playerId) {
        if (playerId == currentPlayer) {
            return;
        }
        currentPlayer = playerId;
        if (state.activePlay != null && state.activePlay.playerId != playerId) {
            if (state.activePlay.yours && !state.activePlay.drawn.isEmpty()) {
                state.activePlay.placing = null;
                return;
            }
            state.activePlay = null;
        }
    }

    private void startConversion(String kind) {
        PlayerState player = state.player(currentPlayer);
        if ("Heat".equals(kind)) {
            setActive(currentPlayer, "Convert heat", null, false);
            state.activePlay.colorLabel = "Conversion";
            state.activePlay.effect = "Spend 8 heat to raise temperature 1 step.";
            state.activePlay.remember = new ArrayList<>(List.of("Raises temperature and TR."));
            return;
        }
        boolean ecoline = "Ecoline".equals(player.corporation);
        int plants = ecoline ? 7 : 8;
        setActive(currentPlayer, "Convert plants", "Greenery", false);
        state.activePlay.colorLabel = "Conversion";
        state.activePlay.effect = "Spend " + plants + " plants to place a greenery.";
        state.activePlay.remember = new ArrayList<>(cards.remember(null, "Greenery", state.board));
        if (ecoline) {
            state.activePlay.remember.add(0, "Ecoline: 7 plants instead of 8.");
        }
    }

    private void beginDrawCollect() {
        PlayerState player = state.player(currentPlayer);
        if (!player.human) {
            collectDraws = false;
            pendingHandCard = "";
            return;
        }
        collectDraws = true;
        pendingHandCard = "";
        if (state.activePlay == null || !state.activePlay.yours) {
            setActive(currentPlayer, "Draw cards", null, false);
            if (state.activePlay != null) {
                state.activePlay.colorLabel = "Draw";
                state.activePlay.effect = "Cards added to your hand.";
            }
        }
    }

    private void addDrawn(String rawName) {
        if (state.activePlay == null || !state.activePlay.yours) {
            return;
        }
        String name = cards.displayName(rawName);
        for (ActivePlay.DrawnCard existing : state.activePlay.drawn) {
            if (name.equals(existing.name)) {
                return;
            }
        }
        state.activePlay.drawn.add(drawnCard(rawName));
    }

    /** A drawn card with what the page needs to describe it (banner "Drew" list, game log popovers). */
    private ActivePlay.DrawnCard drawnCard(String rawName) {
        ActivePlay.DrawnCard drawn = new ActivePlay.DrawnCard();
        drawn.name = cards.displayName(rawName);
        Card card = cards.find(rawName);
        if (card != null) {
            drawn.color = card.color;
            drawn.cost = card.cost;
            drawn.vp = card.vp;
            drawn.tags = List.copyOf(card.tags);
            drawn.extra = card.extra == null ? "" : card.extra;
            if (card.production != null) {
                drawn.production = new java.util.LinkedHashMap<>(card.production);
            }
            if (card.resources != null) {
                drawn.resources = new java.util.LinkedHashMap<>(card.resources);
            }
            if (card.req != null) {
                drawn.req = new java.util.LinkedHashMap<>(card.req);
            }
            if (card.place != null) {
                drawn.place = List.copyOf(card.place);
            }
        }
        return drawn;
    }

    private void setActive(int playerId, String name, String placing, boolean preview) {
        PlayerState player = state.player(playerId);
        String lookup = name.startsWith("Using ") ? name.substring(6) : name.replace("Standard Project: ", "");
        Card card = cards.find(lookup);
        ActivePlay play = new ActivePlay();
        play.playerId = playerId;
        play.playerLabel = player.label();
        play.cardName = name.startsWith("Standard Project:") ? name : cards.displayName(lookup);
        play.yours = player.human;
        play.preview = preview;
        play.colorLabel = name.startsWith("Using ")
                ? "Action"
                : (card != null ? card.colorLabel() : (preview ? "Confirming" : ""));
        play.playerColor = player.color;
        play.fromCard(card);
        play.effect = card != null ? cards.cardText(card) : (preview ? "Waiting for confirm." : "");
        play.placing = placing;
        play.remember.addAll(cards.remember(card, placing, state.board));
        if ("Standard Project: Build Colony".equals(name)) {
            play.remember.add("17 M€: place a colony and take that tile's placement bonus now.");
            play.remember.add("After that, you get the tile's colony bonus whenever anyone trades there.");
        } else if ("Standard Project: Air Scrapping".equals(name)) {
            play.remember.add("15 M€: raise Venus 1 step (+1 TR). Venus doesn't end the game.");
        }
        state.activePlay = play;
        state.live = true;
    }

    private void pinPlacement(String kind) {
        if (state.activePlay == null || state.activePlay.playerId != currentPlayer) {
            setActive(currentPlayer, "Placing " + kind, kind, false);
            return;
        }
        state.activePlay.placing = kind;
        String lookup = state.activePlay.cardName == null ? "" : state.activePlay.cardName
                .replace("Using ", "")
                .replace("Standard Project: ", "")
                .replace("Placing ", "");
        Card card = cards.find(lookup);
        state.activePlay.remember = cards.remember(card, kind, state.board);
        if (card != null && (state.activePlay.effect == null || state.activePlay.effect.isBlank())) {
            state.activePlay.effect = cards.cardText(card);
        }
    }

    private void onPlace(String type, int hex) {
        PlayerState player = state.player(currentPlayer);
        String kind = type.toLowerCase().replace(" ", "").replace("_", "");
        String cardName = state.activePlay == null ? "" : state.activePlay.cardName;
        if (kind.contains("city") || "capital".equals(kind)) {
            player.cities++;
            if (!CardDatabase.offMarsCity(cardName) && BoardLayout.onMars(hex)) {
                player.citiesOnMars++;
            }
        } else if (kind.contains("greenery")) {
            player.greeneries++;
        } else if (kind.contains("ocean")) {
            player.oceans++;
        } else {
            player.specialTiles++;
        }
        if (hex > 0) {
            placeOnBoard(hex, kind, cardName, player.id);
        }
        if ((kind.contains("city") || "capital".equals(kind)) && state.generation <= 1
                && !CardDatabase.offMarsCity(cardName)) {
            assignTharsisIfPending();
        }
        if (player.human) {
            if (state.activePlay == null || !state.activePlay.yours) {
                String label = kind.contains("ocean") ? "Ocean"
                        : kind.contains("city") || "capital".equals(kind) ? "City"
                        : kind.contains("greenery") ? "Greenery"
                        : "tile";
                setActive(currentPlayer, "Placing " + label, kind, false);
                if (state.activePlay != null) {
                    state.activePlay.colorLabel = "Placement";
                }
            }
            collectDraws = true;
            pendingHandCard = "";
        }
        clearPlacing();
    }

    private void clearPlacing() {
        if (state.activePlay == null) {
            return;
        }
        state.activePlay.placing = null;
        String lookup = state.activePlay.cardName == null ? "" : state.activePlay.cardName
                .replace("Using ", "")
                .replace("Standard Project: ", "");
        Card card = cards.find(lookup);
        state.activePlay.remember = new ArrayList<>(cards.remember(card, null, state.board));
    }

    private void placeOnBoard(int hex, String kind, String cardName, int playerId) {
        PlacedTile tile = new PlacedTile();
        tile.hex = hex;
        String card = cardName == null ? "" : cardName.toLowerCase();
        if (card.contains("capital") || "capital".equals(kind)) {
            tile.type = "capital";
            tile.ownerId = playerId;
        } else if (card.contains("commercial district") || "commercialdistrict".equals(kind)) {
            tile.type = "commercialdistrict";
            tile.ownerId = playerId;
        } else if (kind.contains("city")) {
            tile.type = "city";
            tile.ownerId = playerId;
        } else if (kind.contains("greenery")) {
            tile.type = "greenery";
            tile.ownerId = playerId;
        } else if (kind.contains("ocean")) {
            tile.type = "ocean";
            tile.ownerId = 0;
        } else {
            tile.type = kind;
            tile.ownerId = playerId;
        }
        state.tiles.put(hex, tile);
    }

    private static String standardProjectName(String raw) {
        return switch (raw) {
            case "PlaceColony" -> "Build Colony";
            case "AirScrapping" -> "Air Scrapping";
            case "PowerPlant" -> "Power Plant";
            case "SellPatents" -> "Sell Patents";
            default -> raw;
        };
    }

    /** Colony tiles by the game's id: alphabetical from 3001 (Callisto 3001 and Titan 3010 seen in logs). */
    private static final String[] COLONY_TILES = {
            "Callisto", "Ceres", "Enceladus", "Europa", "Ganymede", "Io", "Luna", "Miranda", "Pluto", "Titan", "Triton",
    };

    static String colonyName(int id) {
        int i = id - 3001;
        return i >= 0 && i < COLONY_TILES.length ? COLONY_TILES[i] : "a colony tile";
    }

    /** Resource flags in the game's event dumps; only the ones seen in logs. */
    static String resourceName(int type, boolean production) {
        String name = switch (type) {
            case 1 -> "M€";
            case 2 -> "steel";
            case 4 -> "titanium";
            case 8 -> "plants";
            case 16 -> "energy";
            case 32 -> "heat";
            case 64 -> "microbes";
            case 16384 -> "floaters";
            case 262144 -> "camps";
            default -> null;
        };
        return name == null ? null : production ? singular(name) + " production" : name;
    }

    private static String singular(String name) {
        return name.endsWith("s") ? name.substring(0, name.length() - 1) : name;
    }

    /** "+3 energy" for a resource flag and amount, or "" when the flag isn't one we know. */
    private static String gainText(int type, boolean production, int quantity) {
        String what = resourceName(type, production);
        if (what == null || quantity == 0) {
            return "";
        }
        if (Math.abs(quantity) == 1) {
            what = singular(what);
        }
        return (quantity > 0 ? "+" : "") + quantity + " " + what;
    }

    /** A colony build (ActionType 43) or trade (45): record it, log it and show it in the banner. */
    private void finishEvent(ActionEvent e) {
        if (e.actionType != 43 && e.actionType != 45) {
            return;
        }
        if (!seenEvents.add(e.time + "|" + e.actionId)) {
            return;
        }
        PlayerState player = state.player(e.playerId);
        String tile = colonyName(e.chosen);
        ActionEvent.Sub paid = e.payout();
        String gain = paid == null ? "" : gainText(paid.resourceType, paid.production, paid.quantity);
        boolean build = e.actionType == 43;
        ColonyTile colony = state.colonyTile(tile);
        if (build) {
            player.colonies.add(tile);
            colony.built(player.id);
        } else {
            player.fleetsUsed++;
            colony.traded(e.quantity, paid == null ? 0 : paid.quantity);
        }
        LogEntry entry = state.addLog(player.id, build ? "colony" : "trade", tile, "");
        if (entry != null) {
            entry.detail = gain;
        }
        List<String> bonusTips = new ArrayList<>();
        for (ActionEvent.Sub bonus : e.colonyBonuses()) {
            String got = gainText(bonus.resourceType, bonus.production, bonus.quantity);
            PlayerState owner = state.player(bonus.playerId);
            LogEntry line = state.addLog(owner.id, "bonus", tile, "");
            if (line != null) {
                line.detail = got;
            }
            bonusTips.add((owner.human ? "You get " : owner.label() + " gets ")
                    + (got.isEmpty() ? "the" : got + ",") + " colony bonus.");
        }
        List<String> tips = new ArrayList<>();
        if (build) {
            tips.add("Placement bonus now" + (gain.isEmpty() ? "." : ": " + gain + "."));
            tips.add("This colony earns its owner the colony bonus whenever anyone trades with " + tile + ".");
            tips.add("Up to 3 colonies per tile, normally only one per player.");
        } else {
            tips.add("Takes what the " + tile + " track shows now" + (gain.isEmpty() ? "." : ": " + gain + "."));
            tips.addAll(bonusTips);
            tips.add("The track drops back to just past the colonies, then climbs 1 step each Solar phase.");
            tips.add("That trade fleet is out until the end of the generation.");
        }
        ActivePlay play = new ActivePlay();
        play.playerId = player.id;
        play.playerLabel = player.label();
        play.cardName = (build ? "Colony on " : "Trade with ") + tile;
        play.colorLabel = "Colonies";
        play.playerColor = player.color;
        play.yours = player.human;
        play.effect = gain;
        play.remember.addAll(tips);
        state.activePlay = play;
        state.live = true;
    }

    /**
     * One GameActionEvent dump: its top-level fields and every sub-action, by indent depth. A trade's
     * sub-actions are the payout (ActionType 11) and one colony bonus per owner (ActionType 48, with the
     * resource one level deeper); its own Quantity is the track step it traded at.
     */
    static final class ActionEvent {
        static final class Sub {
            final int depth;
            final int playerId;
            int actionType = -1;
            int resourceType = -1;
            int quantity;
            boolean production;

            Sub(int depth, int playerId) {
                this.depth = depth;
                this.playerId = playerId;
            }
        }

        final int playerId;
        int actionType = -1;
        String actionId = "";
        String time = "";
        int chosen = -1;
        int quantity = -1;
        final List<Sub> subs = new ArrayList<>();

        ActionEvent(int playerId) {
            this.playerId = playerId;
        }

        void feed(String line) {
            if (line.startsWith("\tChosenCards(1):")) {
                chosen = intOr(line.substring(line.indexOf(':') + 1), -1);
                return;
            }
            int colon = line.indexOf(" : ");
            if (colon < 0) {
                return;
            }
            int depth = 0;
            while (depth < line.length() && line.charAt(depth) == '\t') {
                depth++;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 3).trim();
            if (depth == 1) {
                switch (key) {
                    case "ActionType" -> actionType = intOr(value, -1);
                    case "GameActionID" -> actionId = value;
                    case "Time" -> time = value;
                    case "Quantity" -> quantity = intOr(value, -1);
                    default -> { }
                }
                return;
            }
            if ("PlayerLocalID".equals(key)) {
                subs.add(new Sub(depth, intOr(value, 0)));
                return;
            }
            Sub sub = lastAt(depth);
            if (sub == null) {
                return;
            }
            switch (key) {
                case "ActionType" -> sub.actionType = intOr(value, -1);
                case "ResourceType" -> sub.resourceType = intOr(value, -1);
                case "IsProdResource" -> sub.production = "True".equals(value);
                case "Quantity" -> sub.quantity = intOr(value, 0);
                default -> { }
            }
        }

        private Sub lastAt(int depth) {
            for (int i = subs.size() - 1; i >= 0; i--) {
                if (subs.get(i).depth == depth) {
                    return subs.get(i);
                }
            }
            return null;
        }

        /** The first direct sub-action that moves a resource: what the build or trade paid out. */
        Sub payout() {
            int top = topDepth();
            for (Sub sub : subs) {
                if (sub.depth == top && sub.resourceType >= 0 && sub.actionType != 48) {
                    return sub;
                }
            }
            return null;
        }

        /** Colony bonuses: each direct ActionType 48 sub-action and the first resource nested under it. */
        List<Sub> colonyBonuses() {
            int top = topDepth();
            List<Sub> out = new ArrayList<>();
            for (int i = 0; i < subs.size(); i++) {
                Sub sub = subs.get(i);
                if (sub.depth != top || sub.actionType != 48) {
                    continue;
                }
                for (int j = i + 1; j < subs.size() && subs.get(j).depth > top; j++) {
                    Sub inner = subs.get(j);
                    if (inner.resourceType >= 0) {
                        Sub bonus = new Sub(top, sub.playerId);
                        bonus.resourceType = inner.resourceType;
                        bonus.production = inner.production;
                        bonus.quantity = inner.quantity;
                        out.add(bonus);
                        break;
                    }
                }
            }
            return out;
        }

        private int topDepth() {
            int top = Integer.MAX_VALUE;
            for (Sub sub : subs) {
                top = Math.min(top, sub.depth);
            }
            return top;
        }

        private static int intOr(String s, int fallback) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
    }

    private static String humanPhase(String raw) {
        return switch (raw) {
            case "ActionPhase" -> "Actions";
            case "ProductionPhase" -> "Production";
            case "ResearchPhase" -> "Research";
            case "PlayerOrderPhase" -> "Player order";
            case "PlayerSetup", "InitializationPhase" -> "Setup";
            case "SaveGameState" -> "Saving";
            case "SolarPhase" -> "Solar";
            case "PlayerSetupCorporationPassiveEffectsState", "SpliceNotUsedState" -> "Setup";
            default -> raw.replace("Phase", "").replace("State", "");
        };
    }
}
