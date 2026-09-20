package com.countryguesser.game.repository;

import com.countryguesser.game.entity.PrecalculatedPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PrecalculatedPointRepository extends JpaRepository<PrecalculatedPoint, Long> {

    @Query(value = "SELECT * FROM precalculated_points ORDER BY random() LIMIT 1", nativeQuery = true)
    Optional<PrecalculatedPoint> findRandomPoint();

    @Modifying
    @Query(value = """
    INSERT INTO precalculated_points (country_code, lat, lng, used)
    SELECT c.iso_code, ST_Y((pt).geom), ST_X((pt).geom), false
    FROM countries c
    CROSS JOIN LATERAL ST_Dump(ST_GeneratePoints(ST_MakeValid(c.geom), :pointsPerCountry)) AS pt
    WHERE c.has_coverage = true
    """, nativeQuery = true)
    void generatePoints(@Param("pointsPerCountry") int pointsPerCountry);
}
