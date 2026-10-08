# transfereasy 2

A small banking REST API: customers, accounts, deposits, withdrawals, transfers and statements.

This is a ground-up rewrite of my original take-home exercise. The reviewers' feedback on the first version was:

| Feedback | What this version does about it |
|---|---|
| **No concurrent tests** | `ConcurrencyTest` races 32 threads on shared accounts (withdrawals, transfers, opposite-direction transfers, random storms). The Docker smoke test also fires 20 parallel HTTP withdrawals. |
| **Confusing tests** | Tests are named as sentences describing behaviour (`withdrawing_more_than_the_balance_is_refused_and_changes_nothing`), grouped with `@Nested`, and share one small fixture (`TestEnvironment`). Every test uses its own isolated database. |
| **TX leak on `Throwable`** | One class, `TransactionRunner`, owns begin/commit/rollback/close. It catches `Throwable`, always rolls back and always returns the connection. `TransactionRunnerTest` runs on a **single-connection pool**, so any leak would hang the next call. |
| **Balances checked outside transactions → negative balances** | The check and the debit are one atomic SQL statement (`UPDATE ... WHERE balance >= :amount`), and the schema has `CHECK (balance >= 0)` as a last line of defence. |

## Stack

- **Java 21** (records, sealed interfaces, pattern-matching `switch`, virtual threads)
- **No Spring, no DI container, no servlet container.** HTTP is the JDK's built-in `com.sun.net.httpserver`, one virtual thread per request. Wiring is done by hand in `Application`.
- **H2** in-memory database (embedded) and **jOOQ** for all SQL. jOOQ classes are *generated at build time* from `src/main/resources/db/schema.sql`, which is also the file executed at startup, so there is a single source of truth for the schema.
- **Jackson** for JSON (the JDK has no JSON parser), configured strictly.
- **JUnit 5** + **JaCoCo** (the build fails below 95% line / 85% branch coverage).

## Design

### Layers

```
http/        HttpApplication (server, error mapping), Router, ApiRoutes (contract), Json, Dtos
service/     CustomerService, AccountService   <- business rules, one transaction per operation
repository/  CustomerRepository, AccountRepository, LedgerRepository   <- jOOQ queries only
db/          Database (H2 + pool), TransactionRunner
domain/      records (Customer, Account, LedgerEntry...), Amounts, DomainException
```

Dependencies only point downwards. Repositories receive a `DSLContext` that is bound to the current transaction, so they can never open, commit or leak one.

### Money

- Amounts are `BigDecimal` with scale 2 (`NUMERIC(19,2)` in the database). Never `double`.
- Input is validated: positive, at most 2 decimal places (`5.100` is accepted, `1.001` is not), at most 1 trillion.
- JSON numbers are parsed as exact decimals and written as plain numbers.

### Concurrency model

Three mechanisms, each covering a specific hazard:

1. **Atomic conditional debit.** `debitIfSufficient` is a single `UPDATE accounts SET balance = balance - ? WHERE id = ? AND balance >= ?`. If no row is updated, funds are insufficient. There is no read-then-write gap, so two concurrent withdrawals can never both spend the same money.
2. **Ordered row locks for transfers.** A transfer runs `SELECT ... FOR UPDATE` on both accounts **ordered by id**. Every transfer acquires locks in the same global order, so `A→B` and `B→A` running at once cannot deadlock.
3. **Database constraints as the final arbiter.** `UNIQUE(tax_number)` decides concurrent customer creation (exactly one winner, the rest get 409), and `CHECK (balance >= 0)` makes a negative balance physically impossible even if the code were wrong.

### Ledger

Every movement appends an immutable `ledger_entries` row in the same transaction as the balance change, with a signed amount and the resulting `balance_after`. A transfer writes two entries sharing a `transferId`. Invariant, asserted by the concurrency tests: **sum of all balances = sum of all ledger entries**.

Statements are read in a repeatable-read, read-only transaction, so the account and its entries come from one consistent snapshot. They are returned newest first and capped (`limit`, default 100, max 1000).

### Transactions and failures

`TransactionRunner.inTransaction(work)`:

- begins, runs the work, commits;
- on **any `Throwable`** (including `Error`) rolls back, then rethrows the original failure (a rollback failure is attached as *suppressed*, never masking the cause);
- always restores connection defaults and returns it to the pool.

Services contain no transaction code at all.

### Error mapping

| Situation | HTTP | `code` |
|---|---|---|
| Malformed JSON, unknown field, wrong type, invalid amount, bad parameter | 400 | `INVALID_REQUEST` |
| Customer / account not found, unknown route | 404 | `NOT_FOUND` |
| Wrong HTTP method (adds an `Allow` header) | 405 | `METHOD_NOT_ALLOWED` |
| Duplicate customer tax number | 409 | `CONFLICT` |
| Body larger than 64 KB | 413 | `PAYLOAD_TOO_LARGE` |
| Insufficient funds | 422 | `INSUFFICIENT_FUNDS` |
| Anything unexpected (including `Error`s) | 500 | `INTERNAL_ERROR` (generic message, details only in the log) |

Errors look like `{"code":"INSUFFICIENT_FUNDS","message":"Insufficient funds for this operation."}`.

## API

All bodies are JSON. Successful creations and money movements return `201`; reads return `200`.

| Method | Path | Body | Notes |
|---|---|---|---|
| GET | `/health` | | `{"status":"UP"}` |
| POST | `/api/customers` | `{"taxNumber","name"}` | 409 if the tax number exists |
| GET | `/api/customers[?name=frag]` | | case-insensitive name search |
| GET | `/api/customers/{taxNumber}` | | |
| GET | `/api/customers/{taxNumber}/accounts` | | customer + their accounts |
| POST | `/api/accounts` | `{"taxNumber"}` | opens an account with balance 0 |
| GET | `/api/accounts/{number}` | | |
| POST | `/api/accounts/{number}/deposits` | `{"amount"}` | |
| POST | `/api/accounts/{number}/withdrawals` | `{"amount"}` | 422 if funds are insufficient |
| POST | `/api/accounts/{number}/transfers` | `{"toAccountNumber","amount"}` | atomic; returns both resulting accounts and a `transferId` |
| GET | `/api/accounts/{number}/statement[?limit=n]` | | newest first |

Note the API changed from v1 (a single polymorphic `POST /accounts/{n}` with a `type` field, and the `ammount` typo) to explicit, resource-style endpoints.

## Running

### With Docker (recommended)

```bash
docker compose up --build            # API on http://localhost:8080
```

The image build runs the full test suite and the coverage gate, so a broken build never produces an image.

Automated acceptance tests against the running container (exit code reflects the result):

```bash
docker compose --profile test up --build --exit-code-from smoke-test
docker compose --profile test down -v
```

### Without Docker

Requires JDK 21 and Maven.

```bash
mvn verify                       # tests + coverage gate; report in target/site/jacoco/index.html
java -jar target/transfereasy.jar
```

Configuration (environment variables): `PORT` (default 8080), `DB_POOL_SIZE` (default 32). Data is in memory only and is lost when the process stops.

## Manual test cases (against the app in Docker)

Start the app (`docker compose up --build -d`) and run these in order. `jq` is optional but handy.

```bash
B=http://localhost:8080
j='Content-Type: application/json'
```

**1. Create customers**
```bash
curl -i -X POST $B/api/customers -H "$j" -d '{"taxNumber":"111","name":"Ada Lovelace"}'   # 201
curl -i -X POST $B/api/customers -H "$j" -d '{"taxNumber":"222","name":"Alan Turing"}'    # 201
```

**2. Duplicate customer → 409**
```bash
curl -i -X POST $B/api/customers -H "$j" -d '{"taxNumber":"111","name":"Someone else"}'
```

**3. Open accounts and keep the numbers**
```bash
A=$(curl -s -X POST $B/api/accounts -H "$j" -d '{"taxNumber":"111"}' | jq -r .number)
C=$(curl -s -X POST $B/api/accounts -H "$j" -d '{"taxNumber":"222"}' | jq -r .number)
```

**4. Deposit → balance 100.00**
```bash
curl -s -X POST $B/api/accounts/$A/deposits -H "$j" -d '{"amount":100}'
```

**5. Invalid amounts → 400**
```bash
curl -i -X POST $B/api/accounts/$A/deposits -H "$j" -d '{"amount":-1}'
curl -i -X POST $B/api/accounts/$A/deposits -H "$j" -d '{"amount":1.001}'
curl -i -X POST $B/api/accounts/$A/deposits -H "$j" -d '{"amount":"10"}'
curl -i -X POST $B/api/accounts/$A/deposits -H "$j" -d '{"amount":5,"typo":1}'
```

**6. Withdraw, then overdraft**
```bash
curl -s -X POST $B/api/accounts/$A/withdrawals -H "$j" -d '{"amount":30.50}'   # balance 69.50
curl -i -X POST $B/api/accounts/$A/withdrawals -H "$j" -d '{"amount":1000}'    # 422, balance unchanged
```

**7. Transfer**
```bash
curl -s -X POST $B/api/accounts/$A/transfers -H "$j" -d "{\"toAccountNumber\":\"$C\",\"amount\":19.50}"
curl -s $B/api/accounts/$A | jq .balance    # 50.00
curl -s $B/api/accounts/$C | jq .balance    # 19.50
```

**8. Transfer failures** (all leave both balances untouched)
```bash
curl -i -X POST $B/api/accounts/$A/transfers -H "$j" -d "{\"toAccountNumber\":\"$A\",\"amount\":1}"      # 400 same account
curl -i -X POST $B/api/accounts/$A/transfers -H "$j" -d '{"toAccountNumber":"ghost","amount":1}'        # 404
curl -i -X POST $B/api/accounts/$A/transfers -H "$j" -d "{\"toAccountNumber\":\"$C\",\"amount\":9999}"  # 422
```

**9. Statement** (newest first, `balanceAfter` on every line, transfer lines share a `transferId`)
```bash
curl -s "$B/api/accounts/$A/statement" | jq .
curl -s "$B/api/accounts/$A/statement?limit=1" | jq '.entries | length'   # 1
curl -i "$B/api/accounts/$A/statement?limit=0"                            # 400
```

**10. Concurrent withdrawals must never overdraw.** Fund an account with 100, then fire 20 parallel withdrawals of 30. Exactly 3 must return 201, 17 must return 422, and the final balance must be 10.00.
```bash
D=$(curl -s -X POST $B/api/accounts -H "$j" -d '{"taxNumber":"111"}' | jq -r .number)
curl -s -X POST $B/api/accounts/$D/deposits -H "$j" -d '{"amount":100}' >/dev/null
for i in $(seq 20); do
  curl -s -o /dev/null -w '%{http_code}\n' -X POST $B/api/accounts/$D/withdrawals -H "$j" -d '{"amount":30}' &
done | sort | uniq -c ; wait
curl -s $B/api/accounts/$D | jq .balance    # 10.00
```

**11. Concurrent opposite transfers must not deadlock.** Run `A→C` and `C→A` loops simultaneously; both finish and `balance(A)+balance(C)` is unchanged.
```bash
total() { echo "$(curl -s $B/api/accounts/$A | jq .balance) + $(curl -s $B/api/accounts/$C | jq .balance)" | bc; }
T0=$(total)
for i in $(seq 25); do
  curl -s -o /dev/null -X POST $B/api/accounts/$A/transfers -H "$j" -d "{\"toAccountNumber\":\"$C\",\"amount\":0.01}" &
  curl -s -o /dev/null -X POST $B/api/accounts/$C/transfers -H "$j" -d "{\"toAccountNumber\":\"$A\",\"amount\":0.01}" &
done; wait
echo "before=$T0 after=$(total)"
```

**12. Routing**
```bash
curl -i -X DELETE $B/api/customers     # 405 with "Allow: GET, POST"
curl -i $B/api/nope                    # 404
curl -i $B/api/accounts/ghost          # 404
```

The same scenarios (and more) are automated in `scripts/smoke-test.sh`, which `docker compose --profile test` runs.

## Test suite

| Suite | What it proves |
|---|---|
| `service/AccountServiceTest`, `CustomerServiceTest` | Business rules and edge cases, including that failures leave **no** trace (balances and ledger unchanged). |
| `service/ConcurrencyTest` | Under 32-thread races: no overdraft, no lost updates, no deadlock, money conserved, ledger equals balances, exactly one winner for duplicate creation. Repeated runs to flush out flakiness. |
| `db/TransactionRunnerTest` | Rollback and connection release after `RuntimeException`, `Error` and constraint violations, on a pool of size 1 (a leak would hang). |
| `db/TransactionRunnerFailureTest` | Every JDBC failure mode (begin / commit / rollback / close / restore) via a scripted connection. |
| `http/ApiTest` | End-to-end over real HTTP: all endpoints, status mapping, payload validation, 405/413, concurrent HTTP withdrawals. |
| `http/RouterTest`, `JsonTest`, `HttpApplicationInternalsTest` | Routing, strict JSON, query parsing, and that unexpected errors (even `StackOverflowError`) become a generic 500 without leaking details. |
| `domain/*`, `config/*`, `ApplicationLifecycleTest` | Amount validation, configuration, startup failure cleanup. |
| `scripts/smoke-test.sh` (Docker) | Black-box acceptance checks against the real container, including a parallel-withdrawal race. |

`mvn verify` currently reports roughly 98% line and 94% branch coverage (generated jOOQ code, `Main` and the container `HealthCheck` excluded).

## Trade-offs and limitations

- **In-memory only.** Restarting loses all data (by design for this exercise).
- **Single node.** Correctness comes from database locking; scaling out would need a shared database, which the same SQL would support.
- **No authentication/authorization**, and no idempotency keys: a retried `POST` is applied twice. A production system would add both.
- **Single currency.**
- **The ledger is capped on read** (`limit`); there is no cursor pagination.
