#!/bin/bash

api_key="XXXXXXXXXXX"
secret_key="XXXXXXXXXXX"
time_in_seconds=$(date +%s)
timestamp=$((time_in_seconds * 1000))
coin="USDT"
amount="5"
transfer_id=$(uuidgen)
sign=$(echo -n "amount=${amount}&api_key=${api_key}&coin=${coin}&from_account_type=SPOT&recv_window=50000&timestamp=${timestamp}&to_account_type=CONTRACT&transferId=${transfer_id}" | openssl dgst -sha256 -hmac "${secret_key}")
response=$(curl --request POST --silent  'https://api-testnet.bybit.com/asset/v1/private/transfer' \
--data-raw '{ 
    "transferId": "'"${transfer_id}"'",
    "amount": "'"${amount}"'",
    "coin": "'"${coin}"'",
    "from_account_type":"SPOT",
    "to_account_type":"CONTRACT",
    "sign": "'"${sign}"'",
    "timestamp": "'"${timestamp}"'",
    "api_key": "'"${api_key}"'",
    "recv_window": "50000"
}'
)
echo "${response}"
