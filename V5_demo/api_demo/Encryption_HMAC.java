import com.alibaba.fastjson.JSON;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Encryption {
    private static final String API_KEY = "XXXXXXXXXX";
    private static final String API_SECRET = "XXXXXXXXXX";
    private static final String TIMESTAMP = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());
    private static final String RECV_WINDOW = "5000";

    public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeyException {
        Encryption encryption = new Encryption();
        encryption.placeOrder();
//        encryption.getOpenOrder();
    }

    public void placeOrder() throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("category", "linear");
        parameters.put("symbol", "BTCUSDT");
        parameters.put("side", "Buy");
        parameters.put("positionIdx", 0);
        parameters.put("orderType", "Limit");
        parameters.put("qty", "0.001");
        parameters.put("price", "18900");
        parameters.put("timeInForce", "GTC");

        String signature = generatePostSignature(parameters);
        String jsonPayload = JSON.toJSONString(parameters);

        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json");
        Request request = new Request.Builder()
                .url("https://api-testnet.bybit.com/v5/order/create")
                .post(RequestBody.create(mediaType, jsonPayload))
                .addHeader("X-BAPI-API-KEY", API_KEY)
                .addHeader("X-BAPI-SIGN", signature)
                .addHeader("X-BAPI-SIGN-TYPE", "2")
                .addHeader("X-BAPI-TIMESTAMP", TIMESTAMP)
                .addHeader("X-BAPI-RECV-WINDOW", RECV_WINDOW)
                .addHeader("Content-Type", "application/json")
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

    public void getOpenOrder() throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("category", "linear");
        parameters.put("symbol", "BTCUSDT");
        parameters.put("settleCoin", "USDT");

        String signature = generateGetSignature(parameters);
        StringBuilder queryString = generateQueryString(parameters);

        OkHttpClient client = new OkHttpClient.Builder().build();
        Request request = new Request.Builder()
                .url("https://api-testnet.bybit.com/v5/order/realtime?" + queryString)
                .get()
                .addHeader("X-BAPI-API-KEY", API_KEY)
                .addHeader("X-BAPI-SIGN", signature)
                .addHeader("X-BAPI-SIGN-TYPE", "2")
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

    private static String generatePostSignature(Map<String, Object> parameters) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(API_SECRET.getBytes(), "HmacSHA256");
        sha256Hmac.init(secretKey);

        String paramJson = JSON.toJSONString(parameters);
        String payload = TIMESTAMP + API_KEY + RECV_WINDOW + paramJson;
        return bytesToHex(sha256Hmac.doFinal(payload.getBytes()));
    }

    private static String generateGetSignature(Map<String, Object> parameters) throws NoSuchAlgorithmException, InvalidKeyException {
        StringBuilder queryString = generateQueryString(parameters);
        String payload = TIMESTAMP + API_KEY + RECV_WINDOW + queryString;

        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(API_SECRET.getBytes(), "HmacSHA256");
        sha256Hmac.init(secretKey);
        return bytesToHex(sha256Hmac.doFinal(payload.getBytes()));
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte value : hash) {
            String hex = Integer.toHexString(0xff & value);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
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
}
