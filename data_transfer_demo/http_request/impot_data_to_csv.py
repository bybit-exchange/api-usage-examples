import requests
import time
import hashlib
import hmac
import csv

api_key='xxxxxxxxxxxxxxxxx'
secret_key='xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
httpClient=requests.Session()
recv_window=str(5000)
url="https://api-testnet.bybit.com" # Testnet endpoint

def HTTP_Request(endPoint,method,payload,Info):
    global time_stamp
    time_stamp=str(int(time.time() * 10 ** 3))
    signature=genSignature(payload)
    headers = {
        'X-BAPI-API-KEY': api_key,
        'X-BAPI-SIGN': signature,
        'X-BAPI-SIGN-TYPE': '2',
        'X-BAPI-TIMESTAMP': time_stamp,
        'X-BAPI-RECV-WINDOW': recv_window,
        'Content-Type': 'application/json'
    }
    if(method=="POST"):
        response = httpClient.request(method, url+endPoint, headers=headers, data=payload)
    else:
        response = httpClient.request(method, url+endPoint+"?"+payload, headers=headers)
    print(response.text)
    print(Info + " Elapsed Time : " + str(response.elapsed))

    response_json = response.json()
    list_data = response_json['result']['list']
    with open('BybitMarketDataTicker.csv', 'w', newline='') as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=list_data[0].keys())
        writer.writeheader()
        for item in list_data:
            writer.writerow(item)

def genSignature(payload):
    param_str= str(time_stamp) + api_key + recv_window + payload
    hash = hmac.new(bytes(secret_key, "utf-8"), param_str.encode("utf-8"),hashlib.sha256)
    signature = hash.hexdigest()
    return signature

endpoint="/v5/market/tickers"
method="GET"
params='category=linear'
HTTP_Request(endpoint,method,params,"Market Ticker Data")