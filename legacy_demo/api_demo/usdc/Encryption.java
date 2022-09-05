package com.bybit.techops.usdc;

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
import java.util.Map;


/**
 * a sample for how to create&get an active order for USDC OPTION
 */
public class Encryption {
    final static String API_KEY = "your api key";
    final static String API_SECRET = "your api secret";
    final static String TIMESTAMP = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());
    final static String RECV_WINDOW = "10000";

    public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeyException {
        Encryption encryptionTest = new Encryption();

//        encryptionTest.placeActiveOrder();

        encryptionTest.getActiveOrder();
    }

    /**
     * POST: place an active usdc option order
     */
    private void placeActiveOrder() throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, String> map = new HashMap<>();
        map.put("side", "Buy");
        map.put("symbol", "BTC-13JUN22-28500-C");
        map.put("orderType", "Limit");
        map.put("orderQty", "5");
        map.put("orderPrice", "20");
        map.put("timeInForce", "GoodTillCancel");
        map.put("recvWindow", RECV_WINDOW);
        map.put("orderLinkId", "option30001");

        String signature = genSign(map);
        //POST use json request body to send the request
        String jsonMap = JSON.toJSONString(map);
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, jsonMap);
        Request request = new Request.Builder()
                .url("https://api-testnet.bybit.com/option/usdc/openapi/private/v1/place-order")
                .method("post", body)
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
     * POST: query unfilled/partial filler order
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    public void getActiveOrder() throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, String> map = new HashMap<>();

        map.put("category", "OPTION");
        map.put("orderLinkId", "option30001");

        String signature = genSign(map);
        String jsonMap = JSON.toJSONString(map);
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, jsonMap);
        Request request = new Request.Builder()
                .url("https://api-testnet.bybit.com/option/usdc/openapi/private/v1/query-active-orders")
                .method("post", body)
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
     * To generate the signature with all your parameters
     * the signature generation method is different from inverse/linear perpetual or spot
     * @param params: Map input parameters
     * @return signature used to be a parameter in the request header
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    private static String genSign(Map<String, String> params) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(API_SECRET.getBytes(), "HmacSHA256");
        sha256_HMAC.init(secret_key);

        String paramJson = JSON.toJSONString(params);
        String sb = timestamp + API_KEY + RECV_WINDOW + paramJson;
        return bytesToHex(sha256_HMAC.doFinal(sb.getBytes()));
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
}


