import * as crypto from 'crypto';
import axios, { AxiosRequestConfig } from 'axios';

const url = 'https://api-testnet.bybit.com';

const apiKey = "xxxxxxxxxxxxxxxxx";
const secret = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
const recvWindow = 5000;
const timestamp = Date.now().toString();

function generateSignature(parameters: string, secret: string): string {
    return crypto.createHmac('sha256', secret).update(timestamp + apiKey + recvWindow + parameters).digest('hex');
}

async function http_request(endpoint: string, method: "GET" | "POST", data: string, Info: string): Promise<void> {
    const sign = generateSignature(data, secret);
    const fullendpoint = method === "POST" ? url + endpoint : url + endpoint + "?" + data;

    const config: AxiosRequestConfig = {
        method: method,
        url: fullendpoint,
        headers: {
            'X-BAPI-SIGN-TYPE': '2',
            'X-BAPI-SIGN': sign,
            'X-BAPI-API-KEY': apiKey,
            'X-BAPI-TIMESTAMP': timestamp,
            'X-BAPI-RECV-WINDOW': '5000',
            'Content-Type': 'application/json; charset=utf-8'
        },
        data: data
    };

    // console.log(`${Info} Calling....`);
    try {
        const response = await axios(config);
        console.log(JSON.stringify(response.data));
    } catch (error) {
        console.log(error);
    }
}

async function TestCase(): Promise<void> {
    const orderLinkId = crypto.randomBytes(16).toString("hex");

    // Create Order
    let endpoint = "/v5/order/create";
    let data = `{"category":"linear","symbol": "BTCUSDT","side": "Buy","positionIdx": 0,"orderType": "Limit","qty": "0.001","price": "10000","timeInForce": "GTC","orderLinkId": "${orderLinkId}"}`;
    await http_request(endpoint, "POST", data, "Create");

    // Get unfilled Order List
    endpoint = "/v5/order/realtime";
    data = 'category=linear&symbol=BTCUSDT';
    await http_request(endpoint, "GET", data, "Realtime");

    // Cancel order
    endpoint = "/v5/order/cancel";
    data = `{"category":"linear","symbol": "BTCUSDT","orderLinkId": "${orderLinkId}"}`;
    await http_request(endpoint, "POST", data, "Cancel");
}

// Create, List and Cancel Orders
TestCase();
