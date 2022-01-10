import hashlib
import hmac
import json
import requests
import urllib3
import time
from urllib.parse import quote_plus

def create_order(apiKey,secretKey,symbol,side,order_type,qty,price):
    timestamp = int(time.time() * 10 ** 3)
    url = 'https://api-testnet.bybit.com/spot/v1/order'
    headers = {}
    method="POST"
    params = {
        "side": side,
        "symbol": symbol,
        "type": order_type,
        "qty": qty,
        "price": price,
        "time_in_force": "GoodTillCancel",
        "api_key": apiKey,
        "timestamp": str(timestamp),
        "recv_window": "5000"
    }
    param_str = ''
    for key in sorted(params.keys()):
        v = params[key]
        if isinstance(params[key], bool):
            if params[key]:
                v = 'true'
            else :
                v = 'false'
        param_str += key + '=' + v + '&'
    param_str = param_str[:-1]
    hash = hmac.new(secretKey, param_str.encode("utf-8"), hashlib.sha256)
    signature = hash.hexdigest()
    sign_real = {
        "sign": signature
    }
    param_str = quote_plus(param_str, safe="=&")
    full_param_str = f"{param_str}&sign={sign_real['sign']}"
    urllib3.disable_warnings()
    s = requests.session()
    s.keep_alive = False
    response = requests.request(method, f"{url}?{full_param_str}",headers=headers, verify=False)
    print(response.text)
def main():
    apiKey = "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
    secret = b"XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
    create_order(apiKey, secret,'BITUSDT','Buy','Limit','10','1')
    create_order(apiKey, secret,'BITUSDT','Buy','Market','10','0') #for market order, we are just passing the price as 0
if __name__ == '__main__':
    main()
