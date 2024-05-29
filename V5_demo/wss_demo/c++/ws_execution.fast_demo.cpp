#define _WIN32_WINNT_WIN7 0x0601  //depends on you system
#include <boost/asio.hpp>
#include <boost/asio/ssl.hpp>
#include <boost/beast.hpp>
#include <boost/beast/ssl.hpp>
#include <iostream>
#include <openssl/hmac.h>
#include <nlohmann/json.hpp>
#include <chrono>

namespace net = boost::asio;
namespace ssl = net::ssl;
namespace beast = boost::beast;
namespace http = beast::http;
namespace websocket = beast::websocket;

using tcp = net::ip::tcp;
using Request = http::request<http::string_body>;
using Stream = beast::ssl_stream<beast::tcp_stream>;
using Response = http::response<http::dynamic_body>;
using json = nlohmann::json;

class Exchange {
public:
    Exchange(std::string name, const std::string& http_host)
        : m_name(std::move(name))
    {
        init_http(http_host);
    }

    void init_http(std::string const& host)
    {
        const auto results{ resolver.resolve(host, "443") };
        get_lowest_layer(stream).connect(results);
        // Set SNI Hostname (many hosts need this to handshake successfully)
        if (!SSL_set_tlsext_host_name(stream.native_handle(), host.c_str())) {
            boost::system::error_code ec{
                static_cast<int>(::ERR_get_error()),
                boost::asio::error::get_ssl_category() };
            throw boost::system::system_error{ ec };
        }
        stream.handshake(ssl::stream_base::client);
    }

    void init_webSocket(std::string const& host, std::string const& port,
        const char* p = "")
    {
        // Set SNI Hostname (many hosts need this to handshake successfully)
        if (!SSL_set_tlsext_host_name(ws.next_layer().native_handle(),
            host.c_str()))
            throw beast::system_error(
                beast::error_code(static_cast<int>(::ERR_get_error()),
                    net::error::get_ssl_category()),
                "Failed to set SNI Hostname");
        auto const results = resolver_webSocket.resolve(host, port);
        net::connect(ws.next_layer().next_layer(), results.begin(),
            results.end());
        ws.next_layer().handshake(ssl::stream_base::client);

        ws.handshake(host, p);
    }

    void read_Socket() { ws.read(buffer); }

    bool is_socket_open()
    {
        if (ws.is_open())
            return true;
        return false;
    }

    void write_Socket(const std::string& text) { ws.write(net::buffer(text)); }

    std::string get_socket_data()
    {
        return beast::buffers_to_string(buffer.data());
    }
    void buffer_clear() { buffer.clear(); }

    void webSocket_close() { ws.close(websocket::close_code::none); }

    void authenticate()
    {
        long long int expires = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count() + 1000;
        
        std::string val = "GET/realtime" + std::to_string(expires);

        std::string signature = sign_message(val, secret_key);

        json auth_msg = {
            {"op", "auth"},
            {"args", {api_key, expires, signature}}
        };

        write_Socket(auth_msg.dump());
    }

private:
    std::string     m_name;
    net::io_context ioc;
    ssl::context    ctx{ ssl::context::tlsv12_client };
    tcp::resolver   resolver{ ioc };
    Stream          stream{ ioc, ctx };
    std::string        m_web_socket_host;
    std::string        m_web_socket_port;
    beast::flat_buffer buffer;
    net::io_context    ioc_webSocket;
    ssl::context       ctx_webSocket{ ssl::context::tlsv12_client };
    tcp::resolver      resolver_webSocket{ ioc_webSocket };
    websocket::stream<beast::ssl_stream<tcp::socket>> ws{ ioc_webSocket,
                                                         ctx_webSocket };

    std::string api_key;
    std::string secret_key;

    std::string sign_message(const std::string& message, const std::string& secret)
    {
        unsigned char* digest = HMAC(EVP_sha256(), secret.c_str(), secret.size(), (unsigned char*)message.c_str(), message.size(), NULL, NULL);

        std::ostringstream os;
        for (size_t i = 0; i < 32; ++i) {
            os << std::hex << std::setw(2) << std::setfill('0') << (int)digest[i];
        }
        return os.str();
    }
};

int main()
{
    std::string api_key = "xxxxxxxxxxxxx";
    std::string secret_key = "xxxxxxxxxxxxxxxxxxxxxxx";
    Exchange bybit{ "bybit", "stream-testnet.bybit.com" };

    try {
        bybit.init_webSocket("stream-testnet.bybit.com", "443", "/v5/private/linear");

        bybit.authenticate();

        if (bybit.is_socket_open()) {
            std::string subscription_message = R"({"op": "subscribe", "args": ["execution.fast"]})";
            bybit.write_Socket(subscription_message);
        }
        while (true) {
            bybit.read_Socket();
            std::cout << bybit.get_socket_data();

            bybit.buffer_clear();
        }
        bybit.webSocket_close();
    }
    catch (std::exception const& e) {
        std::cerr << "Error: " << e.what() << std::endl;
        return 1;
    }
}
