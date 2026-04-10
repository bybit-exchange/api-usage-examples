import hashlib
import hmac
import json
import threading
import time
from datetime import datetime
from random import randrange

import requests

import websocket
from urllib3.connection import HTTPConnection

API_KEY = "xxxx"
API_SECRET = "xxxx"
SESSION = requests.Session()
URL = "https://api-testnet.bybit.com"
WSS_URL = "wss://stream-testnet.bybit.com"
TOPIC = "execution"
SYMBOL = "BTCUSDT"
TOPIC_PUBLIC = "publicTrade." + SYMBOL
TRADE_RESULTS = {}
TRADE_PUBLIC_RESULTS = {}

HTTPConnection.debuglevel = 0


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
ORDER_STATUS = {"init": False, "results": {}, "subtasks": []}


def place_public_usdt_order(payload, time_stamp, order_link_id):
    url = URL + "/v5/order/create"
    recv_window = str(5000)
    param_str = str(time_stamp) + API_KEY + recv_window + payload
    signature_hash = hmac.new(
        bytes(API_SECRET, "utf-8"),
        param_str.encode("utf-8"),
        hashlib.sha256,
    )
    signature = signature_hash.hexdigest()
    headers = {
        "X-BAPI-API-KEY": API_KEY,
        "X-BAPI-SIGN": signature,
        "X-BAPI-SIGN-TYPE": "2",
        "X-BAPI-TIMESTAMP": str(time_stamp),
        "X-BAPI-RECV-WINDOW": recv_window,
        "cdn-request-id": order_link_id,
        "Content-Type": "application/json",
    }
    ORDER_STATUS["results"][order_link_id] = {}
    ORDER_STATUS["results"][order_link_id]["orderPlaceTime"] = time_stamp
    response = SESSION.request("POST", url, headers=headers, data=payload)
    ORDER_STATUS["results"][order_link_id]["Traceid"] = response.headers["Traceid"]
    ORDER_STATUS["results"][order_link_id]["elapsed"] = int(response.elapsed.microseconds / 1000)


def place_order(side):
    current_time = int(time.time() * 1000)
    order_link_id = str(current_time) + "AK" + str(randrange(1000, 9999))
    payload = json.dumps(
        {
            "category": "linear",
            "symbol": SYMBOL,
            "side": side,
            "positionIdx": 0,
            "orderType": "Market",
            "qty": "0.001",
            "orderLinkId": order_link_id,
        }
    )
    place_public_usdt_order(payload, current_time, order_link_id)


def on_message(ws, message):
    ts = int(time.time() * 1000)
    data = json.loads(message)
    if "success" in data and data["success"] and data["ret_msg"] == "" and not ORDER_STATUS["init"]:
        ORDER_STATUS["conn_id"] = data["conn_id"]
        print("starting to place orders")
        thread = threading.Thread(target=loop_place_order, args=())
        ORDER_STATUS["subtasks"].append(thread)
        thread.start()
        ORDER_STATUS["init"] = True
    elif (
        "topic" in data
        and data["topic"] == TOPIC
        and "data" in data
        and data["data"][0]["orderLinkId"] in ORDER_STATUS["results"]
    ):
        TRADE_RESULTS[data["data"][0]["execId"]] = {}
        TRADE_RESULTS[data["data"][0]["execId"]]["privateTS"] = ts
        if data["data"][0]["execId"] in TRADE_PUBLIC_RESULTS:
            TRADE_RESULTS[data["data"][0]["execId"]]["publicTS"] = TRADE_PUBLIC_RESULTS[
                data["data"][0]["execId"]
            ]


def on_message_public(ws, message):
    ts = int(time.time() * 1000)
    data = json.loads(message)
    if "topic" in data and data["topic"] == TOPIC_PUBLIC:
        for trade in data["data"]:
            TRADE_PUBLIC_RESULTS[trade["i"]] = ts
            if trade["i"] in TRADE_RESULTS:
                TRADE_RESULTS[trade["i"]]["publicTS"] = ts


def on_error(ws, error):
    print("we got error")
    print("the type is " + type(error).__name__)


def on_close(ws):
    print("### about to close please don't close ###")


def send_auth(ws):
    expires = int((time.time() + 1) * 1000)
    value = f"GET/realtime{expires}"
    signature = str(hmac.new(
        bytes(API_SECRET, "utf-8"),
        bytes(value, "utf-8"),
        digestmod="sha256",
    ).hexdigest())
    ws.send(json.dumps({"op": "auth", "args": [API_KEY, expires, signature]}))


def on_open(ws):
    print("opened")
    send_auth(ws)
    print("send topic")
    ws.send(json.dumps({"op": "subscribe", "args": [TOPIC]}))


def on_open_public(ws):
    print("opened")
    print("send subscription " + TOPIC_PUBLIC)
    ws.send(json.dumps({"op": "subscribe", "args": [TOPIC_PUBLIC]}))


def on_pong(ws, *data):
    print("pong received")


def on_ping(ws, *data):
    now = datetime.now()
    dt_string = now.strftime("%d/%m/%Y %H:%M:%S")
    print("date and time =", dt_string)
    print("ping received")


def loop_place_order():
    while True:
        for i in range(10):
            if i % 2 == 0:
                place_order("Buy")
            elif i % 2 == 1:
                place_order("Sell")
        time.sleep(1)
        for trade_id in TRADE_RESULTS:
            print(
                trade_id
                + ":private-public="
                + str(TRADE_RESULTS[trade_id]["privateTS"] - TRADE_RESULTS[trade_id]["publicTS"])
            )


def connect_ws():
    ws = websocket.WebSocketApp(
        WSS_URL + "/v5/private",
        on_message=on_message,
        on_error=on_error,
        on_close=on_close,
        on_ping=on_ping,
        on_pong=on_pong,
        on_open=on_open,
    )
    ws.run_forever(
        ping_interval=15,
        ping_timeout=10,
    )


def connect_ws_public():
    ws = websocket.WebSocketApp(
        WSS_URL + "/v5/public/linear",
        on_message=on_message_public,
        on_error=on_error,
        on_close=on_close,
        on_ping=on_ping,
        on_pong=on_pong,
        on_open=on_open_public,
    )
    ws.run_forever(
        ping_interval=15,
        ping_timeout=10,
    )


if __name__ == "__main__":
    websocket.enableTrace(True)
    public_thread = threading.Thread(target=connect_ws_public, args=())
    private_thread = threading.Thread(target=connect_ws, args=())
    public_thread.start()
    time.sleep(1)
    private_thread.start()
    time.sleep(3)
