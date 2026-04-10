import hashlib
import hmac
import json
import time
import uuid

import requests

API_KEY = "XXXXXXXXXX"
SECRET_KEY = "XXXXXXXXXX"
HTTP_CLIENT = requests.Session()
RECV_WINDOW = str(5000)
URL = "https://api-testnet.bybit.com"


def http_request(endpoint, method, payload, info):
    global time_stamp
    time_stamp = str(int(time.time() * 10 ** 3))
    signature = gen_signature(payload)
    headers = {
        "X-BAPI-API-KEY": API_KEY,
        "X-BAPI-SIGN": signature,
        "X-BAPI-SIGN-TYPE": "2",
        "X-BAPI-TIMESTAMP": time_stamp,
        "X-BAPI-RECV-WINDOW": RECV_WINDOW,
        "Content-Type": "application/json",
    }
    if method == "POST":
        response = HTTP_CLIENT.request(method, URL + endpoint, headers=headers, data=payload)
    else:
        response = HTTP_CLIENT.request(method, URL + endpoint + "?" + payload, headers=headers)
    print(response.text)
    print(response.headers)
    print(info + " Elapsed Time : " + str(response.elapsed))


def gen_signature(payload):
    param_str = str(time_stamp) + API_KEY + RECV_WINDOW + payload
    signature_hash = hmac.new(
        bytes(SECRET_KEY, "utf-8"),
        param_str.encode("utf-8"),
        hashlib.sha256,
    )
    return signature_hash.hexdigest()


endpoint = "/v5/order/create"
method = "POST"
order_link_id = uuid.uuid4().hex
params = json.dumps(
    {
        "category": "linear",
        "symbol": "BTCUSDT",
        "side": "Buy",
        "positionIdx": 0,
        "orderType": "Limit",
        "qty": "0.001",
        "price": "10000",
        "timeInForce": "GTC",
        "orderLinkId": order_link_id,
    },
    separators=(",", ":"),
)
http_request(endpoint, method, params, "Create")


endpoint = "/v5/order/realtime"
method = "GET"
params = "category=linear&settleCoin=USDT"
http_request(endpoint, method, params, "UnFilled")


endpoint = "/v5/order/cancel"
method = "POST"
params = json.dumps(
    {
        "category": "linear",
        "symbol": "BTCUSDT",
        "orderLinkId": order_link_id,
    },
    separators=(",", ":"),
)
http_request(endpoint, method, params, "Cancel")
