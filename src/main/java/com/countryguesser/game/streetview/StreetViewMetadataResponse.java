package com.countryguesser.game.streetview;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StreetViewMetadataResponse(
        String status,
        @JsonProperty("pano_id") String panoId,
        Location location
) {
    public record Location(double lat, double lng) {}
}