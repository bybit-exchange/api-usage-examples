import base64
import json
import time
import uuid

import requests
from Crypto.Hash import SHA256
from Crypto.PublicKey import RSA
from Crypto.Signature import PKCS1_v1_5

API_KEY = "XXXXXXXXXX"
RSA_PRIVATE_KEY_PATH = "/Users/XXXXXXXXXX/private.pem"

HTTP_CLIENT = requests.Session()
RECV_WINDOW = str(5000)
URL = "https://api-testnet.bybit.com"


def http_request(endpoint, method, payload, info):
    global time_stamp
    time_stamp = str(int(time.time() * 10 ** 3))
    signature = gen_signature(payload, RSA_PRIVATE_KEY_PATH)
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
    print(response.status_code)
    print(info + " Elapsed Time : " + str(response.elapsed))


def gen_signature(payload, rsa_private_key_path):
    param_str = str(time_stamp) + API_KEY + RECV_WINDOW + payload

    with open(rsa_private_key_path, "r") as private_key_obj:
        private_key_str = private_key_obj.read()
    private_key = RSA.importKey(private_key_str)
    encoded_param = SHA256.new(param_str.encode("utf-8"))
    signature = PKCS1_v1_5.new(private_key).sign(encoded_param)

    return base64.b64encode(signature).decode()

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
