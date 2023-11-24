const axios = require('axios');
const crypto = require('crypto');
const ExcelJS = require('exceljs');

const api_key = 'xxxxxxxxxxxxxxxxx';
const secret_key = 'xxxxxxxxxxxxxxxxx';
const recv_window = '5000';
const url = 'https://api-testnet.bybit.com';

function genSignature(payload) {
    const timeStamp = Date.now().toString();
    const param_str = timeStamp + api_key + recv_window + payload;
    const hash = crypto.createHmac('sha256', secret_key).update(param_str).digest('hex');
    return hash;
}

async function HTTP_Request(endPoint, method, payload, info) {
    const timeStamp = Date.now().toString();
    const signature = genSignature(payload);

    const headers = {
        'X-BAPI-API-KEY': api_key,
        'X-BAPI-SIGN': signature,
        'X-BAPI-SIGN-TYPE': '2',
        'X-BAPI-TIMESTAMP': timeStamp,
        'X-BAPI-RECV-WINDOW': recv_window,
        'Content-Type': 'application/json'
    };

    let response;
    if (method === "POST") {
        response = await axios.post(url + endPoint, payload, { headers });
    } else {
        response = await axios.get(url + endPoint + '?' + payload, { headers });
    }

    console.log(response.data);
    console.log(`${info} Elapsed Time: ${response.elapsedTime}`);

    const listData = response.data.result.list;
    if (listData && listData.length > 0) {
        const workbook = new ExcelJS.Workbook();
        const worksheet = workbook.addWorksheet('Data');

        // Create header row
        const headers = Object.keys(listData[0]);
        worksheet.addRow(headers);

        // Populate data
        listData.forEach((item) => {
            const row = [];
            headers.forEach((header) => {
                row.push(item[header]);
            });
            worksheet.addRow(row);
        });

        // Save the workbook to a file
        await workbook.xlsx.writeFile('BybitMarketDataTicker.xlsx');
    }
}

const endpoint = '/v5/market/tickers';
const method = 'GET';
const params = 'category=linear';
HTTP_Request(endpoint, method, params, 'Market Ticker Data')
    .then(() => console.log('Done!'))
    .catch(error => console.error(error));
