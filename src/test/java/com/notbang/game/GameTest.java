package com.notbang.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GameTest {

    private static int nextTestCardId = 5000;

    private static Card card(CardType type) {
        return new Card(nextTestCardId++, type, Suit.SPADES, 10);
    }

    private static Card redCard(CardType type) {
        return new Card(nextTestCardId++, type, Suit.HEARTS, 10);
    }

    private static List<Player> makePlayers(int n) {
        List<Player> ps = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ps.add(new Player("p" + i, "Player" + i));
        }
        return ps;
    }

    /**
     * Makes the game deterministic for rule tests: everyone becomes Sid Ketchum
     * (no passive combat ability), empty hand and table, full health.
     */
    private static Game normalizedGame(int n) {
        Game g = new Game(makePlayers(n), new Random(42));
        for (Player p : g.players()) {
            p.character = CharacterType.SID_KETCHUM;
            p.maxHealth = 4 + (p.role == Role.SHERIFF ? 1 : 0);
            p.health = p.maxHealth;
            p.hand.clear();
            p.table.clear();
        }
        return g;
    }

    private static Player after(Game g, Player p) {
        List<Player> ps = g.players();
        return ps.get((ps.indexOf(p) + 1) % ps.size());
    }

    // ----------------------------------------------------------- deck & setup

    @Test
    void standardDeckHas80CardsWithCorrectCounts() {
        List<Card> deck = Deck.standardDeck();
        assertEquals(80, deck.size());
        assertEquals(25, deck.stream().filter(c -> c.type() == CardType.BANG).count());
        assertEquals(12, deck.stream().filter(c -> c.type() == CardType.MISSED).count());
        assertEquals(6, deck.stream().filter(c -> c.type() == CardType.BEER).count());
        assertEquals(3, deck.stream().filter(c -> c.type() == CardType.JAIL).count());
        assertEquals(1, deck.stream().filter(c -> c.type() == CardType.DYNAMITE).count());
        assertEquals(2, deck.stream().filter(c -> c.type() == CardType.VOLCANIC).count());
        // All card ids unique
        assertEquals(80, deck.stream().map(Card::id).distinct().count());
    }

    @Test
    void rolesAndSetupForFivePlayers() {
        Game g = new Game(makePlayers(5), new Random(1));
        List<Player> ps = g.players();
        assertEquals(1, ps.stream().filter(p -> p.role == Role.SHERIFF).count());
        assertEquals(1, ps.stream().filter(p -> p.role == Role.RENEGADE).count());
        assertEquals(2, ps.stream().filter(p -> p.role == Role.OUTLAW).count());
        assertEquals(1, ps.stream().filter(p -> p.role == Role.DEPUTY).count());

        Player sheriff = ps.stream().filter(p -> p.role == Role.SHERIFF).findFirst().orElseThrow();
        assertEquals(sheriff.character.baseHealth + 1, sheriff.maxHealth);
        assertSame(sheriff, g.currentPlayer(), "the Sheriff plays first");
        // Sheriff has drawn 2 cards on top of the initial hand equal to health
        // (Black Jack may draw 3; allow either)
        assertTrue(sheriff.hand.size() >= sheriff.maxHealth + 2);
    }

    // ------------------------------------------------------------------ bang

    @Test
    void bangHitsWhenTargetCannotDodge() {
        Game g = normalizedGame(4);
        Player shooter = g.currentPlayer();
        Player target = after(g, shooter);
        Card bang = card(CardType.BANG);
        shooter.hand.add(bang);

        g.playCard(shooter.id, bang.id(), target.id, null);

        assertEquals(target.maxHealth - 1, target.health);
        assertNull(g.pendingTop(), "auto-resolved because the target had no Missed!");
    }

    @Test
    void bangCanBeDodgedWithMissed() {
        Game g = normalizedGame(4);
        Player shooter = g.currentPlayer();
        Player target = after(g, shooter);
        Card bang = card(CardType.BANG);
        Card missed = card(CardType.MISSED);
        shooter.hand.add(bang);
        target.hand.add(missed);

        g.playCard(shooter.id, bang.id(), target.id, null);
        assertNotNull(g.pendingTop());
        assertEquals(target.id, g.pendingTop().awaiting);

        g.respond(target.id, List.of(missed.id()));
        assertEquals(target.maxHealth, target.health);
        assertTrue(target.hand.isEmpty());
        assertNull(g.pendingTop());
    }

    @Test
    void onlyOneBangPerTurnWithDefaultWeapon() {
        Game g = normalizedGame(4);
        Player shooter = g.currentPlayer();
        Player target = after(g, shooter);
        Card b1 = card(CardType.BANG);
        Card b2 = card(CardType.BANG);
        shooter.hand.add(b1);
        shooter.hand.add(b2);

        g.playCard(shooter.id, b1.id(), target.id, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> g.playCard(shooter.id, b2.id(), target.id, null));
        assertTrue(ex.getMessage().contains("one Bang!"));
    }

    @Test
    void volcanicAllowsMultipleBangs() {
        Game g = normalizedGame(4);
        Player shooter = g.currentPlayer();
        Player target = after(g, shooter);
        shooter.table.add(card(CardType.VOLCANIC));
        Card b1 = card(CardType.BANG);
        Card b2 = card(CardType.BANG);
        shooter.hand.add(b1);
        shooter.hand.add(b2);

        g.playCard(shooter.id, b1.id(), target.id, null);
        g.playCard(shooter.id, b2.id(), target.id, null);
        assertEquals(target.maxHealth - 2, target.health);
    }

    @Test
    void mustangIncreasesDistanceOutOfDefaultRange() {
        Game g = normalizedGame(4);
        Player shooter = g.currentPlayer();
        Player target = after(g, shooter);
        target.table.add(card(CardType.MUSTANG));
        assertEquals(2, g.distance(shooter, target));

        Card bang = card(CardType.BANG);
        shooter.hand.add(bang);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> g.playCard(shooter.id, bang.id(), target.id, null));
        assertTrue(ex.getMessage().contains("out of range"));
    }

    @Test
    void slabTheKillerNeedsTwoMissed() {
        Game g = normalizedGame(4);
        Player shooter = g.currentPlayer();
        Player target = after(g, shooter);
        shooter.character = CharacterType.SLAB_THE_KILLER;
        Card bang = card(CardType.BANG);
        Card m1 = card(CardType.MISSED);
        Card m2 = card(CardType.MISSED);
        shooter.hand.add(bang);
        target.hand.add(m1);
        target.hand.add(m2);

        g.playCard(shooter.id, bang.id(), target.id, null);
        assertEquals(2, g.pendingTop().needed);
        assertThrows(IllegalArgumentException.class,
                () -> g.respond(target.id, List.of(m1.id())), "one Missed! is not enough");
        g.respond(target.id, List.of(m1.id(), m2.id()));
        assertEquals(target.maxHealth, target.health);
    }

    // ------------------------------------------------------------- reactions

    @Test
    void duelIsLostImmediatelyWithoutBang() {
        Game g = normalizedGame(4);
        Player challenger = g.currentPlayer();
        Player target = after(g, challenger);
        Card duel = card(CardType.DUEL);
        challenger.hand.add(duel);

        g.playCard(challenger.id, duel.id(), target.id, null);
        assertEquals(target.maxHealth - 1, target.health);
        assertNull(g.pendingTop());
    }

    @Test
    void duelAlternatesUntilSomeoneRunsOut() {
        Game g = normalizedGame(4);
        Player challenger = g.currentPlayer();
        Player target = after(g, challenger);
        Card duel = card(CardType.DUEL);
        Card tBang = card(CardType.BANG);
        challenger.hand.add(duel);
        target.hand.add(tBang);

        g.playCard(challenger.id, duel.id(), target.id, null);
        assertEquals(target.id, g.pendingTop().awaiting);
        g.respond(target.id, List.of(tBang.id()));
        // Challenger has no Bang! left -> loses 1 life automatically
        assertEquals(challenger.maxHealth - 1, challenger.health);
        assertEquals(target.maxHealth, target.health);
        assertNull(g.pendingTop());
    }

    @Test
    void indiansDamageEveryoneWithoutBang() {
        Game g = normalizedGame(4);
        Player p = g.currentPlayer();
        Card indians = card(CardType.INDIANS);
        p.hand.add(indians);

        g.playCard(p.id, indians.id(), null, null);
        for (Player q : g.players()) {
            if (q == p) assertEquals(q.maxHealth, q.health);
            else assertEquals(q.maxHealth - 1, q.health);
        }
    }

    @Test
    void gatlingShootsEveryoneElse() {
        Game g = normalizedGame(4);
        Player p = g.currentPlayer();
        Card gatling = card(CardType.GATLING);
        p.hand.add(gatling);

        g.playCard(p.id, gatling.id(), null, null);
        for (Player q : g.players()) {
            if (q != p) assertEquals(q.maxHealth - 1, q.health);
        }
    }

    // -------------------------------------------------------------- lifecycle

    @Test
    void beerHealsOneLife() {
        Game g = normalizedGame(4);
        Player p = g.currentPlayer();
        p.health = p.maxHealth - 2;
        Card beer = redCard(CardType.BEER);
        p.hand.add(beer);

        g.playCard(p.id, beer.id(), null, null);
        assertEquals(p.maxHealth - 1, p.health);
    }

    @Test
    void dyingPlayerCanSaveHimselfWithBeer() {
        Game g = normalizedGame(4);
        Player shooter = g.currentPlayer();
        Player target = after(g, shooter);
        target.health = 1;
        Card bang = card(CardType.BANG);
        Card beer = redCard(CardType.BEER);
        shooter.hand.add(bang);
        target.hand.add(beer);

        g.playCard(shooter.id, bang.id(), target.id, null);
        assertEquals(Pending.Type.DYING, g.pendingTop().type);
        g.respond(target.id, List.of(beer.id()));
        assertTrue(target.alive);
        assertEquals(1, target.health);
        assertNull(g.pendingTop());
    }

    @Test
    void killingTheSheriffEndsTheGame() {
        Game g = normalizedGame(4);
        Player sheriff = g.currentPlayer();
        assertEquals(Role.SHERIFF, sheriff.role);
        g.endTurn(sheriff.id);

        Player next = g.currentPlayer();
        assertNotSame(sheriff, next);
        sheriff.health = 1;
        Card bang = card(CardType.BANG);
        next.hand.add(bang);
        // sheriff sits directly before `next`, so distance is 1
        g.playCard(next.id, bang.id(), sheriff.id, null);

        assertTrue(g.isOver());
        assertFalse(sheriff.alive);
        assertTrue(g.result().contains("Outlaws") || g.result().contains("Renegade"));
    }

    @Test
    void killingAnOutlawGrantsThreeCards() {
        Game g = normalizedGame(5);
        Player sheriff = g.currentPlayer();
        Player outlaw = g.players().stream()
                .filter(p -> p.role == Role.OUTLAW && g.distance(sheriff, p) <= 1)
                .findFirst().orElse(null);
        if (outlaw == null) {
            // No adjacent outlaw with this seed: give the sheriff a long-range weapon.
            sheriff.table.add(card(CardType.WINCHESTER));
            outlaw = g.players().stream().filter(p -> p.role == Role.OUTLAW).findFirst().orElseThrow();
        }
        outlaw.health = 1;
        Card bang = card(CardType.BANG);
        sheriff.hand.add(bang);

        g.playCard(sheriff.id, bang.id(), outlaw.id, null);
        assertFalse(outlaw.alive);
        assertEquals(3, sheriff.hand.size(), "bounty: 3 cards for killing an Outlaw");
        assertFalse(g.isOver());
    }

    @Test
    void endTurnForcesDiscardDownToHandLimit() {
        Game g = normalizedGame(4);
        Player p = g.currentPlayer();
        p.health = 2;
        Card c1 = card(CardType.BANG);
        Card c2 = card(CardType.MISSED);
        Card c3 = card(CardType.BEER);
        Card c4 = card(CardType.SCHOFIELD);
        p.hand.addAll(List.of(c1, c2, c3, c4));

        g.endTurn(p.id);
        assertEquals(Pending.Type.DISCARD_EXCESS, g.pendingTop().type);
        assertThrows(IllegalArgumentException.class, () -> g.respond(p.id, List.of(c1.id())));
        g.respond(p.id, List.of(c1.id(), c2.id()));

        assertEquals(2, p.hand.size());
        assertNotSame(p, g.currentPlayer(), "turn passed to the next player");
    }

    @Test
    void generalStoreLetsEveryonePickInOrder() {
        Game g = normalizedGame(4);
        Player p = g.currentPlayer();
        Card store = card(CardType.GENERAL_STORE);
        p.hand.add(store);
        int before = p.hand.size() - 1;

        g.playCard(p.id, store.id(), null, null);
        Pending pend = g.pendingTop();
        assertEquals(Pending.Type.GENERAL_STORE, pend.type);
        assertEquals(4, pend.storeCards.size());
        assertEquals(p.id, pend.awaiting);

        // Everyone picks; the last card is assigned automatically.
        for (int i = 0; i < 3; i++) {
            Pending cur = g.pendingTop();
            g.pick(cur.awaiting, cur.storeCards.get(0).id());
        }
        assertNull(g.pendingTop());
        assertEquals(before + 1, p.hand.size());
        for (Player q : g.players()) {
            if (q != p) assertEquals(1, q.hand.size());
        }
    }

    @Test
    void cannotJailTheSheriff() {
        Game g = normalizedGame(4);
        Player sheriff = g.currentPlayer();
        g.endTurn(sheriff.id);
        Player p = g.currentPlayer();
        Card jail = card(CardType.JAIL);
        p.hand.add(jail);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> g.playCard(p.id, jail.id(), sheriff.id, null));
        assertTrue(ex.getMessage().contains("Sheriff"));
    }

    @Test
    void catBalouDiscardsATargetTableCard() {
        Game g = normalizedGame(4);
        Player p = g.currentPlayer();
        Player target = after(g, after(g, p)); // any distance is fine for Cat Balou
        Card mustang = card(CardType.MUSTANG);
        target.table.add(mustang);
        Card cat = card(CardType.CAT_BALOU);
        p.hand.add(cat);

        g.playCard(p.id, cat.id(), target.id, mustang.id());
        assertTrue(target.table.isEmpty());
    }

    @Test
    void sidKetchumTradesTwoCardsForLife() {
        Game g = normalizedGame(4);
        Player p = g.currentPlayer();
        p.health = p.maxHealth - 1;
        Card c1 = card(CardType.BANG);
        Card c2 = card(CardType.MISSED);
        p.hand.addAll(List.of(c1, c2));

        g.useAbility(p.id, List.of(c1.id(), c2.id()));
        assertEquals(p.maxHealth, p.health);
        assertTrue(p.hand.isEmpty());
    }

    @Test
    void viewHidesOtherPlayersSecrets() {
        Game g = new Game(makePlayers(4), new Random(7));
        Player sheriff = g.currentPlayer();
        Player other = after(g, sheriff);

        Map<String, Object> view = g.viewFor(other.id);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) view.get("players");
        for (Map<String, Object> pm : players) {
            assertFalse(pm.containsKey("hand"), "hands are never exposed in the players list");
            String role = (String) pm.get("role");
            if (pm.get("id").equals(sheriff.id)) {
                assertEquals("Sheriff", role, "the Sheriff is public");
            } else if (!pm.get("id").equals(other.id)) {
                assertNull(role, "other roles stay hidden");
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> you = (Map<String, Object>) view.get("you");
        assertNotNull(you.get("hand"), "you can see your own hand");
    }
}
