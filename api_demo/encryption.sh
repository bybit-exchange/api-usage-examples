#!/bin/bash
 
api_key="XXXXXXXXXXXXXXX"
secret_key="XXXXXXXXXXXXXXX"
time_in_seconds=$(date +%s)
timestamp=$((time_in_seconds * 1000))
sign=$(echo -n "api_key=${api_key}&close_on_trigger=false&order_type=Market&qty=0.001&reduce_only=false&side=Buy&symbol=BTCUSDT&time_in_force=GoodTillCancel&timestamp=${timestamp}" | openssl dgst -sha256 -hmac "${secret_key}")
 
response=$(curl --silent https://api-testnet.bybit.com/private/linear/order/create \
-H "Content-Type: application/json" \
-d '{"api_key":"'"${api_key}"'","side":"Buy","symbol":"BTCUSDT","order_type":"Market","qty":"0.001","time_in_force":"GoodTillCancel","close_on_trigger":false,"reduce_only":false,"timestamp":"'"${timestamp}"'","sign":"'"${sign}"'"}')
 
echo "Curl Response : $?"
#echo "${response}"
order_id=$(jq '.result.order_id' <<< ${response})
if [ "${order_id}" == "null" ]
then
    echo "${response}"
else
    echo "Order ID # ${order_id}"
fi
