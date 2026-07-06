package com.notbang.game;

public record Card(int id, CardType type, Suit suit, int rank) {

    public String rankName() {
        return switch (rank) {
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            case 14 -> "A";
            default -> String.valueOf(rank);
        };
    }

    @Override
    public String toString() {
        return type.displayName + " " + rankName() + suit.symbol;
    }
}
