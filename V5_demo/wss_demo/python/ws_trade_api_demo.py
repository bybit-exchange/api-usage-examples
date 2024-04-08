import asyncio
import websockets
import json
import hmac
import hashlib
from datetime import datetime


async def authenticate(ws):
    api_key = "xxxxxx"
    api_secret = "xxxxxxxxxxx"
    expires = int((datetime.now().timestamp() + 10) * 1000)  # Expires in 10 seconds
    signature = hmac.new(api_secret.encode(), f'GET/realtime{expires}'.encode(), hashlib.sha256).hexdigest()

    auth_msg = {
        "reqId": "auth",
        "op": "auth",
        "args": [api_key, expires, signature]
    }

    await ws.send(json.dumps(auth_msg))
    response = await ws.recv()
    print(f"Auth response: {response}")


async def send_subscription(ws):
    sub_msg = {
        "reqId": "sub-001",
        "headers": {
            "X-BAPI-TIMESTAMP": str(int(datetime.now().timestamp() * 1000)),
            "X-BAPI-RECV-WINDOW": "8000",
        },
        "op": "order.create",
        "args": [
            {
                "symbol": "XRPUSDT",
                "side": "Buy",
                "orderType": "Market",
                "qty": "10",
                "category": "spot"
            }
        ]
    }

    await ws.send(json.dumps(sub_msg))
    print("Subscription message sent.")


async def listen_messages(ws):
    while True:
        message = await ws.recv()
        print(f"Message received: {message}")


async def main():
    uri = "wss://stream-testnet.bybit.com/v5/trade"
    async with websockets.connect(uri) as ws:
        await authenticate(ws)
        await send_subscription(ws)
        await listen_messages(ws)


# Run the main function in an asyncio event loop
if __name__ == "__main__":
    asyncio.run(main())
