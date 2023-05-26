import com.alibaba.fastjson.JSON;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * a sample for how to create & get an order for contract v5 - Linear perp
 */
public class Encryption {
    final static String API_KEY = "XXXXXXXXXX";
    final static String TIMESTAMP = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());
    final static String RECV_WINDOW = "5000";

    public static void main(String[] args){
        Encryption encryptionTest = new Encryption();

        encryptionTest.placeOrder();

//        encryptionTest.getOpenOrder();
    }

    /**
     * POST: place a Linear perp order - contract v5
     */
    public void placeOrder(){
        String keyPem = "-----BEGIN PRIVATE KEY-----\n" +
                "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
                "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
                "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
                "R4gmvtpPbI3J2XKwzUXNDdGbXSZD/fONOfoyO5w05Ci3hDOyZtxFI33onEwGiZfp\n" +
                "woeYLAB4Q8QhVRobi3NOC6BBmaRBYw6KWsx+qL6QoQQakJZAFtlZuVYUxXzMMU+M\n" +
                "vDPvszOl3cFIO3UWxrUCqriM1b86DH6f7WrkP80nzTfWG5TMlzM6zYK0rgUiTrqH\n" +
                "cDMsqT+ZAgMBAAECggEAAaUVGt8v4TB+8lTqvoprQJxtp85FQLpc78+v9Tw1sU9p\n" +
                "Tz5gGR6Gra6Vs4FoQnmQdRt1bQrYxZQcNY23PlNLxYrtTz4cYvCYtEf0ivP+6igI\n" +
                "ZExtwEZso998VzZNB+CcTp6vSXobtTq+Lzg5SbuxeHLnG30Lr6gHBaxxbfuCbIT9\n" +
                "v/+Co+og0WqSmjLNuRu0gzfbK0X4Xl/fO3yuJxIr8ofSt2FW45Q/k+JWR9FHUJyN\n" +
                "BQ0AxaNg9/DlLwwP0jlsQEcSv+i8VCOp9K2TYvv75uUSeq4AujdJjdIw/j9BQpDf\n" +
                "1op59uGzjNgywsUptsePIObZXID+QdrxwGIuBHgcgQKBgQD6LrMI64706qf+4Eb9\n" +
                "D6fXVxlbucyo7TZn0cZG8Cf/Dr7eg7hs6kK41+ZiMB5trfJ/1d6L65sDGmb/+KTF\n" +
                "AGV1FiHpyx9hvGoIupEBEyeS7pH10eSk69THWrAgKW60Xeve1oCsRin+B9UwYtRz\n" +
                "NL8o/+9W54yqLaRgeD7sRaETMQKBgQC81T25vssikzBSkrHCacYig5JCXmTce9NU\n" +
                "L5BZzuCv5zWXRI95Q7FmsqlFY2BsByI1uiwxZJiEOj8IWjjxvd1CNj1ByQd7B8Sb\n" +
                "ALQHetdel/D51mrbV1OyMdNsDuZJu7NiwDV92aVNiiKmTE6epP81qkFpwLQ19XQe\n" +
                "YFx85flI6QKBgQDTSWVCf034YcUHZ/oL9pDVOGXeJYhGki+EdpFxj5j3u0hPPAch\n" +
                "VKaM3Slgeyr3jhRjCggtOwlrEX0zaJYfGjqVK9/wRu91513ViVq1AaxGVt1GMcFb\n" +
                "1x+YTWq5fsRT544wYA/Dbm5Ab/UILC8oLL/UrHFBf8Q4ZNuR7XuWpydlwQKBgBeZ\n" +
                "FnOl8kDJ4BoRlwFSsp4Rjy+YGEatesVkhEeU4ONao4nZ2Ywv93V8EkdHmf8mDRJl\n" +
                "x6wMhDrSBJqIm+Ep9wKVQKZ99t9bIyizt8vPgCakGks+jnAGw8DbFS7F1eWU/V/z\n" +
                "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
                "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
                "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
                "6DHWbgI1hfX8/y8pKHJkkyXn\n" +
                "-----END PRIVATE KEY-----\n";

        Map<String, Object> map = new HashMap<>();
        map.put("category","linear");
        map.put("symbol", "BTCUSDT");
        map.put("side", "Buy");
        map.put("positionIdx", 0);
        map.put("orderType", "Limit");
        map.put("qty", "0.001");
        map.put("price", "18900");
        map.put("timeInForce", "GTC");

        String jsonMap = JSON.toJSONString(map);

        String plainText = TIMESTAMP + API_KEY + RECV_WINDOW + jsonMap;

        String signature = genSignature(keyPem, plainText);

        OkHttpClient client = new OkHttpClient().newBuilder().build();
        MediaType mediaType = MediaType.parse("application/json");
        Request request = new Request.Builder()
                .url("https://api-testnet.bybit.com/v5/order/create")
                .post(RequestBody.create(mediaType, jsonMap))
                .addHeader("X-BAPI-API-KEY", API_KEY)
                .addHeader("X-BAPI-SIGN", signature)
                .addHeader("X-BAPI-TIMESTAMP", TIMESTAMP)
                .addHeader("X-BAPI-RECV-WINDOW", RECV_WINDOW)
                .addHeader("Content-Type", "application/json")
                .build();
        System.out.println(request.headers());
        System.out.println(request);
        Call call = client.newCall(request);
        try {
            Response response = call.execute();
            assert response.body() != null;
            System.out.println(response.body().string());
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * GET: query unfilled order
     */
    public void getOpenOrder() {
        Map<String, Object> map = new HashMap<>();

        map.put("category","linear");
        map.put("symbol", "BTCUSDT");
        map.put("settleCoin", "USDT");

        StringBuilder sb = genQueryStr(map);

        String plainText = TIMESTAMP + API_KEY + RECV_WINDOW + sb;

        String signature = genSignature("/Users/xxxx/xxxxxx.pem", plainText);

        OkHttpClient client = new OkHttpClient().newBuilder().build();
        Request request = new Request.Builder()
                .url("https://api-testnet.bybit.com/v5/order/realtime?" + sb)
                .get()
                .addHeader("X-BAPI-API-KEY", API_KEY)
                .addHeader("X-BAPI-SIGN", signature)
                .addHeader("X-BAPI-TIMESTAMP", TIMESTAMP)
                .addHeader("X-BAPI-RECV-WINDOW", RECV_WINDOW)
                .build();
        Call call = client.newCall(request);
        try {
            Response response = call.execute();
            assert response.body() != null;
            System.out.println(response.body().string());
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * To generate query string for GET requests
     * @param map
     * @return
     */
    private static StringBuilder genQueryStr(Map<String, Object> map) {
        Set<String> keySet = map.keySet();
        Iterator<String> iter = keySet.iterator();
        StringBuilder sb = new StringBuilder();
        while (iter.hasNext()) {
            String key = iter.next();
            sb.append(key)
                    .append("=")
                    .append(map.get(key))
                    .append("&");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb;
    }

    /**
     *
     * @param privateKey: either absolute pem path or original private key pem string
     * @param data: plain text
     * @return
     */
    private String genSignature(String privateKey, String data) {
        RSAPrivateKey rsaPrivateKey;
        try {
            String privateKeyPem = privateKey;
            if (new File(privateKey).exists()) {
                privateKeyPem = new String(Files.readAllBytes(Paths.get(privateKey)));
            }
            rsaPrivateKey = parsePrivateKey(privateKeyPem);
        } catch (IOException e) {
            throw new RuntimeException("Unable to find/read private key at given file path", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse RSA private key", e);
        }
        try {
            Signature sha256Sign = Signature.getInstance("SHA256withRSA");
            sha256Sign.initSign(rsaPrivateKey);
            sha256Sign.update(data.getBytes());

            byte[] signature = sha256Sign.sign();

            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate rsa-sha256");
        }
    }

    private RSAPrivateKey parsePrivateKey(String privateKeyPem) throws Exception {
        // Private key in PKCS#8 standard
        String parsedPem = privateKeyPem.replace("\n", "").trim();
        parsedPem = parsedPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "");

        byte[] encoded = Base64.getDecoder().decode(parsedPem);
        PKCS8EncodedKeySpec encodedKeySpec;
        encodedKeySpec = new PKCS8EncodedKeySpec(encoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.generatePrivate(encodedKeySpec);
    }
}
