import * as crypto from 'crypto';
import axios from 'axios';

const url = 'https://api-testnet.bybit.com';
const apiKey = "xxxxxxxxxxxx";
const secret = "xxxxxxxxxxxxxxxxxxxxxxxxxxx";
const recvWindow = 5000;
const timestamp = Date.now().toString();

function generateSignature(parameters: string, secretKey: string): string {
    return crypto.createHmac('sha256', secretKey).update(timestamp + apiKey + recvWindow + parameters).digest('hex');
}

async function httpRequest(
    endpoint: string,
    method: string,
    data: string,
    info: string,
): Promise<void> {
    const sign = generateSignature(data, secret);
    let fullEndpoint: string;

    if (method === "POST") {
        fullEndpoint = url + endpoint;
    } else {
        fullEndpoint = url + endpoint + "?" + data;
        data = "";
    }

    const headers: Record<string, string> = {
        'X-BAPI-SIGN-TYPE': '2',
        'X-BAPI-SIGN': sign,
        'X-BAPI-API-KEY': apiKey,
        'X-BAPI-TIMESTAMP': timestamp,
        'X-BAPI-RECV-WINDOW': recvWindow.toString()
    };

    if (method === "POST") {
        headers['Content-Type'] = 'application/json; charset=utf-8';
    }

    const config = {
        method: method,
        url: fullEndpoint,
        headers: headers,
        data: data
    };

    console.log(info + " Calling....");
    await axios(config)
        .then(function (response) {
            console.log(JSON.stringify(response.data));
        })
        .catch(function (error) {
            console.log(error.response.data);
        });
}

async function testCase(): Promise<void> {
    const orderLinkId = crypto.randomBytes(16).toString("hex");

    let endpoint = "/v5/order/create";
    let data = `{"category":"linear","symbol": "BTCUSDT","side": "Buy","positionIdx": 0,"orderType": "Limit","qty": "0.001","price": "10000","timeInForce": "GTC","orderLinkId": "${orderLinkId}"}`;
    await httpRequest(endpoint, "POST", data, "Create");

    endpoint = "/v5/order/realtime";
    data = 'category=linear&symbol=BTCUSDT';
    await httpRequest(endpoint, "GET", data, "Realtime");

    endpoint = "/v5/order/cancel";
    data = `{"category":"linear","symbol": "BTCUSDT","orderLinkId": "${orderLinkId}"}`;
    await httpRequest(endpoint, "POST", data, "Cancel");
}

testCase();
