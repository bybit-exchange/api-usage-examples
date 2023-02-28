<?php

$api_key='XXXXXXXXXXXX'; # Input your API Key
$secret_key='XXXXXXXXXXXX'; # Input your Secret Key
$url="https://api-testnet.bybit.com"; #Testnet environment
$curl = curl_init();

function guidv4($data)
{
    assert(strlen($data) == 16);
    $data[6] = chr(ord($data[6]) & 0x0f | 0x40); // set version to 0100
    $data[8] = chr(ord($data[8]) & 0x3f | 0x80); // set bits 6-7 to 10
    return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($data), 4));
}

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

#Create Internal Transfer ( SPOT to UNIFIED )
$endpoint="/asset/v3/private/transfer/inter-transfer";
$method="POST";
$transferId=guidv4(openssl_random_pseudo_bytes(16));
$params='{"transferId": "' . $transferId .  '","coin": "USDT","amount": "1","from_account_type": "SPOT","to_account_type": "UNIFIED"}';
http_req("$endpoint","$method","$params","InternalTransfer");


#Query Internal Transfer List
$endpoint="/asset/v3/private/transfer/inter-transfer/list/query";
$method="GET";
$params='coin=USDT';
http_req($endpoint,$method,$params,"InternalTransferList");

curl_close($curl);


