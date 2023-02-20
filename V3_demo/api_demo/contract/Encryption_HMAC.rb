require 'digest'
require "uri"
require "net/http"
require "date"
require 'open-uri'
require 'openssl'
require 'securerandom'


$api_key='XXXXXXXX'
$secret_key='XXXXXXXX'
$recv_window = '5000'
$url = URI("https://api-testnet.bybit.com") # Testnet endpoint
$time_stamp = ""

def HTTP_Request(endPoint,method,payload)
    $time_stamp = DateTime.now.strftime('%Q')
    signature = genSignature(payload)
    fullUrl = $url + endPoint
    if method == "POST"
        request = Net::HTTP::Post.new(fullUrl,'Content-Type' => 'application/json')
    elsif method == "GET"
        payload="?"+payload
        fullUrl = $url + endPoint + payload
        request = Net::HTTP::Get.new(fullUrl)
    else
        puts "Check the method. It should be either GET or POST"
        exit
    end
    https = Net::HTTP.new(fullUrl.host, fullUrl.port)
    https.set_debug_output($stdout)
    https.use_ssl = true
    request["X-BAPI-API-KEY"] = $api_key
    request["X-BAPI-TIMESTAMP"] = $time_stamp
    request["X-BAPI-RECV-WINDOW"] = $recv_window
    request["X-BAPI-SIGN"] = signature
    request.body = payload
    puts payload
    response = https.request(request)
    puts response.read_body
end

def genSignature(payload)
    param_str= $time_stamp + $api_key + $recv_window + payload
    OpenSSL::HMAC.hexdigest('sha256', $secret_key, param_str)
end

#Create Order ( POST Method )
endPoint = "/contract/v3/private/order/create"
method = "POST"
orderLinkId = SecureRandom.uuid
payload='{"symbol": "BTCUSDT","side": "Buy","positionIdx": 1,"orderType": "Limit","qty": "0.001","price": "10000","timeInForce": "GoodTillCancel","orderLinkId": "' + orderLinkId + '"}'
HTTP_Request(endPoint,method,payload)

#Retrieve Unfilled Orders ( GET Method )
endPoint = "/contract/v3/private/order/unfilled-orders"
method = "GET"
payload='settleCoin=USDT'
HTTP_Request(endPoint,method,payload)
