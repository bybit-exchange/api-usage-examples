import hashlib
import hmac
import json
import requests
import urllib3
import time
def create_order(apiKey,secretKey,symbol,side,order_type,qty,price):
    timestamp = int(time.time() * 10 ** 3)
    params = {
        "side": side,
        "symbol": symbol,
        "order_type": order_type,
        "qty": qty,
        "price": price,
        "time_in_force": "PostOnly",
        "api_key": apiKey,
        "timestamp": str(timestamp),
        "recv_window": "5000",
        "reduce_only": False,
        "close_on_trigger": False,
    }
    sign = ''
    for key in sorted(params.keys()):
        v = params[key]
        if isinstance(params[key], bool):
            if params[key]:
                v = 'true'
            else :
                v = 'false'
        sign += key + '=' + v + '&'
    sign = sign[:-1]
    hash = hmac.new(secretKey, sign.encode("utf-8"), hashlib.sha256)
    signature = hash.hexdigest()
    sign_real = {
        "sign": signature
    }
    url = 'https://api-testnet.bybit.com/private/linear/order/create'
    headers = {"Content-Type": "application/json"}
    body = dict(params,**sign_real)
    urllib3.disable_warnings()
    s = requests.session()
    s.keep_alive = False
    response = requests.post(url, data=json.dumps(body), headers=headers,verify=False)
    print(response.text)
def main():
    apiKey = "XXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
    secret = b"XXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
    create_order(apiKey, secret,'UNIUSDT','Buy','Limit','1','10')
    create_order(apiKey, secret,'BTCUSDT','Buy','Market','0.001','0') #for market order, we are just passing the price as 0
if __name__ == '__main__':
    main()
