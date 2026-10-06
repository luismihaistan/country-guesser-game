package com.countryguesser.game.redis;

public record GameSession(String panoId, String countryCode, int streak) {}