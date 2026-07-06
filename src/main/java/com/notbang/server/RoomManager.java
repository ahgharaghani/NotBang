package com.notbang.server;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public Room create() {
        while (true) {
            String code = randomCode();
            Room room = new Room(code);
            if (rooms.putIfAbsent(code, room) == null) {
                return room;
            }
        }
    }

    public Room get(String code) {
        return code == null ? null : rooms.get(code.toUpperCase());
    }

    public void removeIfEmpty(Room room) {
        synchronized (room) {
            if (room.isEmpty()) {
                rooms.remove(room.code);
            }
        }
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
