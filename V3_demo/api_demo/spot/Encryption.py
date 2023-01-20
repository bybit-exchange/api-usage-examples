import requests
import time
import hashlib
import hmac
import uuid

api_key='XXXXXXXX'
secret_key='XXXXXXXX'
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
    print(Info + " Response Time : " + str(response.elapsed))

def genSignature(payload):
    param_str= str(time_stamp) + api_key + recv_window + payload
    hash = hmac.new(bytes(secret_key, "utf-8"), param_str.encode("utf-8"),hashlib.sha256)
    signature = hash.hexdigest()
    return signature

#Create Order
endpoint="/spot/v3/private/order"
method="POST"
orderLinkId=uuid.uuid4().hex
params='{"symbol":"BTCUSDT","orderType":"Limit","side":"Buy","orderLinkId":"' +  orderLinkId + '","orderQty":"0.001","orderPrice":"10000","timeInForce":"GTC"}';
HTTP_Request(endpoint,method,params,"Create")

#Get Order List
endpoint="/spot/v3/private/order"
method="GET"
params='orderLinkId=' + orderLinkId
HTTP_Request(endpoint,method,params,"List")

#Cancel Order
endpoint="/spot/v3/private/cancel-order"
method="POST"
params='{"orderLinkId":"' +  orderLinkId +'"}'
HTTP_Request(endpoint,method,params,"Cancel")
