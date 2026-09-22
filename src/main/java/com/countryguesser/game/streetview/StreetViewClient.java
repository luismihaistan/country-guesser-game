package com.countryguesser.game.streetview;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Slf4j
@Service
public class StreetViewClient {

    private static final String METADATA_URL = "https://maps.googleapis.com/maps/api/streetview/metadata";

    private final RestClient restClient;
    private final String apiKey;
    private final int radiusMeters;

    public StreetViewClient(
            RestClient.Builder restClientBuilder,
            @Value("${google.streetview.api-key}") String apiKey,
            @Value("${google.streetview.radius-meters:10000}") int radiusMeters
    ) {
        this.restClient = restClientBuilder.baseUrl(METADATA_URL).build();
        this.apiKey = apiKey;
        this.radiusMeters = radiusMeters;
    }

    public Optional<StreetViewMetadataResponse> fetchMetadata(double lat, double lng) {
        try {
            StreetViewMetadataResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("location", lat + "," + lng)
                            .queryParam("radius", radiusMeters)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(StreetViewMetadataResponse.class);

            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.error("Failed to fetch Street View metadata for ({}, {})", lat, lng, e);
            return Optional.empty();
        }
    }

    public boolean isFound(StreetViewMetadataResponse response) {
        return "OK".equals(response.status());
    }
}