import hmac
import json
import logging
import time
import threading
import websocket

logging.basicConfig(filename='logfile_wrapper.log', level=logging.DEBUG,
                    format='%(asctime)s %(levelname)s %(message)s')
topic = "order"

def on_message(ws, message):
    data = json.loads(message)
    print(data)

def on_error(ws, error):
    print('we got error')
    print(error)

def on_close(ws):
    print("### about to close please don't close ###")

def send_auth(ws):
    key = 'XXXXXXXXXX'
    secret = 'XXXXXXXXXX'
    expires = int((time.time() + 10) * 1000)
    _val = f'GET/realtime{expires}'
    print(_val)
    signature = str(hmac.new(
        bytes(secret, 'utf-8'),
        bytes(_val, 'utf-8'), digestmod='sha256'
    ).hexdigest())
    ws.send(json.dumps({"op": "auth", "args": [key, expires, signature]}))

def on_pong(ws, *data):
    print('pong received')

def on_ping(ws, *data):
    print('ping received')

def on_open(ws):
    print('opened')
    send_auth(ws)
    print('send subscription ' + topic)
    ws.send(json.dumps({"op": "subscribe", "args": [topic]}))
    def pingPer(ws):
        while True:
          ws.send(json.dumps({'op': 'ping'}))
          time.sleep(15)
    t1 = threading.Thread(target=pingPer, args=(ws,))
    t1.start()

def connWS():
    ws = websocket.WebSocketApp("wss://stream-testnet.bybit.com/v5/private",
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
    websocket.enableTrace(True)
    connWS()
