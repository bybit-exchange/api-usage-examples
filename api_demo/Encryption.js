const crypto = require('crypto');

var apiKey = "";
var secret = "";
var timestamp = Date.now();
var params = {
	"order_id":"876b0ac1-bafe-4110-b404-6a7c8211a6d9",
	"symbol":"BTCUSD",
	"timestamp":timestamp,
	"api_key" : apiKey,
};

console.log(getSignature(params, secret));

function getSignature(parameters, secret) {
	var orderedParams = "";
	Object.keys(parameters).sort().forEach(function(key) {
	  orderedParams += key + "=" + parameters[key] + "&";
	});
	orderedParams = orderedParams.substring(0, orderedParams.length - 1);

	return crypto.createHmac('sha256', secret).update(orderedParams).digest('hex');
}

