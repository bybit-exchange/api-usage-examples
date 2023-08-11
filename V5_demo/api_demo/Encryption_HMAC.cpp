#define WIN32_LEAN_AND_MEAN

#define CPPHTTPLIB_OPENSSL_SUPPORT
#include <iostream>
#include <map>
#include <vector>
#include <sstream>
#include <chrono>
#include <iomanip> // for setfill and setw
#include <openssl/hmac.h>
#include <nlohmann/json.hpp>
#include <httplib.h>

class Encryption {
private:
    static const std::string ApiKey;
    static const std::string ApiSecret;
    static const std::string Timestamp;
    static const std::string RecvWindow;

public:
    void PlaceOrder();
    void GetOpenOrder();
private:
    static std::string GeneratePostSignature(const nlohmann::json& parameters);
    static std::string GenerateGetSignature(const nlohmann::json& parameters);
    static std::string ComputeSignature(const std::string& data);
    static std::string GenerateQueryString(const nlohmann::json& parameters);
};

const std::string Encryption::ApiKey = "xxxxxxxxxxxxxxxx";
const std::string Encryption::ApiSecret = "xxxxxxxxxxxxxxxxxxxxxxxxxxxx";
const std::string Encryption::Timestamp = std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count());
const std::string Encryption::RecvWindow = "5000";

std::string Encryption::GeneratePostSignature(const nlohmann::json& parameters) {
    std::string paramJson = parameters.dump();
    std::string rawData = Timestamp + ApiKey + RecvWindow + paramJson;
    return ComputeSignature(rawData);
}

std::string Encryption::GenerateGetSignature(const nlohmann::json& parameters) {
    std::string queryString = GenerateQueryString(parameters);
    std::string rawData = Timestamp + ApiKey + RecvWindow + queryString;
    return ComputeSignature(rawData);
}

std::string Encryption::ComputeSignature(const std::string& data) {
    unsigned char* digest = HMAC(EVP_sha256(), ApiSecret.c_str(), static_cast<int>(ApiSecret.length()),
        (unsigned char*)data.c_str(), static_cast<int>(data.size()), NULL, NULL);

    std::ostringstream result;
    for (size_t i = 0; i < 32; i++) {
        result << std::hex << std::setw(2) << std::setfill('0') << (int)digest[i];
    }

    return result.str();
}

std::string Encryption::GenerateQueryString(const nlohmann::json& parameters) {
    std::ostringstream oss;
    for (const auto& item : parameters.items()) {
        std::string key = item.key();
        std::string value = item.value().get<std::string>();
        oss << key << "=" << value << "&";
    }
    std::string result = oss.str();
    return result.substr(0, result.length() - 1); // Remove trailing '&'
}

void Encryption::PlaceOrder() {
    nlohmann::json parameters = {
        {"category", "linear"},
        {"symbol", "BTCUSDT"},
        {"side", "Buy"},
        {"positionIdx", 0},
        {"orderType", "Limit"},
        {"qty", "0.001"},
        {"price", "18900"},
        {"timeInForce", "GTC"}
    };

    std::string signature = Encryption::GeneratePostSignature(parameters);
    std::string jsonPayload = parameters.dump();

    httplib::SSLClient client("api-testnet.bybit.com", 443); // 443 is the default port for HTTPS
    httplib::Headers headers = {
        {"X-BAPI-API-KEY", ApiKey},
        {"X-BAPI-SIGN", signature},
        {"X-BAPI-SIGN-TYPE", "2"},
        {"X-BAPI-TIMESTAMP", Timestamp},
        {"X-BAPI-RECV-WINDOW", RecvWindow}
    };

    auto res = client.Post("/v5/order/create", headers, jsonPayload, "application/json");
    if (res) {
        std::cout << res->body << std::endl;
    }
}

void Encryption::GetOpenOrder() {
    std::map<std::string, std::string> parameters = {
        {"category", "linear"},
        {"symbol", "BTCUSDT"}
    };

    std::string signature = Encryption::GenerateGetSignature(parameters);
    std::string queryString = Encryption::GenerateQueryString(parameters);

    httplib::SSLClient client("api-testnet.bybit.com", 443); // 443 is the default port for HTTPS
    httplib::Headers headers = {
        {"X-BAPI-API-KEY", ApiKey},
        {"X-BAPI-SIGN", signature},
        {"X-BAPI-SIGN-TYPE", "2"},
        {"X-BAPI-TIMESTAMP", Timestamp},
        {"X-BAPI-RECV-WINDOW", RecvWindow}
    };

    auto res = client.Get(("/v5/order/realtime?" + queryString).c_str(), headers);
    if (res) {
        std::cout << res->body << std::endl;
    }
}

int main() {
    Encryption encryptionTest;
    encryptionTest.PlaceOrder();
    encryptionTest.GetOpenOrder();
    return 0;
}