require 'uri'

require "openssl"

def get_signature(param_str, secret)
  OpenSSL::HMAC.hexdigest('sha256', secret, param_str)
end

api_key = "XXXXXXXX"
secret = "XXXXXXXX"

params = {
  symbol: "BTCUSD",
  timestamp: Time.now.to_i * 1000,
  leverage: 100,
}.merge(api_key: api_key)

signature = get_signature(URI.encode_www_form(params.sort), secret)
puts signature