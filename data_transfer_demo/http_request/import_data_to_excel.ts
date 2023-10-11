import axios, { AxiosResponse } from 'axios';
import * as ExcelJS from 'exceljs';
import * as crypto from 'crypto';


const api_key = 'xxxxxxxxxx';
const secret_key = 'xxxxxxxxxxxxx';
const recv_window = '5000';
const url = 'https://api-testnet.bybit.com';

function genSignature(payload: string): string {
    const timeStamp = (Date.now()).toString();
    const param_str = timeStamp + api_key + recv_window + payload;
    const hash = crypto.createHmac('sha256', secret_key).update(param_str).digest('hex');
    return hash;
}

async function HTTP_Request(endPoint: string, method: 'GET' | 'POST', payload: string, info: string) {
    const timeStamp = (Date.now()).toString();
    const signature = genSignature(payload);

    const headers = {
        'X-BAPI-API-KEY': api_key,
        'X-BAPI-SIGN': signature,
        'X-BAPI-SIGN-TYPE': '2',
        'X-BAPI-TIMESTAMP': timeStamp,
        'X-BAPI-RECV-WINDOW': recv_window,
        'Content-Type': 'application/json'
    };

    let response: AxiosResponse<any, any>;
    if (method === "POST") {
        response = await axios.post(url + endPoint, payload, { headers });
    } else {
        response = await axios.get(url + endPoint + '?' + payload, { headers });
    }

    console.log(response.data);

    const listData = response.data.result.list;
    if (listData && listData.length > 0) {
        // Create a new Excel workbook and worksheet
        const workbook = new ExcelJS.Workbook();
        const worksheet = workbook.addWorksheet('LinearData');

        // Define headers based on your data structure
        const headers = Object.keys(listData[0]);
        worksheet.addRow(headers);

        // Populate data rows
        listData.forEach((item: { [x: string]: any; }) => {
            const row = [];
            headers.forEach((header) => {
                row.push(item[header]);
            });
            worksheet.addRow(row);
        });

        // Save the workbook to a file
        const excelFileName = 'BybitMarketDataTicker.xlsx';
        await workbook.xlsx.writeFile(excelFileName);
        console.log(`Data exported to ${excelFileName}`);
    }
}

const endpoint = '/v5/market/tickers';
const method: 'GET' | 'POST' = 'GET';
const params = 'category=linear';
HTTP_Request(endpoint, method, params, 'Market Ticker Data')
    .then(() => console.log('Done!'))
    .catch(error => console.error(error));