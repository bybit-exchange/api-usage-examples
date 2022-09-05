<?php

function get_signed_params($public_key, $secret_key, $params) {
    $params = array_merge(['api_key' => $public_key], $params);
    ksort($params);
    //decode return value of http_build_query to make sure signing by plain parameter string
    $signature = hash_hmac('sha256', urldecode(http_build_query($params)), $secret_key);
    return http_build_query($params) . "&sign=$signature";
}

$params = [
	'symbol' => 'BTCUSDT', 
	'side' => 'Buy', 
	'order_type' => 'Limit', 
	'qty' => '0.01', 
	'price' => '30000', 
	'time_in_force' => 'GoodTillCancel',
	'reduce_only' => false,
	'close_on_trigger' => false,
	'timestamp' => time() * 1000,
  'position_idx' => 0
];

$url = 'https://api-testnet.bybit.com/private/linear/order/create';

$public_key = 'XXXXXXXXXXXXXXXXX';
$secret_key = 'XXXXXXXXXXXXXXXXX';
$qs=get_signed_params($public_key, $secret_key, $params);
$curl_url=$url."?".$qs;
$curl=curl_init($curl_url);
echo $curl_url;
curl_setopt($curl, CURLOPT_URL, $curl_url);
#curl_setopt($curl, CURLOPT_POSTFIELDS, $qs);
curl_setopt($curl, CURLOPT_POST, true);
curl_setopt($curl, CURLOPT_RETURNTRANSFER, 1);
curl_setopt($curl, CURLOPT_FOLLOWLOCATION, 1);
curl_setopt($curl, CURLOPT_SSL_VERIFYPEER, 0);
curl_setopt($curl, CURLOPT_SSL_VERIFYHOST, 0);
#curl_setopt($curl, CURLOPT_PROXY,"127.0.0.1:1087");
$response=curl_exec($curl);
echo $response;
