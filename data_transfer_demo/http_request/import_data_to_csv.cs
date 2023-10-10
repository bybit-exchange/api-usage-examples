using System.Globalization;
using System.Net.Http.Json;
using System.Reflection.Metadata;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using CsvHelper;

namespace BybitApiExample
{
    class Program
    {
        private const string ApiKey = "xxxxxxxxxxxxxxxx";
        private const string SecretKey = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        private const string Url = "https://api-testnet.bybit.com";

        static async Task Main(string[] args)
        {
            var endPoint = "/v5/market/tickers";
            var payload = "category=linear";
            await HttpRequest(endPoint, HttpMethod.Get, payload);
            Console.WriteLine("Done!");
        }

        private static async Task HttpRequest(string endPoint, HttpMethod method, string payload)
        {
            using var httpClient = new HttpClient { BaseAddress = new Uri(Url) };

            var timeStamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds().ToString();
            var signature = GenerateSignature(payload, timeStamp);

            httpClient.DefaultRequestHeaders.Add("X-BAPI-API-KEY", ApiKey);
            httpClient.DefaultRequestHeaders.Add("X-BAPI-SIGN", signature);
            httpClient.DefaultRequestHeaders.Add("X-BAPI-SIGN-TYPE", "2");
            httpClient.DefaultRequestHeaders.Add("X-BAPI-TIMESTAMP", timeStamp);
            httpClient.DefaultRequestHeaders.Add("X-BAPI-RECV-WINDOW", "5000");

            HttpResponseMessage response;
            if (method == HttpMethod.Post)
            {
                httpClient.DefaultRequestHeaders.Add("Content-Type", "application/json");
                var content = new StringContent(payload, Encoding.UTF8, "application/json");
                response = await httpClient.PostAsync(endPoint, content);
            }
            else
            {
                response = await httpClient.GetAsync(endPoint + "?" + payload);
            }

            var responseBody = await response.Content.ReadAsStringAsync();
            Console.WriteLine(responseBody);

            var responseObject = JsonSerializer.Deserialize<ApiResponse>(responseBody);
            if (responseObject?.Result?.List != null && responseObject.Result.List.Count > 0)
            {
                using var writer = new StreamWriter("BybitMarketDataTicker.csv");
                using var csv = new CsvWriter(writer, CultureInfo.InvariantCulture);
                csv.WriteRecords(responseObject.Result.List);
            }
        }

        private static string GenerateSignature(string payload, string timeStamp)
        {
            var paramStr = $"{timeStamp}{ApiKey}5000{payload}";
            using var hmacsha256 = new HMACSHA256(Encoding.UTF8.GetBytes(SecretKey));
            var hashBytes = hmacsha256.ComputeHash(Encoding.UTF8.GetBytes(paramStr));
            return BitConverter.ToString(hashBytes).Replace("-", "").ToLower();
        }

        public class ApiResponse
        {
            [JsonPropertyName("result")]
            public ApiResult? Result { get; set; }
        }

        public class ApiResult
        {
            [JsonPropertyName("list")]
            public List<MarketData>? List { get; set; }
        }

        public class MarketData
        {
            [JsonPropertyName("symbol")]
            public string? Symbol { get; set; }

            [JsonPropertyName("lastPrice")]
            public string? LastPrice { get; set; }

            [JsonPropertyName("indexPrice")]
            public string? IndexPrice { get; set; }

            [JsonPropertyName("markPrice")]
            public string? MarkPrice { get; set; }

            [JsonPropertyName("prevPrice24h")]
            public string? PrevPrice24h { get; set; }

            [JsonPropertyName("price24hPcnt")]
            public string? Price24hPcnt { get; set; }

            [JsonPropertyName("highPrice24h")]
            public string? HighPrice24h { get; set; }

            [JsonPropertyName("lowPrice24h")]
            public string? LowPrice24h { get; set; }

            [JsonPropertyName("prevPrice1h")]
            public string? PrevPrice1h { get; set; }

            [JsonPropertyName("openInterest")]
            public string? OpenInterest { get; set; }

            [JsonPropertyName("openInterestValue")]
            public string? OpenInterestValue { get; set; }

            [JsonPropertyName("turnover24h")]
            public string? Turnover24h { get; set; }

            [JsonPropertyName("volume24h")]
            public string? Volume24h { get; set; }

            [JsonPropertyName("fundingRate")]
            public string? FundingRate { get; set; }

            [JsonPropertyName("nextFundingTime")]
            public string? NextFundingTime { get; set; }

            [JsonPropertyName("predictedDeliveryPrice")]
            public string? PredictedDeliveryPrice { get; set; }

            [JsonPropertyName("basisRate")]
            public string? BasisRate { get; set; }

            [JsonPropertyName("deliveryFeeRate")]
            public string? DeliveryFeeRate { get; set; }

            [JsonPropertyName("deliveryTime")]
            public string? DeliveryTime { get; set; }

            [JsonPropertyName("ask1Size")]
            public string? Ask1Size { get; set; }

            [JsonPropertyName("bid1Price")]
            public string? Bid1Price { get; set; }

            [JsonPropertyName("ask1Price")]
            public string? Ask1Price { get; set; }

            [JsonPropertyName("bid1Size")]
            public string? Bid1Size { get; set; }

            [JsonPropertyName("basis")]
            public string? Basis { get; set; }
        }

    }
}
