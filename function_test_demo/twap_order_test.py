import requests
import time
import hmac
import hashlib
import json

api_key = 'xxxx'
api_secret = 'xxxx'
base_url = 'https://api-testnet.bybit.com'
recv_window = '5000'
httpClient = requests.Session()


def create_order(category, symbol, qty, side, order_type):
    global time_stamp
    """ Function to create an order on Bybit """
    endpoint = "/v5/order/create"
    url = base_url + endpoint

    time_stamp = str(int(time.time() * 1000))
    params = {
        "symbol": symbol,
        "category": category,
        "qty": str(qty),
        "side": side,
        "orderType": order_type,
    }
    signature = genSignature(params)

    headers = {
        'X-BAPI-API-KEY': api_key,
        'X-BAPI-SIGN': signature,
        'X-BAPI-SIGN-TYPE': '2',
        'X-BAPI-TIMESTAMP': time_stamp,
        'X-BAPI-RECV-WINDOW': recv_window,
        'Content-Type': 'application/json'
    }
    response = httpClient.request("POST", url, headers=headers, json=params)
    print("Response Text:", response.text)
    print("Response Headers:", response.headers)


def genSignature(payload):
    param_str = str(time_stamp) + api_key + recv_window + json.dumps(payload)
    hash = hmac.new(bytes(api_secret, "utf-8"), param_str.encode("utf-8"), hashlib.sha256)
    signature = hash.hexdigest()
    return signature


def twap_order(category, symbol, total_qty, side, duration, interval):
    """ Function to execute a TWAP strategy """
    chunks = duration // interval
    qty_per_chunk = total_qty / chunks

    for _ in range(chunks):
        create_order(category, symbol, qty_per_chunk, side, 'Market')  # set order params as you need
        time.sleep(interval * 60)  # sleep for 'interval' minutes


# Example usage
twap_order('linear', 'BTCUSDT', 1, 'Buy', 100, 10)  # TWAP over 100 mins, order every 10 minutes
