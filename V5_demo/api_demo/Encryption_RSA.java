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

public class Encryption {
    private static final String API_KEY = "XXXXXXXXXX";
    private static final String TIMESTAMP = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());
    private static final String RECV_WINDOW = "5000";

    public static void main(String[] args) {
        Encryption encryption = new Encryption();
        encryption.placeOrder();
//        encryption.getOpenOrder();
    }

    public void placeOrder() {
        String keyPem = "-----BEGIN PRIVATE KEY-----\n"
                + "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n"
                + "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n"
                + "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n"
                + "R4gmvtpPbI3J2XKwzUXNDdGbXSZD/fONOfoyO5w05Ci3hDOyZtxFI33onEwGiZfp\n"
                + "woeYLAB4Q8QhVRobi3NOC6BBmaRBYw6KWsx+qL6QoQQakJZAFtlZuVYUxXzMMU+M\n"
                + "vDPvszOl3cFIO3UWxrUCqriM1b86DH6f7WrkP80nzTfWG5TMlzM6zYK0rgUiTrqH\n"
                + "cDMsqT+ZAgMBAAECggEAAaUVGt8v4TB+8lTqvoprQJxtp85FQLpc78+v9Tw1sU9p\n"
                + "Tz5gGR6Gra6Vs4FoQnmQdRt1bQrYxZQcNY23PlNLxYrtTz4cYvCYtEf0ivP+6igI\n"
                + "ZExtwEZso998VzZNB+CcTp6vSXobtTq+Lzg5SbuxeHLnG30Lr6gHBaxxbfuCbIT9\n"
                + "v/+Co+og0WqSmjLNuRu0gzfbK0X4Xl/fO3yuJxIr8ofSt2FW45Q/k+JWR9FHUJyN\n"
                + "BQ0AxaNg9/DlLwwP0jlsQEcSv+i8VCOp9K2TYvv75uUSeq4AujdJjdIw/j9BQpDf\n"
                + "1op59uGzjNgywsUptsePIObZXID+QdrxwGIuBHgcgQKBgQD6LrMI64706qf+4Eb9\n"
                + "D6fXVxlbucyo7TZn0cZG8Cf/Dr7eg7hs6kK41+ZiMB5trfJ/1d6L65sDGmb/+KTF\n"
                + "AGV1FiHpyx9hvGoIupEBEyeS7pH10eSk69THWrAgKW60Xeve1oCsRin+B9UwYtRz\n"
                + "NL8o/+9W54yqLaRgeD7sRaETMQKBgQC81T25vssikzBSkrHCacYig5JCXmTce9NU\n"
                + "L5BZzuCv5zWXRI95Q7FmsqlFY2BsByI1uiwxZJiEOj8IWjjxvd1CNj1ByQd7B8Sb\n"
                + "ALQHetdel/D51mrbV1OyMdNsDuZJu7NiwDV92aVNiiKmTE6epP81qkFpwLQ19XQe\n"
                + "YFx85flI6QKBgQDTSWVCf034YcUHZ/oL9pDVOGXeJYhGki+EdpFxj5j3u0hPPAch\n"
                + "VKaM3Slgeyr3jhRjCggtOwlrEX0zaJYfGjqVK9/wRu91513ViVq1AaxGVt1GMcFb\n"
                + "1x+YTWq5fsRT544wYA/Dbm5Ab/UILC8oLL/UrHFBf8Q4ZNuR7XuWpydlwQKBgBeZ\n"
                + "FnOl8kDJ4BoRlwFSsp4Rjy+YGEatesVkhEeU4ONao4nZ2Ywv93V8EkdHmf8mDRJl\n"
                + "x6wMhDrSBJqIm+Ep9wKVQKZ99t9bIyizt8vPgCakGks+jnAGw8DbFS7F1eWU/V/z\n"
                + "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n"
                + "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n"
                + "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n"
                + "6DHWbgI1hfX8/y8pKHJkkyXn\n"
                + "-----END PRIVATE KEY-----\n";

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("category", "linear");
        parameters.put("symbol", "BTCUSDT");
        parameters.put("side", "Buy");
        parameters.put("positionIdx", 0);
        parameters.put("orderType", "Limit");
        parameters.put("qty", "0.001");
        parameters.put("price", "18900");
        parameters.put("timeInForce", "GTC");

        String jsonPayload = JSON.toJSONString(parameters);
        String plainText = TIMESTAMP + API_KEY + RECV_WINDOW + jsonPayload;
        String signature = generateSignature(keyPem, plainText);

        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json");
        Request request = new Request.Builder()
                .url("https://api-testnet.bybit.com/v5/order/create")
                .post(RequestBody.create(mediaType, jsonPayload))
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
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public void getOpenOrder() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("category", "linear");
        parameters.put("symbol", "BTCUSDT");
        parameters.put("settleCoin", "USDT");

        StringBuilder queryString = generateQueryString(parameters);
        String plainText = TIMESTAMP + API_KEY + RECV_WINDOW + queryString;
        String signature = generateSignature("/Users/xxxx/xxxxxx.pem", plainText);

        OkHttpClient client = new OkHttpClient.Builder().build();
        Request request = new Request.Builder()
                .url("https://api-testnet.bybit.com/v5/order/realtime?" + queryString)
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
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private static StringBuilder generateQueryString(Map<String, Object> parameters) {
        Set<String> keySet = parameters.keySet();
        Iterator<String> iterator = keySet.iterator();
        StringBuilder query = new StringBuilder();
        while (iterator.hasNext()) {
            String key = iterator.next();
            query.append(key).append("=").append(parameters.get(key)).append("&");
        }
        query.deleteCharAt(query.length() - 1);
        return query;
    }

    private String generateSignature(String privateKey, String data) {
        RSAPrivateKey rsaPrivateKey;
        try {
            String privateKeyPem = privateKey;
            if (new File(privateKey).exists()) {
                privateKeyPem = new String(Files.readAllBytes(Paths.get(privateKey)));
            }
            rsaPrivateKey = parsePrivateKey(privateKeyPem);
        } catch (IOException exception) {
            throw new RuntimeException("Unable to find/read private key at given file path", exception);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to parse RSA private key", exception);
        }

        try {
            Signature sha256Sign = Signature.getInstance("SHA256withRSA");
            sha256Sign.initSign(rsaPrivateKey);
            sha256Sign.update(data.getBytes());
            byte[] signature = sha256Sign.sign();
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to calculate rsa-sha256");
        }
    }

    private RSAPrivateKey parsePrivateKey(String privateKeyPem) throws Exception {
        String parsedPem = privateKeyPem.replace("\n", "").trim();
        parsedPem = parsedPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "");

        byte[] encoded = Base64.getDecoder().decode(parsedPem);
        PKCS8EncodedKeySpec encodedKeySpec = new PKCS8EncodedKeySpec(encoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(encodedKeySpec);
    }
}
