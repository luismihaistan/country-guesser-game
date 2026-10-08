package com.countryguesser.game.country;

import com.countryguesser.game.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CountryGeoJsonService {

    private final CountryRepository countryRepository;
    private volatile String cached;

    public String getGeoJson() {
        String result = cached;
        if (result == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = countryRepository.findAllAsSimplifiedGeoJson();
                }
                result = cached;
            }
        }
        return result;
    }
}