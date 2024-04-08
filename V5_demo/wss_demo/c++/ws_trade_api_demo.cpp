#include <websocketpp/config/asio_no_tls_client.hpp>
#include <websocketpp/client.hpp>
#include <openssl/hmac.h>
#include <iostream>
#include <sstream>
#include <nlohmann/json.hpp>

typedef websocketpp::client<websocketpp::config::asio_client> client;

std::string generateSignature(const std::string &message, const std::string &secret) {
    unsigned char* digest;
    digest = HMAC(EVP_sha256(), secret.c_str(), secret.length(), (unsigned char*)message.c_str(), message.length(), NULL, NULL);    
    char mdString[SHA256_DIGEST_LENGTH*2+1];
    for(int i = 0; i < SHA256_DIGEST_LENGTH; ++i)
        sprintf(&mdString[i*2], "%02x", (unsigned int)digest[i]);
    return std::string(mdString);
}

void on_message(websocketpp::connection_hdl, client::message_ptr msg) {
    std::cout << "Received message: " << msg->get_payload() << std::endl;
}

int main() {
    client c;
    std::string uri = "wss://stream-testnet.bybit.com/v5/trade";

    try {
        c.set_access_channels(websocketpp::log::alevel::all);
        c.clear_access_channels(websocketpp::log::alevel::frame_payload);
        c.init_asio();
        c.set_message_handler(&on_message);

        websocketpp::lib::error_code ec;
        client::connection_ptr con = c.get_connection(uri, ec);
        if (ec) {
            std::cout << "Could not create connection because: " << ec.message() << std::endl;
            return 0;
        }

        c.connect(con);
        c.run();

        std::string apiKey = "your_api_key";
        std::string apiSecret = "your_api_secret";
        long expires = std::time(nullptr) * 1000 + 10000; // Unix timestamp in milliseconds
        std::string signature = generateSignature("GET/realtime" + std::to_string(expires), apiSecret);

        std::stringstream ss;
        ss << "{\"op\":\"auth\",\"args\":[\"" << apiKey << "\"," << expires << ",\"" << signature << "\"]}";
        con->send(ss.str());

        json order = {
                {"reqId", generate_uuid()},
                {"op", "order.create"},
                {"args", json::array({
                    {   
                        {"category", "spot"},
                        {"symbol", "XRPUSDT"},
                        {"side", "Buy"},
                        {"orderType", "Market"},
                        {"qty", "10"},
                        {"timeInForce", "IOC"}
                    }
                })}
            };
        std::string placeOrderMessage = placeOrderSS.str();
        con->send(placeOrderMessage);

    } catch (websocketpp::exception const & e) {
        std::cout << e.what() << std::endl;
    }
}
