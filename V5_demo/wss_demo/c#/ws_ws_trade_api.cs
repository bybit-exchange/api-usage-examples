using Newtonsoft.Json;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Text;

class Program
{
    private static readonly ClientWebSocket ws = new ClientWebSocket();

    static async Task Main(string[] args)
    {
        await ConnectAsync(new Uri("wss://stream-testnet.bybit.com/v5/trade"));
        _ = ReceiveAsync();
        await SendPingAsync(); // Regularly send ping messages
        Console.ReadKey();
        await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "Closing", CancellationToken.None);
    }

    static async Task ReceiveAsync()
    {
        var buffer = new byte[2048];
        while (ws.State == WebSocketState.Open)
        {
            var result = await ws.ReceiveAsync(new ArraySegment<byte>(buffer), CancellationToken.None);
            if (result.MessageType == WebSocketMessageType.Close)
            {
                await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, string.Empty, CancellationToken.None);
            }
            else
            {
                var message = Encoding.UTF8.GetString(buffer, 0, result.Count);
                Console.WriteLine($"Message received: {message}");
            }
        }
    }

    static async Task ConnectAsync(Uri uri)
    {
        await ws.ConnectAsync(uri, CancellationToken.None);
        Console.WriteLine("Connected.");
        await SendAuthAsync();
        await SendSubscriptionAsync();
    }

    static async Task SendPingAsync()
    {
        while (ws.State == WebSocketState.Open)
        {
            Console.WriteLine("Ping sent");
            await ws.SendAsync(new ArraySegment<byte>(Encoding.UTF8.GetBytes("{\"op\":\"ping\"}")), WebSocketMessageType.Text, true, CancellationToken.None);
            await Task.Delay(20000);
        }
    }

    static async Task SendAuthAsync()
    {
        string apiKey = "xxxxx";
        string apiSecret = "xxxxxx";
        long expires = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() + 10000;
        string signature = CreateSignature(apiSecret, $"GET/realtime{expires}");

        var authMap = new
        {
            req_id = Guid.NewGuid().ToString(),
            op = "auth",
            args = new List<object> { apiKey, expires, signature }
        };

        var authJson = JsonConvert.SerializeObject(authMap);
        Console.WriteLine("Auth Message: " + authJson);
        await ws.SendAsync(new ArraySegment<byte>(Encoding.UTF8.GetBytes(authJson)), WebSocketMessageType.Text, true, CancellationToken.None);
    }

    static async Task SendSubscriptionAsync()
    {
        var subMessage = new
        {
            reqId = Guid.NewGuid().ToString(),
            headers = new Dictionary<string, string>
            {
                { "X-BAPI-TIMESTAMP", DateTimeOffset.UtcNow.ToUnixTimeMilliseconds().ToString() },
                { "X-BAPI-RECV-WINDOW", "8000" }
            },
            op = "order.create",
            args = new List<Dictionary<string, object>>
            {
                new Dictionary<string, object>
                {
                    { "symbol", "XRPUSDT" },
                    { "side", "Buy" },
                    { "orderType", "Market" },
                    { "qty", "10" },
                    { "category", "spot" },
                }
            }
        };

        var json = JsonConvert.SerializeObject(subMessage);
        Console.WriteLine("Trade Message: " + json);
        await ws.SendAsync(new ArraySegment<byte>(Encoding.UTF8.GetBytes(json)), WebSocketMessageType.Text, true, CancellationToken.None);
    }

    static string CreateSignature(string secret, string message)
    {
        var encoding = new ASCIIEncoding();
        byte[] keyByte = encoding.GetBytes(secret);
        byte[] messageBytes = encoding.GetBytes(message);
        using (var hmacsha256 = new HMACSHA256(keyByte))
        {
            byte[] hashmessage = hmacsha256.ComputeHash(messageBytes);
            return BitConverter.ToString(hashmessage).Replace("-", "").ToLower();
        }
    }
}