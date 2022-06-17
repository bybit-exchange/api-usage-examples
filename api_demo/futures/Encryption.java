package com.bybit.techops.futures;

import com.alibaba.fastjson.JSON;
import okhttp3.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * a sample for how to create&get an active order for USDT Perpetual
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
     * POST: place an active linear perpetual order
     */
    private void placeActiveOrder() throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> map = new TreeMap(
            new Comparator<String>() {
                @Override
                // sort paramKey in A-Z
                public int compare(String o1, String o2) {
                    return o1.compareTo(o2);
            }
        });
        map.put("api_key", API_KEY);
        map.put("timestamp", TIMESTAMP);
        map.put("side", "Buy");
        map.put("symbol", "EOSUSDT");
        map.put("order_type", "Limit");
        map.put("qty", "50");
        map.put("price", "1.97");
        map.put("time_in_force", "GoodTillCancel");
        map.put("take_profit", "1.6");
        map.put("stop_loss", "0.8");
        map.put("reduce_only", false);
        map.put("close_on_trigger", false);
        map.put("recv_window", RECV_WINDOW);
        String signature = genSign(map);
        map.put("sign", signature);

        // use json request body to send the request
        String jsonMap = JSON.toJSONString(map);
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body= RequestBody.create(mediaType, jsonMap);
        Request request = new Request.Builder()
                .url("https://api-testnet.bybit.com/private/linear/order/create")
                .method("post", body)
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
     * GET: get the active order list
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    public void getActiveOrder() throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> map = new TreeMap<>(new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                return s1.compareTo(s2);
            }
        });
        map.put("api_key", API_KEY);
        map.put("timestamp", TIMESTAMP);
        map.put("symbol", "BITUSDT");
        map.put("order_status", "Created,New,Filled,Cancelled");
        String signature = genSign(map);
        map.put("sign", signature);
        Set<Map.Entry<String, Object>> entries = map.entrySet();
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<String, Object> e : entries ) {
            sb.append(e.getKey())
                    .append("=")
                    .append(e.getValue())
                    .append("&");
        }
        sb.deleteCharAt(sb.length() - 1);

        OkHttpClient client = new OkHttpClient().newBuilder().build();
        Request request = new Request.Builder()
                .url("https://api-testnet.bybit.com/private/linear/order/list?" + sb)
                .get()
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
     * @param params: TreeMap input parameters exclude "sign"
     * @return signature used to be a parameter in the request body
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    private static String genSign(Map<String, Object> params) throws NoSuchAlgorithmException, InvalidKeyException {
        Set<String> keySet = params.keySet();
        Iterator<String> iter = keySet.iterator();
        StringBuilder sb = new StringBuilder();
        while (iter.hasNext()) {
            String key = iter.next();
            sb.append(key)
                .append("=")
                .append(params.get(key))
                .append("&");
        }
        sb.deleteCharAt(sb.length() - 1);
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(API_SECRET.getBytes(), "HmacSHA256");
        sha256_HMAC.init(secret_key);

        return bytesToHex(sha256_HMAC.doFinal(sb.toString().getBytes()));
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


