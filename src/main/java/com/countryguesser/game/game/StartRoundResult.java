package com.countryguesser.game.game;

public record StartRoundResult(Status status, String panoId) {
    public enum Status { STARTED, ALREADY_ACTIVE, POOL_EMPTY }
}