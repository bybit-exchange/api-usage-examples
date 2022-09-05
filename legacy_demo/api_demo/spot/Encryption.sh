#!/bin/bash

api_key="XXXXXXXXXXXXXXXXXXXXX"
secret_key="XXXXXXXXXXXXXXXXXXXXX"
time_in_seconds=$(date +%s)
timestamp=$((time_in_seconds * 1000))
#Sort your paramters in Alphabetical order - https://bybit-exchange.github.io/docs/spot/#t-constructingtherequest
params="api_key=${api_key}&price=30000&qty=0.001&side=Buy&symbol=BTCUSDT&timeInForce=GTC&timestamp=${timestamp}&type=Limit"
sign=$(echo -n "${params}" | openssl dgst -sha256 -hmac "${secret_key}")

response=$(curl -d "${params}&sign=${sign}" -X POST "https://api-testnet.bybit.com/spot/v1/order")

echo "Curl Response : $?"
orderId=$(jq '.result.orderId' <<< ${response})
if [ "${orderId}" == "null" ]
then
	echo "${response}"
else
	echo "Order ID # ${orderId}"
fi
