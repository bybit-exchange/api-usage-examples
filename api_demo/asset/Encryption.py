import hashlib
import hmac
import json
import requests
import urllib3
def create(apiKey,secretKey,symbol,side,order_type,qty,price):
    params = {
        "side": side,
        "symbol": symbol,
        "order_type": order_type,
        "qty": qty,
        "price": price,
        "time_in_force": "PostOnly",
        "api_key": apiKey,
        "timestamp": "1542782900000",
        "recv_window": "93800000000",
        "reduce_only":False,
        "close_on_trigger":False,
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
    print(sign)
    hash = hmac.new(secretKey, sign.encode("utf-8"), hashlib.sha256)
    signature = hash.hexdigest()
    print(signature)
    sign_real = {
        "sign": signature
    }
    #url = 'https://api-testnet.bybit.com/private/linear/order/create'
    url = 'https://api.bybit.com/private/linear/order/create'
    headers = {"Content-Type": "application/json"}
    body = dict(params,**sign_real)
    urllib3.disable_warnings()
    s = requests.session()
    s.keep_alive = False
    response = requests.post(url, data=json.dumps(body), headers=headers,verify=False)
    print(response.text)
def main():
    apiKey = "1wE1ciGhCIKvwN4G8s"
    secret = b"efVFoOta02XaUQQ9cW0Lp6Nn950m1JqLEZ4Q"
    #create(apiKey, secret,'BTCUSDT','Buy','Market','0.001')
    #create(apiKey, secret,'ETHUSDT','Buy','Market','0.01')
    create(apiKey, secret,'UNIUSDT','Buy','Limit','0.1','10')
    create(apiKey, secret,'CHZUSDT','Buy','Limit','1','0.1')
    create(apiKey, secret,'ETHUSDT','Buy','Limit','0.01','1000')
if __name__ == '__main__':
    main()
