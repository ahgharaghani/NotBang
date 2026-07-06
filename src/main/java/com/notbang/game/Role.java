package com.notbang.game;

public enum Role {
    SHERIFF("Sheriff", "Eliminate all Outlaws and the Renegade."),
    DEPUTY("Deputy", "Protect the Sheriff. Win when all Outlaws and the Renegade are dead."),
    OUTLAW("Outlaw", "Kill the Sheriff."),
    RENEGADE("Renegade", "Be the last one standing.");

    public final String displayName;
    public final String goal;

    Role(String displayName, String goal) {
        this.displayName = displayName;
        this.goal = goal;
    }
}
