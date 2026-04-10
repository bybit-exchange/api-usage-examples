require 'digest'
require 'uri'
require 'net/http'
require 'date'
require 'open-uri'
require 'openssl'
require 'securerandom'


$api_key = 'XXXXXXXXXX'
$secret_key = 'XXXXXXXXXX'
$recv_window = '5000'
$url = URI('https://api-testnet.bybit.com')
$time_stamp = ''

def http_request(end_point, method, payload)
    $time_stamp = DateTime.now.strftime('%Q')
    signature = gen_signature(payload)
    full_url = $url + end_point
    if method == 'POST'
        request = Net::HTTP::Post.new(full_url, 'Content-Type' => 'application/json')
        request.body = payload
    elsif method == 'GET'
        payload = '?' + payload
        full_url = $url + end_point + payload
        request = Net::HTTP::Get.new(full_url)
    else
        puts 'Check the method. It should be either GET or POST'
        exit
    end
    https = Net::HTTP.new(full_url.host, full_url.port)
    https.use_ssl = true
    request['X-BAPI-API-KEY'] = $api_key
    request['X-BAPI-TIMESTAMP'] = $time_stamp
    request['X-BAPI-RECV-WINDOW'] = $recv_window
    request['X-BAPI-SIGN'] = signature
    response = https.request(request)
    puts response.read_body
end

def gen_signature(payload)
    param_str = $time_stamp + $api_key + $recv_window + payload
    OpenSSL::HMAC.hexdigest('sha256', $secret_key, param_str)
end

end_point = '/v5/order/create'
method = 'POST'
order_link_id = SecureRandom.uuid
payload = '{"category":"linear","symbol": "BTCUSDT","side": "Buy","positionIdx": 0,"orderType": "Limit","qty": "0.001","price": "10000","timeInForce": "GTC","orderLinkId": "' + order_link_id + '"}'
http_request(end_point, method, payload)

end_point = '/v5/order/realtime'
method = 'GET'
payload = 'category=linear&settleCoin=USDT'
http_request(end_point, method, payload)

end_point = '/v5/order/cancel'
method = 'POST'
payload = '{"category":"linear","symbol": "BTCUSDT","orderLinkId": "' + order_link_id + '"}'
http_request(end_point, method, payload)
