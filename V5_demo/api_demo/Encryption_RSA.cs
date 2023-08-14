using System.Text;
using System.Security.Cryptography;
using Newtonsoft.Json;
using System.Net.Http.Headers;


public class Encryption
{
    private const string API_KEY = "xxxxxxxxxxxxxxxxxxxxxxxxxx";
    private static readonly string TIMESTAMP = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds().ToString();
    private const string RECV_WINDOW = "5000";
    private const string RsaPrivateKeyPath = @"C:\Path\To\private.pem"; // update path
    private static readonly HttpClient httpClient = new HttpClient();

    public static void Main(string[] args)
    {
        Encryption encryptionTest = new Encryption();
        encryptionTest.PlaceOrder();
        encryptionTest.GetOpenOrder();
    }

    public void PlaceOrder()
    {
        string keyPem = @"-----BEGIN PRIVATE KEY-----
XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
...
...
XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
-----END PRIVATE KEY-----";

        Dictionary<string, object> map = new Dictionary<string, object>
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
        string jsonMap = JsonConvert.SerializeObject(map);

        string plainText = TIMESTAMP + API_KEY + RECV_WINDOW + jsonMap;

        string signature = GenSignature(keyPem, plainText);

        httpClient.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
        httpClient.DefaultRequestHeaders.Add("X-BAPI-API-KEY", API_KEY);
        httpClient.DefaultRequestHeaders.Add("X-BAPI-SIGN", signature);
        httpClient.DefaultRequestHeaders.Add("X-BAPI-TIMESTAMP", TIMESTAMP);
        httpClient.DefaultRequestHeaders.Add("X-BAPI-RECV-WINDOW", RECV_WINDOW);

        StringContent content = new StringContent(jsonMap, Encoding.UTF8, "application/json");
        var response = httpClient.PostAsync("https://api-testnet.bybit.com/v5/order/create", content).Result;

        Console.WriteLine(response.Content.ReadAsStringAsync().Result);
    }

    public void GetOpenOrder()
    {
        var parameters = new Dictionary<string, object>
        {
            { "category", "linear" },
            { "symbol", "BTCUSDT" },
            { "settleCoin", "USDT" }
        };

        var queryString = GenQueryString(parameters);
        var plainText = TIMESTAMP + API_KEY + RECV_WINDOW + queryString;

        var signature = GenSignature(RsaPrivateKeyPath, plainText);
        var request = new HttpRequestMessage(HttpMethod.Get, "https://api-testnet.bybit.com/v5/order/realtime?" + queryString)
        {
            Headers =
            {
                { "X-BAPI-API-KEY", API_KEY },
                { "X-BAPI-SIGN", signature },
                { "X-BAPI-TIMESTAMP", TIMESTAMP },
                { "X-BAPI-RECV-WINDOW", "5000" }
            }
        };

        var response = httpClient.SendAsync(request).Result;
        Console.WriteLine(response.Content.ReadAsStringAsync().Result);
    }

    /// <summary>
    /// generate signature
    /// </summary>
    /// <param name="privateKey">either absolute pem path or original private key pem string</param>
    /// <param name="data">plain text</param>
    /// <returns></returns>
    private string GenSignature(string privateKey, string data)
    {
        RSA rsaPrivateKey = RSA.Create();
        rsaPrivateKey.ImportPkcs8PrivateKey(Convert.FromBase64String(privateKey), out _);

        byte[] dataBytes = Encoding.UTF8.GetBytes(data);
        byte[] signatureBytes = rsaPrivateKey.SignData(dataBytes, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        return Convert.ToBase64String(signatureBytes);
    }

    private static string GenQueryString(Dictionary<string, object> map)
    {
        return string.Join("&", map.Select(kvp => $"{kvp.Key}={kvp.Value}"));
    }
}
