import hashlib
import hmac
import json
import requests
import urllib3
from random import randrange
import time
def transfer(apiKey,secretKey,coin,fromAccountType,toAccountType,amount,transferId):
    params = {
        "fromAccountType": fromAccountType,
        "toAccountType": toAccountType,
        "coin": coin,
        "amount": amount,
        "transferId": transferId,
        "api_key": apiKey,
        "timestamp": str(int(time.time()*1000))
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
    url = 'https://api.bybit.com/asset/v1/private/transfer'
    headers = {"Content-Type": "application/json"}
    body = dict(params,**sign_real)
    urllib3.disable_warnings()
    response = requests.post(url, data=json.dumps(body), headers=headers,verify=False)
    print(response.text)
def main():
    apiKey = "xxxx"
    secret = b"xxxx"
    transfer(apiKey, secret,'USDT','CONTRACT','SPOT','0.001','21ff1b44-2d5d-4293-913d-4545c5ad'+str(randrange(1000,9999)))
if __name__ == '__main__':
    main()
