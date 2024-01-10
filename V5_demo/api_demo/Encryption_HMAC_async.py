import aiohttp
import asyncio
import time
import hmac
import hashlib
import uuid

api_key = 'XXXXXXXXXX'
secret_key = 'XXXXXXXXXX'
recv_window = str(5000)
url = "https://api-testnet.bybit.com"  # Testnet endpoint

async def HTTP_Request(endPoint, method, payload, Info):
    global time_stamp
    time_stamp = str(int(time.time() * 10 ** 3))
    signature = genSignature(payload)
    headers = {
        'X-BAPI-API-KEY': api_key,
        'X-BAPI-SIGN': signature,
        'X-BAPI-SIGN-TYPE': '2',
        'X-BAPI-TIMESTAMP': time_stamp,
        'X-BAPI-RECV-WINDOW': recv_window,
        'Content-Type': 'application/json'
    }

    async with aiohttp.ClientSession() as session:
        if method == "POST":
            async with session.post(url + endPoint, headers=headers, data=payload) as response:
                response_text = await response.text()
        else:
            async with session.get(url + endPoint + "?" + payload, headers=headers) as response:
                response_text = await response.text()

        print(response_text)
        print(Info + " Elapsed Time : " + str(response.elapsed))

def genSignature(payload):
    param_str = time_stamp + api_key + recv_window + payload
    hash = hmac.new(bytes(secret_key, "utf-8"), param_str.encode("utf-8"), hashlib.sha256)
    signature = hash.hexdigest()
    return signature

async def main():
    # Create Order
    endpoint = "/v5/order/create"
    method = "POST"
    orderLinkId = uuid.uuid4().hex
    params = '{"category":"linear","symbol": "BTCUSDT","side": "Buy","positionIdx": 0,"orderType": "Limit","qty": "0.001","price": "10000","timeInForce": "GTC","orderLinkId": "' + orderLinkId + '"}'
    await HTTP_Request(endpoint, method, params, "Create")

# Run the main function using asyncio
asyncio.run(main())
