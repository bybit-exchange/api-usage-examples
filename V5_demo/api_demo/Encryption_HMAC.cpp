#define WIN32_LEAN_AND_MEAN
#define CPPHTTPLIB_OPENSSL_SUPPORT

#include <chrono>
#include <iomanip>
#include <iostream>
#include <map>
#include <sstream>

#include <httplib.h>
#include <nlohmann/json.hpp>
#include <openssl/hmac.h>

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
const std::string Encryption::Timestamp = std::to_string(
    std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count()
);
const std::string Encryption::RecvWindow = "5000";

std::string Encryption::GeneratePostSignature(const nlohmann::json& parameters) {
    const std::string paramJson = parameters.dump();
    const std::string rawData = Timestamp + ApiKey + RecvWindow + paramJson;
    return ComputeSignature(rawData);
}

std::string Encryption::GenerateGetSignature(const nlohmann::json& parameters) {
    const std::string queryString = GenerateQueryString(parameters);
    const std::string rawData = Timestamp + ApiKey + RecvWindow + queryString;
    return ComputeSignature(rawData);
}

std::string Encryption::ComputeSignature(const std::string& data) {
    unsigned char* digest = HMAC(
        EVP_sha256(),
        ApiSecret.c_str(),
        static_cast<int>(ApiSecret.length()),
        reinterpret_cast<const unsigned char*>(data.c_str()),
        static_cast<int>(data.size()),
        nullptr,
        nullptr
    );

    std::ostringstream result;
    for (size_t i = 0; i < 32; i++) {
        result << std::hex << std::setw(2) << std::setfill('0') << static_cast<int>(digest[i]);
    }

    return result.str();
}

std::string Encryption::GenerateQueryString(const nlohmann::json& parameters) {
    std::ostringstream query;
    for (const auto& item : parameters.items()) {
        query << item.key() << "=" << item.value().get<std::string>() << "&";
    }

    const std::string result = query.str();
    return result.substr(0, result.length() - 1);
}

void Encryption::PlaceOrder() {
    const nlohmann::json parameters = {
        {"category", "linear"},
        {"symbol", "BTCUSDT"},
        {"side", "Buy"},
        {"positionIdx", 0},
        {"orderType", "Limit"},
        {"qty", "0.001"},
        {"price", "18900"},
        {"timeInForce", "GTC"}
    };

    const std::string signature = GeneratePostSignature(parameters);
    const std::string jsonPayload = parameters.dump();

    httplib::SSLClient client("api-testnet.bybit.com", 443);
    const httplib::Headers headers = {
        {"X-BAPI-API-KEY", ApiKey},
        {"X-BAPI-SIGN", signature},
        {"X-BAPI-SIGN-TYPE", "2"},
        {"X-BAPI-TIMESTAMP", Timestamp},
        {"X-BAPI-RECV-WINDOW", RecvWindow}
    };

    const auto response = client.Post("/v5/order/create", headers, jsonPayload, "application/json");
    if (response) {
        std::cout << response->body << std::endl;
    }
}

void Encryption::GetOpenOrder() {
    const std::map<std::string, std::string> parameters = {
        {"category", "linear"},
        {"symbol", "BTCUSDT"}
    };

    const std::string signature = GenerateGetSignature(parameters);
    const std::string queryString = GenerateQueryString(parameters);

    httplib::SSLClient client("api-testnet.bybit.com", 443);
    const httplib::Headers headers = {
        {"X-BAPI-API-KEY", ApiKey},
        {"X-BAPI-SIGN", signature},
        {"X-BAPI-SIGN-TYPE", "2"},
        {"X-BAPI-TIMESTAMP", Timestamp},
        {"X-BAPI-RECV-WINDOW", RecvWindow}
    };

    const auto response = client.Get(("/v5/order/realtime?" + queryString).c_str(), headers);
    if (response) {
        std::cout << response->body << std::endl;
    }
}

int main() {
    Encryption encryption;
    encryption.PlaceOrder();
    encryption.GetOpenOrder();
    return 0;
}
