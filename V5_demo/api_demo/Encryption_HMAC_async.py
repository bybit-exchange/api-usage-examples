import asyncio
import hashlib
import hmac
import json
import time
import uuid

import aiohttp

API_KEY = "XXXXXXXXXX"
SECRET_KEY = "XXXXXXXXXX"
RECV_WINDOW = str(5000)
URL = "https://api-testnet.bybit.com"


async def http_request(endpoint, method, payload, info):
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

    async with aiohttp.ClientSession() as session:
        start_time = time.perf_counter()
        if method == "POST":
            async with session.post(URL + endpoint, headers=headers, data=payload) as response:
                response_text = await response.text()
        else:
            async with session.get(URL + endpoint + "?" + payload, headers=headers) as response:
                response_text = await response.text()
        elapsed = time.perf_counter() - start_time

        print(response_text)
        print(info + " Elapsed Time : " + str(elapsed))


def gen_signature(payload):
    param_str = time_stamp + API_KEY + RECV_WINDOW + payload
    signature_hash = hmac.new(
        bytes(SECRET_KEY, "utf-8"),
        param_str.encode("utf-8"),
        hashlib.sha256,
    )
    return signature_hash.hexdigest()


async def main():
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
    await http_request(endpoint, method, params, "Create")


asyncio.run(main())
