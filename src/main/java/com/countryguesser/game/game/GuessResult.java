package com.countryguesser.game.game;

public record GuessResult(Status status, boolean correct, int streak, String actualCountryCode) {
    public enum Status { NO_ACTIVE_ROUND, CORRECT, INCORRECT }
}