# Mafia Game Manager

A browser-based multiplayer Mafia game. A host creates a room and shares its join code; guests join with nicknames and play together in real time. This README describes the implementation currently in the repository.

## Features

- Create a room and join with a generated code; the creator becomes the host-player.
- Configure the Mafia count while the room is waiting, then start with randomly assigned roles.
- Play Night and Day rounds, cast or change votes, record eliminations, and save the winning team.
- Let the host start the game, advance phases, change waiting-room settings, or delete a waiting room.
- View player details and game state, including the current phase and completed result.
- Send room chat messages, receive real-time room events, and load recent chat history.
- Use guest sessions without player accounts. The server stores a hash of each session token.

A game cannot be started until at least four players are present. Its Mafia count must be at least one and no greater than `floor((playerCount - 1) / 2)`. The first Night has no vote; in later Night phases only living Mafia players can vote, and in Day all living players can vote. A vote can be changed until the host advances the phase. Advancement requires all eligible players to have voted; a tie results in no elimination. Civilians win when no Mafia remain, and Mafia win when the number of living Mafia is at least the number of living Civilians.

**Vote visibility:** the current implementation broadcasts Day vote updates, including voter and target IDs, to the room and writes vote activity to room chat. Night vote updates use the Mafia-only topic. Day votes should therefore not be treated as private.

There is no registered-user login, admin account, or admin dashboard in the current implementation.

## Technology

| Area | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 4.0.0 |
| Web/API | Spring MVC, REST controllers, Bean Validation |
| Real-time messaging | Spring WebSocket, STOMP, SockJS, and Spring's in-memory simple broker |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL, and Flyway |
| API documentation | Springdoc OpenAPI UI 3.0.3 |
| Browser UI | Static HTML, vanilla JavaScript, Bootstrap 5.3.8, and STOMP.js 7.5.0 |
| Build | Maven Wrapper |

The browser pages and pinned third-party assets are served from `src/main/resources/static/`. Bootstrap and STOMP.js licenses are included with their local vendor files. There is no separate frontend server or Node.js build.

## Architecture

The application is a layered Spring Boot monolith. Controllers expose REST and STOMP entry points, services implement game rules and transactions, repositories provide persistence, and DTOs define API/event payloads rather than serializing JPA entities directly.

| Path | Responsibility |
|---|---|
| `src/main/java/com/mafiagame/config/` | Global REST exception handling and WebSocket/STOMP configuration |
| `src/main/java/com/mafiagame/controller/` | Game, player, vote, and chat message handlers |
| `src/main/java/com/mafiagame/dto/` | Request, response, and event payload types |
| `src/main/java/com/mafiagame/exception/` | Application/API exception types |
| `src/main/java/com/mafiagame/model/` | JPA entities |
| `src/main/java/com/mafiagame/repository/` | Spring Data JPA repositories |
| `src/main/java/com/mafiagame/service/` | Game lifecycle, player, voting, and chat logic |
| `src/main/java/com/mafiagame/utils/` | Join-code and session-token generators; game enums are in `enums/` |
| `src/main/java/com/mafiagame/websocket/` | STOMP authorization, player principals, and room event broadcasting |
| `src/main/resources/db/migration/` | Flyway SQL migrations |
| `src/main/resources/static/` | Browser pages and local vendor assets |
| `src/test/java/` | Controller and service tests |

The database has seven JPA entities: `Game`, `Player`, `GamePhase`, `Vote`, `Elimination`, `GameResult`, and `ChatMessageRecord`. `V1__create_initial_schema.sql` creates the game tables; `V2__create_chat_message.sql` adds persisted room chat.

Guest identity uses the `X-Session-Token` header. Room creation and joining return the raw token in that response header; the browser keeps its session data in `sessionStorage`. The database stores only the SHA-256 token hash. Host actions require the host player's token. A player-details request is token-bound; Mafia players also receive identity and status metadata for their teammates.

## REST API

| Method and path | Purpose |
|---|---|
| `POST /api/games` | Create a room and host-player; returns `X-Session-Token`. |
| `GET /api/games/{id}` | Read room state, roster, current phase, and result if complete. |
| `PUT /api/games/{id}/settings` | Update the Mafia count in a waiting room; host token required. |
| `POST /api/games/{id}/start` | Start a waiting game; host token required. |
| `POST /api/games/{id}/advance` | Advance the expected phase and round; host token required. |
| `DELETE /api/games/{id}` | Delete a waiting room; host token required. |
| `GET /api/games/{id}/result` | Read a completed game's result. |
| `GET /api/games/{id}/chat` | Read recent room chat; room-player token required. |
| `POST /api/players` | Join using a join code and nickname; returns `X-Session-Token`. |
| `GET /api/players/{id}` | Read the authenticated player's own details. |
| `POST /api/votes` | Create or update the authenticated player's vote. |
| `GET /api/votes/me?phaseId={phaseId}` | Read the authenticated player's vote for a phase. |
| `GET /api/votes/status?phaseId={phaseId}` | Read voting progress for a phase. |

Use the `X-Session-Token` request header for player and host operations that require a guest session. Request bodies are JSON.

## WebSocket messaging

- Connect through `/ws` using SockJS and provide `X-Session-Token` in the STOMP `CONNECT` headers.
- Subscribe to `/game-session/game/{gameId}` for room events and chat.
- Mafia players may subscribe to `/game-session/game/{gameId}/mafia` for Mafia-only events.
- Publish chat messages to `/app/{gameId}/send-message`.

The simple broker is in-memory; no external message broker is configured.

## Run locally

You need JDK 21 and a running PostgreSQL instance. Create the application database, then set the datasource environment variables in the same shell used to run Maven. Spring Boot does not load a `.env` file automatically.

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/mafia_db"
$env:DB_USERNAME = "your_username"
$env:DB_PASSWORD = "your_password"
```

Run the tests and application with the Maven Wrapper:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

The application listens on port `8080`. Flyway applies migrations from `classpath:db/migration`, and Hibernate validates the resulting schema at startup. The full test suite also loads the Spring application context, so a valid datasource URL and available PostgreSQL database are needed when running all tests.
