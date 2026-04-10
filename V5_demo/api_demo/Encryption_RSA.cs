using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using Newtonsoft.Json;

public class Encryption
{
    private const string API_KEY = "xxxxxxxxxxxxxxxxxxxxxxxxxx";
    private static readonly string TIMESTAMP = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds().ToString();
    private const string RECV_WINDOW = "5000";
    private const string RsaPrivateKeyPath = @"C:\Path\To\private.pem";
    private static readonly HttpClient HttpClient = new();

    public static void Main(string[] args)
    {
        var encryption = new Encryption();
        encryption.PlaceOrder();
        encryption.GetOpenOrder();
    }

    public void PlaceOrder()
    {
        const string keyPem = @"-----BEGIN PRIVATE KEY-----
XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
...
...
XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
-----END PRIVATE KEY-----";

        var map = new Dictionary<string, object>
        {
            {"category", "linear"},
            {"symbol", "BTCUSDT"},
            {"side", "Buy"},
            {"positionIdx", 0},
            {"orderType", "Limit"},
            {"qty", "0.001"},
            {"price", "18900"},
            {"timeInForce", "GTC"}
        };
        var jsonMap = JsonConvert.SerializeObject(map);
        var plainText = TIMESTAMP + API_KEY + RECV_WINDOW + jsonMap;
        var signature = GenSignature(keyPem, plainText);

        HttpClient.DefaultRequestHeaders.Accept.Clear();
        HttpClient.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
        HttpClient.DefaultRequestHeaders.Remove("X-BAPI-API-KEY");
        HttpClient.DefaultRequestHeaders.Remove("X-BAPI-SIGN");
        HttpClient.DefaultRequestHeaders.Remove("X-BAPI-TIMESTAMP");
        HttpClient.DefaultRequestHeaders.Remove("X-BAPI-RECV-WINDOW");
        HttpClient.DefaultRequestHeaders.Add("X-BAPI-API-KEY", API_KEY);
        HttpClient.DefaultRequestHeaders.Add("X-BAPI-SIGN", signature);
        HttpClient.DefaultRequestHeaders.Add("X-BAPI-TIMESTAMP", TIMESTAMP);
        HttpClient.DefaultRequestHeaders.Add("X-BAPI-RECV-WINDOW", RECV_WINDOW);

        var content = new StringContent(jsonMap, Encoding.UTF8, "application/json");
        var response = HttpClient.PostAsync("https://api-testnet.bybit.com/v5/order/create", content).Result;

        Console.WriteLine(response.Content.ReadAsStringAsync().Result);
    }

    public void GetOpenOrder()
    {
        var parameters = new Dictionary<string, object>
        {
            {"category", "linear"},
            {"symbol", "BTCUSDT"},
            {"settleCoin", "USDT"}
        };

        var queryString = GenQueryString(parameters);
        var plainText = TIMESTAMP + API_KEY + RECV_WINDOW + queryString;
        var signature = GenSignature(RsaPrivateKeyPath, plainText);
        var request = new HttpRequestMessage(HttpMethod.Get, "https://api-testnet.bybit.com/v5/order/realtime?" + queryString)
        {
            Headers =
            {
                {"X-BAPI-API-KEY", API_KEY},
                {"X-BAPI-SIGN", signature},
                {"X-BAPI-TIMESTAMP", TIMESTAMP},
                {"X-BAPI-RECV-WINDOW", "5000"}
            }
        };

        var response = HttpClient.SendAsync(request).Result;
        Console.WriteLine(response.Content.ReadAsStringAsync().Result);
    }

    private string GenSignature(string privateKey, string data)
    {
        var rsaPrivateKey = RSA.Create();
        rsaPrivateKey.ImportPkcs8PrivateKey(Convert.FromBase64String(privateKey), out _);

        var dataBytes = Encoding.UTF8.GetBytes(data);
        var signatureBytes = rsaPrivateKey.SignData(dataBytes, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        return Convert.ToBase64String(signatureBytes);
    }

    private static string GenQueryString(Dictionary<string, object> map)
    {
        return string.Join("&", map.Select(kvp => $"{kvp.Key}={kvp.Value}"));
    }
}
