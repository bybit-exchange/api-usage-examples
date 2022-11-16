#!/bin/bash
 

api_key='XXXXXXXXXX'
secret_key='XXXXXXXXXX'
time_in_seconds=$(date +%s)
timestamp=$((time_in_seconds * 1000))
sign=$(echo -n "${timestamp}${api_key}"5000'{"symbol": "BTCUSDT","side": "Buy","positionIdx": 1,"orderType": "Limit","qty": "0.001","price": "8000","timeInForce": "GoodTillCancel","reduce_only": false,"closeOnTrigger": false,"position_idx":1,"orderLinkId": "TESTING123"}' | openssl dgst -sha256 -hmac "${secret_key}")

response=$(curl --location --request POST 'https://api-testnet.bybit.com/contract/v3/private/order/create' \
--header 'X-BAPI-SIGN-TYPE: 2' \
--header 'X-BAPI-SIGN: '${sign} \
--header 'X-BAPI-API-KEY: '${api_key} \
--header 'X-BAPI-TIMESTAMP: '${timestamp}  \
--header 'X-BAPI-RECV-WINDOW: 5000' \
--header 'Content-Type: application/json' \
--data-raw '{"symbol": "BTCUSDT","side": "Buy","positionIdx": 1,"orderType": "Limit","qty": "0.001","price": "8000","timeInForce": "GoodTillCancel","reduce_only": false,"closeOnTrigger": false,"position_idx":1,"orderLinkId": "TESTING123"}')

echo "Curl Response : $?"
echo "${response}"
order_id=$(jq '.result.orderId' <<< ${response})
if [ "${order_id}" == "null" ]
then
    echo "${response}"
else
    echo "Order ID # ${order_id}"
fi
