#!/bin/bash
TS=$(date +%Y%m%d_%H%M%S)
LOG_FILE="logs/w1_stabilize_verify_${TS}.log"
mkdir -p logs

echo "--- W1 Stabilize Verification: ${TS} ---" > $LOG_FILE

# 1. Wait for backend to be available
echo "[1] Waiting for backend at http://127.0.0.1:8080/health..." >> $LOG_FILE
for i in {1..15}; do
  # The -f flag makes curl fail silently on HTTP errors (like 404), which is what we want.
  if curl -s -f http://127.0.0.1:8080/health > /dev/null; then
    echo "Backend is up!" >> $LOG_FILE
    break
  fi
  echo "Attempt $i: Backend not ready, waiting 1s..." >> $LOG_FILE
  sleep 1
done

# 2. Curl health check
echo "\n[2] Performing Health Check..." >> $LOG_FILE
curl -i http://127.0.0.1:8080/health >> $LOG_FILE 2>&1

# 3. WebSocket smoke test
echo "\n[3] Performing WebSocket Smoke Test..." >> $LOG_FILE
# Ensure the ws smoke test script exists before trying to run it
if [ -f tools/ws_smoke_test.js ]; then
    node tools/ws_smoke_test.js >> $LOG_FILE 2>&1
else
    echo "WebSocket smoke test script not found!" >> $LOG_FILE
fi

echo "\n--- Verification End ---" >> $LOG_FILE
tail -n 80 $LOG_FILE
