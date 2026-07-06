package com.notbang.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notbang.game.Game;
import com.notbang.game.Player;
import io.javalin.websocket.WsContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A game room: a lobby of connected players and, once started, the running game.
 * All message handling is synchronized on the room instance.
 */
public class Room {

    private static final ObjectMapper JSON = new ObjectMapper();

    public final String code;
    private final List<Player> seats = new ArrayList<>(); // join order = seat order
    private final Map<String, String> names = new LinkedHashMap<>(); // pid -> name
    private final Map<String, WsContext> connections = new ConcurrentHashMap<>(); // pid -> live socket
    private String hostPid;
    private Game game;

    public Room(String code) {
        this.code = code;
    }

    public synchronized boolean isEmpty() {
        return connections.isEmpty();
    }

    public synchronized boolean started() {
        return game != null;
    }

    public synchronized void join(String pid, String name, WsContext ctx) {
        if (game == null) {
            if (!names.containsKey(pid)) {
                if (names.size() >= 7) {
                    send(ctx, Map.of("type", "error", "message", "This room is full (7 players max)."));
                    return;
                }
                names.put(pid, name);
                if (hostPid == null) hostPid = pid;
            }
            connections.put(pid, ctx);
            broadcastLobby();
        } else {
            // Reconnect to a running game
            if (!names.containsKey(pid)) {
                send(ctx, Map.of("type", "error", "message", "This game has already started."));
                return;
            }
            connections.put(pid, ctx);
            broadcastState();
        }
    }

    public synchronized void disconnect(String pid, WsContext ctx) {
        connections.remove(pid, ctx);
        if (game == null) {
            names.remove(pid);
            if (pid.equals(hostPid)) {
                hostPid = names.keySet().stream().findFirst().orElse(null);
            }
            broadcastLobby();
        }
        // During a game we keep the seat so the player can reconnect.
    }

    public synchronized void handle(String pid, JsonNode msg) {
        String action = msg.path("action").asText("");
        try {
            switch (action) {
                case "start" -> start(pid);
                case "play" -> requireGame().playCard(pid,
                        msg.path("cardId").asInt(),
                        msg.hasNonNull("targetId") ? msg.get("targetId").asText() : null,
                        msg.hasNonNull("targetCardId") ? msg.get("targetCardId").asInt() : null);
                case "respond" -> requireGame().respond(pid, intList(msg.get("cardIds")));
                case "pick" -> requireGame().pick(pid, msg.path("cardId").asInt());
                case "ability" -> requireGame().useAbility(pid, intList(msg.get("cardIds")));
                case "endTurn" -> requireGame().endTurn(pid);
                default -> throw new IllegalArgumentException("Unknown action: " + action);
            }
            broadcastState();
        } catch (IllegalArgumentException | IllegalStateException e) {
            WsContext ctx = connections.get(pid);
            if (ctx != null) {
                send(ctx, Map.of("type", "error", "message", e.getMessage() == null ? "Invalid move." : e.getMessage()));
            }
        }
    }

    private Game requireGame() {
        if (game == null) throw new IllegalStateException("The game has not started yet.");
        return game;
    }

    private void start(String pid) {
        if (game != null) throw new IllegalStateException("The game has already started.");
        if (!pid.equals(hostPid)) throw new IllegalStateException("Only the host can start the game.");
        if (names.size() < 3) throw new IllegalStateException("You need at least 3 players (4+ recommended).");
        seats.clear();
        for (Map.Entry<String, String> e : names.entrySet()) {
            seats.add(new Player(e.getKey(), e.getValue()));
        }
        game = new Game(seats, new Random());
        broadcastState();
    }

    private List<Integer> intList(JsonNode node) {
        List<Integer> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> out.add(n.asInt()));
        }
        return out;
    }

    // ---------------------------------------------------------------- output

    public synchronized void broadcastLobby() {
        List<Map<String, Object>> ps = new ArrayList<>();
        for (Map.Entry<String, String> e : names.entrySet()) {
            ps.add(Map.of(
                    "id", e.getKey(),
                    "name", e.getValue(),
                    "host", e.getKey().equals(hostPid),
                    "connected", connections.containsKey(e.getKey())));
        }
        for (Map.Entry<String, WsContext> e : connections.entrySet()) {
            send(e.getValue(), Map.of(
                    "type", "lobby",
                    "room", code,
                    "you", e.getKey(),
                    "host", e.getKey().equals(hostPid),
                    "players", ps));
        }
    }

    public synchronized void broadcastState() {
        if (game == null) {
            broadcastLobby();
            return;
        }
        for (Map.Entry<String, WsContext> e : connections.entrySet()) {
            Map<String, Object> view = game.viewFor(e.getKey());
            view.put("type", "state");
            view.put("room", code);
            send(e.getValue(), view);
        }
    }

    private void send(WsContext ctx, Object payload) {
        try {
            if (ctx.session.isOpen()) {
                ctx.send(JSON.writeValueAsString(payload));
            }
        } catch (Exception ignored) {
            // Socket died mid-send; the close handler will clean up.
        }
    }
}
