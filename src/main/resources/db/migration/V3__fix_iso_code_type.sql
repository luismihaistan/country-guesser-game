ALTER TABLE precalculated_points DROP CONSTRAINT precalculated_points_country_code_fkey;

ALTER TABLE countries ALTER COLUMN iso_code TYPE VARCHAR(2);
ALTER TABLE precalculated_points ALTER COLUMN country_code TYPE VARCHAR(2);

ALTER TABLE precalculated_points
    ADD CONSTRAINT precalculated_points_country_code_fkey
        FOREIGN KEY (country_code) REFERENCES countries(iso_code);