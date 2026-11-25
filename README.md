# sto-backend

Backend for the game Sto (Spring Boot 4, Java 25). Game rules: `../sto/GAME_DESIGN.md`. API contract: [`docs/API.md`](docs/API.md).

## Running

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Default port is **8080** (overridden with the `PORT` variable). To check:

```bash
curl http://localhost:8080/api/health   # {"status":"ok"}
```

## Configuration

| Property | Environment variable | Default |
|---|---|---|
| `server.port` | `PORT` | `8080` |
| `app.cors.allowed-origins` | `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:4200,https://kacperkk2.github.io` |
| `app.game.va-banque-pick` | `APP_GAME_VA_BANQUE_PICK` | `10s` |
| `app.room.ttl` | `APP_ROOM_TTL` | `1h` |
| `app.dev.bots-enabled` | `APP_DEV_BOTS_ENABLED` | `false`; `true` under `mvnw spring-boot:run` (set in `pom.xml`). Dev only: `POST /rooms/{code}/bots` |

State is kept in memory: a restart removes all rooms.

## Structure

- `game/GameEngine`: the rules of the game (events, phases, scoring, tie-break), with no Spring and no clock.
- `game/GameService`: player actions under the room lock and phase deadlines (`TaskScheduler`).
- `game/QuestionBank`: question scenarios, one per file in `src/main/resources/questions/` (format: `docs/API.md`).
- `room/`: rooms, joining, closing and expiry.
- `events/RoomEvents`: SSE.
- `auth/`, `error/`, `config/`: same as in spy-backend.
