import hashlib
import hmac
import json
import time
import uuid
import requests

API_KEY = 'xxxxxxxxxxxxxxxxxx'
API_SECRET = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
HTTP_CLIENT = requests.Session()
RECV_WINDOW = '5000'
BASE_URL = "https://api-testnet.bybit.com"  # Testnet endpoint


def http_request(endpoint: str, method: str, payload: str, info: str) -> None:
    timestamp = str(int(time.time() * 10**3))
    signature = gen_signature(payload, timestamp)
    headers = {
        'X-BAPI-API-KEY': API_KEY,
        'X-BAPI-SIGN': signature,
        'X-BAPI-SIGN-TYPE': '2',
        'X-BAPI-TIMESTAMP': timestamp,
        'X-BAPI-RECV-WINDOW': RECV_WINDOW,
        'Content-Type': 'application/json',
    }

    url = BASE_URL + endpoint
    if method == "POST":
        response = HTTP_CLIENT.request(method, url, headers=headers, data=payload)
    else:
        response = HTTP_CLIENT.request(method, url + "?" + payload, headers=headers)

    print(response.text)
    print(response.headers)
    print(f"[{info}] Elapsed Time: {response.elapsed}")


def gen_signature(payload: str, timestamp: str) -> str:
    param_str = f"{timestamp}{API_KEY}{RECV_WINDOW}{payload}"
    mac = hmac.new(
        API_SECRET.encode('utf-8'),
        param_str.encode('utf-8'),
        hashlib.sha256
    )
    return mac.hexdigest()


# Create Order
endpoint = "/v5/order/create"
method = "POST"
order_link_id = uuid.uuid4().hex
order_payload = {
    "category": "linear",
    "symbol": "BTCUSDT",
    "side": "Buy",
    "positionIdx": 0,
    "orderType": "Limit",
    "qty": "0.001",
    "price": "80000",
    "timeInForce": "GTC",
    "orderLinkId": order_link_id
}
params = json.dumps(order_payload)
http_request(endpoint, method, params, "Create order")

# Get unfilled Orders
endpoint = "/v5/order/realtime"
method = "GET"
params = 'category=linear&settleCoin=USDT'
http_request(endpoint, method, params, "Get unfilled orders")

# Cancel Order
endpoint = "/v5/order/cancel"
method = "POST"
cancel_payload = {
    "category": "linear",
    "symbol": "BTCUSDT",
    "orderLinkId": order_link_id
}
params = json.dumps(cancel_payload)
http_request(endpoint, method, params, "Cancel an order")
