import requests
import time
import hashlib
import hmac
import uuid

api_key='XXXXXXXXXX'
secret_key='XXXXXXXXXX'
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

def genSignature(payload):
    param_str= str(time_stamp) + api_key + recv_window + payload
    hash = hmac.new(bytes(secret_key, "utf-8"), param_str.encode("utf-8"),hashlib.sha256)
    signature = hash.hexdigest()
    return signature

#Create Order
endpoint="/unified/v3/private/order/create"
method="POST"
orderLinkId=uuid.uuid4().hex
params='{"symbol":"BTCUSDT","orderType":"Limit","side":"Buy","qty":"0.001","price":"10000","timeInForce":"GoodTillCancel","category":"linear","orderLinkId": "' + orderLinkId + '"}'
HTTP_Request(endpoint,method,params,"Create")

#Get unfilled Orders
endpoint="/unified/v3/private/order/unfilled-orders"
method="GET"
params='category=linear&symbol=BTCUSDT'
HTTP_Request(endpoint,method,params,"UnFilled")

#Get Order List
endpoint="/unified/v3/private/order/list"
method="GET"
params="symbol=BTCUSDT&category=linear&orderLinkId="+orderLinkId
HTTP_Request(endpoint,method,params,"List")

#Cancel Order
endpoint="/unified/v3/private/order/cancel"
method="POST"
params='{"symbol": "BTCUSDT","category":"linear","orderLinkId": "'+orderLinkId+'"}'
HTTP_Request(endpoint,method,params,"Cancel")
