import requests
import time
import hashlib
import hmac

api_key = 'xxxx'
secret_key = 'xxxxx'
httpClient = requests.Session()
recv_window = str(5000)
url = "https://api.bybit.com"


def HTTP_Request(endPoint, method, payload, Info):
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
    if (method == "POST"):
        response = httpClient.request(method, url + endPoint, headers=headers, data=payload)
    else:
        response = httpClient.request(method, url + endPoint + "?" + payload, headers=headers)
    print(response.text)
    print(response.headers)
    print(Info + " Elapsed Time : " + str(response.elapsed))


def genSignature(payload):
    param_str = str(time_stamp) + api_key + recv_window + payload
    hash = hmac.new(bytes(secret_key, "utf-8"), param_str.encode("utf-8"), hashlib.sha256)
    signature = hash.hexdigest()
    return signature


# Get VIP Margin Data
endpoint = "/v5/spot-margin-trade/data"
method = "GET"
params = 'currency=USDT'
HTTP_Request(endpoint, method, params, "Get usdt margin data")

# Get Historical Interest Rate
endpoint = "/v5/spot-margin-trade/interest-rate-history"
method = "GET"
params = 'currency=USDT&vipLevel=No%20VIP'
HTTP_Request(endpoint, method, params, "Get usdt interest borrow rate")
