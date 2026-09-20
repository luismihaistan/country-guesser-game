package com.countryguesser.game.repository;

import com.countryguesser.game.entity.Country;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CountryRepository extends JpaRepository<Country, String> {

    List<Country> findByHasCoverageTrue();

    @Modifying
    @Query(value = "UPDATE countries SET area_weight = ST_Area(geography(geom))", nativeQuery = true)
    void recalculateAreaWeights();

    @Modifying
    @Query(value = "UPDATE countries SET has_coverage = true WHERE iso_code IN (:codes)", nativeQuery = true)
    void markCoverage(@Param("codes") Collection<String> codes);
}
