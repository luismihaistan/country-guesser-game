package com.countryguesser.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.MultiPolygon;

@Entity
@Table(name="countries")
@Getter
@Setter
@NoArgsConstructor
public class Country {

    @Id
    @Column(name = "iso_code", length = 2, nullable = false)
    private String isoCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "geometry(MultiPolygon,4326)")
    private MultiPolygon geom;

    @Column(name = "has_coverage", nullable = false)
    private Boolean hasCoverage = false;

    @Column(name = "area_weight")
    private Double areaWeight;
}
