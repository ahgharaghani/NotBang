package com.notbang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notbang.server.Room;
import com.notbang.server.RoomManager;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Main {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final RoomManager ROOMS = new RoomManager();
    /** Which room each live socket belongs to, and as which player. */
    private static final Map<WsContext, Session> SESSIONS = new ConcurrentHashMap<>();

    private record Session(Room room, String pid) {}

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
            config.showJavalinBanner = false;
        });

        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> ctx.enableAutomaticPings());
            ws.onMessage(ctx -> handleMessage(ctx, ctx.message()));
            ws.onClose(ctx -> handleClose(ctx));
            ws.onError(ctx -> handleClose(ctx));
        });

        app.start("0.0.0.0", port);
        System.out.println("NotBang is running on http://localhost:" + port);
    }

    private static void handleMessage(WsContext ctx, String raw) {
        JsonNode msg;
        try {
            msg = JSON.readTree(raw);
        } catch (Exception e) {
            sendError(ctx, "Malformed message.");
            return;
        }
        String action = msg.path("action").asText("");

        // Lobby-level actions establish the session.
        if (action.equals("create") || action.equals("join")) {
            String name = msg.path("name").asText("").trim();
            String pid = msg.path("pid").asText("").trim();
            if (name.isEmpty() || name.length() > 20 || pid.isEmpty() || pid.length() > 64) {
                sendError(ctx, "Please enter a name (max 20 characters).");
                return;
            }
            Room room;
            if (action.equals("create")) {
                room = ROOMS.create();
            } else {
                room = ROOMS.get(msg.path("room").asText(""));
                if (room == null) {
                    sendError(ctx, "Room not found. Check the code and try again.");
                    return;
                }
            }
            Session old = SESSIONS.put(ctx, new Session(room, pid));
            if (old != null && old.room() != room) {
                old.room().disconnect(old.pid(), ctx);
            }
            room.join(pid, name, ctx);
            return;
        }

        Session session = SESSIONS.get(ctx);
        if (session == null) {
            sendError(ctx, "Join a room first.");
            return;
        }
        session.room().handle(session.pid(), msg);
    }

    private static void handleClose(WsContext ctx) {
        Session session = SESSIONS.remove(ctx);
        if (session != null) {
            session.room().disconnect(session.pid(), ctx);
            ROOMS.removeIfEmpty(session.room());
        }
    }

    private static void sendError(WsContext ctx, String message) {
        try {
            ctx.send(JSON.writeValueAsString(Map.of("type", "error", "message", message)));
        } catch (Exception ignored) {
        }
    }
}
