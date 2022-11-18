require 'digest'
require "uri"
require "net/http"
require "date"
require 'open-uri'
require 'openssl'
require 'securerandom'


$api_key='XXXXXXXXX'
$secret_key='XXXXXXXXX'
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

#Create Internal Transfer ( SPOT to UNIFIED )
endPoint = "/asset/v3/private/transfer/inter-transfer"
method = "POST"
transferId = SecureRandom.uuid
payload='{"transferId": "' + transferId +  '","coin": "USDT","amount": "1","from_account_type": "SPOT","to_account_type": "UNIFIED"}'
HTTP_Request(endPoint,method,payload)

#Query Internal Transfer List
endPoint = "/asset/v3/private/transfer/inter-transfer/list/query"
method = "GET"
payload="coin=USDT"
HTTP_Request(endPoint,method,payload)
