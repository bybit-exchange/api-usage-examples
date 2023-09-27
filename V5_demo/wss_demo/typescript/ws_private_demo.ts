import WebSocket from 'ws';
import * as crypto from 'crypto';

const endpoint: string = "wss://stream-testnet.bybit.com/v5/private";
console.log('attempting to connect to WebSocket %j', endpoint);

const client: WebSocket = new WebSocket(endpoint);
const apiKey: string = "xxxxxxxxxxxxxxxx";
const apiSecret: string = "xxxxxxxxxxxxxxxxxxxxxxxx";

client.on('open', () => {
    console.log('"open" event!');
    console.log('WebSocket Client Connected');
    
    const expires: number = new Date().getTime() + 10000;
    const signature: string = crypto.createHmac("sha256", apiSecret).update("GET/realtime" + expires).digest("hex");
    
    const payload = {
        op: "auth",
        args: [apiKey, expires.toFixed(0), signature]
    };

    client.send(JSON.stringify(payload));
    setInterval(() => { client.ping(); }, 30000);
    client.ping();
    client.send(JSON.stringify({"op": "subscribe", "args": ['order']}));
});

client.on('message', (data: WebSocket.Data) => {
    console.log('"message" event! %j', JSON.parse(data.toString()));
});

client.on('ping', (data: Buffer) => {
    console.log("ping received");
});

client.on('pong', (data: Buffer) => {
    console.log("pong received");
});
