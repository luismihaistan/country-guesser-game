package com.countryguesser.game.scheduler;

import com.countryguesser.game.entity.PrecalculatedPoint;
import com.countryguesser.game.redis.LocationEntry;
import com.countryguesser.game.redis.LocationPoolService;
import com.countryguesser.game.redis.PanoDeduplicationService;
import com.countryguesser.game.repository.CountryRepository;
import com.countryguesser.game.repository.PrecalculatedPointRepository;
import com.countryguesser.game.repository.RandomPointProjection;
import com.countryguesser.game.streetview.StreetViewClient;
import com.countryguesser.game.streetview.StreetViewMetadataResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocationGeneratorWorker {

    private static final int TARGET_POOL_SIZE = 100;
    private static final int MAX_ATTEMPTS_PER_CYCLE = 20;

    private final PrecalculatedPointRepository precalculatedPointRepository;
    private final CountryRepository countryRepository;
    private final StreetViewClient streetViewClient;
    private final LocationPoolService locationPoolService;
    private final PanoDeduplicationService panoDeduplicationService;

    @Scheduled(fixedDelay = 10 * 1000)
    public void generateLocations() {
        int attempts = 0;

        while (locationPoolService.size() < TARGET_POOL_SIZE && attempts < MAX_ATTEMPTS_PER_CYCLE) {
            attempts++;
            processOneCandidate();
        }

        if (attempts > 0) {
            log.info("Cycle finished: {} attempts, pool size now {}", attempts, locationPoolService.size());
        }
    }

    private void processOneCandidate() {
        Optional<RandomPointProjection> candidateOpt = precalculatedPointRepository.findRandomPoint();
        if (candidateOpt.isEmpty()) {
            log.warn("No unused precalculated points available.");
            return;
        }

        RandomPointProjection candidate = candidateOpt.get();

        try {
            Optional<StreetViewMetadataResponse> responseOpt =
                    streetViewClient.fetchMetadata(candidate.getLat(), candidate.getLng());

            if (responseOpt.isEmpty() || !streetViewClient.isFound(responseOpt.get())) {
                return;
            }

            StreetViewMetadataResponse response = responseOpt.get();
            double realLat = response.location().lat();
            double realLng = response.location().lng();

            boolean stillInExpectedCountry =
                    countryRepository.isPointInCountry(candidate.getCountryCode(), realLat, realLng);

            if (!stillInExpectedCountry) {
                log.debug("Panorama for {} landed outside expected country border.", candidate.getCountryCode());
                return;
            }

            if (panoDeduplicationService.isAlreadyUsed(response.panoId())) {
                return;
            }

            LocationEntry entry = new LocationEntry(
                    response.panoId(), candidate.getCountryCode(), realLat, realLng);
            locationPoolService.push(entry);
            panoDeduplicationService.markAsUsed(response.panoId());

        } finally {
            precalculatedPointRepository.markAsUsed(candidate.getId());
        }
    }
}