import hmac
import websocket
import threading
import time
from Crypto.PublicKey import RSA
import json
import base64
from Crypto.Hash import SHA256
from Crypto.Signature import PKCS1_v1_5


class StreamManager(object):
    def __init__(self, ws_url: str, topic_list: list, rsa_api_key: str, private_key: str = None):
        self.ws = None
        self.ws_url = ws_url
        self.topic_list = topic_list
        self.rsa_api_key = rsa_api_key
        self.private_key = private_key

    @staticmethod
    def on_message(ws, message):
        print(message)

    @staticmethod
    def on_error(ws, error):
        print(f"we got error:{error}")

    @staticmethod
    def on_close(ws):
        print("### about to close please don't close ###")

    def on_open(self, ws):
        def send_auth(ws):
            expires = int((time.time() + 1000000000) * 1000)
            _val = f'GET/realtime{expires}'
            private_key = RSA.importKey(self.private_key)
            h = SHA256.new(_val.encode('utf-8'))
            signature_temp = PKCS1_v1_5.new(private_key).sign(h)
            result = base64.b64encode(signature_temp).decode()

            auth_data = json.dumps({
                "op": "auth",
                "args": [self.rsa_api_key, expires, result]
            })
            print(auth_data)
            ws.send(auth_data)

        t0 = threading.Thread(target=send_auth, args=(ws,))
        t0.start()

        def pingPer(ws):
            while True:
                ping_data = json.dumps({
                    "op": "ping",
                    "args": [[int((time.time() + 1) * 1000)]]
                })

                ws.send(ping_data)
                print(ping_data)
                time.sleep(20)

        t1 = threading.Thread(target=pingPer, args=(ws,))
        t1.start()
        subscribe_data = json.dumps({
            "op": "subscribe",
            "args": self.topic_list
        })
        print(subscribe_data)
        time.sleep(1)
        ws.send(subscribe_data)

    def start(self):
        self.ws = websocket.WebSocketApp(
            url=self.ws_url,
            on_message=lambda ws, message: self.on_message(ws, message),
            on_error=lambda ws, error: self.on_error(ws, error),
            on_close=lambda ws: self.on_close(ws),
            on_open=lambda ws: self.on_open(ws)
        )
        self.ws.run_forever()


if __name__ == "__main__":
    private_key_file = "xxxx_private.pem"  # private pem file name
    with open(f"/Users/xxxxx/{private_key_file}", "r") as pk:
        private_key_str = pk.read()  # 获取private_key

    testnet_domain = "stream-testnet.bybit.com"  # wss testnet
    mainnet_domain = "stream.bybit.com"  # wss mainnet

    contract_path = "/contract/private/v3"  # contract v3
    unified_path = "/unified/private/v3"  # unified v3
    uta_path = "/v5/private"  # v5

    url_wss = f"wss://{testnet_domain}{contract_path}"

    rsa_apiKey = "XXXXXXXX"  # the one that provided by Bybit after you input rsa public key
    privateKey = private_key_str

    v3_contract_topics = ["user.order.contractAccount", "user.position.contractAccount", "user.wallet.contractAccount",
                          "user.execution.contractAccount"]

    v3_unified_topic = ["user.order.unifiedAccount", "user.position.unifiedAccount", "user.execution.unifiedAccount",
                        "user.wallet.unifiedAccount"]

    v5_topics = ["order", "position", "wallet", "execution"]

    sm = StreamManager(ws_url=url_wss, topic_list=v5_uta_topics, rsa_api_key=rsa_apiKey, private_key=privateKey)
    sm.start()
