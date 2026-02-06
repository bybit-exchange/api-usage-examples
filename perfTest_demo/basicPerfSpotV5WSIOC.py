import time
import json
import hmac
import hashlib
import threading
import statistics
from websocket import WebSocketApp
from datetime import datetime

API_KEY = "xxxxxxxxxxx"
API_SECRET = "xxxxxxxxxxx"

TRADE_WS = "wss://stream-testnet.bybit.com/v5/trade"
PRIVATE_WS = "wss://stream-testnet.bybit.com/v5/private"

submit_ts = {}
latencies = []
lock = threading.Lock()

# ---------------- Utilities ----------------

def now_ms():
    return int(time.time() * 1000)

def sign(api_key, secret, expires):
    signature = hmac.new(secret.encode(), f'GET/realtime{expires}'.encode(), hashlib.sha256).hexdigest()
    return signature

# ---------------- Trade WS (order.create) ----------------

def on_trade_open(ws):
    exp = int((datetime.now().timestamp() + 10) * 1000) 
    ws.send(json.dumps({
        "op": "auth",
        "args": [API_KEY, exp, sign(API_KEY, API_SECRET, exp)]
    }))
    print("[TRADE] authenticated")

def on_trade_message(ws, message):
    # Only ACKs come here; not needed for RTT
    print(message)
    pass

def on_trade_error(ws, error):
    print("[TRADE] error:", error)

# ---------------- Private WS (order topic) ----------------

def on_private_open(ws):
    exp = int((datetime.now().timestamp() + 10) * 1000) 
    ws.send(json.dumps({
        "op": "auth",
        "args": [API_KEY, exp, sign(API_KEY, API_SECRET, exp)]
    }))

    ws.send(json.dumps({
        "op": "subscribe",
        "args": ["order"]
    }))

    print("[PRIVATE] authenticated & subscribed")

def on_private_message(ws, message):
    msg = json.loads(message)
    print(msg)
    if msg.get("topic") != "order":
        return

    for d in msg["data"]:
        if d.get("orderStatus") == "Cancelled":
            link_id = d.get("orderLinkId")
            if link_id in submit_ts:
                rtt = int(d["updatedTime"]) - submit_ts[link_id]
                with lock:
                    latencies.append(rtt)
                print(f"[{link_id}] Cancelled RTT={rtt} ms")

def on_private_error(ws, error):
    print("[PRIVATE] error:", error)

# ---------------- Start WebSockets ----------------

trade_ws = WebSocketApp(
    TRADE_WS,
    on_open=on_trade_open,
    on_message=on_trade_message,
    on_error=on_trade_error,
)

private_ws = WebSocketApp(
    PRIVATE_WS,
    on_open=on_private_open,
    on_message=on_private_message,
    on_error=on_private_error,
)

threading.Thread(target=trade_ws.run_forever, daemon=True).start()
threading.Thread(target=private_ws.run_forever, daemon=True).start()

time.sleep(2)  # allow auth & subscribe to complete

# ---------------- Send 10 IOC Orders ----------------

symbol = "ETHUSDT"
qty = "0.01"

for i in range(10):
    link_id = f"ioc_v5_{i}_{now_ms()}"
    submit_ts[link_id] = now_ms()

    trade_ws.send(json.dumps({
        "header": {
            "X-BAPI-TIMESTAMP": str(int(datetime.now().timestamp() * 1000)),
            "X-BAPI-RECV-WINDOW": "8000",
        },
        "op": "order.create",
        "args": [{
            "category": "spot",
            "symbol": symbol,
            "side": "Buy",
            "orderType": "Limit",
            "price": "1000",        # deliberately unfillable
            "qty": qty,
            "timeInForce": "IOC",
            "orderLinkId": link_id
        }]
    }))
    print(f"[{link_id}] sent")
    time.sleep(1)

# ---------------- Wait + Stats ----------------

time.sleep(5)

with lock:
    if not latencies:
        print("No Cancelled events received")
    else:
        p50 = statistics.quantiles(latencies, n=100)[49]
        p90 = statistics.quantiles(latencies, n=100)[89]
        p95 = statistics.quantiles(latencies, n=100)[94]
        p99 = statistics.quantiles(latencies, n=100)[98]

        print("\n IOC Cancel RTT (ms)")
        print(f"p50: {p50}")
        print(f"p90: {p90}")
        print(f"p95: {p95}")
        print(f"p99: {p99}")
