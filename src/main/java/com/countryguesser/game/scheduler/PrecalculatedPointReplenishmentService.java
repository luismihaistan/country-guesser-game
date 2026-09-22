package com.countryguesser.game.scheduler;

import com.countryguesser.game.repository.PrecalculatedPointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrecalculatedPointReplenishmentService {

    private static final long THRESHOLD = 20;
    private static final int REGENERATE_COUNT = 150;

    private final PrecalculatedPointRepository precalculatedPointRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replenishCountry(String isoCode) {
        long unused = precalculatedPointRepository.countUnusedByCountry(isoCode);
        if (unused >= THRESHOLD) {
            return;
        }

        log.info("Country {} has only {} unused points — regenerating.", isoCode, unused);
        precalculatedPointRepository.deleteUsedByCountry(isoCode);
        precalculatedPointRepository.generatePointsForCountry(isoCode, REGENERATE_COUNT);
    }
}