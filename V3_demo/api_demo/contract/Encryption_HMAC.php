<?php

$api_key='XXXXXXXX'; # Input your API Key
$secret_key='XXXXXXXX'; # Input your Secret Key
$url="https://api-testnet.bybit.com"; #Testnet environment
$curl = curl_init();

function http_req($endpoint,$method,$params,$Info){
    global $api_key, $secret_key, $url, $curl;
    $timestamp = time() * 1000;
    $params_for_signature= $timestamp . $api_key . "5000" . $params;
    $signature = hash_hmac('sha256', $params_for_signature, $secret_key);
    if($method=="GET")
    {
        $endpoint=$endpoint . "?" . $params;
    }
    curl_setopt_array($curl, array(
        CURLOPT_URL => $url . $endpoint,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_ENCODING => '',
        CURLOPT_MAXREDIRS => 10,
        CURLOPT_TIMEOUT => 0,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_1_1,
        CURLOPT_CUSTOMREQUEST => $method,
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
    if($method=="GET")
    {
      curl_setopt($curl, CURLOPT_HTTPGET, true);
    }
    echo $Info . "\n";
    $response = curl_exec($curl);
    echo $response . "\n";
}

#Create Order
$endpoint="/contract/v3/private/order/create";
$method="POST";
$orderLinkId=uniqid();
$params='{"symbol": "BTCUSDT","side": "Buy","positionIdx": 1,"orderType": "Limit","qty": "0.001","price": "10000","timeInForce": "GoodTillCancel","orderLinkId": "' . $orderLinkId . '"}';
http_req("$endpoint","$method","$params","Create Order");

#Get Order List
$endpoint="/contract/v3/private/order/list";
$method="GET";
$params="symbol=BTCUSDT&orderLinkId=" . $orderLinkId;
http_req($endpoint,$method,$params,"List Order");

#Cancel Order
$endpoint="/contract/v3/private/order/cancel";
$method="POST";
$params='{"symbol": "BTCUSDT","orderLinkId": "' . $orderLinkId . '"}';
http_req($endpoint,$method,$params,"Cancel Order");

curl_close($curl);


