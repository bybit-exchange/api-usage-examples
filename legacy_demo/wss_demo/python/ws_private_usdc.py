import requests
import hmac
import websocket
from websocket import create_connection
import threading
import time
import json
import argparse
import logging
logging.basicConfig(filename='logfile_wrapper.log', level=logging.DEBUG, format='%(asctime)s %(levelname)s %(message)s')
from datetime import datetime

def on_message(ws, message):
	print(json.loads(message))

def on_error(ws, error):
	print('we got error')
	print(error)

def on_close(ws):
	print("### about to close please don't close ###")

def send_auth(ws):
	key='xxxx'
	secret='xxxx'
	expires=int((time.time() +10) * 1000)
	_val = f'GET/realtime{expires}'
	print(_val)
	signature = str(hmac.new(
            bytes(secret, 'utf-8'),
            bytes(_val, 'utf-8'), digestmod='sha256'
        ).hexdigest())
	ws.send(json.dumps({"op": "auth","args": [key, expires, signature]}))

def on_pong(ws, *data):
	print('pong received')

def on_ping(ws, *data):
	print('ping received')

def on_open(ws):
	print('opened')
	send_auth(ws)
	ws.send(json.dumps({"op": "subscribe", "args": ["user.openapi.perp.position","user.openapi.perp.order","user.openapi.perp.trade","user.openapi.option.position","user.openapi.option.order","user.openapi.option.trade"]}))

def connWS():
	ws = websocket.WebSocketApp("wss://stream-testnet.bybit.com/trade/option/usdc/private/v1",
		on_message = on_message,
		on_error = on_error,
		on_close = on_close,
		on_ping = on_ping,
		on_pong = on_pong,
		on_open=on_open
	)
	ws.run_forever(
		#http_proxy_host='127.0.0.1',
		#http_proxy_port=1087,
		ping_interval=15,
		ping_timeout=10
	)

if __name__ == "__main__":
	websocket.enableTrace(True)
	connWS()
