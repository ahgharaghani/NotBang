package com.notbang.game;

import java.util.ArrayList;
import java.util.List;

/** Factory for the 80-card base game deck. */
public final class Deck {

    private Deck() {}

    /** Builds the standard Bang! base deck (80 cards), unshuffled. */
    public static List<Card> standardDeck() {
        List<Card> cards = new ArrayList<>(80);
        int[] id = {0};

        // Helper lambda-ish via local method not possible; use small helper.
        // BANG! x25
        addRun(cards, id, CardType.BANG, Suit.CLUBS, 2, 9);          // 2-9 clubs (8)
        addRun(cards, id, CardType.BANG, Suit.DIAMONDS, 2, 14);      // 2-A diamonds (13)
        add(cards, id, CardType.BANG, Suit.HEARTS, 12);
        add(cards, id, CardType.BANG, Suit.HEARTS, 13);
        add(cards, id, CardType.BANG, Suit.HEARTS, 14);
        add(cards, id, CardType.BANG, Suit.SPADES, 14);

        // MISSED! x12
        addRun(cards, id, CardType.MISSED, Suit.SPADES, 2, 8);       // 2-8 spades (7)
        addRun(cards, id, CardType.MISSED, Suit.CLUBS, 10, 14);      // 10-A clubs (5)

        // BEER x6
        addRun(cards, id, CardType.BEER, Suit.HEARTS, 6, 11);        // 6-J hearts (6)

        // PANIC x4
        add(cards, id, CardType.PANIC, Suit.HEARTS, 11);
        add(cards, id, CardType.PANIC, Suit.HEARTS, 12);
        add(cards, id, CardType.PANIC, Suit.HEARTS, 14);
        add(cards, id, CardType.PANIC, Suit.DIAMONDS, 8);

        // CAT BALOU x4
        add(cards, id, CardType.CAT_BALOU, Suit.HEARTS, 13);
        add(cards, id, CardType.CAT_BALOU, Suit.DIAMONDS, 9);
        add(cards, id, CardType.CAT_BALOU, Suit.DIAMONDS, 10);
        add(cards, id, CardType.CAT_BALOU, Suit.DIAMONDS, 11);

        // DUEL x3
        add(cards, id, CardType.DUEL, Suit.DIAMONDS, 12);
        add(cards, id, CardType.DUEL, Suit.SPADES, 11);
        add(cards, id, CardType.DUEL, Suit.CLUBS, 8);

        // GATLING x2 (official: 1; we include 1) -> keep official single copy
        add(cards, id, CardType.GATLING, Suit.HEARTS, 10);

        // INDIANS! x2
        add(cards, id, CardType.INDIANS, Suit.DIAMONDS, 13);
        add(cards, id, CardType.INDIANS, Suit.DIAMONDS, 14);

        // STAGECOACH x2
        add(cards, id, CardType.STAGECOACH, Suit.SPADES, 9);
        add(cards, id, CardType.STAGECOACH, Suit.SPADES, 9);

        // WELLS FARGO x1
        add(cards, id, CardType.WELLS_FARGO, Suit.HEARTS, 3);

        // SALOON x1
        add(cards, id, CardType.SALOON, Suit.HEARTS, 5);

        // GENERAL STORE x2
        add(cards, id, CardType.GENERAL_STORE, Suit.CLUBS, 9);
        add(cards, id, CardType.GENERAL_STORE, Suit.SPADES, 12);

        // BARREL x2
        add(cards, id, CardType.BARREL, Suit.SPADES, 12);
        add(cards, id, CardType.BARREL, Suit.SPADES, 13);

        // SCOPE x1
        add(cards, id, CardType.SCOPE, Suit.SPADES, 14);

        // MUSTANG x2
        add(cards, id, CardType.MUSTANG, Suit.HEARTS, 8);
        add(cards, id, CardType.MUSTANG, Suit.HEARTS, 9);

        // JAIL x3
        add(cards, id, CardType.JAIL, Suit.SPADES, 10);
        add(cards, id, CardType.JAIL, Suit.SPADES, 11);
        add(cards, id, CardType.JAIL, Suit.HEARTS, 4);

        // DYNAMITE x1
        add(cards, id, CardType.DYNAMITE, Suit.HEARTS, 2);

        // Weapons
        add(cards, id, CardType.VOLCANIC, Suit.SPADES, 10);
        add(cards, id, CardType.VOLCANIC, Suit.CLUBS, 10);
        add(cards, id, CardType.SCHOFIELD, Suit.CLUBS, 11);
        add(cards, id, CardType.SCHOFIELD, Suit.CLUBS, 12);
        add(cards, id, CardType.SCHOFIELD, Suit.SPADES, 13);
        add(cards, id, CardType.REMINGTON, Suit.CLUBS, 13);
        add(cards, id, CardType.REV_CARABINE, Suit.CLUBS, 14);
        add(cards, id, CardType.WINCHESTER, Suit.SPADES, 8);

        return cards;
    }

    private static void add(List<Card> cards, int[] id, CardType type, Suit suit, int rank) {
        cards.add(new Card(id[0]++, type, suit, rank));
    }

    private static void addRun(List<Card> cards, int[] id, CardType type, Suit suit, int fromRank, int toRank) {
        for (int r = fromRank; r <= toRank; r++) {
            add(cards, id, type, suit, r);
        }
    }
}
