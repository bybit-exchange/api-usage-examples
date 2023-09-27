import WebSocket from 'ws';

const endpoint: string = "wss://stream-testnet.bybit.com/v5/public/linear";
console.log('attempting to connect to WebSocket %j', endpoint);
const client = new WebSocket(endpoint);

client.on('open', () => {
    console.log('"open" event!');
    console.log('WebSocket Client Connected');
    setInterval(() => { client.ping(); }, 30000);
    client.ping();
    client.send(JSON.stringify({ "op": "subscribe", "args": ["orderbook.50.BTCUSDT"] }));
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
