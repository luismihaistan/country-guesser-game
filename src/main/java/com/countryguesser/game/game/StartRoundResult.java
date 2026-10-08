package com.countryguesser.game.game;

public record StartRoundResult(Status status, RoundResponse round) {
    public enum Status { STARTED, ALREADY_ACTIVE, POOL_EMPTY }
}