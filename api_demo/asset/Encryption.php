<?php

$curl = curl_init();

function get_signed_params($public_key, $secret_key, $params) {
    ksort($params);
    $signature = hash_hmac('sha256', urldecode(http_build_query($params)), $secret_key);
    return $signature;
}

function guidv4($data)
{
    assert(strlen($data) == 16);
    $data[6] = chr(ord($data[6]) & 0x0f | 0x40); // set version to 0100
    $data[8] = chr(ord($data[8]) & 0x3f | 0x80); // set bits 6-7 to 10
    return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($data), 4));
}

$public_key = 'XXXXXXXXXXXXXXX';
$secret_key = 'XXXXXXXXXXXXXXX';
$params = [
 "transferId" => guidv4(openssl_random_pseudo_bytes(16)),
    "amount" => "10",
    "coin" => "USDT",
    "from_account_type" => "CONTRACT",
    "to_account_type" => "SPOT",
    "timestamp" => time() * 1000,
    "api_key" => $public_key,
    "recv_window" => "50000"
];

$sign=get_signed_params($public_key, $secret_key, $params);
$params = array_merge(['sign' => $sign], $params);
$json_value=json_encode($params);

curl_setopt_array($curl, array(
  CURLOPT_URL => 'https://api-testnet.bybit.com/asset/v1/private/transfer',
  CURLOPT_RETURNTRANSFER => true,
  CURLOPT_ENCODING => '',
  CURLOPT_MAXREDIRS => 10,
  CURLOPT_TIMEOUT => 0,
  CURLOPT_FOLLOWLOCATION => true,
  CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_1_1,
  CURLOPT_CUSTOMREQUEST => 'POST',
  CURLOPT_POSTFIELDS => $json_value
));

$response = curl_exec($curl);

curl_close($curl);
echo $response;
