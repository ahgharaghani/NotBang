package com.notbang.game;

public enum CharacterType {
    BART_CASSIDY("Bart Cassidy", 4, "Each time he loses a life point, he draws a card."),
    BLACK_JACK("Black Jack", 4, "He shows the second card he draws; on a Heart or Diamond he draws one more."),
    CALAMITY_JANET("Calamity Janet", 4, "She may play Bang! cards as Missed! and vice versa."),
    EL_GRINGO("El Gringo", 3, "Each time a player hits him, he draws a random card from that player's hand."),
    JOURDONNAIS("Jourdonnais", 4, "He is considered to have a Barrel in play at all times."),
    LUCKY_DUKE("Lucky Duke", 4, "Each time he 'draws!', he flips two cards and the best result counts."),
    PAUL_REGRET("Paul Regret", 3, "Other players see him at distance +1."),
    ROSE_DOOLAN("Rose Doolan", 4, "She sees all other players at distance -1."),
    SID_KETCHUM("Sid Ketchum", 4, "He may discard 2 cards to regain 1 life point."),
    SLAB_THE_KILLER("Slab the Killer", 4, "Players need 2 Missed! cards to cancel his Bang!."),
    SUZY_LAFAYETTE("Suzy Lafayette", 4, "As soon as she has no cards in hand, she draws a card."),
    VULTURE_SAM("Vulture Sam", 4, "When a player is eliminated, he takes all that player's cards."),
    WILLY_THE_KID("Willy the Kid", 4, "He can play any number of Bang! cards per turn.");

    public final String displayName;
    public final int baseHealth;
    public final String ability;

    CharacterType(String displayName, int baseHealth, String ability) {
        this.displayName = displayName;
        this.baseHealth = baseHealth;
        this.ability = ability;
    }
}
