import os
import threading
import hashlib
import hmac
import json
import time
from datetime import datetime
from random import randrange
from functools import reduce
import re
import requests
import sys
import numpy as np
apiKey='xxxx'
apiSecret='xxxx'
session=requests.Session()
URL="https://api.bybit.com"
#session.request("GET",URL+"/v2/public/time")
topic="order"
symbol="BTCUSDT"

import websocket
from urllib3.connection import HTTPConnection
HTTPConnection.debuglevel=0
def getresponse(self, *args, **kwargs):
    response = self._old_getresponse(*args, **kwargs)
    if self.sock:
        self.peer = self.sock.getpeername()
        response._peer = self.sock.getpeername()
        response._client = self.sock
    else:
        response.peer = None
    return response

HTTPConnection._old_getresponse = HTTPConnection.getresponse
HTTPConnection.getresponse = getresponse
orderStatus={}
orderStatus['init']=False

def placeV5USDTOrder(payload,timeStamp,orderLinkId):
    url=URL+"/v5/order/create"
    dataObj=json.loads(payload)
    recv_window=str(5000)
    param_str= str(timeStamp) + apiKey + recv_window + payload
    hash = hmac.new(bytes(apiSecret, "utf-8"), param_str.encode("utf-8"),hashlib.sha256)
    signature = hash.hexdigest()
    headers = {
        'X-BAPI-API-KEY': apiKey,
        'X-BAPI-SIGN': signature,
        'X-BAPI-SIGN-TYPE': '2',
        'X-BAPI-TIMESTAMP': str(timeStamp),
        'X-BAPI-RECV-WINDOW': recv_window,
        'cdn-request-id': orderLinkId,
        'Content-Type': 'application/json'
    }
    orderStatus[orderLinkId]={}
    orderStatus[orderLinkId]['orderPlaceTime']=timeStamp
    response = session.request("POST", url, headers=headers, data=payload)
    #print(response.text)
    #orderStatus[orderLinkId]['headers']=response.headers
    #orderStatus[orderLinkId]['responseText']=response.text
    #orderStatus[orderLinkId]['_client']=response.raw._original_response._client
    orderStatus[orderLinkId]['elapsed']=int(response.elapsed.microseconds/1000)

def placeOrder():
    currentTime=int(time.time()*1000)
    orderLinkId=str(currentTime)+'CDN'+str(randrange(1000,9999))
    #placeV3USDTOrder(json.dumps({"symbol": symbol,"side": "Buy","positionIdx": 0,"orderType": "Limit","qty": "0.001","price": "2400","timeInForce": "GoodTillCancel","orderLinkId": orderLinkId,"reduce_only": "false","closeOnTrigger": "false"}),currentTime,orderLinkId)
    placeV5USDTOrder(json.dumps({"category":"linear","symbol": symbol,"side": "Buy","positionIdx": 0,"orderType": "Limit","qty": "0.001","price": "3000","timeInForce": "IOC","orderLinkId": orderLinkId,"reduce_only": "false","closeOnTrigger": "false"}),currentTime,orderLinkId)

def on_message(ws, message):
    data = json.loads(message)
    if 'success' in data and data['success'] and data['ret_msg'] == "" and not orderStatus['init']:
        orderStatus["conn_id"]=data["conn_id"]
        x=threading.Thread(target=loopPlaceOrder,args=())
        x.start()
        orderStatus['init']=True
    #elif 'topic' in data and data['topic'] == topic and 'data' in data and data['data'][0]['orderLinkId'] in orderStatus and data['data'][0]['orderStatus']=='New':
    elif 'topic' in data and data['topic'] == topic and 'data' in data and data['data'][0]['orderLinkId'] in orderStatus and data['data'][0]['orderStatus']=='Cancelled':
        orderStatus[data['data'][0]['orderLinkId']]['orderPlaceSend2Create']=int(data['data'][0]['createdTime'])-orderStatus[data['data'][0]['orderLinkId']]['orderPlaceTime']
        orderStatus[data['data'][0]['orderLinkId']]['orderPlaceRoundTrip']=int(time.time()*1000)-orderStatus[data['data'][0]['orderLinkId']]['orderPlaceTime']


def on_error(ws, error):
    print('we got error')
    print("the type is "+type(error).__name__)

def on_close(ws):
    print("### about to close please don't close ###")

def send_auth(ws):
    expires=int((time.time() + 1) * 1000)
    _val = f'GET/realtime{expires}'
    signature = str(hmac.new(
        bytes(apiSecret, 'utf-8'),
        bytes(_val, 'utf-8'), digestmod='sha256'
    ).hexdigest())
    ws.send(json.dumps({"op":"auth","args":[apiKey,expires,signature]}))

def on_open(ws):
    print('opened')
    send_auth(ws)
    print('send topic')
    ws.send(json.dumps({"op": "subscribe", "args": [topic]}))

def on_open(ws):
    print('opened')
    send_auth(ws)
    print('send topic')
    ws.send(json.dumps({"op": "subscribe", "args": [topic]}))

def on_pong(ws, *data):
    print('pong received')

def on_ping(ws, *data):
    now = datetime.now()
    dt_string = now.strftime("%d/%m/%Y %H:%M:%S")
    print("date and time =", dt_string)
    print('ping received')

def loopPlaceOrder():
    for i in range(10):
        placeOrder()

def connWS():
    ws = websocket.WebSocketApp("wss://stream.bybit.com/v5/private",
        on_message = on_message,
        on_error = on_error,
        on_close = on_close,
        on_ping=on_ping,
        on_pong=on_pong,
        on_open=on_open
    )

    ws_thread=threading.Thread(target=lambda: ws.run_forever(
        ping_interval=15,
        ping_timeout=10
    ))
    ws_thread.daemon=True
    ws_thread.start()

websocket.enableTrace(True)
connWS()


time.sleep(5)
print(json.dumps(orderStatus,indent=4))
del orderStatus["init"]
del orderStatus["conn_id"]
for i in [0.1,1,5,10,25,50,75,90,95,97,99,99.9]:
    print(str(i)+" is "+str(np.percentile([orderStatus[key]["orderPlaceRoundTrip"] for key in orderStatus.keys()],i)))
