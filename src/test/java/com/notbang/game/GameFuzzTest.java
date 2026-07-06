package com.notbang.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plays hundreds of complete games with random (mostly legal) moves and checks
 * engine invariants after every single action:
 * - all 80 cards are always accounted for (no duplication, no loss),
 * - health stays within [0, maxHealth],
 * - dead players hold no cards,
 * - every game terminates with a winner.
 */
class GameFuzzTest {

    @Test
    void randomGamesTerminateAndConserveCards() {
        int finished = 0;
        for (int seed = 0; seed < 300; seed++) {
            Random rng = new Random(seed);
            int n = 4 + rng.nextInt(4); // 4-7 players
            List<Player> players = new ArrayList<>();
            for (int i = 0; i < n; i++) players.add(new Player("p" + i, "P" + i));
            Game g = new Game(players, rng);

            for (int step = 0; step < 4000 && !g.isOver(); step++) {
                try {
                    act(g, rng);
                } catch (IllegalArgumentException | IllegalStateException expected) {
                    // Illegal random move; the engine must reject it and stay consistent.
                }
                checkInvariants(g, "seed=" + seed + " step=" + step);
            }
            assertTrue(g.isOver(), "game with seed " + seed + " should finish");
            assertNotNull(g.result());
            finished++;
        }
        assertEquals(300, finished);
    }

    private void act(Game g, Random rng) {
        Pending pend = g.pendingTop();
        if (pend != null && pend.awaiting != null) {
            Player p = g.playerById(pend.awaiting);
            switch (pend.type) {
                case GENERAL_STORE -> g.pick(p.id, pend.storeCards.get(rng.nextInt(pend.storeCards.size())).id());
                case DISCARD_EXCESS -> {
                    int excess = p.hand.size() - Math.max(0, p.health);
                    List<Integer> ids = new ArrayList<>();
                    for (int i = 0; i < excess; i++) ids.add(p.hand.get(i).id());
                    g.respond(p.id, ids);
                }
                case BANG -> {
                    List<Card> options = p.missedResponses();
                    if (!options.isEmpty() && rng.nextBoolean()) {
                        List<Integer> ids = new ArrayList<>();
                        for (int i = 0; i < Math.min(pend.needed, options.size()); i++) ids.add(options.get(i).id());
                        g.respond(p.id, ids);
                    } else {
                        g.respond(p.id, List.of());
                    }
                }
                case INDIANS, DUEL -> {
                    List<Card> options = p.bangResponses();
                    if (!options.isEmpty() && rng.nextBoolean()) {
                        g.respond(p.id, List.of(options.get(0).id()));
                    } else {
                        g.respond(p.id, List.of());
                    }
                }
                case DYING -> {
                    List<Card> beers = p.hand.stream().filter(c -> c.type() == CardType.BEER).toList();
                    if (!beers.isEmpty() && rng.nextBoolean()) {
                        g.respond(p.id, List.of(beers.get(0).id()));
                    } else {
                        g.respond(p.id, List.of());
                    }
                }
            }
            return;
        }

        Player cur = g.currentPlayer();
        if (cur.hand.isEmpty() || rng.nextInt(4) == 0) {
            g.endTurn(cur.id);
            return;
        }
        // Try to play a random card at a random target; illegal combos are rejected.
        Card card = cur.hand.get(rng.nextInt(cur.hand.size()));
        List<Player> others = g.players().stream().filter(p -> p.alive && p != cur).toList();
        Player target = others.isEmpty() ? null : others.get(rng.nextInt(others.size()));
        Integer targetCardId = null;
        if (target != null && !target.table.isEmpty() && rng.nextBoolean()) {
            targetCardId = target.table.get(rng.nextInt(target.table.size())).id();
        }
        g.playCard(cur.id, card.id(), target == null ? null : target.id, targetCardId);
    }

    private void checkInvariants(Game g, String ctx) {
        Pending top = g.pendingTop();
        int total = g.deckSize() + g.discardSize();
        for (Player p : g.players()) {
            total += p.hand.size() + p.table.size();
            assertTrue(p.health <= p.maxHealth, ctx + ": health above max for " + p.name);
            boolean dying = top != null && top.type == Pending.Type.DYING && p.id.equals(top.awaiting);
            if (p.alive) {
                assertTrue(p.health >= 0 || dying, ctx + ": negative health on living player " + p.name);
            } else {
                assertEquals(0, p.hand.size(), ctx + ": dead player " + p.name + " holds hand cards");
                assertEquals(0, p.table.size(), ctx + ": dead player " + p.name + " holds table cards");
            }
        }
        Pending pend = g.pendingTop();
        if (pend != null) total += pend.storeCards.size();
        assertEquals(80, total, ctx + ": cards were lost or duplicated");
    }
}
