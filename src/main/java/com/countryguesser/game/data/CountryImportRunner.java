package com.countryguesser.game.data;

import com.countryguesser.game.entity.Country;
import com.countryguesser.game.repository.CountryRepository;
import com.countryguesser.game.repository.PrecalculatedPointRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.wololo.geojson.Feature;
import org.wololo.geojson.FeatureCollection;
import org.wololo.geojson.GeoJSONFactory;
import org.wololo.jts2geojson.GeoJSONReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class CountryImportRunner implements CommandLineRunner {

    private static final int WGS84_SRID = 4326;
    private static final String GEOJSON_PATH = "data/countries.geojson";
    private static final int POINTS_PER_COUNTRY = 150;
    private final CountryRepository countryRepository;
    private final PrecalculatedPointRepository precalculatedPointRepository;
    private final GeoJSONReader geoJsonReader = new GeoJSONReader();
    private static final Map<String, String> ISO_CODE_OVERRIDES = Map.of(
            "France", "FR",
            "Norway", "NO"
    );
    private static final Set<String> COVERAGE_COUNTRY_CODES = Set.of(
            // Europe
            "GB","IE","FR","DE","NL","BE","LU","CH","AT","ES","PT","IT",
            "DK","SE","NO","FI","IS","PL","CZ","SK","HU","RO","BG","GR",
            "HR","SI","EE","LV","LT","AD",
            // North America
            "US","CA","MX",
            // South America
            "BR","AR","CL","UY","PE","CO","EC",
            // Oceania
            "AU","NZ",
            // Asia
            "JP","SG","TH","MY","IL","KR","ID","IN","TR",
            // Africa
            "ZA","BW","LS","NA","GH","KE","RW","UG"
    );

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        importCountriesIfNeeded();
        generatePointsIfNeeded();
    }

    private void importCountriesIfNeeded() throws IOException {
        long existingCount = countryRepository.count();
        if (existingCount > 0) {
            log.info("Countries already imported ({} rows) — skipping import.", existingCount);
            return;
        }

        log.info("Importing countries from {}", GEOJSON_PATH);

        String geoJsonContent = readResourceAsString(GEOJSON_PATH);
        FeatureCollection featureCollection = (FeatureCollection) GeoJSONFactory.create(geoJsonContent);

        List<Country> countries = new ArrayList<>();
        int skipped = 0;

        for (Feature feature : featureCollection.getFeatures()) {
            Map<String, Object> properties = feature.getProperties();
            String isoCode = (String) properties.get("ISO3166-1-Alpha-2");
            String name = (String) properties.get("name");

            if (isoCode == null || isoCode.isBlank() || isoCode.equals("-99")) {
                String override = ISO_CODE_OVERRIDES.get(name);
                if (override != null) {
                    isoCode = override;
                } else {
                    log.warn("Skipping feature without valid ISO code: {}", name);
                    skipped++;
                    continue;
                }
            }

            if (isoCode.length() != 2) {
                log.warn("Skipping feature with unexpected ISO code length: {} (code='{}')", name, isoCode);
                skipped++;
                continue;
            }

            Geometry geometry = geoJsonReader.read(feature.getGeometry());
            MultiPolygon multiPolygon = toMultiPolygon(geometry);
            multiPolygon.setSRID(WGS84_SRID);

            Country country = new Country();
            country.setIsoCode(isoCode);
            country.setName(name);
            country.setGeom(multiPolygon);
            country.setHasCoverage(false);

            countries.add(country);
        }

        countryRepository.saveAll(countries);
        countryRepository.flush();
        countryRepository.recalculateAreaWeights();
        countryRepository.markCoverage(COVERAGE_COUNTRY_CODES);
        log.info("Imported {} countries ({} skipped due to missing ISO code).", countries.size(), skipped);
    }

    private void generatePointsIfNeeded() {
        long existingPoints = precalculatedPointRepository.count();
        if (existingPoints > 0) {
            log.info("Precalculated points already exist ({} rows) — skipping generation.", existingPoints);
            return;
        }

        log.info("Generating {} points per covered country...", POINTS_PER_COUNTRY);
        precalculatedPointRepository.generatePoints(POINTS_PER_COUNTRY);
        log.info("Generated {} precalculated points total.", precalculatedPointRepository.count());
    }

    private MultiPolygon toMultiPolygon(Geometry geometry) {
        if (geometry instanceof MultiPolygon multiPolygon) {
            return multiPolygon;
        }
        if (geometry instanceof Polygon polygon) {
            GeometryFactory factory = polygon.getFactory();
            return factory.createMultiPolygon(new Polygon[]{polygon});
        }
        throw new IllegalArgumentException("Unexpected geometry type: " + geometry.getGeometryType());
    }

    private String readResourceAsString(String path) throws java.io.IOException {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}