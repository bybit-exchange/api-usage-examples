import numpy as np
import os
import threading
import hashlib
import hmac
import json
import logging
import logging.config
import logging.handlers
import time
from datetime import datetime
from random import randrange
from functools import reduce
import re
import requests
import sys
apiKey='xxxx'
apiSecret='xxxx'
session=requests.Session()
URL="https://api.bybit.com"
topic="order"
symbol="BTC-29SEP23-8000-C"
wssResp={}
wssResp['responseHeaders']={}

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
orderStatus['results']={}
orderStatus['subtasks']=[]

def placeV5Option(payload,timeStamp,orderLinkId):
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
    orderStatus['results'][orderLinkId]={}
    orderStatus['results'][orderLinkId]['orderPlaceTime']=timeStamp
    response = session.request("POST", url, headers=headers, data=payload)
    orderStatus['results'][orderLinkId]['Traceid']=response.headers["Traceid"]
    orderStatus['results'][orderLinkId]['elapsed']=int(response.elapsed.microseconds/1000)
    #print(response.text)
    #orderStatus['results'][orderLinkId]['responseText']=response.text
    #orderStatus[orderLinkId]['_client']=response.raw._original_response._client

def placeOrder():
    currentTime=int(time.time()*1000)
    orderLinkId=str(currentTime)+'AK'+str(randrange(1000,9999))
    placeV5Option(json.dumps({"category":"option","symbol": symbol,"side": "Buy","orderType": "Limit","qty": "0.01","price": "5","timeInForce": "IOC","orderLinkId": orderLinkId}),currentTime,orderLinkId)

def on_message(ws, message):
    data = json.loads(message)
    if 'success' in data and data['success'] and data['ret_msg'] == "" and not orderStatus['init']:
        orderStatus["conn_id"]=data["conn_id"]
        x=threading.Thread(target=loopPlaceOrder,args=())
        orderStatus['subtasks'].append(x)
        x.start()
        orderStatus['init']=True
    #elif 'topic' in data and data['topic'] == topic and 'data' in data and data['data'][0]['orderLinkId'] in orderStatus and data['data'][0]['orderStatus']=='New':
    elif 'topic' in data and data['topic'] == topic and 'data' in data and data['data'][0]['orderLinkId'] in orderStatus["results"] and data['data'][0]['orderStatus']=='Cancelled':
        orderStatus["results"][data['data'][0]['orderLinkId']]['orderPlaceSend2Create']=int(data['data'][0]['createdTime'])-orderStatus["results"][data['data'][0]['orderLinkId']]['orderPlaceTime']
        orderStatus["results"][data['data'][0]['orderLinkId']]['orderPlaceRoundTrip']=int(time.time()*1000)-orderStatus["results"][data['data'][0]['orderLinkId']]['orderPlaceTime']
        orderStatus["results"][data['data'][0]['orderLinkId']]['wss']=data

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
    time.sleep(1)
    for key in orderStatus["results"].keys():
        if "orderPlaceRoundTrip" not in orderStatus["results"][key]:
            print(key)
            print(orderStatus["results"][key])
    print("V5 Option mean is "+str(np.mean([orderStatus["results"][key]["orderPlaceRoundTrip"] for key in orderStatus["results"].keys()])))
    for i in [0.1,1,5,10,25,50,75,90,95,97,99,99.9]:
        print("V5 Option "+str(i)+"th is "+str(np.percentile([orderStatus["results"][key]["orderPlaceRoundTrip"] for key in orderStatus["results"].keys()],i)))

def connWS():
    ws = websocket.WebSocketApp("wss://stream.bybit.com/v5/private",
        on_message = on_message,
        on_error = on_error,
        on_close = on_close,
        on_ping=on_ping,
        on_pong=on_pong,
        on_open=on_open
    )
    ws_thread=threading.Thread(target=lambda: ws.run_forever())
    ws_thread.daemon=True
    ws_thread.start()

websocket.enableTrace(True)
connWS()
while True:
    if orderStatus['init'] is True:
        for x in orderStatus['subtasks']:
            x.join()
        break
    time.sleep(1)
