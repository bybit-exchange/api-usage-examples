const crypto = require('crypto');
const axios = require('axios')
url='https://api-testnet.bybit.com/option/usdc/openapi/private/v1/place-order';

var apiKey = "xxxx";
var secret = "xxxx";
var headers={
	'X-BAPI-API-KEY':apiKey,
	'X-BAPI-TIMESTAMP':Date.now()
}
var timestamp = Date.now().toString();
var params = {
	"outRequestId":Date.now().toString(),
	"symbol":"BTC-28JAN22-140000-C",
	"orderType":"Limit",
	"side":"Sell",
	"timeInForce":"GoodTillCancel",
	"orderQty":"0.01",
	"orderPrice":"40"
};

headers["X-BAPI-SIGN"]=getSignature(headers,params, secret);

axios.post(url,params,
{
	headers:headers,
}).then(res => console.log(res.data)).catch(err=>console.log(err));

function getSignature(headers, params, secret) {
	var queryString = headers['X-BAPI-TIMESTAMP'].toString()+headers['X-BAPI-API-KEY']+JSON.stringify(params);
	return crypto.createHmac('sha256', secret).update(queryString).digest('hex');
}

