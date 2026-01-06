#!/bin/bash

echo "=== PAYFLOW EDGE CASE TESTING ==="

# 1. Normal Order Creation
echo "1. Normal Order Creation"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: test-normal-123" \
  -H "Content-Type: application/json" \
  -d '{"amount":29.99,"currency":"GBP"}'
echo -e "\n"

# 2. Duplicate Idempotency Key (Should return same order)
echo "2. Duplicate Idempotency Key"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: test-normal-123" \
  -H "Content-Type: application/json" \
  -d '{"amount":29.99,"currency":"GBP"}'
echo -e "\n"

# 3. Missing Idempotency Key (Should generate one)
echo "3. Missing Idempotency Key"
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"amount":15.50,"currency":"USD"}'
echo -e "\n"

# 4. Invalid Amount (Below minimum)
echo "4. Invalid Amount (0.00)"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: test-invalid-amount" \
  -H "Content-Type: application/json" \
  -d '{"amount":0.00,"currency":"EUR"}'
echo -e "\n"

# 5. Invalid Currency Format
echo "5. Invalid Currency (lowercase)"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: test-invalid-currency" \
  -H "Content-Type: application/json" \
  -d '{"amount":25.99,"currency":"gbp"}'
echo -e "\n"

# 6. Invalid Currency Length
echo "6. Invalid Currency (4 characters)"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: test-invalid-currency2" \
  -H "Content-Type: application/json" \
  -d '{"amount":25.99,"currency":"USDD"}'
echo -e "\n"

# 7. Missing Required Fields
echo "7. Missing Amount"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: test-missing-amount" \
  -H "Content-Type: application/json" \
  -d '{"currency":"GBP"}'
echo -e "\n"

# 8. Get Non-Existent Order
echo "8. Get Non-Existent Order"
curl -X GET http://localhost:8080/orders/999999
echo -e "\n"

# 9. Very Large Amount
echo "9. Very Large Amount"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: test-large-amount" \
  -H "Content-Type: application/json" \
  -d '{"amount":999999.99,"currency":"JPY"}'
echo -e "\n"

# 10. Special Characters in Idempotency Key
echo "10. Special Characters in Idempotency Key"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: test-special-chars_123-ABC@" \
  -H "Content-Type: application/json" \
  -d '{"amount":10.00,"currency":"CAD"}'
echo -e "\n"

# 11. Get Health Status
echo "11. Get Health Status"
curl -X GET http://localhost:8080/actuator/health
echo -e "\n"

# 12. Get Info
echo "12. Get Application Info"
curl -X GET http://localhost:8080/actuator/info
echo -e "\n"

# 13. Get Metrics
echo "13. Get Metrics"
curl -X GET http://localhost:8080/actuator/metrics
echo -e "\n"

# 14. Invalid HTTP Method
echo "14. Invalid HTTP Method (PUT on POST endpoint)"
curl -X PUT http://localhost:8080/orders \
  -H "Idempotency-Key: test-put-method" \
  -H "Content-Type: application/json" \
  -d '{"amount":20.00,"currency":"EUR"}'
echo -e "\n"

# 15. Malformed JSON
echo "15. Malformed JSON"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: test-malformed-json" \
  -H "Content-Type: application/json" \
  -d '{"amount":20.00 "currency":"EUR"}'
echo -e "\n"

# 16. Maximum allowed amount (177777.7777)
echo "16. Maximum allowed amount"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: test-max-amount" \
  -H "Content-Type: application/json" \
  -d '{"amount":177777.7777,"currency":"GBP"}'
echo -e "\n"

# 17. Amount exceeding maximum limit
echo "17. Amount exceeding maximum limit"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: test-exceed-max-amount" \
  -H "Content-Type: application/json" \
  -d '{"amount":177777.7778,"currency":"GBP"}'
echo -e "\n"

# 18. Valid idempotency key with underscore and hyphen
echo "18. Valid idempotency key with underscore and hyphen"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: valid_key-123" \
  -H "Content-Type: application/json" \
  -d '{"amount":25.00,"currency":"GBP"}'
echo -e "\n"

# 19. Empty idempotency key (should generate UUID)
echo "19. Empty idempotency key"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: " \
  -H "Content-Type: application/json" \
  -d '{"amount":30.00,"currency":"GBP"}'
echo -e "\n"

# 20. Get existing order by ID
echo "20. Get existing order by ID"
response=$(curl -s -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: get-order-test" \
  -H "Content-Type: application/json" \
  -d '{"amount":40.00,"currency":"GBP"}')
order_id=$(echo $response | grep -o '"id":[0-9]*' | cut -d: -f2)
echo "Created order with ID: $order_id"
curl -X GET http://localhost:8080/orders/$order_id
echo -e "\n"

# 21. Negative amount
echo "21. Negative amount"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: test-negative-amount" \
  -H "Content-Type: application/json" \
  -d '{"amount":-10.00,"currency":"GBP"}'
echo -e "\n"

# 22. Zero amount
echo "22. Zero amount"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: test-zero-amount" \
  -H "Content-Type: application/json" \
  -d '{"amount":0.00,"currency":"GBP"}'
echo -e "\n"

# 23. Currency with numbers (invalid)
echo "23. Currency with numbers (invalid)"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: test-currency-numbers" \
  -H "Content-Type: application/json" \
  -d '{"amount":25.99,"currency":"GB1"}'
echo -e "\n"

# 24. Currency with special characters (invalid)
echo "24. Currency with special characters (invalid)"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: test-currency-special" \
  -H "Content-Type: application/json" \
  -d '{"amount":25.99,"currency":"GB£"}'
echo -e "\n"

# 25. Very long idempotency key
echo "25. Very long idempotency key"
curl -X POST http://localhost:8080/orders \
  -H "Idempotency-Key: a_very_long_idempotency_key_that_exceeds_typical_length_requirements_1234567890" \
  -H "Content-Type: application/json" \
  -d '{"amount":25.00,"currency":"GBP"}'
echo -e "\n"

echo "=== END OF TESTING ==="