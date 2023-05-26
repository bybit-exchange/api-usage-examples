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


/**
 * a sample for how to create & get an order for contract v5 - Linear perp
 */
public class Encryption {
    final static String API_KEY = "XXXXXXXXXX";
    final static String API_SECRET = "XXXXXXXXXX";
    final static String TIMESTAMP = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());
    final static String RECV_WINDOW = "5000";

    public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeyException {
        Encryption encryptionTest = new Encryption();

        encryptionTest.placeOrder();

//        encryptionTest.getOpenOrder();
    }

    /**
     * POST: place a Linear perp order - contract v5
     */
    public void placeOrder() throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> map = new HashMap<>();
        map.put("category","linear");
        map.put("symbol", "BTCUSDT");
        map.put("side", "Buy");
        map.put("positionIdx", 0);
        map.put("orderType", "Limit");
        map.put("qty", "0.001");
        map.put("price", "18900");
        map.put("timeInForce", "GTC");

        String signature = genPostSign(map);
        String jsonMap = JSON.toJSONString(map);

        OkHttpClient client = new OkHttpClient().newBuilder().build();
        MediaType mediaType = MediaType.parse("application/json");
        Request request = new Request.Builder()
                .url("https://api-testnet.bybit.com/v5/order/create")
                .post(RequestBody.create(mediaType, jsonMap))
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
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * GET: query unfilled order
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    public void getOpenOrder() throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> map = new HashMap<>();
        map.put("category","linear");
        map.put("symbol", "BTCUSDT");
        map.put("settleCoin", "USDT");

        String signature = genGetSign(map);
        StringBuilder sb = genQueryStr(map);

        OkHttpClient client = new OkHttpClient().newBuilder().build();
        Request request = new Request.Builder()
                .url("https://api-testnet.bybit.com/v5/order/realtime?" + sb)
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
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * The way to generate the sign for POST requests
     * @param params: Map input parameters
     * @return signature used to be a parameter in the header
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    private static String genPostSign(Map<String, Object> params) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(API_SECRET.getBytes(), "HmacSHA256");
        sha256_HMAC.init(secret_key);

        String paramJson = JSON.toJSONString(params);
        String sb = TIMESTAMP + API_KEY + RECV_WINDOW + paramJson;
        return bytesToHex(sha256_HMAC.doFinal(sb.getBytes()));
    }

    /**
     * The way to generate the sign for GET requests
     * @param params: Map input parameters
     * @return signature used to be a parameter in the header
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    private static String genGetSign(Map<String, Object> params) throws NoSuchAlgorithmException, InvalidKeyException {
        StringBuilder sb = genQueryStr(params);
        String queryStr = TIMESTAMP + API_KEY + RECV_WINDOW + sb;

        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(API_SECRET.getBytes(), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        return bytesToHex(sha256_HMAC.doFinal(queryStr.getBytes()));
    }

    /**
     * To convert bytes to hex
     * @param hash
     * @return hex string
     */
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
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
}



