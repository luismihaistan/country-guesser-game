package com.countryguesser.game.scheduler;

import com.countryguesser.game.entity.Country;
import com.countryguesser.game.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PrecalculatedPointReplenishmentJob {

    private final CountryRepository countryRepository;
    private final PrecalculatedPointReplenishmentService replenishmentService;

    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void replenishIfNeeded() {
        List<Country> coveredCountries = countryRepository.findByHasCoverageTrue();
        for (Country country : coveredCountries) {
            replenishmentService.replenishCountry(country.getIsoCode());
        }
    }
}