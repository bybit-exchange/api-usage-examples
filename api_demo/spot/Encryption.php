<?php

function get_signed_params($public_key, $secret_key, $params) {
    $params = array_merge(['api_key' => $public_key], $params);
    ksort($params);
    //decode return value of http_build_query to make sure signing by plain parameter string
    $signature = hash_hmac('sha256', urldecode(http_build_query($params)), $secret_key);
    return http_build_query($params) . "&sign=$signature";
}

$params = [
	'symbol' => 'BITUSDT', 
	'side' => 'Buy', 
	'type' => 'Limit', 
	'qty' => '10', 
	'price' => '1', 
	'time_in_force' => 'GoodTillCancel',
	'reduce_only' => false,
	'close_on_trigger' => false,
	'timestamp' => time() * 1000
];

$url = 'https://api-testnet.bybit.com/spot/v1/order';

$public_key = 'XXXXXXXXXXXXXXXXXXXXXXX';
$secret_key = 'XXXXXXXXXXXXXXXXXXXXXXX';
$qs=get_signed_params($public_key, $secret_key, $params);
$curl_url=$url."?".$qs;
$curl=curl_init($curl_url);
echo $curl_url . "\n";
curl_setopt($curl, CURLOPT_URL, $curl_url);
#curl_setopt($curl, CURLOPT_POSTFIELDS, $qs);
curl_setopt($curl, CURLOPT_POST, true);
curl_setopt($curl, CURLOPT_RETURNTRANSFER, 1);
curl_setopt($curl, CURLOPT_FOLLOWLOCATION, 1);
curl_setopt($curl, CURLOPT_SSL_VERIFYPEER, 0);
curl_setopt($curl, CURLOPT_SSL_VERIFYHOST, 0);
#curl_setopt($curl, CURLOPT_PROXY,"127.0.0.1:1087");
$response=curl_exec($curl);
echo $response . "\n";
