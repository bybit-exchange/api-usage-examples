var url = require('url');
var WebSocket = require('ws');
var crypto = require('crypto');
var endpoint = "wss://stream-testnet.bybit.com/v5/private"
console.log('attempting to connect to WebSocket %j', endpoint);
var client = new WebSocket(endpoint);
const apiKey="XXXXXXXXX";
const apiSecret="XXXXXXXXX";
client.on('open', function () {
	console.log('"open" event!');
	console.log('WebSocket Client Connected');
	const expires = new Date().getTime() + 10000;
	const signature = crypto.createHmac("sha256", apiSecret).update("GET/realtime" + expires).digest("hex");
	const payload={
		op: "auth",
		args: [apiKey, expires.toFixed(0), signature],
	}
	client.send(JSON.stringify(payload));
	setInterval(()=>{client.ping()}, 30000);
	client.ping();
	client.send(JSON.stringify({"op": "subscribe", "args": ['order']}));
});

client.on('message', function (data) {
	console.log('"message" event! %j', JSON.parse(Buffer.from(data).toString()));
});
client.on('ping', function (data, flags) {
	console.log("ping received");
});
client.on('pong', function (data, flags) {
	console.log("pong received");
});
