package com.countryguesser.game.repository;

import com.countryguesser.game.entity.PrecalculatedPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PrecalculatedPointRepository extends JpaRepository<PrecalculatedPoint, Long> {

    @Query(value = "SELECT * FROM precalculated_points ORDER BY random() LIMIT 1", nativeQuery = true)
    Optional<PrecalculatedPoint> findRandomPoint();
}
