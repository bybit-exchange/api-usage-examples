import WebSocket from 'ws';
import crypto from 'crypto';

const apiKey: string = 'your_api_key';
const apiSecret: string = 'your_api_secret';
const wsUrl: string = 'wss://stream-testnet.bybit.com/v5/trade';

const ws: WebSocket = new WebSocket(wsUrl);

ws.on('open', () => {
    authenticate(ws);
    setTimeout(() => {
        sendSubscription(ws);
    }, 2000); // Wait for authentication to complete
});

ws.on('message', (data: string) => {
    console.log(data);
});

function authenticate(ws: WebSocket): void {
    const expires: number = Date.now() + 10000; // 10 seconds from now
    const signature: string = crypto.createHmac('sha256', apiSecret).update(`GET/realtime${expires}`).digest('hex');

    const authMessage: any = {
        reqId: 'auth',
        op: 'auth',
        args: [apiKey, expires, signature],
    };

    ws.send(JSON.stringify(authMessage));
}

function sendSubscription(ws: WebSocket): void {
    const subscriptionMessage: any = {
        reqId: 'sub-001',
        headers: {
            "X-BAPI-TIMESTAMP": String(Date.now()),
            "X-BAPI-RECV-WINDOW": "8000",
        },
        op: "order.create",
        args: [
            {
                "symbol": "XRPUSDT",
                "side": "Buy",
                "orderType": "Market",
                "qty": "10",
                "category": "spot"
            }
        ]
    };

    ws.send(JSON.stringify(subscriptionMessage));
}
