#!/bin/bash

# ── Flags ─────────────────────────────────────────────────────────────────────
DLQ_DEMO=false
for arg in "$@"; do
    [ "$arg" = "--dlq-demo" ] && DLQ_DEMO=true
done

# ── Config ────────────────────────────────────────────────────────────────────
BASE="http://localhost:8080"
RUN_ID=$(date +%s)   # unique per run - keeps idempotency keys fresh

# ── Helpers ───────────────────────────────────────────────────────────────────
section() { echo -e "\n━━━ $1 ━━━\n"; }
step()    { echo "▶ $1"; }
ok()      { echo "✔ $1"; }
fail()    { echo "✘ $1"; }
pause()   { sleep 1; echo ""; }

json()  { python3 -m json.tool 2>/dev/null || cat; }
field() { python3 -c "import sys,json; print(json.load(sys.stdin)['$1'])" 2>/dev/null; }

# ── 1. Health Check ───────────────────────────────────────────────────────────
section "1. HEALTH CHECK"
step "Checking stack is up..."
HEALTH=$(curl -s "$BASE/actuator/health" | field status)
if [ "$HEALTH" != "UP" ]; then
    fail "Stack not ready (got: '$HEALTH'). Run: docker compose up -d --build"
    exit 1
fi
ok "Stack is UP - PostgreSQL, RabbitMQ, Keycloak, Zipkin all healthy"

# ── 2. Authenticate ───────────────────────────────────────────────────────────
section "2. AUTHENTICATE - GET A TOKEN FROM KEYCLOAK"
step "Fetching JWT via password grant..."
TOKEN=$(curl -s -X POST "http://localhost:8180/realms/payflow/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&client_id=payflow-app&client_secret=payflow-dev-secret&username=testuser&password=testuser123&scope=openid orders:read orders:write" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])" 2>/dev/null)

if [ -z "$TOKEN" ]; then
    fail "Token fetch failed - is the stack fully up?"
    exit 1
fi
ok "JWT acquired: ${TOKEN:0:60}..."

AUTH="Authorization: Bearer $TOKEN"

# ── 3. Normal Flow ───────────────────────────────────────────────────────────
section "3. NORMAL FLOW - CREATE ORDER AND WAIT FOR PAYMENT"
step "POST /orders"
RESPONSE=$(curl -s -X POST "$BASE/orders" \
    -H "$AUTH" \
    -H "Idempotency-Key: demo-happy-$RUN_ID" \
    -H "Content-Type: application/json" \
    -d '{"amount": 49.99, "currency": "GBP"}')
echo "$RESPONSE" | json
ORDER_ID=$(echo "$RESPONSE" | field id)
ok "Order $ORDER_ID created - RabbitMQ will process this asynchronously"

echo ""
step "Polling GET /orders/$ORDER_ID until payment resolves..."
STATUS="PROCESSING"
for i in $(seq 1 15); do
    sleep 1
    STATUS=$(curl -s "$BASE/orders/$ORDER_ID" -H "$AUTH" | field status)
    printf "  [%2ds] %s\n" "$i" "$STATUS"
    [ "$STATUS" = "PAID" ] || [ "$STATUS" = "FAILED" ] && break
done
ok "Final status: $STATUS"

# ── 4. Idempotency ────────────────────────────────────────────────────────────
section "4. IDEMPOTENCY - NO DUPLICATE CHARGES ON RETRY"
step "Sending the exact same request again with Idempotency-Key: demo-happy-$RUN_ID"
RESPONSE2=$(curl -s -X POST "$BASE/orders" \
    -H "$AUTH" \
    -H "Idempotency-Key: demo-happy-$RUN_ID" \
    -H "Content-Type: application/json" \
    -d '{"amount": 49.99, "currency": "GBP"}')
echo "$RESPONSE2" | json
ID2=$(echo "$RESPONSE2" | field id)
if [ "$ORDER_ID" = "$ID2" ]; then
    ok "Same order returned (id=$ORDER_ID) - duplicate safely ignored, no double charge"
else
    fail "Different IDs - idempotency broken"
fi

# ── 5. Validation ─────────────────────────────────────────────────────────────
section "5. VALIDATION - BAD INPUT, CLEAR FEEDBACK"

step "Missing amount → 400"
curl -s -X POST "$BASE/orders" -H "$AUTH" \
    -H "Idempotency-Key: demo-v1" -H "Content-Type: application/json" \
    -d '{"currency":"GBP"}' | json
pause

step "Amount below minimum (0.00) → 400"
curl -s -X POST "$BASE/orders" -H "$AUTH" \
    -H "Idempotency-Key: demo-v2" -H "Content-Type: application/json" \
    -d '{"amount":0.00,"currency":"GBP"}' | json
pause

step "Invalid idempotency key (special characters) → 400"
curl -s -X POST "$BASE/orders" -H "$AUTH" \
    -H "Idempotency-Key: bad key@!" -H "Content-Type: application/json" \
    -d '{"amount":25.00,"currency":"GBP"}' | json
pause

step "Wrong Content-Type → 415"
curl -s -X POST "$BASE/orders" -H "$AUTH" \
    -H "Idempotency-Key: demo-v3" -H "Content-Type: text/plain" \
    -d 'not json' | json
pause

step "Unsupported HTTP method (DELETE) → 405"
curl -s -X DELETE "$BASE/orders/1" -H "$AUTH" | json
pause

step "Unknown route → 404"
curl -s "$BASE/does-not-exist" -H "$AUTH" | json

# ── 6. Rate Limiter ───────────────────────────────────────────────────────────
section "6. RATE LIMITER - 50 req/s ENFORCED"
step "Firing 60 requests in parallel (limit is 50/s)..."

TMPDIR=$(mktemp -d)
for i in $(seq 1 60); do
    (curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/orders" \
        -H "$AUTH" \
        -H "Idempotency-Key: demo-rl-$i" \
        -H "Content-Type: application/json" \
        -d '{"amount":1.00,"currency":"GBP"}' \
        > "$TMPDIR/rl_$i.txt") &
done
wait

GOT_201=0
GOT_429=0
for f in "$TMPDIR"/rl_*.txt; do
    CODE=$(cat "$f" 2>/dev/null)
    case "$CODE" in
        201) GOT_201=$((GOT_201 + 1)) ;;
        429) GOT_429=$((GOT_429 + 1)) ;;
    esac
done
rm -rf "$TMPDIR"

ok "201 Created: $GOT_201  |  429 Too Many Requests: $GOT_429"

# ── 7. DLQ Retry ─────────────────────────────────────────────────────────────
section "7. DLQ RETRY - FAILURES CYCLE THROUGH THE DEAD LETTER QUEUE"

if [ "$DLQ_DEMO" = "true" ]; then
    step "Switching app to always-fail mode (PAYMENT_FAILURE_RATE=1)..."
    PAYMENT_FAILURE_RATE=1 docker compose up -d --no-deps app > /dev/null 2>&1
    step "Waiting for app to restart..."
    for i in $(seq 1 20); do
        sleep 1
        HEALTH=$(curl -s "$BASE/actuator/health" | field status 2>/dev/null)
        [ "$HEALTH" = "UP" ] && break
        printf "  [%2ds] waiting...\n" "$i"
    done
    ok "App is back up"

    step "Creating 2 orders - every payment will fail and cycle through the DLQ..."
    DLQ_ID1=$(curl -s -X POST "$BASE/orders" -H "$AUTH" \
        -H "Idempotency-Key: demo-dlq-1-$RUN_ID" -H "Content-Type: application/json" \
        -d '{"amount": 25.00, "currency": "GBP"}' | field id)
    DLQ_ID2=$(curl -s -X POST "$BASE/orders" -H "$AUTH" \
        -H "Idempotency-Key: demo-dlq-2-$RUN_ID" -H "Content-Type: application/json" \
        -d '{"amount": 50.00, "currency": "USD"}' | field id)
    ok "Orders $DLQ_ID1 and $DLQ_ID2 created - watch RabbitMQ Management UI at http://localhost:15672"

    echo ""
    echo "  Each order goes through two retry layers:"
    echo "  1. App-level: 3 payment attempts per delivery, 500ms apart"
    echo "  2. Message-level: after 3 failures, the message routes to the DLQ,"
    echo "     waits 5s, then returns to payment.queue"
    echo "  3. The DLQ cycle repeats 3 times: 3 x 3 = 9 attempts total"
    echo "  4. Then the order is permanently marked FAILED (~20-40s)"
    echo ""

    step "Polling order status and circuit breaker state live..."
    CB_TRIPPED=false
    for i in $(seq 1 40); do
        sleep 2
        S1=$(curl -s "$BASE/orders/$DLQ_ID1" -H "$AUTH" | field status 2>/dev/null)
        S2=$(curl -s "$BASE/orders/$DLQ_ID2" -H "$AUTH" | field status 2>/dev/null)
        CB=$(curl -s "$BASE/actuator/circuitbreakers" \
            | python3 -c "import sys,json; print(json.load(sys.stdin)['circuitBreakers']['paymentGateway']['state'])" 2>/dev/null)
        if [ "$CB" = "OPEN" ] || [ "$CB" = "HALF_OPEN" ]; then
            CB_TRIPPED=true
        fi
        printf "  [%3ds] orders: %-11s %-11s |  circuit: %s\n" "$((i*2))" "$S1" "$S2" "$CB"
        [ "$S1" = "FAILED" ] && [ "$S2" = "FAILED" ] && break
    done

    if [ "$S1" = "FAILED" ] && [ "$S2" = "FAILED" ]; then
        ok "Both orders permanently FAILED after exhausting all retries"
    else
        fail "Orders did not reach FAILED in time (A=$S1, B=$S2) - check RabbitMQ queue topology"
    fi

    echo ""
    if [ "$CB_TRIPPED" = "true" ]; then
        ok "Circuit breaker tripped during the run - it opened to stop hammering the failing gateway"
    else
        step "Circuit breaker stayed CLOSED this run"
    fi
    echo ""

    step "Restoring normal failure rate (10%)..."
    docker compose up -d --no-deps app > /dev/null 2>&1
    step "Waiting for app to come back..."
    for i in $(seq 1 20); do
        sleep 1
        HEALTH=$(curl -s "$BASE/actuator/health" | field status 2>/dev/null)
        [ "$HEALTH" = "UP" ] && break
    done
    ok "App restored to normal (90% success rate)"
else
    step "Payment processing has a 10% failure rate (simulated gateway instability)."
    echo "  When a payment fails:"
    echo "  1. @Retry retries 3x within the same RabbitMQ delivery (500ms between each)"
    echo "  2. If all retries fail → AmqpRejectAndDontRequeueException → message goes to DLQ"
    echo "  3. DLQ holds the message for 5s → routes back to payment.queue"
    echo "  4. After 3 RabbitMQ deliveries (9 total attempts) → order marked FAILED"
    echo ""
    step "Run with --dlq-demo flag to see this live"
    echo "  ./demo.sh --dlq-demo"
    echo ""
    step "Or watch it in the logs: docker logs payflow-app --follow"
    echo "  Look for: 'routing to DLQ', 'x-death count', 'permanently marked as FAILED'"
fi

# ── Done ──────────────────────────────────────────────────────────────────────
echo -e "\n━━━ DEMO COMPLETE ━━━\n"
echo "  Swagger UI:          $BASE/swagger-ui/index.html"
echo "  RabbitMQ Management: http://localhost:15672  (guest / guest)"
echo "  Zipkin Traces:       http://localhost:9411"
echo "  Keycloak Admin:      http://localhost:8180   (admin / admin)"
echo ""