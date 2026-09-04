CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE users (
                       id            BIGSERIAL PRIMARY KEY,
                       email         VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       max_streak    INTEGER NOT NULL DEFAULT 0,
                       total_games   INTEGER NOT NULL DEFAULT 0,
                       created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE countries (
                           iso_code     CHAR(2) PRIMARY KEY,
                           name         VARCHAR(255) NOT NULL,
                           geom         GEOMETRY(MULTIPOLYGON, 4326) NOT NULL,
                           has_coverage BOOLEAN NOT NULL DEFAULT false,
                           area_weight  NUMERIC
);

CREATE INDEX idx_countries_geom ON countries USING GIST (geom);

CREATE TABLE precalculated_points (
                                      id           BIGSERIAL PRIMARY KEY,
                                      country_code CHAR(2) NOT NULL REFERENCES countries(iso_code),
                                      lat          NUMERIC NOT NULL,
                                      lng          NUMERIC NOT NULL,
                                      used         BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_precalculated_points_country ON precalculated_points(country_code);

CREATE TABLE games_history (
                               id           BIGSERIAL PRIMARY KEY,
                               user_id      BIGINT NOT NULL REFERENCES users(id),
                               streak       INTEGER NOT NULL,
                               ended_reason VARCHAR(20) NOT NULL CHECK (ended_reason IN ('WRONG_GUESS', 'ABANDONED')),
                               played_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_games_history_user ON games_history(user_id);