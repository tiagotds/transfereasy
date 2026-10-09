# IMPROVE — transfereasy v3 refactoring plan

## Context

transfereasy v2 is a framework-free Java 21 REST API for customers, accounts, deposits, withdrawals, transfers and statements. It uses the JDK `HttpServer` with virtual threads, H2 in-memory, jOOQ generated from `schema.sql`, Jackson, JUnit 5, a JaCoCo gate and Maven, plus Docker and a curl smoke test. It is correct, but:
- the services are transaction scripts. Business rules live in SQL (`debitIfSufficient`) and in `AccountService`, and the domain records are anaemic;
- deposit, withdraw and transfer repeat the same find/move/append-ledger skeleton;
- static values are hard-coded constants: `MAX_BODY_BYTES`, statement limits, `Amounts.MAX`, `LOCK_TIMEOUT`, pool size;
- the concurrency design has one fixed mechanism. It has no idempotency (the README admits a retried POST is applied twice), no retry and no backpressure.

**Goal:** a simpler, self-explanatory, DDD-shaped codebase on Gradle, built test-first, with concurrency as the headline: pluggable locking, retry, idempotency keys and a bulkhead.

**Decisions from the interview:**
- Stay framework-free.
- No WireMock: there are no outbound calls. Integration tests run over real HTTP with the JDK `HttpClient` in a dedicated Gradle test suite.
- Add all three concurrency features.
- Keep Docker, built with Gradle.

## Target architecture

The codebase is layered by DDD role, and dependencies point inwards.

```
domain/          pure Java, no jOOQ/Jackson
  model/         Money, AccountNumber, TaxNumber, CustomerName (validated value objects)
                 Account (aggregate: deposit/withdraw -> new state + LedgerEntry, carries version)
                 Customer, LedgerEntry, EntryKind, Transfer (domain service: debit+credit pair)
  port/          AccountRepository, CustomerRepository, LedgerRepository, IdempotencyStore
  error/         sealed DomainException -> InvalidInput, NotFound, Conflict,
                 InsufficientFunds, ConcurrentModification   (replaces the Code enum)
application/
  command/       sealed Command<R> (CreateCustomer, OpenAccount, Deposit, Withdraw, Transfer)
                 CommandHandler<C extends Command<R>, R>; SingleAccountMovementHandler (Template Method
                 shared by Deposit/Withdraw); TransferHandler
  pipeline/      CommandBus = Chain of Responsibility of generic Middleware:
                 Retry -> Transaction -> Idempotency -> Handler
  query/         AccountQueries, CustomerQueries (read-only snapshot tx)
infrastructure/
  config/        Config (application.properties + system props + env overlay), typed *Settings records
  persistence/   Database, TransactionRunner (kept, already robust), JooqRepository<R,D> base,
                 jOOQ adapters, LockingStrategy {PESSIMISTIC, OPTIMISTIC}
  http/          HttpServerAdapter, Router, Routes (generic JsonRoute<Req,Res>), ErrorMapper
                 (pattern-switch on exception type), Bulkhead filter, Json, Dtos
Application      composition root (hand wiring, chosen strategy from config)
```

### Concurrency design (headline)

1. **Domain decides, the database arbitrates.** `Account.withdraw(Money)` throws `InsufficientFunds`. The save is `UPDATE accounts SET balance=?, version=version+1 WHERE id=? AND version=?`, and 0 rows means `ConcurrentModification`. `CHECK (balance >= 0)` stays as the last line of defence.
2. **Pluggable `LockingStrategy`** (Strategy pattern, property `account.locking`):
   - `PESSIMISTIC` (default): `SELECT … FOR UPDATE` ordered by id. Deterministic, and the version check never fails.
   - `OPTIMISTIC`: plain read and a version-checked write. Transfers write in id order to avoid update deadlocks.
   - Both strategies share the same load→decide→save path. Only how the aggregate is loaded differs.
3. **Retry middleware.** Exponential backoff with full jitter. Attempts, initial delay and max delay come from properties. It retries only `TransientFailure` (a marker interface): optimistic conflicts, H2 deadlock (40001) and lock timeout (50200), and idempotency races. Each attempt is a fresh transaction, because Retry sits *outside* Transaction. When attempts run out, the API returns 409 `CONCURRENT_MODIFICATION`. A `Sleeper` is injected for deterministic tests.
4. **Idempotency-Key** (optional header on every POST). The middleware runs *inside* the movement's transaction:
   - look up the key; if it is found and the fingerprint matches, replay the stored result with header `Idempotent-Replayed: true`;
   - if the fingerprint differs, return 409 `IDEMPOTENCY_KEY_REUSED`;
   - otherwise run the handler and insert the key with the serialised result at the end;
   - a concurrent duplicate hits the PK violation and rolls back its movement, then is retried (`TransientFailure`) and replays.
   - Result: exactly-once under races.
   - New table `idempotency_keys(key PK, fingerprint, result_json, created_at)`. The fingerprint is SHA-256 of the command type plus its canonical JSON.
5. **Bulkhead / backpressure** at the HTTP edge (`/health` exempt). It is a `Semaphore(http.max-in-flight)` with `tryAcquire(http.acquire-timeout)`. When no permit is free in time, it returns 503 `OVERLOADED` with `Retry-After`, so requests don't pile up on the DB pool.

### Configuration

- `src/main/resources/application.properties` holds the defaults. It is overridden by `-Dkey=value` system properties and by environment variables (`HTTP_PORT` ↔ `http.port`).
- `Config.get(key, Function<String,T>)` is generic. It fails fast with the key name, and its output is bound into records: `HttpSettings`, `DatabaseSettings`, `RetrySettings`, `StatementSettings`, `MoneySettings`, `LockingSettings`.
- Invariants tied to the schema stay as constants in the value objects: money scale 2 (`NUMERIC(19,2)`) and column widths (`VARCHAR(32/120)`). Making them configurable could break the DDL. IMPROVE.md documents this.

## Delivery: one commit per deliverable, test-first

- Work happens on a new branch, `refactor/v3-gradle-ddd-concurrency`.
- Each commit contains its tests and the implementation, and builds green: the tests were written first, and the commit message describes red→green.
- Commit messages explain what is delivered and why.

1. **docs: add refactoring plan.** `docs/IMPROVE.md` (this plan, expanded).
2. **build: migrate Maven to Gradle.**
   - Groovy DSL: `settings.gradle`, `build.gradle`, `gradle/libs.versions.toml`, wrapper (Gradle 9.x), Java 21 toolchain.
   - Plugins: `org.jooq.jooq-codegen-gradle` with DDLDatabase on `schema.sql`, `application`, JaCoCo with the 95% line / 85% branch gate on `check`.
   - JVM Test Suite `integrationTest`, with `ApiTest` moved into it.
   - Dockerfile build stage on Gradle. Delete `pom.xml`.
   - Existing tests pass unchanged.
3. **config: properties-driven configuration.** `ConfigTest` first, covering defaults, the env/system override order, and invalid values naming the key. Replaces `AppConfig` and the hard-coded constants.
4. **domain: value objects and a sealed error hierarchy.** `Money`, `AccountNumber`, `TaxNumber`, `CustomerName`. Replaces `Amounts` and `DomainException.Code`.
5. **domain: rich `Account` aggregate and `Transfer` domain service.** Pure unit tests with no DB: invariants, ledger entries produced, version bump.
6. **persistence: ports and adapters.**
   - A generic `JooqRepository<R extends Record, D>` base (shared find/map/insert).
   - Adapters implement the domain ports.
   - Schema gains `accounts.version` and `idempotency_keys`.
   - Tests against H2.
7. **application: CommandBus, handlers and queries.**
   - The generic middleware pipeline, with Transaction middleware first.
   - `SingleAccountMovementHandler` as the template for deposit and withdraw.
   - Removes `AccountService` and `CustomerService`; their tests are migrated to handler tests.
8. **http: simplify the HTTP layer.**
   - Generic `JsonRoute<Req,Res>` removes the per-route body/parse boilerplate in `ApiRoutes`.
   - `ErrorMapper` switches over the sealed exceptions.
   - Header access (`Idempotency-Key`).
9. **concurrency: retry with backoff and jitter.**
    - Unit tests with a fake `Sleeper`: attempts, delays, only transient failures retried, exhaustion → 409.
    - Integration test with a real transaction: failed attempts roll back, only the successful one commits.
10. **concurrency: pluggable `LockingStrategy`.** `ConcurrencyTest` becomes `@ParameterizedTest @EnumSource(LockingStrategy)`, with the same invariants for both strategies: no overdraft, conservation of money, ledger = balances, no deadlock.
    - Delivered after retry (order swapped while implementing): OPTIMISTIC relies on retry to absorb conflicts.
11. **concurrency: Idempotency-Key.**
    - Tests: replay, fingerprint mismatch, and 32 threads with the same key → exactly one movement and identical responses, for both strategies.
12. **concurrency: bulkhead.**
    - `BulkheadTest` with latches: permits honoured, timeout → `Overloaded`, permit released on exception.
    - HTTP test → 503 with `Retry-After`.
13. **test: end-to-end integration suite.**
    - `integrationTest` runs over real HTTP for both strategies: the full API contract, a parallel-withdrawal race, opposite transfers, an idempotent retry storm, and overload → 503.
14. **docs: README, smoke test and compose.** Updated for Gradle and the new config keys. The smoke test adds idempotency replay and 503 checks.

## Reused as-is or with light changes

- `db/TransactionRunner.java`, which already handles Throwable, rollback and restore. It gains a typed exception translation hook for `TransientFailure`.
- `db/Database.java`, with the pool size and lock timeout now coming from config.
- `http/Router.java`, `http/Json.java`, and the test `support/TestEnvironment.java` and `Money`, adapted to the new wiring.
- The `race(...)` latch helper in `ConcurrencyTest`, extracted to `support/Race.java` and shared by the unit and integration suites.

## Verification

- `./gradlew clean build` must pass: compile, jOOQ codegen, `test`, `integrationTest`, and the JaCoCo gate.
- Concurrency suites use `@RepeatedTest` to flush out flakiness.
- `docker compose --profile test up --build --exit-code-from smoke-test` must pass. If Docker is not available locally, I will say so.
- SonarQube check per the global instructions, if the project is reachable on the Sonar MCP or plugin. Otherwise I will report that it was not reachable rather than skip it silently.
- `git log --oneline` should show the 14 deliverable commits in order.
