import asyncio
import websockets
import json
import hmac
import hashlib
from datetime import datetime


async def authenticate(ws):
    api_key = "xxxxxxxxxx"
    api_secret = "xxxxxxxxxx"
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
        "reqId": "sub-003",
        "header": {
            "X-BAPI-TIMESTAMP": str(int(datetime.now().timestamp() * 1000)),
            "X-BAPI-RECV-WINDOW": "8000",
        },
        "op": "order.create",
        "args": [
            {
                "category": "linear",
                "orderLinkId": "crzyO2.0-1716303565222835550",
                "orderType": "Market",
                "positionIdx": 1,
                "price": "0.5496",
                "qty": "11",
                "side": "Buy",
                "symbol": "XRPUSDT",
                "reduceOnly": False,
                "closeOnTrigger": False
            }
        ]
    }

    await ws.send(json.dumps(sub_msg))
    print("Subscription message sent.")


async def on_ping(ws):
    while True:
        try:
            print('Sending ping')
            await ws.send(json.dumps({'op': 'ping'}))
            await asyncio.sleep(10)  # Sleep for 10 seconds
        except websockets.exceptions.ConnectionClosed:
            print("WebSocket connection closed.")
            break


async def listen_messages(ws):
    try:
        while True:
            message = await ws.recv()
            print(f"Message received: {message}")
    except websockets.exceptions.ConnectionClosed:
        print("WebSocket connection closed.")


async def main():
    uri = "wss://stream-testnet.bybit.com/v5/trade"
    async with websockets.connect(uri) as ws:
        await authenticate(ws)
        await send_subscription(ws)
        listen_task = asyncio.create_task(listen_messages(ws))
        ping_task = asyncio.create_task(on_ping(ws))
        await asyncio.gather(listen_task, ping_task)


# Run the main function in an asyncio event loop
if __name__ == "__main__":
    asyncio.run(main())
