#!/bin/sh
# Black-box acceptance tests for the running API. Needs only POSIX sh + curl.
# Usage: BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh
BASE_URL="${BASE_URL:-http://localhost:8080}"
TIGHT_URL="${TIGHT_URL:-}"   # optional: an instance with HTTP_MAX_IN_FLIGHT=1, HTTP_ACQUIRE_TIMEOUT=0ms
PASS=0
FAIL=0
RUN="$(date +%s)$$"

# call METHOD PATH [BODY] [IDEMPOTENCY_KEY]  -> sets STATUS, BODY and REPLAYED
call() {
  HDRS=$(mktemp)
  if [ -n "$4" ]; then
    RESP=$(curl -s -D "$HDRS" -w '\n%{http_code}' -X "$1" -H 'Content-Type: application/json' \
      -H "Idempotency-Key: $4" -d "$3" "$BASE_URL$2")
  elif [ -n "$3" ]; then
    RESP=$(curl -s -D "$HDRS" -w '\n%{http_code}' -X "$1" -H 'Content-Type: application/json' -d "$3" "$BASE_URL$2")
  else
    RESP=$(curl -s -D "$HDRS" -w '\n%{http_code}' -X "$1" "$BASE_URL$2")
  fi
  STATUS=$(printf '%s' "$RESP" | tail -n 1)
  BODY=$(printf '%s' "$RESP" | sed '$d')
  REPLAYED=$(grep -i '^Idempotent-Replayed:' "$HDRS" | tr -d '\r' | sed 's/^[^:]*: *//')
  rm -f "$HDRS"
}

# json_field NAME -> first string/number value of "NAME" in $BODY
json_field() {
  printf '%s' "$BODY" | sed -n 's/.*"'"$1"'":"\{0,1\}\([^",}]*\)"\{0,1\}.*/\1/p' | head -n 1
}

check() { # description expected actual
  if [ "$2" = "$3" ]; then PASS=$((PASS + 1)); echo "  PASS  $1"
  else FAIL=$((FAIL + 1)); echo "  FAIL  $1 (expected '$2', got '$3')"; fi
}

echo "== Waiting for $BASE_URL"
i=0
until curl -sf "$BASE_URL/health" >/dev/null; do
  i=$((i + 1)); [ "$i" -gt 30 ] && { echo "API did not become healthy"; exit 2; }
  sleep 1
done

echo "== Customers"
call POST /api/customers "{\"taxNumber\":\"A-$RUN\",\"name\":\"Ada Lovelace\"}"
check "create customer A -> 201" 201 "$STATUS"
call POST /api/customers "{\"taxNumber\":\"A-$RUN\",\"name\":\"Duplicate\"}"
check "duplicate tax number -> 409" 409 "$STATUS"
call POST /api/customers "{\"taxNumber\":\"B-$RUN\",\"name\":\"Alan Turing\"}"
check "create customer B -> 201" 201 "$STATUS"
call GET "/api/customers/A-$RUN"
check "get customer -> 200" 200 "$STATUS"
call GET /api/customers/nobody
check "unknown customer -> 404" 404 "$STATUS"
call GET "/api/customers?name=lovelace"
check "search by name -> 200" 200 "$STATUS"

echo "== Accounts"
call POST /api/accounts "{\"taxNumber\":\"A-$RUN\"}"
check "open account A -> 201" 201 "$STATUS"
ACC_A=$(json_field number)
call POST /api/accounts "{\"taxNumber\":\"B-$RUN\"}"
ACC_B=$(json_field number)
call POST /api/accounts '{"taxNumber":"nobody"}'
check "open account for unknown customer -> 404" 404 "$STATUS"
call GET "/api/customers/A-$RUN/accounts"
check "list customer accounts -> 200" 200 "$STATUS"

echo "== Deposit / withdraw"
call POST "/api/accounts/$ACC_A/deposits" '{"amount":100.00}'
check "deposit 100 -> 201" 201 "$STATUS"
check "balance is 100.00" 100.00 "$(json_field balance)"
call POST "/api/accounts/$ACC_A/deposits" '{"amount":-5}'
check "negative deposit -> 400" 400 "$STATUS"
call POST "/api/accounts/$ACC_A/deposits" '{"amount":1.001}'
check "3 decimal places -> 400" 400 "$STATUS"
call POST "/api/accounts/$ACC_A/deposits" 'garbage'
check "malformed json -> 400" 400 "$STATUS"
call POST "/api/accounts/$ACC_A/withdrawals" '{"amount":30.50}'
check "withdraw 30.50 -> 201" 201 "$STATUS"
check "balance is 69.50" 69.50 "$(json_field balance)"
call POST "/api/accounts/$ACC_A/withdrawals" '{"amount":1000}'
check "overdraft -> 422" 422 "$STATUS"
call GET "/api/accounts/$ACC_A"
check "overdraft left balance untouched (69.50)" 69.50 "$(json_field balance)"

echo "== Transfers"
call POST "/api/accounts/$ACC_A/transfers" "{\"toAccountNumber\":\"$ACC_B\",\"amount\":19.50}"
check "transfer 19.50 A->B -> 201" 201 "$STATUS"
call GET "/api/accounts/$ACC_A"
check "A is now 50.00" 50.00 "$(json_field balance)"
call GET "/api/accounts/$ACC_B"
check "B is now 19.50" 19.50 "$(json_field balance)"
call POST "/api/accounts/$ACC_A/transfers" "{\"toAccountNumber\":\"$ACC_A\",\"amount\":1}"
check "transfer to same account -> 400" 400 "$STATUS"
call POST "/api/accounts/$ACC_A/transfers" '{"toAccountNumber":"ghost","amount":1}'
check "transfer to unknown account -> 404" 404 "$STATUS"
call POST "/api/accounts/$ACC_A/transfers" "{\"toAccountNumber\":\"$ACC_B\",\"amount\":9999}"
check "transfer without funds -> 422" 422 "$STATUS"

echo "== Statement"
call GET "/api/accounts/$ACC_A/statement"
check "statement -> 200" 200 "$STATUS"
call GET "/api/accounts/$ACC_A/statement?limit=1"
check "statement limit=1 -> 200" 200 "$STATUS"
call GET "/api/accounts/$ACC_A/statement?limit=0"
check "statement limit=0 -> 400" 400 "$STATUS"

echo "== Concurrency: 20 parallel withdrawals of 30 from a balance of 100"
call POST /api/customers "{\"taxNumber\":\"C-$RUN\",\"name\":\"Concurrent\"}"
call POST /api/accounts "{\"taxNumber\":\"C-$RUN\"}"
ACC_C=$(json_field number)
call POST "/api/accounts/$ACC_C/deposits" '{"amount":100}'
OUT=$(mktemp)
for n in $(seq 1 20); do
  curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' \
    -d '{"amount":30}' "$BASE_URL/api/accounts/$ACC_C/withdrawals" >>"$OUT" &
done
wait
check "exactly 3 withdrawals succeeded" 3 "$(grep -c '^201$' "$OUT")"
check "exactly 17 withdrawals refused" 17 "$(grep -c '^422$' "$OUT")"
call GET "/api/accounts/$ACC_C"
check "final balance is 10.00 (never negative)" 10.00 "$(json_field balance)"
rm -f "$OUT"

echo "== Idempotency-Key"
call POST "/api/accounts/$ACC_A/withdrawals" '{"amount":5}' "smoke-$RUN"
check "first keyed withdrawal -> 201" 201 "$STATUS"
check "first keyed withdrawal is not a replay" "" "$REPLAYED"
check "balance is 45.00" 45.00 "$(json_field balance)"
call POST "/api/accounts/$ACC_A/withdrawals" '{"amount":5.00}' "smoke-$RUN"
check "same key, same request -> 201" 201 "$STATUS"
check "same key, same request is replayed" true "$REPLAYED"
check "replay returns the original balance (45.00)" 45.00 "$(json_field balance)"
call POST "/api/accounts/$ACC_A/withdrawals" '{"amount":6}' "smoke-$RUN"
check "same key, different request -> 422" 422 "$STATUS"
call GET "/api/accounts/$ACC_A"
check "money left the account only once (45.00)" 45.00 "$(json_field balance)"

echo "== Concurrency: 20 parallel retries of one transfer with the same Idempotency-Key"
OUT=$(mktemp)
for n in $(seq 1 20); do
  curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' \
    -H "Idempotency-Key: storm-$RUN" -d "{\"toAccountNumber\":\"$ACC_B\",\"amount\":10}" \
    "$BASE_URL/api/accounts/$ACC_A/transfers" >>"$OUT" &
done
wait
check "all 20 retries answered 201" 20 "$(grep -c '^201$' "$OUT")"
call GET "/api/accounts/$ACC_A"
check "transfer applied exactly once (A is 35.00)" 35.00 "$(json_field balance)"
call GET "/api/accounts/$ACC_B"
check "transfer applied exactly once (B is 29.50)" 29.50 "$(json_field balance)"
rm -f "$OUT"

if [ -n "$TIGHT_URL" ]; then
  echo "== Load shedding: 30 parallel requests against a 1-slot bulkhead"
  until curl -sf "$TIGHT_URL/health" >/dev/null; do sleep 1; done
  OUT=$(mktemp)
  for n in $(seq 1 30); do
    curl -s -o /dev/null -w '%{http_code}\n' "$TIGHT_URL/api/customers" >>"$OUT" &
  done
  wait
  SHED=$(grep -c '^503$' "$OUT")
  OK=$(grep -c '^200$' "$OUT")
  check "some requests were shed with 503" yes "$([ "$SHED" -gt 0 ] && echo yes || echo no)"
  check "every request got 200 or 503" 30 "$((SHED + OK))"
  check "health still answers while saturated" 200 "$(curl -s -o /dev/null -w '%{http_code}' "$TIGHT_URL/health")"
  rm -f "$OUT"
fi

echo "== Routing"
call DELETE /api/customers
check "wrong method -> 405" 405 "$STATUS"
call GET /api/unknown
check "unknown route -> 404" 404 "$STATUS"

echo
echo "Result: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
