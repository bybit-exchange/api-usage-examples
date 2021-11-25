using System;
using System.Text;
using System.Security.Cryptography;

public class Test
{
    
    public static string CreateSignature(string secret, string message)
    {
        var signatureBytes = Hmacsha256(Encoding.UTF8.GetBytes(secret), Encoding.UTF8.GetBytes(message));

        return ByteArrayToString(signatureBytes);
    }
    
    private static byte[] Hmacsha256(byte[] keyByte, byte[] messageBytes)
    {
        using (var hash = new HMACSHA256(keyByte))
        {
            return hash.ComputeHash(messageBytes);
        }
    }

    
    public static string ByteArrayToString(byte[] ba)
    {
        var hex = new StringBuilder(ba.Length * 2);

        foreach (var b in ba)
        {
            hex.AppendFormat("{0:x2}", b);
        }
        return hex.ToString();
    }
    
	public static void Main()
	{
		string secret = "t7T0YlFnYXk0Fx3JswQsDrViLg1Gh3DUU5Mr";
		// Note bool should be lowercase while generating sign string
		string paramstr = "api_key=B2Rou0PLPpGqcU0Vu2&side=Buy&symbol=BTCUSD&qty=100&price=3001.5&time_in_force=GoodTillCancel&order_type=Limit&reduce_only=true&timestamp=1542434791000";
		Console.WriteLine(CreateSignature(secret, paramstr));// 52a6a6fc4da6d395baa8daccd21eb903eb47c19c03112cc2c80b005f2d181aef
	}

}