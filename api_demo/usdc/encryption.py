import requests
import json
import time
import hashlib
import hmac

api_key='API-KEY'
secret_key='SECRET-KEY'
time_stamp=str(int(time.time() * 10 ** 3))
recv_window=str(5000)
url = "https://api-testnet.bybit.com/option/usdc/openapi/private/v1/place-order"

payload = json.dumps({"outRequestId":"8c7af60012","symbol":"BTC-17DEC21-40000-C","orderType":"Limit","side":"Buy",
                      "orderQty":"0.01","orderPrice":"308","iv":"72","timeInForce":"GoodTillCancel","orderLinkId":"c3d5cb801a",
                      "reduceOnly":True,"placeMode":1,"placeType":1})

payload = json.dumps({"outRequestId":"8c7f60012","symbol":"BTC-28JAN22-140000-C","side":"Sell","orderType":"Limit","orderQty":"0.01","orderPrice":"258","timeInForce":"GoodTillCancel","reduceOnly":True,"placeMode":1,"placeType":1})
param_str= str(time_stamp) + api_key + recv_window + payload
hash = hmac.new(bytes(secret_key, "utf-8"), param_str.encode("utf-8"),hashlib.sha256)
signature = hash.hexdigest()

headers = {
  'X-BAPI-API-KEY': api_key,
  'X-BAPI-SIGN': signature,
  'X-BAPI-SIGN-TYPE': '2',
  'X-BAPI-TIMESTAMP': time_stamp,
  'X-BAPI-RECV-WINDOW': recv_window,
  'Content-Type': 'application/json'
}
response = requests.request("POST", url, headers=headers, data=payload)
print(response.text)
