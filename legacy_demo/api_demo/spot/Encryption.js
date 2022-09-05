const crypto = require('crypto');
const axios = require('axios')
url='https://api-testnet.bybit.com/spot/v1/order';

var apiKey = "xxxx";
var secret = "xxxx";
var timestamp = Date.now().toString();
var params = {
	"symbol":"BTCUSDT",
	"side":"Buy",
	"qty":"0.001",
	"price":"10000",
	"type":"Limit",
	"timestamp":timestamp,
	"api_key" : apiKey,
	"timeInForce":"GTC"
};

paramsQueryString=getSignature(params, secret);
axios.post(url,paramsQueryString).then(res => console.log(res.data)).catch(err=>console.log(err));

function getSignature(parameters, secret) {
	var orderedParams = "";
	Object.keys(parameters).sort().forEach(function(key) {
	  orderedParams += key + "=" + parameters[key] + "&";
	});
	orderedParams = orderedParams.substring(0, orderedParams.length - 1);

	return orderedParams+'&sign='+crypto.createHmac('sha256', secret).update(orderedParams).digest('hex');
}

