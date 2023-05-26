require 'digest'
require "uri"
require "net/http"
require "date"
require 'open-uri'
require 'openssl'
require 'securerandom'


$api_key='XXXXXXXXXX'
$secret_key='XXXXXXXXXX'
$recv_window = '5000'
$url = URI("https://api-testnet.bybit.com") # Testnet endpoint
$time_stamp = ""

def HTTP_Request(endPoint,method,payload)
    $time_stamp = DateTime.now.strftime('%Q')
    signature = genSignature(payload)
    fullUrl = $url + endPoint
    if method == "POST"
        request = Net::HTTP::Post.new(fullUrl,'Content-Type' => 'application/json')
        request.body = payload
    elsif method == "GET"
        payload="?"+payload
        fullUrl = $url + endPoint + payload
        request = Net::HTTP::Get.new(fullUrl)
    else
        puts "Check the method. It should be either GET or POST"
        exit
    end
    https = Net::HTTP.new(fullUrl.host, fullUrl.port)
    #https.set_debug_output($stdout)
    https.use_ssl = true
    request["X-BAPI-API-KEY"] = $api_key
    request["X-BAPI-TIMESTAMP"] = $time_stamp
    request["X-BAPI-RECV-WINDOW"] = $recv_window
    request["X-BAPI-SIGN"] = signature
    response = https.request(request)
    puts response.read_body
end

def genSignature(payload)
    param_str= $time_stamp + $api_key + $recv_window + payload
    OpenSSL::HMAC.hexdigest('sha256', $secret_key, param_str)
end

#Create Order ( POST Method )
endPoint = "/v5/order/create"
method = "POST"
orderLinkId = SecureRandom.uuid
payload='{"category":"linear","symbol": "BTCUSDT","side": "Buy","positionIdx": 0,"orderType": "Limit","qty": "0.001","price": "10000","timeInForce": "GTC","orderLinkId": "' + orderLinkId + '"}'
HTTP_Request(endPoint,method,payload)

#Retrieve Unfilled Orders ( GET Method )
endPoint = "/v5/order/realtime"
method = "GET"
payload='category=linear&settleCoin=USDT'
HTTP_Request(endPoint,method,payload)

#Cancel Order
endPoint="/v5/order/cancel"
method="POST"
payload='{"category":"linear","symbol": "BTCUSDT","orderLinkId": "'+orderLinkId+'"}'
HTTP_Request(endPoint,method,payload)
