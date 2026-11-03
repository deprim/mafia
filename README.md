# Mafia Game Manager

A browser-based app for hosting remote Mafia games. Anyone can open the app and create a room; friends join it from their own devices using a link or short code. Players do not register or log in. A separate, restricted administrator account is proposed for a verification dashboard; it is not required to play.

The presentation, database diagram, and final-project requirements describe the product context. This README records the current product decision and a proposed implementation direction; it does not imply that the features are already implemented.

## Product scope

### First-version features

- Create a room without signing in. The creator enters a nickname and becomes a player and the room host.
- Share a link/code so other guests can join with a nickname.
- Choose the Mafia count, randomly assign Mafia/Civilian roles when the host starts, and show each player only their own role.
- Run Night and Day rounds, collect private votes, record eliminations, and prevent eliminated players from taking further actions.
- Let the host advance phases; detect the winning team and save the result.
- Push room and game-state changes to connected browsers in real time.
- Provide an admin-only dashboard with system-wide room statistics (room/player totals, active/completed counts, and winner totals) and a list of active rooms with safe metadata such as current phase and player count. Do not show hidden roles, private votes, or guest session tokens.

Guest gameplay remains account-free. Only the admin dashboard requires an administrator login. Game discussion happens outside the app. In-app chat, extra roles/custom rules, and elaborate animations are nice-to-haves, not first-version requirements.

### Game flow and rules

1. A visitor creates a room and becomes its host-player. The app creates a unique join code/link.
2. Other visitors join the room as guest players. Each player has a nickname unique within that room.
3. When the host starts the game, the server assigns roles at random. Each player, including the host, can retrieve only their own role.
4. During Night, each living Mafia player privately votes for an active target. During Day, all living players privately vote after discussing outside the app. Each eligible player has one current vote per phase and may change it until the host advances.
5. On phase advancement, votes are closed and resolved. The most-voted player is eliminated; a tie means no elimination. Eliminated players cannot vote or act. After an elimination, the app checks for a winner and saves the result when the game ends.

The Civilian team wins when no Mafia remain. The Mafia team wins when the number of Mafia players is greater than or equal to the number of Civilian players. The app should not publish individual ballots or other players' roles in shared room updates.

## Guest identity and permissions

No player needs an account or password. A room's creator is represented by a `PLAYER` row with `is_host = true`; all other participants are also `PLAYER` rows. The host flag grants room-management actions within that room. It is separate from the administrator account, which can view system-wide room metadata.

The database diagram includes `session_token_hash`. Keep this as a **guest-session capability**, not a player account: it lets the app recognize the same player after refresh/reconnect, authorize that player's actions, and return that player's private role. Store only a hash in the database; send the raw, high-entropy token to the browser in a private, same-origin cookie. A join code is only an invitation to join and must not act as a player's identity or grant host control. The admin has a separate authenticated account; guest sessions and admin login must not be conflated.

## Database model

The attached UML proposes six entities. This is a good starting point and already exceeds the final-project minimum of four entities and two relationships.

| Table | Fields in the diagram | Purpose |
|---|---|---|
| `GAME` | `id` UUID PK, `join_code` VARCHAR(8) unique, `mafia_count` INTEGER, `status` VARCHAR(20), `created_at` TIMESTAMP | Room configuration and lifecycle. |
| `PLAYER` | `id` UUID PK, `game_id` FK, `nickname` VARCHAR(40), `is_host` BOOLEAN, `role` VARCHAR(10) nullable, `status` VARCHAR(15), `session_token_hash` VARCHAR(64) unique, `joined_at` TIMESTAMP | Every participant, including the host. A role is null until the game starts. |
| `GAME_PHASE` | `id` UUID PK, `game_id` FK, `round_number` INTEGER, `phase_type` NIGHT/DAY, `status` OPEN/CLOSED, `started_at`, `ended_at` nullable | One row for each Night or Day phase. |
| `VOTE` | `id` UUID PK, `phase_id` FK, `voter_id` FK, `target_id` FK, `created_at` | A player's current ballot in a phase. |
| `ELIMINATION` | `id` UUID PK, `phase_id` FK unique, `player_id` FK unique, `eliminated_at` | Records the one player eliminated by a phase, if any. |
| `GAME_RESULT` | `id` UUID PK, `game_id` FK unique, `winning_team` MAFIA/CIVILIAN, `finished_at` | The single saved outcome for a completed game. |
| `APP_USER` (proposed extension) | `id` UUID PK, `username` unique, `password_hash`, `role` (ADMIN), `created_at` | Admin login for the verification dashboard. This table is not in the attached no-admin diagram; no player accounts or public admin registration are proposed. |

The relationships are: one game has many players and phases; a phase has many votes and at most one elimination; a player can cast many votes and be the target of many votes; an elimination refers to one player; and a game has zero or one result. `PLAYER.game_id` and `GAME_PHASE.game_id` connect those records back to their room.

`APP_USER` is independent of the game/player tables: the admin account is not a player and does not receive a player role. If the project later needs registered player accounts to satisfy the course rubric literally, the model and product decision will need to change again.

Dashboard statistics can be calculated from `GAME`, `PLAYER`, and `GAME_RESULT`; a separate statistics table is unnecessary. Provision the admin account through a controlled setup path, not public registration.

### Database constraints and design notes

- Make nickname uniqueness per room with `UNIQUE (game_id, nickname)`, not global uniqueness. Keep `session_token_hash` globally unique, and enforce at most one host per game.
- If a vote may be changed until the host closes the phase, store one current row per voter and phase with `UNIQUE (phase_id, voter_id)`; update its target rather than inserting another ballot. Validate that the voter and target belong to the same game, that both are eligible, and that the voter may vote in that phase.
- Enforce at most one open phase per game and one occurrence of each phase type per round. Use database constraints where practical and validate game transitions in the service layer as well.
- Keep the `ELIMINATION` uniqueness constraints: a phase eliminates no more than one player, and a player can be eliminated only once. Update `PLAYER.status` and insert the elimination record in the same transaction so the status and history cannot disagree.
- Use constrained enum-like values for game/player/phase statuses, roles, phase types, and winning team. Prefer timezone-aware timestamps (stored in UTC) for a game played by people in different locations.
- At game start, require at least one Mafia and one Civilian, with fewer Mafia than Civilians. The exact minimum roster and any tighter Mafia-count cap are not specified yet.

## Application and API design

Use one Spring Boot application as a modular monolith. Render pages with Spring MVC and Thymeleaf; use vanilla JavaScript and Bootstrap for browser interactions and styling. The browser can call REST for initial data and game commands, and use STOMP over WebSocket for live updates. Keep game rules and transitions in services, persistence in repositories, and REST/WebSocket handling in controllers. Use DTOs rather than serializing JPA entities.

Illustrative routes (not implemented; exact paths can be finalized during development):

| Area | Example operations |
|---|---|
| Room lifecycle | `POST /api/games` creates a room and its host-player; `GET /api/games/{joinCode}` returns a safe public room view; `PATCH /api/games/{id}` updates lobby settings; `DELETE /api/games/{id}` cancels/deletes a lobby. |
| Guest players | `POST /api/games/{joinCode}/players` joins a guest; a player-specific endpoint returns that guest's role/status only. |
| Game actions | Host-only start/advance operations; player-only vote operations. Validate each operation against the current phase and player status. |
| Results | Read the completed game's result without exposing ballots or hidden roles. |
| Admin dashboard | `GET /api/admin/dashboard` and `GET /api/admin/rooms` return aggregate statistics and active-room metadata; require an authenticated `ADMIN` account. |
| Live updates | A WebSocket handshake (for example `/ws`) with authorized per-game STOMP subscriptions and sanitized room events. |

The host's guest capability authorizes lobby/game management; each player's capability authorizes only their own participation and private role view. Admin endpoints use the separate authenticated admin account. Shared room updates may announce joins, phase changes, eliminations, and completed results, but must not broadcast secret roles, individual ballot contents, or guest session tokens. Never trust a client-supplied player ID as proof of identity.

## Frontend

The frontend direction is **Thymeleaf, vanilla JavaScript, and Bootstrap** in the existing Spring MVC application, without a separate Node.js/Vite build. A minimal page at `GET /` renders `src/main/resources/templates/index.html` and references the local browser assets under `src/main/resources/static/vendor/`, so the packaged application has no runtime CDN dependency:

| Asset | Pinned version | Local resource | License |
|---|---:|---|---|
| Bootstrap CSS and bundled JavaScript | 5.3.8 | `vendor/bootstrap/5.3.8/` | MIT |
| `@stomp/stompjs` browser UMD bundle | 7.5.0 | `vendor/stomp/7.5.0/` | Apache-2.0 |

The versioned files are copied from the upstream package distributions: [Bootstrap CSS](https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css), [Bootstrap bundle](https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/js/bootstrap.bundle.min.js), and [STOMP.js UMD bundle](https://cdn.jsdelivr.net/npm/@stomp/stompjs@7.5.0/bundles/stomp.umd.min.js). Each vendor directory includes its upstream license.

## Technology fit and current alignment

| Area | Current/intended choice | Assessment |
|---|---|---|
| Java and Spring Boot | Java 21; presentation targets Spring Boot 4.1.2; `pom.xml` currently uses 4.0.0. | Compatible: Boot 4.1.2 supports Java 21. Align the Maven parent to the intended version before feature work; do not pin Spring Framework modules to unrelated versions. |
| Web/API | Spring MVC/REST and Spring WebSocket/STOMP. | Compatible in one servlet-based app. WebFlux is not needed for this project's scale. |
| Persistence | Spring Data JPA, PostgreSQL, Flyway, and the PostgreSQL Flyway module. | Compatible and already represented in Maven. Keep PostgreSQL as the app database and use Flyway migrations as the schema source of truth. |
| Frontend | Thymeleaf, vanilla JavaScript, Bootstrap 5.3.8, and a browser-ready `@stomp/stompjs` 7.5.0 bundle. | Fits the existing Spring MVC application and avoids a separate frontend build toolchain. Thymeleaf rendering and the pinned local vendor assets are now included under `templates/` and `static/vendor/`. |
| API documentation | `springdoc-openapi-starter-webmvc-ui` 3.0.3. | Springdoc 3.x is the Spring Boot 4 line. The current Maven project packages with Boot 4.0.0 and Springdoc 3.0.3; recheck after aligning Boot to 4.1.2. |
| Security | Guest players use room-scoped session tokens; an admin has a protected account. | Spring Security can protect `/api/admin/**` and the admin UI while leaving room creation/joining open. Do not require player registration/login. |
| Lombok | Present as an optional dependency. | Compatible but optional. Avoid Lombok `@Data` on JPA entities; explicit entity methods are easier to reason about for this small model. |

No separate frontend server, microservices, reactive WebFlux stack, or external broker is needed for the first version.

## Final-project requirements to preserve

- Use layered controller/service/repository architecture, a clean package structure, at least four entities, at least two relationships, and DTOs. The attached model has six entities; the proposed admin-account extension makes seven.
- Provide CRUD for game management, subject to room/host permissions and lifecycle rules.
- Validate inputs, return appropriate HTTP status codes, provide custom/global error handling, and use basic INFO/ERROR logging.
- Add unit tests for game rules and MockMvc tests for HTTP behavior, validation, room/host/player permissions, and the protected admin endpoints.
- Keep production application logic within the 2,000-line limit; the requirement excludes tests, UI, and boilerplate.
- Use the team's Jira board for work tracking and GitHub feature branches, pull requests, and code reviews; raise blockers in daily stand-ups.

> **Requirement gap:** the provided final-project requirements explicitly require registration/login, account roles, and protected endpoints. An authenticated `ADMIN` dashboard covers the admin-role/protected-endpoint portion, while guest session tokens provide room-scoped identity. It still does not provide registered `USER` accounts or player login. If the evaluator requires those literally, the account-free player scope will need approval or revision.

## Repository status and local setup

The repository is currently a Spring Boot scaffold, not a playable game. It has a Java 21 application entry point, a Thymeleaf-rendered root page with pinned local Bootstrap and STOMP.js browser assets, a context-load test, and configuration for PostgreSQL, Flyway, and Hibernate schema validation. The Maven project includes JPA, validation, Spring MVC, Thymeleaf, WebSocket, PostgreSQL, Flyway, and Springdoc dependencies. It does not yet contain the proposed game entities, repositories, game services, admin security/dashboard, or database migrations.

The datasource configuration reads `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. `.env.example` documents these values, but Spring Boot does not load a `.env` file automatically; configure them in the shell or IDE run configuration. For PowerShell, local values can be set like this:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/mafia_db"
$env:DB_USERNAME = "your_username"
$env:DB_PASSWORD = "your_password"
```

Once PostgreSQL is running and Flyway migrations have created the schema, use the Maven wrapper:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

The app is configured for port `8080`, migrations under `classpath:db/migration`, and `spring.jpa.hibernate.ddl-auto=validate`. Until the schema and game features are implemented, these commands do not make the app playable.

## Technology references

- [Spring Boot 4.1 system requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html)
- [Spring Boot WebSocket support](https://docs.spring.io/spring-boot/4.1/reference/messaging/websockets.html)
- [Springdoc OpenAPI compatibility](https://github.com/springdoc/springdoc-openapi#compatibility)
- [Flyway PostgreSQL support](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database)

---

## TODO BACKLOG START — draft for the Kanban board

Everything above this line is product and architecture context. The items below are the proposed implementation backlog; they are not yet Jira tickets and should be reviewed before implementation. Checked items are complete in the repository.

### Toolchain and dependencies — start here

- [ ] **Align the backend version and dependencies** — Use JDK 21 and align the Spring Boot Maven parent to 4.1.2. Keep the existing JPA, Flyway/PostgreSQL, validation, MVC, WebSocket, PostgreSQL driver, and Springdoc dependencies; add Spring Security for the protected admin area only. **Done when:** `.\mvnw.cmd -DskipTests package` succeeds with the documented versions.
- [x] **Set up the Thymeleaf frontend dependencies** — Add `spring-boot-starter-thymeleaf`; vendor Bootstrap 5.3.8 CSS/bundled JS and the `@stomp/stompjs` 7.5.0 browser UMD bundle as local static assets. No React, Vite, or Node.js build is required. **Done when:** Spring renders a Thymeleaf page and serves the local CSS/JavaScript assets from the packaged application.

### Scope and design decisions

- [ ] **Confirm course-scope exception** — Check whether the evaluator accepts anonymous game participants plus an authenticated admin, rather than registered `USER` accounts. **Done when:** the accepted authentication scope is recorded; otherwise revise the account model before building it.
- [ ] **Finalize room and game rules** — Set the minimum room size, allowed Mafia-count formula, game/phase statuses, guest-token lifetime/reconnect behavior, and whether hosts may delete or cancel games after start. Preserve the documented private-vote, tie, phase-advance, and win-condition rules. **Done when:** all start, phase, voting, and room-lifecycle rules are unambiguous.
- [ ] **Confirm admin access boundaries** — Decide how the admin account is provisioned and whether the admin dashboard is strictly read-only or can close/delete rooms. Keep roles, ballots, and guest tokens private in either case. **Done when:** admin login, permissions, and dashboard data are specified.

### Backend

- [ ] **Create PostgreSQL schema migrations** — Add Flyway migrations for `GAME`, `PLAYER`, `GAME_PHASE`, `VOTE`, `ELIMINATION`, `GAME_RESULT`, and the proposed admin account table. Add the documented uniqueness, lifecycle, and referential constraints. **Done when:** a clean PostgreSQL database migrates successfully and Hibernate validates the schema.
- [ ] **Implement domain entities and persistence** — Add JPA entities, enums, repositories, and DTO mappings for the approved schema. Keep guest players separate from the admin account. **Done when:** relationships and constraints are represented without exposing entities directly through the API.
- [ ] **Implement guest identity and room access** — Generate unique join codes and per-player secrets; persist only the session-token hash; create the host-player with a room; allow guests to join by link/code and nickname. **Done when:** host and guests can reconnect as the correct player, duplicate room nicknames are rejected, and the join code cannot grant host or private-role access.
- [ ] **Implement game lifecycle and rules** — Add lobby start, random role assignment, Night/Day transitions, mutable private ballots until phase close, tie handling, eliminations, and win detection. **Done when:** legal transitions work transactionally, invalid actions are rejected, and winner/result records are saved exactly once.
- [ ] **Implement REST API and error handling** — Add room CRUD, guest join/player views, host/game commands, DTO validation, correct HTTP status codes, domain exceptions, global error responses, and INFO/ERROR logging. **Done when:** endpoints enforce guest/host permissions and return no other player's role or ballot.
- [ ] **Implement admin authentication and dashboard API** — Protect admin endpoints with Spring Security; provision the admin account through a controlled setup path; add aggregate stats and active-room metadata endpoints. **Done when:** anonymous users can create/join rooms, only an admin can access dashboard data, and dashboard responses contain no roles, ballots, or session tokens.
- [ ] **Implement real-time room updates** — Configure Spring WebSocket/STOMP events for room membership, phase changes, eliminations, and results. **Done when:** connected clients receive current public room state without private roles or ballot contents leaking to other players.

### Frontend

- [ ] **Build guest and host screens** — Implement room creation/joining, lobby/roster, private role view, voting, phase status, host controls, and game results. **Done when:** a full game can be played from browser screens and each player sees only permitted data.
- [ ] **Build the admin dashboard UI** — Add protected admin sign-in and views for aggregate stats and active rooms. **Done when:** guests cannot open admin data and the dashboard never displays private player information.
- [ ] **Connect live updates to the UI** — Subscribe to room events, update the displayed game state, and recover the view after reconnecting. **Done when:** players see phase/roster/result changes without refreshing and private role data remains player-scoped.

### Verification and delivery

- [ ] **Add automated tests** — Cover game rules with unit tests; HTTP validation, permissions, and admin access with MockMvc; and migrations/persistence with PostgreSQL integration tests. **Done when:** tests cover the principal success and rejection paths, including role privacy and token boundaries.
- [ ] **Complete setup and demo documentation** — Document database setup, environment variables, admin provisioning, API overview, and how to run the Spring Boot application. Prepare the end-to-end demo. **Done when:** a teammate can start the app from a clean checkout and demonstrate room creation through a saved result and admin overview.
- [ ] **Check the project constraints** — Review the production Java logic against the 2,000-line limit and verify the agreed course requirements are met. **Done when:** the team can show the line-count basis, passing tests/builds, and any evaluator-approved scope exception.

### Recommended completion order and manual review gates

Complete the work in small, reviewable slices. Each slice should be a separate pull request with its relevant automated tests and manual check completed before merge.

1. **Toolchain and dependencies** — Align Spring Boot and verify the completed Thymeleaf/Bootstrap setup. Manually verify the app builds, renders a page, and serves Swagger UI.
2. **Scope decisions** — Confirm the course authentication exception, game rules/limits, guest reconnect behavior, and admin permissions before finalizing the schema.
3. **Database foundation** — Add migrations and entities. Verify that a clean PostgreSQL database migrates and Hibernate validates the schema.
4. **Room slice** — Implement room creation/joining and host/guest identity together with the lobby pages. Check creation and joining in two browsers, including refresh/reconnect; use Swagger to inspect REST behavior.
5. **Gameplay slice** — Implement roles, phases, voting, eliminations, and win detection, then connect the game screens. Manually test a complete game, a tie, a win, and that each player sees only their own role.
6. **Admin slice** — Add the protected admin API and dashboard. Verify guests are denied access and the admin sees stats/active rooms without private game data.
7. **Live updates** — Add STOMP after the REST/game flow works. Check that connected browsers receive joins, phase changes, and results, then verify refresh/reconnect behavior.
8. **Delivery** — Run the complete test suite, verify the clean-checkout setup and demo, and check the line-count and course requirements.

Run relevant automated tests throughout development, not only in the final delivery step.
