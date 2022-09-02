const crypto = require('crypto');
const axios = require('axios');

url='https://api-testnet.bybit.com';

var apiKey = "XXXXXXXXXX";
var secret = "XXXXXXXXXX";
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
endpoint="/unified/v3/private/order/create"
const orderLinkId = crypto.randomBytes(16).toString("hex");
var data = '{"symbol":"BTCUSDT","orderType":"Limit","side":"Buy","orderLinkId":"' +  orderLinkId + '","qty":"0.001","price":"10000","timeInForce":"GoodTillCancel","category":"linear"}';
await http_request(endpoint,"POST",data,"Create");

//Get unfilled Order List
endpoint="/unified/v3/private/order/unfilled-orders"
var data = 'symbol=BTCUSDT&category=linear&orderStatus=New&orderLinkId=' + orderLinkId;
await http_request(endpoint,"GET",data,"Unfilled Order List");

//Get Order List
endpoint="/unified/v3/private/order/list"
var data = 'symbol=BTCUSDT&category=linear&orderStatus=New&orderLinkId=' + orderLinkId ;
await http_request(endpoint,"GET",data,"Order List");

//Cancel order
endpoint="/unified/v3/private/order/cancel"
var data = '{"symbol": "BTCUSDT","category":"linear","orderLinkId":"' +  orderLinkId +'"}';
await http_request(endpoint,"POST",data,"Cancel");
}

//Create, List and Cancel Orders
TestCase()




