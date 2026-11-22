# CountryGuesser — *Which country is this?*

A full-stack web geography game inspired by GeoGuessr's Country Streak mode. You are dropped onto a random street-level panorama somewhere in the world and have **10 minutes** to figure out which country you are in. Every correct guess extends your streak; one wrong guess—or letting the clock run out—ends the run.

---

## What the Game Does (Non-Technical Overview)

1. **Create an account & sign in** — Your best streak and completed games are tracked per account, and active rounds are saved so you can refresh the page or come back without losing your spot.
2. **Explore a real street-level location** — Each round drops you onto an official Google Street View road across 59 covered countries worldwide. Street names and address overlays are turned off so you rely on landscapes, road markings, architecture, signage, and driving side.
3. **Pick the country on the map** — Hover over (or focus) the compact world map in the bottom-right corner to expand it, hover over countries to see their names, click to select one, and submit your guess.
4. **Beat the 10-minute timer & build a streak**:
   - **Correct guess**: Your streak increments and you can immediately jump to the next location.
   - **Wrong guess**: The actual country is revealed, your run is recorded, and your streak resets for the next game.
   - **Time runs out (`00:00`)**: The round expires automatically, the answer is revealed, your streak is lost (and saved to your game history), and you can start a fresh run.

---

## Getting Started (How to Run & Play Locally)

### Prerequisites

- **Java 21+** (Maven does not need to be installed separately — the project includes `./mvnw` / `mvnw.cmd`).
- **Docker & Docker Compose** (used to run PostgreSQL with PostGIS and Redis).
- **A Google Cloud account** with billing enabled (Google Maps Platform provides a monthly free tier) and the following two APIs enabled in your project:
  1. **Street View Static API** — used server-side by the background worker to query `/streetview/metadata` (metadata lookups are free).
  2. **Maps JavaScript API** — used in the browser to render the interactive `StreetViewPanorama`.

> **Tip on Google API keys:** For best security, create two separate keys — a server key restricted to **Street View Static API** (`GOOGLE_MAPS_API_KEY`) and a browser key restricted to **Maps JavaScript API** + HTTP referrer `http://localhost:8080/*` (`GOOGLE_MAPS_BROWSER_KEY`). For quick local testing, a single key with both APIs enabled works for both variables.

### Step 1 — Clone the repository

```bash
git clone https://github.com/luismihaistan/country-guesser-game
cd CountryGuesser
```

### Step 2 — Create the `.env` file

Create a `.env` file in the project root (next to `pom.xml` and `docker-compose.yml`). This file is gitignored and loaded automatically by both Docker Compose and Spring Boot:

```properties
DB_USERNAME=postgres
DB_PASSWORD=choose_a_local_password

# Must be at least 32 characters (256 bits) for HMAC-SHA256 JWT signing
JWT_SECRET=replace-with-a-random-secret-key-at-least-32-chars
JWT_EXPIRATION_MS=86400000

GOOGLE_MAPS_API_KEY=your_server_side_google_maps_key
GOOGLE_MAPS_BROWSER_KEY=your_browser_side_google_maps_key
```

| Variable | Required | Description |
| --- | :---: | --- |
| `DB_USERNAME` | Yes | PostgreSQL username (shared by Docker Compose and Spring Boot). |
| `DB_PASSWORD` | Yes | PostgreSQL password. |
| `JWT_SECRET` | Yes | Secret used to sign JWTs (`HS256` — minimum 32 characters). |
| `JWT_EXPIRATION_MS` | Yes | JWT lifetime in milliseconds (e.g., `86400000` = 24 hours). |
| `GOOGLE_MAPS_API_KEY` | Yes | Server-side key for Google Street View Metadata API. |
| `GOOGLE_MAPS_BROWSER_KEY` | Yes | Browser key exposed via `/api/config` for Google Maps JS API. |

*(Optional: `app.game.round-duration-seconds` in `application.properties` defaults to `600` seconds / 10 minutes.)*

### Step 3 — Start PostgreSQL (PostGIS) and Redis

```bash
docker compose up -d
```

This starts:
- `geocountry-postgres` (`postgis/postgis:16-3.4`) on port `5432`
- `geocountry-redis` (`redis:7-alpine`) on port `6379`

### Step 4 — Start the application

```bash
# macOS / Linux
./mvnw spring-boot:run

# Windows (PowerShell / CMD)
.\mvnw.cmd spring-boot:run
```

On the very first startup, the backend initializes everything automatically:
1. **Flyway** creates the PostGIS extension and database tables (`V1`–`V3`).
2. **`CountryImportRunner`** imports 237 countries from `src/main/resources/data/countries.geojson`, calculates area weights, flags 59 countries with Street View coverage, and generates 150 random candidate coordinates per covered country (~8,800 points).
3. **`LocationGeneratorWorker`** starts populating the Redis `location_pool` every 10 seconds. Wait **10–30 seconds** on first launch for the first validated Street View locations to enter the pool.

### Step 5 — Play the game

1. Open **`http://localhost:8080`** in your browser.
2. Click **Create an account**, enter an email and a password (at least 8 characters), and submit — you are signed in automatically.
3. Click **Get a location** to start your first round.
4. Look around the Street View panorama using mouse drag / navigation arrows. Keep an eye on the **10:00** countdown timer in the top bar (it turns red under 1 minute).
5. Hover over the world map in the bottom-right corner to expand it, click a country, and press **Guess \<Country\>**.
6. After each result overlay, click **Next location** (if correct) or **Play again** (if wrong or timed out).

---

## Architecture & Technical Deep Dive

### Tech Stack

| Layer | Technologies |
| --- | --- |
| **Backend** | Java 21, Spring Boot 4.1.1, Spring Web MVC, Spring Security, Spring Data JPA, Spring Data Redis, Spring Validation, `RestClient` |
| **Authentication** | Stateless JWT (`jjwt 0.13.0`, HS256) + BCrypt password hashing |
| **Database & Geospatial** | PostgreSQL 16, PostGIS 3.4, Hibernate Spatial, JTS (`jts2geojson`), Flyway |
| **Cache & Session Store** | Redis 7 (Lists, Sets, Hashes, Sorted Sets, atomic Lua scripts) |
| **Frontend** | Vanilla ES Modules (zero build step), HTML5, CSS3, Leaflet 1.9.4 (Canvas renderer), Google Maps JavaScript API |
| **Infrastructure** | Docker Compose, Maven Wrapper |

---

### Key Engineering Decisions

#### 1. Decoupled Street View Location Pipeline
Randomly sampling Earth coordinates on demand during a player's HTTP request would be slow and unreliable (most random coordinates have no Street View coverage). Instead, location generation is completely decoupled from gameplay:

1. **Precalculated Country Points (`CountryImportRunner`)**: At startup, country `MultiPolygon` geometries (SRID `4326`) are loaded from GeoJSON into PostGIS, indexed with GiST, and sampled using `ST_GeneratePoints(ST_MakeValid(geom), 150)` for 59 Street-View-covered countries.
2. **Async Location Worker (`LocationGeneratorWorker`)**: Runs every 10 seconds (up to 20 attempts per cycle) until Redis `location_pool` reaches 100 ready locations:
   - Picks a random unused point from `precalculated_points` and marks it used in a `finally` block.
   - Calls Google Street View Metadata API (`radius=10000m`).
   - Rejects missing coverage and unofficial user-contributed photo spheres (`copyright` must contain `"Google"`).
   - **Border-snap validation**: Because Google snaps coordinates up to 10 km to the nearest road, a point near a border can land in a neighboring country. The worker verifies the snapped `(lat, lng)` against the expected country polygon using PostGIS `ST_Contains` before accepting it.
   - **Deduplication**: Checks and records `panoId` in Redis Set `used_pano_ids` so players don't receive duplicate panoramas.
   - Pushes the validated `LocationEntry` to Redis List `location_pool`.
3. **Automatic Point Replenishment (`PrecalculatedPointReplenishmentJob`)**: Runs every 10 minutes; if any covered country drops below 20 unused points, it cleans up used rows and regenerates 150 fresh points in a dedicated transaction (`REQUIRES_NEW`).
4. **Instant Round Start**: `GET /api/game/next` simply pops a pre-verified location from `location_pool` in O(1) time with zero external API latency.

#### 2. Active Sessions, Timer & Atomic Expiration
- Each user's active session is stored in Redis Hash `active_sessions:{userId}` (`panoId`, `countryCode`, `streak`, `expiresAt`) and indexed by expiration timestamp in Sorted Set `session_expirations`.
- **Atomic Lua Claim (`SessionService.claimExpiredSession`)**: Expiring a session executes a single Redis Lua script that checks `now >= expiresAt`, reads `HGETALL`, deletes the hash, and removes the user from `session_expirations` atomically. This guarantees that a timed-out game is persisted to PostgreSQL (`games_history` with `EndedReason.ABANDONED`, plus `users.max_streak` / `users.total_games`) **at most once**, even if the browser timeout request and background sweeper fire concurrently.
- **Background Sweeper (`SessionExpirationJob`)**: Runs every 10 seconds with a 5-second grace period (so active tabs can receive the country reveal on timeout, while closed/abandoned tabs are still finalized and recorded as `ABANDONED`).
- **Drift-free Frontend Countdown**: The browser computes remaining time against `Date.now() + secondsRemaining * 1000` and resyncs on `visibilitychange`, preventing timer drift when a tab is throttled in the background. On refresh, the remaining server time is restored from `GET /api/game/current`.

#### 3. Cost & Performance Optimizations
- **Single `StreetViewPanorama` Instance**: Dynamic Street View is billed per panorama instantiation. The frontend creates one `StreetViewPanorama` instance and updates it across rounds with `panorama.setPano(panoId)`.
- **Fair Gameplay Controls**: `addressControl` and `showRoadLabels` are disabled on the panorama.
- **Simplified, Cached GeoJSON Map**: `GET /api/countries` builds a `FeatureCollection` inside PostGIS using `ST_SimplifyPreserveTopology(geom, 0.03)` (4-decimal precision), caches the JSON string in memory (`CountryGeoJsonService`), enables HTTP gzip compression, sets `Cache-Control: private, max-age=86400`, and renders all 237 countries smoothly on Leaflet's Canvas renderer (`preferCanvas: true`).

---

## Database Schema

Managed via Flyway migrations (`src/main/resources/db/migration/V1`–`V3`):

| Table | Purpose |
| --- | --- |
| `users` | Registered players (`email`, BCrypt `password_hash`, `max_streak`, `total_games`, `created_at`). |
| `countries` | ISO-2 country code, name, PostGIS `GEOMETRY(MULTIPOLYGON, 4326)` with GiST index, `has_coverage`, `area_weight`. |
| `precalculated_points` | Candidate `(lat, lng)` points generated inside covered countries, tracked with a `used` flag. |
| `games_history` | Completed runs per user (`streak`, `ended_reason` ∈ `{'WRONG_GUESS', 'ABANDONED'}`, `played_at`). |

---

## REST API Reference

All endpoints except `/api/auth/**` and static assets require an `Authorization: Bearer <jwt>` header.

| Method | Endpoint | Auth | Description |
| --- | --- | :---: | --- |
| `POST` | `/api/auth/register` | Public | Register a new account (`{ email, password }`, min 8 chars). Returns `201` or `409`. |
| `POST` | `/api/auth/login` | Public | Authenticate and receive `{ token }`. Returns `200` or `401`. |
| `GET` | `/api/config` | JWT | Returns `{ mapsApiKey }` for loading Google Maps JS API in the browser. |
| `GET` | `/api/countries` | JWT | Returns simplified GeoJSON `FeatureCollection` of all countries for Leaflet. |
| `GET` | `/api/game/current` | JWT | Returns active round `{ panoId, streak, secondsRemaining }`, or `404` if none. |
| `GET` | `/api/game/next` | JWT | Starts a new round from `location_pool`. Returns `200`, `409` (already active), or `503` (pool empty). |
| `POST` | `/api/game/guess` | JWT | Submits `{ countryCode }`. Returns `{ correct, streak, actualCountryCode }` (`200`), `400` (blank), or `410` (expired/no active round). |
| `POST` | `/api/game/timeout` | JWT | Confirms round expiration on the server and returns `{ expired, secondsRemaining, streak, actualCountryCode }`. |

---

## Project Structure

```text
CountryGuesser/
├── docker-compose.yml              # PostGIS 16 + Redis 7 services
├── pom.xml                         # Maven dependencies & Java 21 build config
└── src/main/
    ├── java/com/countryguesser/game/
    │   ├── auth/                   # AuthController, JwtService, JwtAuthenticationFilter, DTOs
    │   ├── config/                 # SecurityConfig, PasswordEncoderConfig, AppConfigController
    │   ├── country/                # CountryController, CountryGeoJsonService
    │   ├── data/                   # CountryImportRunner (GeoJSON import & point seeding)
    │   ├── entity/                 # JPA entities (User, Country, PrecalculatedPoint, GameHistory, EndedReason)
    │   ├── game/                   # GameController, GameService, round/guess/timeout DTOs
    │   ├── redis/                  # SessionService (Lua expiry), LocationPoolService, PanoDeduplicationService
    │   ├── repository/             # Spring Data JPA & PostGIS native queries
    │   ├── scheduler/              # LocationGeneratorWorker, PrecalculatedPointReplenishmentJob, SessionExpirationJob
    │   └── streetview/             # StreetViewClient & metadata response DTO
    └── resources/
        ├── application.properties
        ├── data/countries.geojson  # World country boundaries
        ├── db/migration/           # Flyway SQL migrations (V1–V3)
        └── static/                 # Frontend SPA (index.html, css/style.css, js/{api,app,game,map,maps}.js)
```
