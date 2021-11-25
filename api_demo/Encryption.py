import hashlib
import hmac
import json
import requests
import urllib3
import time
from urllib.parse import quote_plus


def create(api_key, secret_key, side, order_type, qty, price):
    """Numerous examples for creating a request"""
    #'''
    params = {  # futures create order params
        "side": side,
        "symbol": "BTCUSD",
        "order_type": order_type,
        "qty": qty,
        "price": price,
        "time_in_force": "GoodTillCancel",
        "api_key": api_key,
        "timestamp": round(time.time() * 1000),
        "recv_window": 10000,
    }
    #'''
    '''
    params = {  # spot create order params
        "side": side,
        "symbol": "BTCUSDT",
        "type": order_type,
        "qty": qty,
        "price": price,
        "timeInForce": "GTC",
        "api_key": api_key,
        "timestamp": round(time.time() * 1000),
        "recv_window": 10000,
    }
    #'''
    '''
    params = {  # /v2/private/order/list example demonstrating correct encoding
        "symbol": "BTCUSD",
        "cursor": "42T+FpoQ6orO6YfV/coAZs/pGP2dMKaju56oRjWCOevTN6g5vDf8xFia5iyJYY3o",
        "api_key": api_key,
        "timestamp": round(time.time() * 1000),
        "recv_window": 10000,
    }
    #'''
    '''
    params = {  # spot query order params
        "orderId": 971348300175355648,
        "api_key": api_key,
        "timestamp": round(time.time() * 1000),
        "recv_window": 10000,
    }
    #'''
    # Create the param str
    param_str = ""
    for key in sorted(params.keys()):
        v = params[key]
        if isinstance(params[key], bool):
            if params[key]:
                v = "true"
            else:
                v = "false"
        param_str += f"{key}={v}&"
    param_str = param_str[:-1]
    print(param_str)

    # Generate the signature
    hash = hmac.new(bytes(secret_key, "utf-8"), param_str.encode("utf-8"),
                    hashlib.sha256)
    signature = hash.hexdigest()
    sign_real = {
        "sign": signature
    }

    # Prepare params in the query string format
    # quote_plus helps quote rare characters like "/" and "+"; this must be
    # applied after the signature generation.
    param_str = quote_plus(param_str, safe="=&")
    full_param_str = f"{param_str}&sign={sign_real['sign']}"
    print(f"full_param_str = {full_param_str}")

    # Request information
    method = "POST"
    url = "https://api-testnet.bybit.com/v2/private/order/create"
    #url = "https://api-testnet.bybit.com/spot/v1/order"
    #url = "https://api-testnet.bybit.com/v2/private/order/list"
    if "spot" in url or method == "GET":
        headers = {"Content-Type": "application/x-www-form-urlencoded"}
        body = None
    else:
        headers = {"Content-Type": "application/json"}
        body = dict(params, **sign_real)

    urllib3.disable_warnings()
    s = requests.session()
    s.keep_alive = False

    # Send the request(s)
    for x in range(1):
        if "spot" in url:
            # Send a request to the spot API
            response = requests.request(method, f"{url}?{full_param_str}",
                                        headers=headers, verify=False)
        else:
            # Send a request to the futures API
            if method == "POST":
                response = requests.request(method, url, data=json.dumps(body),
                                            headers=headers, verify=False)
            else:  # GET
                response = requests.request(method, f"{url}?{full_param_str}",
                                            headers=headers, verify=False)
        print(response)
        print(response.text)


def main():
    api_key = "vvO4vBTwmm36bb4L7t"
    secret_key = "P8ctndKad4TdO6yvpuoji6na9szTjl2mI1Al"
    create(api_key, secret_key, "Buy", "Limit", 1, 8700)


if __name__ == "__main__":
    main()
