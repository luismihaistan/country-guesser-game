package com.countryguesser.game.repository;

public interface RandomPointProjection {
    Long getId();
    String getCountryCode();
    Double getLat();
    Double getLng();
}