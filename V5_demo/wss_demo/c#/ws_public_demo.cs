using System.Net.WebSockets;
using System.Text;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;


string topic = "orderbook.50.BTCUSDT";

await ConnectWS();

async Task ConnectWS()
{
    using (var ws = new ClientWebSocket())
    {
        await ws.ConnectAsync(new Uri("wss://stream-testnet.bybit.com/v5/public/linear"), CancellationToken.None);
        await OnOpen(ws);
        await Receive(ws);
    }
}

async Task OnOpen(ClientWebSocket ws)
{
    Console.WriteLine("opened");
    var subscribeRequest = JsonConvert.SerializeObject(new
    {
        op = "subscribe",
        args = new string[] { topic }
    });
    await ws.SendAsync(Encoding.UTF8.GetBytes(subscribeRequest), WebSocketMessageType.Text, true, CancellationToken.None);
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


