<?php

$curl = curl_init();
$api_key='API-KEY'; # Input your API Key
$secret_key='SECRET-KEY'; # Input your Secret Key

$timestamp = time() * 1000;
$params='{"outRequestId":"9c7a6001","symbol":"BTC-17DEC21-40000-C","orderType":"Limit","side":"Buy","orderQty":"0.01","orderPrice":"308","iv":"72","timeInForce":"GoodTillCancel","orderLinkId":"c35cc80a","reduceOnly":true,"placeMode":1,"placeType":1}';
$params_for_signature= $timestamp . $api_key . "5000" . $params;
$signature = hash_hmac('sha256', $params_for_signature, $secret_key);

curl_setopt_array($curl, array(
  CURLOPT_URL => 'https://api-testnet.bybit.com/option/usdc/openapi/private/v1/place-order',
  CURLOPT_RETURNTRANSFER => true,
  CURLOPT_ENCODING => '',
  CURLOPT_MAXREDIRS => 10,
  CURLOPT_TIMEOUT => 0,
  CURLOPT_FOLLOWLOCATION => true,
  CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_1_1,
  CURLOPT_CUSTOMREQUEST => 'POST',
  CURLOPT_POSTFIELDS => $params,
  CURLOPT_HTTPHEADER => array(
    "X-BAPI-API-KEY: $api_key",
    "X-BAPI-SIGN: $signature",
    "X-BAPI-SIGN-TYPE: 2",
    "X-BAPI-TIMESTAMP: $timestamp",
    "X-BAPI-RECV-WINDOW: 5000",
    "Content-Type: application/json"
  ),
));

$response = curl_exec($curl);
curl_close($curl);
echo $response;
