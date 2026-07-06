package com.notbang.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Player {
    public final String id;
    public final String name;
    public Role role;
    public CharacterType character;
    public int health;
    public int maxHealth;
    public boolean alive = true;
    public final List<Card> hand = new ArrayList<>();
    public final List<Card> table = new ArrayList<>(); // blue cards in play, including weapon

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public Optional<Card> weapon() {
        return table.stream().filter(c -> c.type().isWeapon()).findFirst();
    }

    public int weaponRange() {
        return weapon().map(c -> c.type().weaponRange).orElse(1); // Colt .45 default
    }

    public boolean hasTableCard(CardType type) {
        return table.stream().anyMatch(c -> c.type() == type);
    }

    public Optional<Card> tableCard(CardType type) {
        return table.stream().filter(c -> c.type() == type).findFirst();
    }

    public Optional<Card> handCard(int cardId) {
        return hand.stream().filter(c -> c.id() == cardId).findFirst();
    }

    public boolean hasBarrel() {
        return character == CharacterType.JOURDONNAIS || hasTableCard(CardType.BARREL);
    }

    /** Cards this player could use as a Missed! response. */
    public List<Card> missedResponses() {
        List<Card> out = new ArrayList<>();
        for (Card c : hand) {
            if (c.type() == CardType.MISSED) out.add(c);
            else if (c.type() == CardType.BANG && character == CharacterType.CALAMITY_JANET) out.add(c);
        }
        return out;
    }

    /** Cards this player could use as a Bang! response (Duel / Indians). */
    public List<Card> bangResponses() {
        List<Card> out = new ArrayList<>();
        for (Card c : hand) {
            if (c.type() == CardType.BANG) out.add(c);
            else if (c.type() == CardType.MISSED && character == CharacterType.CALAMITY_JANET) out.add(c);
        }
        return out;
    }
}
