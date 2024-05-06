const WebSocket = require('ws');
const crypto = require('crypto');

const apiKey = 'your_api_key';
const apiSecret = 'your_api_secret';
const wsUrl = 'wss://stream-testnet.bybit.com/v5/trade';

const ws = new WebSocket(wsUrl);

ws.on('open', function open() {
    authenticate(ws);
    setTimeout(() => {
        sendSubscription(ws);
    }, 2000); // Wait for authentication to complete
});

ws.on('message', function incoming(data) {
    console.log(data);
});

function authenticate(ws) {
    const expires = Date.now() + 10000; // 10 seconds from now
    const signature = crypto.createHmac('sha256', apiSecret).update(`GET/realtime${expires}`).digest('hex');

    const authMessage = {
        reqId: 'auth',
        op: 'auth',
        args: [apiKey, expires, signature],
    };

    ws.send(JSON.stringify(authMessage));
}

function sendSubscription(ws) {
    const subscriptionMessage = {
        reqId: 'sub-001',
        header: {
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
