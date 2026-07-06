package com.notbang.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The Bang! game engine. All mutating methods validate the move and throw
 * {@link IllegalArgumentException} with a user-facing message when a move is illegal.
 * The engine is UI-agnostic; {@link #viewFor(String)} produces a per-player view
 * that hides secret information (other hands, unrevealed roles, deck order).
 */
public class Game {

    /** Turn stages: 0 = dynamite check, 1 = jail check, 2 = draw phase, 3 = play phase. */
    private int stage = 0;

    private final List<Player> players; // seat order
    private final Deque<Card> deck = new ArrayDeque<>();
    private final List<Card> discard = new ArrayList<>();
    private final Deque<Pending> pendingStack = new ArrayDeque<>();
    private final List<String> log = new ArrayList<>();
    private final Random rng;

    private int turnIndex;
    private int bangsThisTurn = 0;
    private String result = null; // non-null when game over

    public Game(List<Player> seatedPlayers, Random rng) {
        if (seatedPlayers.size() < 3 || seatedPlayers.size() > 7) {
            throw new IllegalArgumentException("Bang! needs 3 to 7 players.");
        }
        this.players = new ArrayList<>(seatedPlayers);
        this.rng = rng;
        setup();
    }

    // ------------------------------------------------------------------ setup

    private void setup() {
        // Roles
        List<Role> roles = rolesFor(players.size());
        Collections.shuffle(roles, rng);
        // Characters
        List<CharacterType> chars = new ArrayList<>(Arrays.asList(CharacterType.values()));
        Collections.shuffle(chars, rng);

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            p.role = roles.get(i);
            p.character = chars.get(i);
            p.maxHealth = p.character.baseHealth + (p.role == Role.SHERIFF ? 1 : 0);
            p.health = p.maxHealth;
        }
        // Sheriff starts
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).role == Role.SHERIFF) {
                turnIndex = i;
                break;
            }
        }
        // Deck
        List<Card> cards = Deck.standardDeck();
        Collections.shuffle(cards, rng);
        deck.addAll(cards);
        // Deal: each player draws cards equal to their life points
        for (Player p : players) {
            for (int i = 0; i < p.health; i++) {
                p.hand.add(deck.pop());
            }
        }
        log("The game begins! " + currentPlayer().name + " is the Sheriff and plays first.");
        resumeTurnStart();
    }

    private static List<Role> rolesFor(int n) {
        List<Role> roles = new ArrayList<>(List.of(Role.SHERIFF, Role.RENEGADE));
        switch (n) {
            case 3 -> roles.add(Role.OUTLAW);
            case 4 -> roles.addAll(List.of(Role.OUTLAW, Role.OUTLAW));
            case 5 -> roles.addAll(List.of(Role.OUTLAW, Role.OUTLAW, Role.DEPUTY));
            case 6 -> roles.addAll(List.of(Role.OUTLAW, Role.OUTLAW, Role.OUTLAW, Role.DEPUTY));
            case 7 -> roles.addAll(List.of(Role.OUTLAW, Role.OUTLAW, Role.OUTLAW, Role.DEPUTY, Role.DEPUTY));
            default -> throw new IllegalArgumentException("Unsupported player count " + n);
        }
        return roles;
    }

    // ------------------------------------------------------------- accessors

    public boolean isOver() {
        return result != null;
    }

    public String result() {
        return result;
    }

    public Player currentPlayer() {
        return players.get(turnIndex);
    }

    public List<Player> players() {
        return players;
    }

    public Pending pendingTop() {
        return pendingStack.peek();
    }

    int deckSize() {
        return deck.size();
    }

    int discardSize() {
        return discard.size();
    }

    public Player playerById(String pid) {
        return players.stream().filter(p -> p.id.equals(pid)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown player"));
    }

    private List<Player> alivePlayers() {
        return players.stream().filter(p -> p.alive).toList();
    }

    // ------------------------------------------------------------- turn flow

    private void resumeTurnStart() {
        while (!isOver()) {
            Player p = currentPlayer();
            if (!p.alive) {
                nextSeat();
                continue;
            }
            if (stage == 0) {
                stage = 1;
                Card dynamite = p.tableCard(CardType.DYNAMITE).orElse(null);
                if (dynamite != null) {
                    Card flip = drawCheck(p, c -> !(c.suit() == Suit.SPADES && c.rank() >= 2 && c.rank() <= 9),
                            "Dynamite");
                    boolean explodes = flip.suit() == Suit.SPADES && flip.rank() >= 2 && flip.rank() <= 9;
                    if (explodes) {
                        p.table.remove(dynamite);
                        discard.add(dynamite);
                        log("💥 The Dynamite explodes in front of " + p.name + "!");
                        damage(p, 3, null);
                        if (!pendingStack.isEmpty()) return; // dying decision pending
                        if (!p.alive) {
                            nextSeat();
                            continue;
                        }
                    } else {
                        p.table.remove(dynamite);
                        Player next = nextAliveAfter(turnIndex);
                        next.table.add(dynamite);
                        log("The Dynamite does not explode and is passed to " + next.name + ".");
                    }
                }
            }
            if (stage == 1) {
                stage = 2;
                Card jail = p.tableCard(CardType.JAIL).orElse(null);
                if (jail != null) {
                    p.table.remove(jail);
                    discard.add(jail);
                    Card flip = drawCheck(p, c -> c.suit() == Suit.HEARTS, "Jail");
                    if (flip.suit() == Suit.HEARTS) {
                        log(p.name + " escapes from Jail!");
                    } else {
                        log(p.name + " stays in Jail and skips this turn.");
                        nextSeat();
                        continue;
                    }
                }
            }
            if (stage == 2) {
                drawPhase(p);
                stage = 3;
                log("— It is " + p.name + "'s turn —");
            }
            return;
        }
    }

    private void nextSeat() {
        turnIndex = (turnIndex + 1) % players.size();
        stage = 0;
        bangsThisTurn = 0;
    }

    private Player nextAliveAfter(int index) {
        for (int i = 1; i <= players.size(); i++) {
            Player p = players.get((index + i) % players.size());
            if (p.alive) return p;
        }
        throw new IllegalStateException("No alive players");
    }

    private void drawPhase(Player p) {
        drawCards(p, 1);
        if (p.character == CharacterType.BLACK_JACK) {
            Card second = drawOne();
            if (second == null) return;
            p.hand.add(second);
            log("Black Jack reveals his second card: " + second + ".");
            if (second.suit().isRed()) {
                drawCards(p, 1);
                log("It's red — Black Jack draws an extra card.");
            }
        } else {
            drawCards(p, 1);
        }
    }

    // ------------------------------------------------------------- deck ops

    private Card drawOne() {
        if (deck.isEmpty()) reshuffle();
        if (deck.isEmpty()) return null;
        return deck.pop();
    }

    private void reshuffle() {
        if (discard.isEmpty()) return;
        List<Card> cards = new ArrayList<>(discard);
        discard.clear();
        Collections.shuffle(cards, rng);
        deck.addAll(cards);
        log("The discard pile is shuffled into a new deck.");
    }

    private void drawCards(Player p, int n) {
        for (int i = 0; i < n; i++) {
            Card c = drawOne();
            if (c == null) return;
            p.hand.add(c);
        }
    }

    /**
     * "Draw!": flip the top card of the deck. Lucky Duke flips two and the
     * favorable result (per {@code success}) is used. Flipped cards are discarded.
     */
    private Card drawCheck(Player p, Predicate<Card> success, String reason) {
        Card first = drawOne();
        discard.add(first);
        if (p.character == CharacterType.LUCKY_DUKE) {
            Card second = drawOne();
            if (second != null) {
                discard.add(second);
                Card chosen = success.test(first) ? first : second;
                log(p.name + " draws! for " + reason + " (Lucky Duke): " + first + " and " + second
                        + " → uses " + chosen + ".");
                return chosen;
            }
        }
        log(p.name + " draws! for " + reason + ": " + first + ".");
        return first;
    }

    // ------------------------------------------------------------ main moves

    public synchronized void playCard(String pid, int cardId, String targetPid, Integer targetCardId) {
        requireRunning();
        Player p = playerById(pid);
        require(pendingStack.isEmpty(), "You must resolve the current action first.");
        require(p == currentPlayer() && stage == 3, "It is not your turn to play cards.");
        Card card = p.handCard(cardId).orElseThrow(() -> new IllegalArgumentException("That card is not in your hand."));

        CardType type = card.type();
        // Calamity Janet may use a Missed! as a Bang!
        if (type == CardType.MISSED) {
            require(p.character == CharacterType.CALAMITY_JANET,
                    "Missed! can only be played in response to a Bang! (unless you are Calamity Janet).");
            type = CardType.BANG;
        }

        switch (type) {
            case BANG -> playBang(p, card, targetPid);
            case BEER -> playBeer(p, card);
            case SALOON -> {
                discardFromHand(p, card);
                for (Player q : alivePlayers()) heal(q, 1);
                log(p.name + " plays Saloon: everyone regains 1 life.");
            }
            case STAGECOACH -> {
                discardFromHand(p, card);
                drawCards(p, 2);
                log(p.name + " plays Stagecoach and draws 2 cards.");
            }
            case WELLS_FARGO -> {
                discardFromHand(p, card);
                drawCards(p, 3);
                log(p.name + " plays Wells Fargo and draws 3 cards.");
            }
            case PANIC -> playPanicOrCatBalou(p, card, targetPid, targetCardId, true);
            case CAT_BALOU -> playPanicOrCatBalou(p, card, targetPid, targetCardId, false);
            case DUEL -> playDuel(p, card, targetPid);
            case GATLING -> playGatling(p, card);
            case INDIANS -> playIndians(p, card);
            case GENERAL_STORE -> playGeneralStore(p, card);
            case JAIL -> playJail(p, card, targetPid);
            case BARREL, SCOPE, MUSTANG, DYNAMITE -> playEquipment(p, card);
            case VOLCANIC, SCHOFIELD, REMINGTON, REV_CARABINE, WINCHESTER -> playWeapon(p, card);
            case MISSED -> throw new IllegalStateException("unreachable");
        }
        checkSuzy(p);
    }

    private void playBang(Player p, Card card, String targetPid) {
        boolean unlimited = p.character == CharacterType.WILLY_THE_KID
                || p.weapon().map(w -> w.type() == CardType.VOLCANIC).orElse(false);
        require(unlimited || bangsThisTurn < 1, "You can only play one Bang! per turn.");
        Player target = requireTarget(p, targetPid);
        int dist = distance(p, target);
        require(dist <= p.weaponRange(),
                "Target out of range: distance " + dist + " but your weapon reaches " + p.weaponRange() + ".");
        discardFromHand(p, card);
        bangsThisTurn++;
        log(p.name + " plays " + card + " against " + target.name + "!");
        int needed = p.character == CharacterType.SLAB_THE_KILLER ? 2 : 1;
        Pending pend = new Pending(Pending.Type.BANG, null, p.id);
        pend.needed = needed;
        pend.queue.add(target.id);
        pendingStack.push(pend);
        continueTop();
    }

    private void playBeer(Player p, Card card) {
        require(alivePlayers().size() > 2, "Beer has no effect when only 2 players remain.");
        require(p.health < p.maxHealth, "You are already at full health.");
        discardFromHand(p, card);
        heal(p, 1);
        log(p.name + " drinks a Beer and regains 1 life (" + p.health + "/" + p.maxHealth + ").");
    }

    private void playPanicOrCatBalou(Player p, Card card, String targetPid, Integer targetCardId, boolean steal) {
        Player target = requireTarget(p, targetPid);
        if (steal) {
            int dist = distance(p, target);
            require(dist <= 1, "Panic! only reaches distance 1 (target is at distance " + dist + ").");
        }
        require(!target.hand.isEmpty() || !target.table.isEmpty(), target.name + " has no cards.");

        Card taken;
        if (targetCardId != null) {
            Card tc = target.table.stream().filter(c -> c.id() == targetCardId).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("That card is not on the target's table."));
            target.table.remove(tc);
            taken = tc;
        } else if (!target.hand.isEmpty()) {
            taken = target.hand.remove(rng.nextInt(target.hand.size()));
        } else {
            taken = target.table.remove(rng.nextInt(target.table.size()));
        }
        discardFromHand(p, card);
        if (steal) {
            p.hand.add(taken);
            log(p.name + " plays Panic! and takes a card from " + target.name + ".");
        } else {
            discard.add(taken);
            log(p.name + " plays Cat Balou: " + target.name + " discards " + taken + ".");
        }
        checkSuzy(target);
    }

    private void playDuel(Player p, Card card, String targetPid) {
        Player target = requireTarget(p, targetPid);
        discardFromHand(p, card);
        log(p.name + " challenges " + target.name + " to a Duel!");
        Pending pend = new Pending(Pending.Type.DUEL, null, p.id);
        pend.queue.add(target.id); // the challenged player responds first
        pendingStack.push(pend);
        continueTop();
    }

    private void playGatling(Player p, Card card) {
        discardFromHand(p, card);
        log(p.name + " fires the Gatling — a Bang! at everyone else!");
        Pending pend = new Pending(Pending.Type.BANG, null, p.id);
        pend.fromGroupEffect = true;
        for (Player q : othersInTurnOrder(p)) pend.queue.add(q.id);
        pendingStack.push(pend);
        continueTop();
    }

    private void playIndians(Player p, Card card) {
        discardFromHand(p, card);
        log(p.name + " plays Indians! — everyone else must discard a Bang! or lose 1 life.");
        Pending pend = new Pending(Pending.Type.INDIANS, null, p.id);
        for (Player q : othersInTurnOrder(p)) pend.queue.add(q.id);
        pendingStack.push(pend);
        continueTop();
    }

    private void playGeneralStore(Player p, Card card) {
        discardFromHand(p, card);
        Pending pend = new Pending(Pending.Type.GENERAL_STORE, null, p.id);
        List<Player> pickers = new ArrayList<>();
        pickers.add(p);
        pickers.addAll(othersInTurnOrder(p));
        for (Player q : pickers) pend.queue.add(q.id);
        for (int i = 0; i < pickers.size(); i++) {
            Card c = drawOne();
            if (c != null) pend.storeCards.add(c);
        }
        log(p.name + " opens the General Store: " + pend.storeCards + ".");
        pendingStack.push(pend);
        continueTop();
    }

    private void playJail(Player p, Card card, String targetPid) {
        Player target = requireTarget(p, targetPid);
        require(target.role != Role.SHERIFF, "You cannot jail the Sheriff.");
        require(!target.hasTableCard(CardType.JAIL), target.name + " is already in jail.");
        p.hand.remove(card);
        target.table.add(card);
        log(p.name + " throws " + target.name + " in Jail!");
    }

    private void playEquipment(Player p, Card card) {
        require(!p.hasTableCard(card.type()), "You already have a " + card.type().displayName + " in play.");
        p.hand.remove(card);
        p.table.add(card);
        log(p.name + " puts " + card.type().displayName + " in play.");
    }

    private void playWeapon(Player p, Card card) {
        p.weapon().ifPresent(old -> {
            p.table.remove(old);
            discard.add(old);
            log(p.name + " discards " + old.type().displayName + ".");
        });
        p.hand.remove(card);
        p.table.add(card);
        log(p.name + " equips " + card.type().displayName + " (range " + card.type().weaponRange + ").");
    }

    // -------------------------------------------------------------- pendings

    /**
     * Advance the top pending until a human decision is required or the stack empties.
     */
    private void continueTop() {
        while (!isOver()) {
            Pending top = pendingStack.peek();
            if (top == null) {
                onStackEmpty();
                return;
            }
            if (top.awaiting != null) {
                Player waiting = playerById(top.awaiting);
                if (waiting.alive) return; // waiting on a live player's decision
                top.awaiting = null; // dead players cannot act; move on
            }
            switch (top.type) {
                case BANG -> {
                    if (!advanceBangQueue(top)) continue;
                    return;
                }
                case INDIANS -> {
                    if (!advanceIndiansQueue(top)) continue;
                    return;
                }
                case DUEL -> {
                    if (!advanceDuel(top)) continue;
                    return;
                }
                case GENERAL_STORE -> {
                    if (!advanceStore(top)) continue;
                    return;
                }
                case DISCARD_EXCESS, DYING -> {
                    // These always have an awaiting player; if they got here the player died.
                    pendingStack.pop();
                }
            }
        }
    }

    /** @return true if now awaiting a player; false if this pending finished (popped) or changed. */
    private boolean advanceBangQueue(Pending pend) {
        while (!pend.queue.isEmpty()) {
            Player target = playerById(pend.queue.poll());
            if (!target.alive) continue;
            Player shooter = playerById(pend.source);
            int needed = pend.needed;
            // Barrel(s): draw! on Hearts the shot is dodged (counts as one Missed!)
            if (target.hasBarrel()) {
                Card flip = drawCheck(target, c -> c.suit() == Suit.HEARTS, "Barrel");
                if (flip.suit() == Suit.HEARTS) {
                    needed--;
                    log(target.name + "'s Barrel deflects the shot" + (needed > 0 ? " — but another Missed! is still needed!" : "!"));
                }
            }
            if (needed <= 0) {
                continue; // fully dodged, next target
            }
            if (target.missedResponses().size() < needed) {
                // Cannot possibly dodge: takes the hit automatically
                log(target.name + " cannot dodge and takes the hit.");
                damage(target, 1, shooter);
                if (!pendingStack.isEmpty() && pendingStack.peek() != pend) {
                    return true; // a DYING pending is now on top; resume this queue later
                }
                continue;
            }
            pend.awaiting = target.id;
            pend.needed = needed;
            return true;
        }
        pendingStack.remove(pend);
        return false;
    }

    private boolean advanceIndiansQueue(Pending pend) {
        while (!pend.queue.isEmpty()) {
            Player target = playerById(pend.queue.poll());
            if (!target.alive) continue;
            if (target.bangResponses().isEmpty()) {
                log(target.name + " has no Bang! against the Indians and loses 1 life.");
                damage(target, 1, playerById(pend.source));
                if (!pendingStack.isEmpty() && pendingStack.peek() != pend) {
                    return true;
                }
                continue;
            }
            pend.awaiting = target.id;
            return true;
        }
        pendingStack.remove(pend);
        return false;
    }

    /**
     * Duel invariant: {@code pend.source} is always the opponent of the player about to
     * respond; the next responder sits in {@code pend.queue}.
     */
    private boolean advanceDuel(Pending pend) {
        if (pend.queue.isEmpty()) {
            pendingStack.remove(pend);
            return false;
        }
        Player responder = playerById(pend.queue.poll());
        Player opponent = playerById(pend.source);
        if (!responder.alive || !opponent.alive) {
            pendingStack.remove(pend);
            return false;
        }
        if (responder.bangResponses().isEmpty()) {
            log(responder.name + " has no Bang! and loses the Duel.");
            pendingStack.remove(pend);
            damage(responder, 1, opponent);
            return false;
        }
        pend.awaiting = responder.id;
        return true;
    }

    private boolean advanceStore(Pending pend) {
        while (!pend.queue.isEmpty()) {
            Player picker = playerById(pend.queue.peek());
            if (!picker.alive) {
                pend.queue.poll();
                continue;
            }
            if (pend.storeCards.size() == 1) {
                pend.queue.poll();
                Card last = pend.storeCards.remove(0);
                picker.hand.add(last);
                log(picker.name + " takes the last card from the General Store: " + last + ".");
                continue;
            }
            if (pend.storeCards.isEmpty()) {
                pend.queue.clear();
                break;
            }
            pend.awaiting = picker.id;
            return true;
        }
        pendingStack.remove(pend);
        return false;
    }

    private void onStackEmpty() {
        if (isOver()) return;
        Player cur = currentPlayer();
        if (!cur.alive) {
            nextSeat();
            resumeTurnStart();
            return;
        }
        if (stage < 3) {
            resumeTurnStart();
        }
    }

    // ------------------------------------------------------------- responses

    /**
     * Respond to the pending action awaiting this player.
     * The meaning of {@code cardIds} depends on the pending type; an empty list means
     * "accept the effect" (take the hit / lose a life / die / keep hand as-is).
     */
    public synchronized void respond(String pid, List<Integer> cardIds) {
        requireRunning();
        Pending pend = pendingStack.peek();
        require(pend != null && pid.equals(pend.awaiting), "You have nothing to respond to.");
        Player p = playerById(pid);

        switch (pend.type) {
            case BANG -> respondBang(pend, p, cardIds);
            case INDIANS -> respondIndians(pend, p, cardIds);
            case DUEL -> respondDuel(pend, p, cardIds);
            case DYING -> respondDying(pend, p, cardIds);
            case DISCARD_EXCESS -> respondDiscard(pend, p, cardIds);
            case GENERAL_STORE -> throw new IllegalArgumentException("Pick a card from the General Store instead.");
        }
        if (p.alive) checkSuzy(p);
        continueTop();
    }

    private void respondBang(Pending pend, Player p, List<Integer> cardIds) {
        Player shooter = playerById(pend.source);
        if (cardIds.isEmpty()) {
            pend.awaiting = null;
            pend.needed = 1; // remaining Gatling targets always need 1
            log(p.name + " takes the hit.");
            damage(p, 1, shooter);
            return;
        }
        require(cardIds.size() == pend.needed,
                "You must play " + pend.needed + " Missed! card(s) or take the hit.");
        List<Card> used = resolveResponseCards(p, cardIds, p.missedResponses());
        for (Card c : used) discardFromHand(p, c);
        log(p.name + " plays " + used + " — the shot misses!");
        pend.awaiting = null;
        pend.needed = 1;
    }

    private void respondIndians(Pending pend, Player p, List<Integer> cardIds) {
        if (cardIds.isEmpty()) {
            pend.awaiting = null;
            log(p.name + " takes the Indians' hit.");
            damage(p, 1, playerById(pend.source));
            return;
        }
        require(cardIds.size() == 1, "Discard exactly one Bang! card.");
        List<Card> used = resolveResponseCards(p, cardIds, p.bangResponses());
        discardFromHand(p, used.get(0));
        log(p.name + " discards " + used.get(0) + " against the Indians.");
        pend.awaiting = null;
    }

    private void respondDuel(Pending pend, Player p, List<Integer> cardIds) {
        Player opponent = playerById(pend.source);
        if (cardIds.isEmpty()) {
            log(p.name + " backs down and loses the Duel.");
            pendingStack.remove(pend);
            damage(p, 1, opponent);
            return;
        }
        require(cardIds.size() == 1, "Discard exactly one Bang! card.");
        List<Card> used = resolveResponseCards(p, cardIds, p.bangResponses());
        discardFromHand(p, used.get(0));
        log(p.name + " fires back in the Duel with " + used.get(0) + "!");
        // Swap roles: the opponent must now respond, and p becomes the opponent.
        pend.source = p.id;
        pend.awaiting = null;
        pend.queue.clear();
        pend.queue.add(opponent.id);
    }

    private void respondDying(Pending pend, Player p, List<Integer> cardIds) {
        if (!cardIds.isEmpty()) {
            require(alivePlayers().size() > 2, "Beer cannot save you when only 2 players remain.");
            for (int cardId : cardIds) {
                Card beer = p.handCard(cardId)
                        .orElseThrow(() -> new IllegalArgumentException("That card is not in your hand."));
                require(beer.type() == CardType.BEER, "Only Beer can save you now.");
                if (p.health >= 1) break;
                discardFromHand(p, beer);
                p.health++;
                log(p.name + " desperately drinks a Beer! (" + p.health + "/" + p.maxHealth + ")");
            }
        }
        if (p.health >= 1) {
            pendingStack.remove(pend); // saved!
        } else if (!cardIds.isEmpty()
                && p.hand.stream().anyMatch(c -> c.type() == CardType.BEER)
                && alivePlayers().size() > 2) {
            // Drank something but still dying and more beer is available: keep asking.
        } else {
            pendingStack.remove(pend);
            die(p, pend.source == null ? null : playerById(pend.source));
        }
    }

    private void respondDiscard(Pending pend, Player p, List<Integer> cardIds) {
        int excess = p.hand.size() - Math.max(0, p.health);
        require(cardIds.size() == excess, "You must discard exactly " + excess + " card(s).");
        Set<Integer> ids = new HashSet<>(cardIds);
        require(ids.size() == cardIds.size(), "Duplicate cards selected.");
        List<Card> toDiscard = new ArrayList<>();
        for (int id : ids) {
            toDiscard.add(p.handCard(id)
                    .orElseThrow(() -> new IllegalArgumentException("That card is not in your hand.")));
        }
        for (Card c : toDiscard) discardFromHand(p, c);
        log(p.name + " discards " + toDiscard.size() + " card(s) and ends their turn.");
        pendingStack.remove(pend);
        nextSeat();
        resumeTurnStart();
    }

    /** Validate that all cardIds are legal response cards, and return them. */
    private List<Card> resolveResponseCards(Player p, List<Integer> cardIds, List<Card> legal) {
        List<Card> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int id : cardIds) {
            require(seen.add(id), "Duplicate cards selected.");
            Card c = legal.stream().filter(x -> x.id() == id).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("That card cannot be used for this response."));
            out.add(c);
        }
        return out;
    }

    /** General Store: pick one of the revealed cards. */
    public synchronized void pick(String pid, int cardId) {
        requireRunning();
        Pending pend = pendingStack.peek();
        require(pend != null && pend.type == Pending.Type.GENERAL_STORE && pid.equals(pend.awaiting),
                "It is not your turn to pick.");
        Player p = playerById(pid);
        Card chosen = pend.storeCards.stream().filter(c -> c.id() == cardId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("That card is not in the store."));
        pend.storeCards.remove(chosen);
        p.hand.add(chosen);
        log(p.name + " takes " + chosen + " from the General Store.");
        pend.queue.poll();
        pend.awaiting = null;
        continueTop();
    }

    /** Sid Ketchum's ability: discard two cards to regain one life point. */
    public synchronized void useAbility(String pid, List<Integer> cardIds) {
        requireRunning();
        Player p = playerById(pid);
        require(p.character == CharacterType.SID_KETCHUM, "Your character has no activated ability.");
        require(p.alive, "You are eliminated.");
        require(p.health < p.maxHealth, "You are already at full health.");
        require(cardIds.size() == 2, "Discard exactly two cards.");
        require(new HashSet<>(cardIds).size() == 2, "Duplicate cards selected.");
        List<Card> cards = new ArrayList<>();
        for (int id : cardIds) {
            cards.add(p.handCard(id)
                    .orElseThrow(() -> new IllegalArgumentException("That card is not in your hand.")));
        }
        for (Card c : cards) discardFromHand(p, c);
        heal(p, 1);
        log("Sid Ketchum (" + p.name + ") discards two cards and regains 1 life ("
                + p.health + "/" + p.maxHealth + ").");
        checkSuzy(p);
    }

    public synchronized void endTurn(String pid) {
        requireRunning();
        Player p = playerById(pid);
        require(pendingStack.isEmpty(), "You must resolve the current action first.");
        require(p == currentPlayer() && stage == 3, "It is not your turn.");
        int limit = Math.max(0, p.health);
        if (p.hand.size() > limit) {
            Pending pend = new Pending(Pending.Type.DISCARD_EXCESS, pid, pid);
            pendingStack.push(pend);
            return;
        }
        log(p.name + " ends their turn.");
        nextSeat();
        resumeTurnStart();
    }

    // ------------------------------------------------------- damage & death

    private void heal(Player p, int n) {
        p.health = Math.min(p.maxHealth, p.health + n);
    }

    private void damage(Player victim, int amount, Player source) {
        victim.health -= amount;
        log(victim.name + " loses " + amount + " life point(s) ("
                + Math.max(0, victim.health) + "/" + victim.maxHealth + ").");

        // Character triggers (before death resolution; cards drawn may still be lost on death)
        if (victim.character == CharacterType.BART_CASSIDY) {
            drawCards(victim, amount);
            log("Bart Cassidy (" + victim.name + ") draws " + amount + " card(s) for his wounds.");
        }
        if (victim.character == CharacterType.EL_GRINGO && source != null && source != victim) {
            for (int i = 0; i < amount && !source.hand.isEmpty(); i++) {
                Card stolen = source.hand.remove(rng.nextInt(source.hand.size()));
                victim.hand.add(stolen);
                log("El Gringo (" + victim.name + ") draws a card from " + source.name + "'s hand.");
            }
            checkSuzy(source);
        }

        if (victim.health <= 0) {
            boolean canBeer = alivePlayers().size() > 2
                    && victim.hand.stream().filter(c -> c.type() == CardType.BEER).count()
                       >= (1 - victim.health);
            if (canBeer) {
                Pending dying = new Pending(Pending.Type.DYING, victim.id, source == null ? null : source.id);
                pendingStack.push(dying);
                log(victim.name + " is dying! Beer could still save them...");
            } else {
                die(victim, source);
            }
        }
    }

    private void die(Player victim, Player killer) {
        victim.alive = false;
        victim.health = 0;
        log("☠ " + victim.name + " is eliminated! They were " + article(victim.role) + " "
                + victim.role.displayName + ".");

        // Vulture Sam takes all cards, otherwise everything is discarded.
        Player sam = alivePlayers().stream()
                .filter(q -> q.character == CharacterType.VULTURE_SAM).findFirst().orElse(null);
        List<Card> everything = new ArrayList<>();
        everything.addAll(victim.hand);
        everything.addAll(victim.table);
        victim.hand.clear();
        victim.table.clear();
        if (sam != null && sam != victim) {
            sam.hand.addAll(everything);
            log("Vulture Sam (" + sam.name + ") takes all of " + victim.name + "'s cards.");
        } else {
            discard.addAll(everything);
        }

        // Rewards and penalties
        if (killer != null && killer.alive) {
            if (victim.role == Role.OUTLAW) {
                drawCards(killer, 3);
                log(killer.name + " claims the bounty and draws 3 cards.");
            }
            if (killer.role == Role.SHERIFF && victim.role == Role.DEPUTY) {
                List<Card> all = new ArrayList<>();
                all.addAll(killer.hand);
                all.addAll(killer.table);
                killer.hand.clear();
                killer.table.clear();
                discard.addAll(all);
                log("The Sheriff killed his own Deputy and must discard all his cards!");
            }
        }

        checkWin();
    }

    private static String article(Role r) {
        return r == Role.OUTLAW ? "an" : "a";
    }

    private void checkWin() {
        boolean sheriffAlive = players.stream().anyMatch(p -> p.alive && p.role == Role.SHERIFF);
        List<Player> alive = alivePlayers();
        if (!sheriffAlive) {
            if (alive.size() == 1 && alive.get(0).role == Role.RENEGADE) {
                result = "🏆 The Renegade (" + alive.get(0).name + ") wins — last one standing!";
            } else {
                result = "🏆 The Outlaws win — the Sheriff is dead!";
            }
        } else {
            boolean threatsRemain = players.stream()
                    .anyMatch(p -> p.alive && (p.role == Role.OUTLAW || p.role == Role.RENEGADE));
            if (!threatsRemain) {
                result = "🏆 The Sheriff and Deputies win — law and order prevail!";
            }
        }
        if (result != null) {
            log(result);
            pendingStack.clear();
        }
    }

    // ------------------------------------------------------------- utilities

    private void discardFromHand(Player p, Card c) {
        p.hand.remove(c);
        discard.add(c);
    }

    private void checkSuzy(Player p) {
        if (p.character == CharacterType.SUZY_LAFAYETTE && p.alive && p.health > 0 && p.hand.isEmpty()
                && !isOver()) {
            drawCards(p, 1);
            log("Suzy Lafayette (" + p.name + ") has an empty hand and draws a card.");
        }
    }

    private List<Player> othersInTurnOrder(Player from) {
        List<Player> out = new ArrayList<>();
        int start = players.indexOf(from);
        for (int i = 1; i < players.size(); i++) {
            Player q = players.get((start + i) % players.size());
            if (q.alive) out.add(q);
        }
        return out;
    }

    public int distance(Player from, Player to) {
        List<Player> alive = alivePlayers();
        int a = alive.indexOf(from);
        int b = alive.indexOf(to);
        int n = alive.size();
        int seat = Math.min(Math.floorMod(a - b, n), Math.floorMod(b - a, n));
        int d = seat;
        if (to.hasTableCard(CardType.MUSTANG)) d++;
        if (to.character == CharacterType.PAUL_REGRET) d++;
        if (from.hasTableCard(CardType.SCOPE)) d--;
        if (from.character == CharacterType.ROSE_DOOLAN) d--;
        return Math.max(1, d);
    }

    private Player requireTarget(Player p, String targetPid) {
        require(targetPid != null, "Choose a target for this card.");
        Player target = playerById(targetPid);
        require(target.alive, target.name + " is already eliminated.");
        require(target != p, "You cannot target yourself with this card.");
        return target;
    }

    private void requireRunning() {
        if (isOver()) throw new IllegalArgumentException("The game is over. " + result);
    }

    private static void require(boolean cond, String message) {
        if (!cond) throw new IllegalArgumentException(message);
    }

    private void log(String msg) {
        log.add(msg);
    }

    // ------------------------------------------------------------------ view

    /** Build the personalized game state for one player (or a spectator with unknown pid). */
    public synchronized Map<String, Object> viewFor(String pid) {
        Map<String, Object> view = new LinkedHashMap<>();
        Player me = players.stream().filter(p -> p.id.equals(pid)).findFirst().orElse(null);

        List<Map<String, Object>> ps = new ArrayList<>();
        for (Player p : players) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id);
            m.put("name", p.name);
            m.put("character", p.character.displayName);
            m.put("ability", p.character.ability);
            m.put("health", Math.max(0, p.health));
            m.put("maxHealth", p.maxHealth);
            m.put("alive", p.alive);
            m.put("handCount", p.hand.size());
            m.put("table", cardsToJson(p.table));
            m.put("weaponRange", p.weaponRange());
            m.put("isTurn", p == currentPlayer() && !isOver());
            boolean roleVisible = p.role == Role.SHERIFF || !p.alive || (me != null && p == me) || isOver();
            m.put("role", roleVisible ? p.role.displayName : null);
            if (me != null && me.alive && p.alive && p != me) {
                m.put("distance", distance(me, p));
            }
            ps.add(m);
        }
        view.put("players", ps);

        if (me != null) {
            Map<String, Object> you = new LinkedHashMap<>();
            you.put("id", me.id);
            you.put("role", me.role.displayName);
            you.put("goal", me.role.goal);
            you.put("hand", cardsToJson(me.hand));
            view.put("you", you);
        }

        view.put("deckCount", deck.size());
        view.put("discardTop", discard.isEmpty() ? null : cardToJson(discard.get(discard.size() - 1)));
        view.put("turn", currentPlayer().id);
        view.put("stage", stage);
        view.put("bangsThisTurn", bangsThisTurn);
        view.put("result", result);
        view.put("log", log.size() <= 150 ? log : log.subList(log.size() - 150, log.size()));

        Pending pend = pendingStack.peek();
        if (pend != null && !isOver()) {
            Map<String, Object> pj = new LinkedHashMap<>();
            pj.put("type", pend.type.name());
            pj.put("awaiting", pend.awaiting);
            pj.put("source", pend.source);
            pj.put("needed", pend.needed);
            if (pend.type == Pending.Type.GENERAL_STORE) {
                pj.put("storeCards", cardsToJson(pend.storeCards));
            }
            if (me != null && pid.equals(pend.awaiting)) {
                List<Card> options = switch (pend.type) {
                    case BANG -> me.missedResponses();
                    case INDIANS, DUEL -> me.bangResponses();
                    case DYING -> me.hand.stream().filter(c -> c.type() == CardType.BEER).toList();
                    default -> List.of();
                };
                pj.put("options", options.stream().map(Card::id).toList());
                if (pend.type == Pending.Type.DISCARD_EXCESS) {
                    pj.put("discardCount", me.hand.size() - Math.max(0, me.health));
                }
                if (pend.type == Pending.Type.DYING) {
                    pj.put("beersNeeded", 1 - me.health);
                }
            }
            view.put("pending", pj);
        } else {
            view.put("pending", null);
        }
        return view;
    }

    private List<Map<String, Object>> cardsToJson(List<Card> cards) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Card c : cards) out.add(cardToJson(c));
        return out;
    }

    private Map<String, Object> cardToJson(Card c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.id());
        m.put("type", c.type().name());
        m.put("name", c.type().displayName);
        m.put("suit", c.suit().name());
        m.put("suitSymbol", c.suit().symbol);
        m.put("rank", c.rankName());
        m.put("blue", c.type().blue);
        m.put("weapon", c.type().isWeapon());
        m.put("description", c.type().description);
        return m;
    }
}
