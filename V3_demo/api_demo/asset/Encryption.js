const crypto = require('crypto');
const axios = require('axios');
const { v4: uuidv4 } = require('uuid');

url='https://api-testnet.bybit.com';

var apiKey = "XXXXXXXXXXXX";
var secret = "XXXXXXXXXXXX";
var recvWindow = 5000;
var timestamp = Date.now().toString();

function getSignature(parameters, secret) {
    return crypto.createHmac('sha256', secret).update(timestamp + apiKey + recvWindow + parameters).digest('hex');
}

async function http_request(endpoint,method,data,Info) {
    var sign=getSignature(data,secret);
    if(method=="POST")
    {
        fullendpoint=url+endpoint;
    }
    else{
        fullendpoint=url+endpoint+"?"+data;
        data="";
    }
    //endpoint=url+endpoint
    var config = {
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
      data : data
    };
    console.log(Info + " Calling....");
    await axios(config)
    .then(function (response) {
        console.log(JSON.stringify(response.data));
      })
      .catch(function (error) {
        console.log(error);
      });
}

//Create Order
async function TestCase()
{
//Create Internal Transfer ( SPOT to UNIFIED )
endpoint="/asset/v3/private/transfer/inter-transfer"
method="POST"
transferId=uuidv4();
var params='{"transferId": "' + transferId +  '","coin": "USDT","amount": "1","from_account_type": "SPOT","to_account_type": "UNIFIED"}'
await http_request(endpoint,method,params,"InternalTransfer")

//Query Internal Transfer List
endpoint="/asset/v3/private/transfer/inter-transfer/list/query"
method="GET"
params='coin=USDT'
await http_request(endpoint,method,params,"InternalTransferList")
}

//Internal Transfer and List
TestCase()




