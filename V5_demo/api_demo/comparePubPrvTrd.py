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
from pybit import HTTP
from datetime import datetime
from random import randrange
from functools import reduce
import re
import requests
import sys
apiKey='xxxx'
apiSecret='xxxx'
session=requests.Session()
URL="https://api-testnet.bybit.com"
WssURL="wss://stream-testnet.bybit.com"
topic="execution"
symbol="BTCUSDT"
topicPub="publicTrade."+symbol
wssResp={}
wssResp['responseHeaders']={}
tradeResults={}
tradePublicResults={}

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

def placePubUSDTOrder(payload,timeStamp,orderLinkId):
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

def placeOrder(side):
    currentTime=int(time.time()*1000)
    orderLinkId=str(currentTime)+'AK'+str(randrange(1000,9999))
    placePubUSDTOrder(json.dumps({"category":"linear","symbol": symbol,"side": side,"positionIdx": 0,"orderType": "Market","qty": "0.001","orderLinkId": orderLinkId}),currentTime,orderLinkId)

def on_message(ws, message):
    ts=int(time.time()*1000)
    data = json.loads(message)
    if 'success' in data and data['success'] and data['ret_msg'] == "" and not orderStatus['init']:
        orderStatus["conn_id"]=data["conn_id"]
        print("starting to place orders")
        x=threading.Thread(target=loopPlaceOrder,args=())
        orderStatus['subtasks'].append(x)
        x.start()
        orderStatus['init']=True
    elif 'topic' in data and data['topic'] == topic and 'data' in data and data['data'][0]['orderLinkId'] in orderStatus["results"]:
        tradeResults[data['data'][0]['execId']]={}
        tradeResults[data['data'][0]['execId']]["privateTS"]=ts
        if data['data'][0]['execId'] in tradePublicResults:
            tradeResults[data['data'][0]['execId']]["publicTS"]=tradePublicResults[data['data'][0]['execId']]

def on_messagePub(ws, message):
    ts=int(time.time()*1000)
    data = json.loads(message)
    currentTime=int(time.time()*1000)
    if "topic" in data and data["topic"]==topicPub:
        for trade in data["data"]:
            tradePublicResults[trade["i"]]=ts
            if trade["i"] in tradeResults:
                tradeResults[trade["i"]]["publicTS"]=ts

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

def on_openPub(ws):
    print('opened')
    print('send subscription ' + topicPub)
    ws.send(json.dumps({"op": "subscribe", "args": [topicPub]}))

def on_pong(ws, *data):
    print('pong received')

def on_ping(ws, *data):
    now = datetime.now()
    dt_string = now.strftime("%d/%m/%Y %H:%M:%S")
    print("date and time =", dt_string)
    print('ping received')

def loopPlaceOrder():
    while True:
        for i in range(10):
            if i%2==0:
                placeOrder("Buy")
            elif i%2==1:
                placeOrder("Sell")
        time.sleep(1)
        for tradeId in tradeResults:
            print(tradeId+":private-public="+str(tradeResults[tradeId]["privateTS"]-tradeResults[tradeId]["publicTS"]))

def connWs():
    ws = websocket.WebSocketApp(WssURL+"/v5/private",
        on_message = on_message,
        on_error = on_error,
        on_close = on_close,
        on_ping=on_ping,
        on_pong=on_pong,
        on_open=on_open
    )
    ws.run_forever(
        ping_interval=15,
        ping_timeout=10
    )

def connWsPub():
    ws = websocket.WebSocketApp(WssURL+"/v5/public/linear",
        on_message=on_messagePub,
        on_error=on_error,
        on_close=on_close,
        on_ping=on_ping,
        on_pong=on_pong,
        on_open=on_openPub
    )
    ws.run_forever(
        ping_interval=15,
        ping_timeout=10
    )

if __name__ == "__main__":
    websocket.enableTrace(True)
    x=threading.Thread(target=connWsPub,args=())
    y=threading.Thread(target=connWs,args=())
    x.start()
    time.sleep(1)
    y.start()
    time.sleep(3)
        #print(tradeResults[tradeId]["privateTS"]-tradeResults[tradeId]["publicTS"])
