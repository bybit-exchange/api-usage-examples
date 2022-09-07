import json
import logging
import time
from datetime import datetime
import websocket

logging.basicConfig(filename='logfile_wrapper.log', level=logging.DEBUG,
                    format='%(asctime)s %(levelname)s %(message)s')

topic = "orderbook.25.BTCUSDT"

def on_message(ws, message):
    data = json.loads(message)
    print(data)

def on_error(ws, error):
    print('we got error')
    print(error)
    print('print error complete')

def on_close(ws):
    print("### about to close please don't close ###")

def on_open(ws):
    print('opened')
    ws.send(json.dumps({"op": "subscribe", "args": [topic]}))

def on_pong(ws, *data):
    print('pong received')

def on_ping(ws, *data):
    now = datetime.now()
    dt_string = now.strftime("%d/%m/%Y %H:%M:%S")
    print("date and time =", dt_string)
    print('ping received')

def connWS():
    ws = websocket.WebSocketApp(
        "wss://stream-testnet.bybit.com/contract/usdt/public/v3",
        on_message=on_message,
        on_error=on_error,
        on_close=on_close,
        on_ping=on_ping,
        on_pong=on_pong,
        on_open=on_open
    )
    ws.run_forever(
		#http_proxy_host='127.0.0.1',
		#http_proxy_port=1087,
		ping_interval=20,
		ping_timeout=10
	)


if __name__ == "__main__":
    #websocket.enableTrace(True)
    connWS()
