package com.countryguesser.game.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AppConfigController {

    private final String mapsBrowserKey;

    public AppConfigController(@Value("${google.maps.browser-key}") String mapsBrowserKey) {
        this.mapsBrowserKey = mapsBrowserKey;
    }

    @GetMapping("/api/config")
    public Map<String, String> config() {
        return Map.of("mapsApiKey", mapsBrowserKey);
    }
}