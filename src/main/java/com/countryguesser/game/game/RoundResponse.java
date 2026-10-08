package com.countryguesser.game.game;

public record RoundResponse(String panoId, int streak, long secondsRemaining) {}