package com.notbang.game;

public enum Suit {
    HEARTS("♥"), DIAMONDS("♦"), CLUBS("♣"), SPADES("♠");

    public final String symbol;

    Suit(String symbol) {
        this.symbol = symbol;
    }

    public boolean isRed() {
        return this == HEARTS || this == DIAMONDS;
    }
}
