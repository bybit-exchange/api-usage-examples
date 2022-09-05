const crypto = require('crypto');
const axios = require('axios')
url='https://api-testnet.bybit.com/v2/private/order/create';

var apiKey = "xxxx";
var secret = "xxxx";
var timestamp = Date.now().toString();
var params = {
	"symbol":"BTCUSD",
	"side":"Buy",
	"qty":"1",
	"price":"10000",
	"order_type":"Limit",
	"timestamp":timestamp,
	"api_key" : apiKey,
	"time_in_force":"GoodTillCancel"
};

params["sign"]=getSignature(params,secret);

axios.post(url,params).then(res => console.log(res.data)).catch(err=>console.log(err));

function getSignature(parameters, secret) {
	var orderedParams = "";
	Object.keys(parameters).sort().forEach(function(key) {
	  orderedParams += key + "=" + parameters[key] + "&";
	});
	orderedParams = orderedParams.substring(0, orderedParams.length - 1);

	return crypto.createHmac('sha256', secret).update(orderedParams).digest('hex');
}

