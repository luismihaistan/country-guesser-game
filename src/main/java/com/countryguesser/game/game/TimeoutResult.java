package com.countryguesser.game.game;

public record TimeoutResult(
        boolean expired,
        long secondsRemaining,
        Integer streak,
        String actualCountryCode
) {}
