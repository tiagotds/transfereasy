# transfereasy

[![Java CI with Gradle](https://github.com/tiagotds/transfereasy/actions/workflows/gradle.yml/badge.svg?branch=master)](https://github.com/tiagotds/transfereasy/actions/workflows/gradle.yml)

A small banking REST API: customers, accounts, deposits, withdrawals, transfers and statements. It is built on plain Java 21, with no framework, and its design centres on **correctness under concurrency**.

v3 refactors v2 into a DDD-shaped, configuration-driven codebase on Gradle, delivered test-first, one commit per deliverable (`git log --reverse`). The plan and the reasoning behind it are in [`docs/IMPROVE.md`](docs/IMPROVE.md).

## Stack

- **Java 21**: records, sealed hierarchies, pattern-matching `switch`, virtual threads.
- **No Spring, no DI container, no servlet container.** HTTP runs on the JDK's `com.sun.net.httpserver`, one virtual thread per request, and the wiring is done by hand (`Core`, `Application`).
- **H2** in-memory database and **jOOQ**. jOOQ classes are generated at build time from `src/main/resources/db/schema.sql`, the same file executed at startup.
- **Jackson** for strict JSON.
- **Gradle** (Groovy DSL, version catalog, wrapper). **JUnit 5**, plus a JaCoCo gate across both test suites (95% line / 85% branch).

## Design

### Layers (dependencies point inwards)

```
domain/            pure Java: no jOOQ, no Jackson, no HTTP
  model/           Money, AccountNumber, TaxNumber, CustomerName  (self-validating value objects)
                   Account aggregate -> Movement(next state, Posting); Transfer domain service
  port/            AccountRepository, CustomerRepository, LedgerRepository, IdempotencyStore
  error/           sealed DomainException: InvalidInput, NotFound, Conflict, InsufficientFunds,
                   ConcurrentModification (Retryable), IdempotencyKeyReused
application/
  command/         sealed Command<R> + CommandHandler<C, R>
  handler/         MoneyMovementHandler -> SingleAccountMovementHandler (Template Method) -> Deposit/Withdraw
                   TransferHandler, CreateCustomerHandler, OpenAccountHandler
  pipeline/        CommandBus + Middleware chain:  Retry -> Transaction -> Idempotency -> handler
  query/           AccountQueries, CustomerQueries (read-only snapshot per query)
infrastructure/
  config/          Config (properties < env < system props) -> typed *Settings records
  persistence/     TransactionRunner, JooqRepository<R, D> base + adapters, LockingStrategy
  http/            HttpApplication (+ Bulkhead), Router, ApiRoutes, CommandRoute, ErrorMapper, Json, Dtos
Core / Application composition roots
```

Every write is a `Command` sent through the bus. Each endpoint is one line in `ApiRoutes`, built either from a query or from the generic `CommandRoute.of(bus, BodyType, toCommand, toResponse)`.

### Patterns and abstractions

| Where | What | Why |
|---|---|---|
| `CommandBus` / `Middleware` | Chain of Responsibility | Retry, transactions and idempotency are cross-cutting; handlers contain none of it |
| `SingleAccountMovementHandler` | Template Method | Deposit and withdraw share every step but the movement itself |
| `LockingStrategy` | Strategy (enum) | Pessimistic and optimistic locking are interchangeable via config |
| `JooqRepository<R, D>` | Generic base class | Table binding, insert, find and mapping are written once |
| `CommandRoute`, `Command<R>`, `CommandHandler<C, R>` | Generics | Typed results end to end, with no casts in callers |
| `DomainException`, `Command` | Sealed hierarchies | Exhaustive `switch` in `ErrorMapper`, so a new error cannot go unmapped |
| `MovementDependencies`, `Settings` | Parameter objects | No long constructor lists repeated across classes |

### Money

- `Money` is non-negative and always has scale 2, so `10` and `10.00` are equal. It is stored as `NUMERIC(19,2)`.
- `AmountPolicy` validates movement amounts: positive, at most 2 decimal places, and at most `money.max-amount`.

## Concurrency model

| Hazard | Mechanism |
|---|---|
| Two withdrawals spend the same money | The aggregate checks funds on a state read inside the transaction, and `save()` is a **compare-and-set on `version`**. `CHECK (balance >= 0)` in the schema is the last line of defence. |
| Lost update | Same compare-and-set: a write based on a stale read fails with `ConcurrentModification`. |
| Deadlock between `A→B` and `B→A` | Both strategies load and write accounts in **ascending id order**, so row locks follow one global order. |
| Contention policy | `account.locking=PESSIMISTIC` (`SELECT … FOR UPDATE`: competitors queue and never conflict) or `OPTIMISTIC` (plain read: no waiting, and losers are retried). |
| Transient failures | `RetryMiddleware` sits **outside** the transaction, so each attempt is a fresh transaction. It retries `Retryable` errors and SQLSTATE `40001` / `HYT00` with exponential backoff and full jitter, and returns 409 `CONCURRENT_MODIFICATION` once attempts run out. |
| A client retries a POST, possibly many times at once | `Idempotency-Key` header. The key is **claimed in the same transaction** as the operation, before it runs, and its primary key decides which duplicate wins. Losers retry, then replay the committed result with `Idempotent-Replayed: true`. A different body under the same key returns 422 `IDEMPOTENCY_KEY_REUSED`. A failed operation releases the key. |
| A burst queues behind the DB pool | A **bulkhead** (fair semaphore, `http.max-in-flight`) lets the excess wait up to `http.acquire-timeout`, then returns 503 `OVERLOADED` with `Retry-After`. `/health` is exempt. |
| Duplicate customers created concurrently | `UNIQUE(tax_number)` is the arbiter: exactly one winner, and the rest get 409. |

Invariant asserted by every race test: **sum of all balances = sum of all ledger entries**.

## Configuration

Every default lives in [`src/main/resources/application.properties`](src/main/resources/application.properties). Each key can be overridden by an environment variable in UPPER_SNAKE_CASE (`db.pool-size` → `DB_POOL_SIZE`) or by a system property (`-Ddb.pool-size=8`). Precedence is system property > environment > file. Invalid values fail at startup, naming the key.

| Key | Default | Meaning |
|---|---|---|
| `http.port` | 8080 | `0` picks an ephemeral port |
| `http.max-body-bytes` | 65536 | Larger bodies get 413 |
| `http.max-in-flight` / `http.acquire-timeout` / `http.retry-after` | 64 / 200ms / 1s | Bulkhead |
| `db.pool-size` / `db.lock-timeout` | 32 / 10s | H2 pool and row-lock wait |
| `account.locking` | PESSIMISTIC | or `OPTIMISTIC` |
| `retry.max-attempts` / `retry.initial-backoff` / `retry.max-backoff` | 10 / 2ms / 100ms | Retry of lost races |
| `statement.default-size` / `statement.max-size` | 100 / 1000 | Statement `limit` |
| `money.max-amount` | 1000000000000.00 | Largest single movement |

Limits tied to the schema stay as constants next to the value objects: money scale 2 and the column widths 32/120. Making them configurable could contradict the DDL.

## API

All bodies are JSON. Creations and money movements return `201`, and reads return `200`. Every `POST` accepts an optional `Idempotency-Key` header (at most 64 characters).

| Method | Path | Body | Notes |
|---|---|---|---|
| GET | `/health` | | `{"status":"UP"}`; bypasses the bulkhead |
| POST | `/api/customers` | `{"taxNumber","name"}` | 409 if the tax number exists |
| GET | `/api/customers[?name=frag]` | | case-insensitive name search |
| GET | `/api/customers/{taxNumber}` | | |
| GET | `/api/customers/{taxNumber}/accounts` | | customer and their accounts |
| POST | `/api/accounts` | `{"taxNumber"}` | opens an account with balance 0 |
| GET | `/api/accounts/{number}` | | |
| POST | `/api/accounts/{number}/deposits` | `{"amount"}` | |
| POST | `/api/accounts/{number}/withdrawals` | `{"amount"}` | 422 if funds are insufficient |
| POST | `/api/accounts/{number}/transfers` | `{"toAccountNumber","amount"}` | atomic; returns both accounts and a `transferId` |
| GET | `/api/accounts/{number}/statement[?limit=n]` | | newest first |

### Errors

Errors look like `{"code":"INSUFFICIENT_FUNDS","message":"Insufficient funds for this operation."}`.

| Situation | HTTP | `code` |
|---|---|---|
| Malformed JSON, unknown field, invalid amount or parameter, key too long | 400 | `INVALID_REQUEST` |
| Unknown customer, account or route | 404 | `NOT_FOUND` |
| Wrong method (with an `Allow` header) | 405 | `METHOD_NOT_ALLOWED` |
| Duplicate tax number | 409 | `CONFLICT` |
| Concurrent conflict that outlived the retries | 409 | `CONCURRENT_MODIFICATION` |
| Body too large | 413 | `PAYLOAD_TOO_LARGE` |
| Insufficient funds | 422 | `INSUFFICIENT_FUNDS` |
| Idempotency key reused with a different body | 422 | `IDEMPOTENCY_KEY_REUSED` |
| Anything unexpected, including `Error`s | 500 | `INTERNAL_ERROR` (generic message; details only in the log) |
| Bulkhead full (with `Retry-After`) | 503 | `OVERLOADED` |

## Running

Requires JDK 21; the Gradle wrapper downloads Gradle itself.

```bash
./gradlew build          # compile, jOOQ codegen, unit + integration tests, coverage gate
./gradlew run            # API on http://localhost:8080
ACCOUNT_LOCKING=OPTIMISTIC ./gradlew run
```

Coverage report: `build/reports/jacoco/test/html/index.html`.

### With Docker

The image build runs the full Gradle build, so a failing test never produces an image.

```bash
docker compose up --build                                        # API on http://localhost:8080
docker compose --profile test up --build --exit-code-from smoke-test
docker compose --profile test down -v
```

`scripts/smoke-test.sh` runs black-box checks with nothing but curl. It covers the full contract, 20 parallel withdrawals, 20 parallel retries of one transfer under one `Idempotency-Key` (applied once), and load shedding against a second container (`app-tight`) configured with a 1-slot bulkhead.

### Try it

```bash
B=http://localhost:8080; j='Content-Type: application/json'
curl -s -X POST $B/api/customers -H "$j" -d '{"taxNumber":"111","name":"Ada Lovelace"}'
A=$(curl -s -X POST $B/api/accounts -H "$j" -d '{"taxNumber":"111"}' | jq -r .number)
curl -s -X POST $B/api/accounts/$A/deposits -H "$j" -d '{"amount":100}'
# retried withdrawal: applied once, second answer carries "Idempotent-Replayed: true"
curl -si -X POST $B/api/accounts/$A/withdrawals -H "$j" -H 'Idempotency-Key: w-1' -d '{"amount":30}'
curl -si -X POST $B/api/accounts/$A/withdrawals -H "$j" -H 'Idempotency-Key: w-1' -d '{"amount":30}'
curl -s $B/api/accounts/$A/statement | jq .
```

## Test suites

| Suite | Where | What it proves |
|---|---|---|
| Domain | `test/.../domain` | Value objects, the aggregate, `Transfer`: pure unit tests, no database |
| Ports & adapters | `test/.../infrastructure/persistence` | Repository contracts on real H2, the version compare-and-set, pessimistic vs optimistic behaviour, transaction safety on a 1-connection pool |
| Pipeline | `test/.../application/pipeline` | Bus routing and ordering; retry (deterministic fake sleeper and jitter); retry outside the transaction; idempotency |
| Scenarios | `test/.../application/scenarios` | Business rules through the real core; **42 races** (7 scenarios × 2 strategies × 3 runs); idempotency storms |
| HTTP | `test/.../infrastructure/http` | Routing, strict JSON, error mapping, bulkhead (latches), 503 with health exempt |
| Integration | `integrationTest/` | The real application over HTTP: the full contract, plus 40-client races, opposite transfers, an idempotent retry storm and overload, for both strategies |
| Smoke | `scripts/smoke-test.sh` | Black-box checks against the Docker containers |

## Trade-offs and limitations

- **In-memory only**: a restart loses all data, by design for this exercise. Correctness relies on database locking and constraints, so a shared database would support scaling out.
- **Idempotency keys never expire.** A production system would add a TTL and a cleanup job.
- **No authentication or authorisation.**
- **Single currency.**
- **Statements are capped** (`limit`); there is no cursor pagination.
