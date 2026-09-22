package com.countryguesser.game.redis;

public record LocationEntry(String panoId, String countryCode, double lat, double lng) {
}