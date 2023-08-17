using System.Net.WebSockets;
using System.Text;
using Newtonsoft.Json.Linq;
using System.Security.Cryptography;
using Newtonsoft.Json;

Connect().Wait();

async Task Connect()
{
    using var ws = new ClientWebSocket();
    await ws.ConnectAsync(new Uri("wss://stream-testnet.bybit.com/v5/private"), CancellationToken.None);

    Console.WriteLine("Connected.");

    var cts = new CancellationTokenSource();

    _ = Task.Run(() => Receive(ws));
    _ = Task.Run(() => Ping(ws, cts.Token));

    await OnOpen(ws);

    await Task.Delay(Timeout.Infinite, cts.Token);
}

async Task OnOpen(ClientWebSocket ws)
{
    Console.WriteLine("opened");
    await SendAuth(ws);
    await SendSubscription(ws);
}

async Task SendAuth(ClientWebSocket ws)
{
    string key = "xxxxxxxxxx";
    string secret = "xxxxxxxxxxxxxxxx";
    long expires = DateTimeOffset.Now.ToUnixTimeMilliseconds() + 10000;
    string _val = $"GET/realtime{expires}";

    var hmacsha256 = new HMACSHA256(Encoding.UTF8.GetBytes(secret));
    var hash = hmacsha256.ComputeHash(Encoding.UTF8.GetBytes(_val));
    string signature = BitConverter.ToString(hash).Replace("-", "").ToLower();

    var authMessage = new { op = "auth", args = new object[] { key, expires, signature } };
    string authMessageJson = JsonConvert.SerializeObject(authMessage);
    Console.WriteLine(authMessageJson);
    await ws.SendAsync(new ArraySegment<byte>(Encoding.UTF8.GetBytes(authMessageJson)), WebSocketMessageType.Text, true, CancellationToken.None);
}

async Task SendSubscription(ClientWebSocket ws)
{
    string topic = "order";
    var subMessage = new { op = "subscribe", args = new string[] { topic } };
    string subMessageJson = JsonConvert.SerializeObject(subMessage);
    Console.WriteLine("send subscription " + topic);
    await ws.SendAsync(new ArraySegment<byte>(Encoding.UTF8.GetBytes(subMessageJson)), WebSocketMessageType.Text, true, CancellationToken.None);
}

async Task Receive(ClientWebSocket ws)
{
    var buffer = new ArraySegment<byte>(new byte[8192]);
    while (true)
    {
        var result = await ws.ReceiveAsync(buffer, CancellationToken.None);
        if (result.MessageType == WebSocketMessageType.Close)
        {
            Console.WriteLine("### about to close ###");
            break;
        }
        else if (result.MessageType == WebSocketMessageType.Text)
        {
            if (buffer.Array != null)
            {
                var message = Encoding.UTF8.GetString(buffer.Array, 0, result.Count);
                var data = JsonConvert.DeserializeObject<JObject>(message);
                if (data != null)
                {
                    Console.WriteLine(data.ToString());
                }
            }
        }
    }
}

async Task Ping(ClientWebSocket ws, CancellationToken token)
{
    while (!token.IsCancellationRequested)
    {
        await Task.Delay(TimeSpan.FromSeconds(20), token);
        if (ws.State == WebSocketState.Open)
        {
            await ws.SendAsync(new ArraySegment<byte>(Encoding.UTF8.GetBytes("ping")), WebSocketMessageType.Text, true, CancellationToken.None);
            Console.WriteLine("ping sent");
        }
    }
}

