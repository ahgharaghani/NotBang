package com.notbang.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** A reaction the game is waiting on. Pendings are stacked: the top one must resolve first. */
public class Pending {

    public enum Type {
        /** Target must play Missed! (x{@code needed}) or take the hit. */
        BANG,
        /** Each player in {@code queue} must play a Bang! or lose 1 life. */
        INDIANS,
        /** Duel: {@code awaiting} must play a Bang! or lose 1 life; alternates with {@code source}. */
        DUEL,
        /** Each player in {@code queue} picks one of {@code storeCards}. */
        GENERAL_STORE,
        /** Current player must discard down to hand limit before the turn ends. */
        DISCARD_EXCESS,
        /** Player at 0 or less health may play Beer(s) to survive. */
        DYING
    }

    public final Type type;
    /** Player currently expected to act. */
    public String awaiting;
    /** The player who caused this (attacker / card player). May be null (e.g. Dynamite). */
    public String source;
    /** Remaining responders (for GATLING-style multi-target effects and General Store). */
    public final Deque<String> queue = new ArrayDeque<>();
    /** Missed! cards still needed (Slab the Killer => 2). */
    public int needed = 1;
    /** Revealed General Store cards. */
    public final List<Card> storeCards = new ArrayList<>();
    /** For DYING: how the hit happened, for kill attribution. For BANG: true if from Gatling/Indians-like group effect. */
    public boolean fromGroupEffect = false;

    public Pending(Type type, String awaiting, String source) {
        this.type = type;
        this.awaiting = awaiting;
        this.source = source;
    }
}
