require 'uri'

require "openssl"

def get_signature(param_str, secret)
  OpenSSL::HMAC.hexdigest('sha256', secret, param_str)
end

api_key = "B2Rou0PLPpGqcU0Vu2"
secret = "t7T0YlFnYXk0Fx3JswQsDrViLg1Gh3DUU5Mr"

params = {
  symbol: "BTCUSD",
  timestamp: Time.now.to_i * 1000,
  leverage: 100,
}.merge(api_key: api_key)

signature = get_signature(URI.encode_www_form(params.sort), secret)
puts signature