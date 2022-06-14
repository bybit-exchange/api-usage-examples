var axios = require('axios');
querystring = require('querystring');
const crypto = require('crypto');
const uuid = require('uuid');


var apiKey = "XXXXXXXXXXXXXXX";
var secret = "XXXXXXXXXXXXXXX";
var transferId = uuid.v4();
var qs="";
var amount="5";
var coin="USDT";
var timestamp = Date.now();
var unordered = { "transferId": transferId,    "amount": amount,    "coin": coin,    "from_account_type":"SPOT",    "to_account_type":"CONTRACT", "timestamp": timestamp,    "api_key": apiKey,    "recv_window": "50000"};

console.log(unordered);
//sort the unordered object and generate signature value
var keys = [],k, i, len;
for (k in unordered) {
  if (unordered.hasOwnProperty(k)) {
    keys.push(k);
  }
}
keys.sort();
len = keys.length;
for (i = 0; i < len; i++) {
  k = keys[i];
  qs=qs + k +'='+unordered[k] + '&'; //Generate QueryString
}
sign = crypto.createHmac('sha256', secret).update(qs.slice(0, -1)).digest('hex');
unordered["sign"] =sign; 

var config = {
  method: 'post',
  url: 'https://api-testnet.bybit.com/asset/v1/private/transfer',
  data : unordered
};

axios(config)
.then(function (response) {
  console.log(JSON.stringify(response.data));
})
.catch(function (error) {
  console.log(error);
});
