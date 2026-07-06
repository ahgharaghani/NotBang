package com.notbang.game;

public enum CardType {
    // Brown (play-and-discard) cards
    BANG("Bang!", false, 0, "Shoot a player within range of your weapon. They lose 1 life unless they play a Missed!"),
    MISSED("Missed!", false, 0, "Cancels a Bang! aimed at you."),
    BEER("Beer", false, 0, "Regain 1 life point. No effect when only 2 players remain."),
    PANIC("Panic!", false, 0, "Take a card from a player at distance 1."),
    CAT_BALOU("Cat Balou", false, 0, "Force any player to discard a card."),
    DUEL("Duel", false, 0, "Challenge a player: you alternate discarding Bang! cards. First unable to loses 1 life."),
    GATLING("Gatling", false, 0, "A Bang! against all other players."),
    INDIANS("Indians!", false, 0, "All other players discard a Bang! or lose 1 life."),
    STAGECOACH("Stagecoach", false, 0, "Draw 2 cards."),
    WELLS_FARGO("Wells Fargo", false, 0, "Draw 3 cards."),
    SALOON("Saloon", false, 0, "All players in play regain 1 life point."),
    GENERAL_STORE("General Store", false, 0, "Reveal as many cards as players; each player picks one, starting with you."),

    // Blue (equipment) cards
    BARREL("Barrel", true, 0, "When shot at, 'draw!': on a Heart the shot misses."),
    SCOPE("Scope", true, 0, "You see all other players at distance -1."),
    MUSTANG("Mustang", true, 0, "Other players see you at distance +1."),
    JAIL("Jail", true, 0, "Put a player in jail. At their turn they 'draw!': on a Heart they escape, otherwise they skip their turn. (Not on the Sheriff.)"),
    DYNAMITE("Dynamite", true, 0, "At your turn 'draw!': on 2-9 of Spades it explodes (lose 3 life), otherwise pass it left."),

    // Weapons (blue)
    VOLCANIC("Volcanic", true, 1, "Weapon, range 1. You may play any number of Bang! cards per turn."),
    SCHOFIELD("Schofield", true, 2, "Weapon, range 2."),
    REMINGTON("Remington", true, 3, "Weapon, range 3."),
    REV_CARABINE("Rev. Carabine", true, 4, "Weapon, range 4."),
    WINCHESTER("Winchester", true, 5, "Weapon, range 5.");

    public final String displayName;
    public final boolean blue;
    public final int weaponRange; // 0 = not a weapon

    CardType(String displayName, boolean blue, int weaponRange, String description) {
        this.displayName = displayName;
        this.blue = blue;
        this.weaponRange = weaponRange;
        this.description = description;
    }

    public final String description;

    public boolean isWeapon() {
        return weaponRange > 0;
    }
}
