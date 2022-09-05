package com.bybit.techops.spot;

import okhttp3.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * a sample for how to create&get an active order for SPOT
 */
public class Encryption {
    final static String API_KEY = "your api key";
    final static String API_SECRET = "your api secret";
    final static String RECV_WINDOW = "10000";
    final static String TIMESTAMP = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());

    public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeyException {
        Encryption encryptionTest = new Encryption();

//        encryptionTest.placeActiveOrder();

        encryptionTest.getActiveOrder();
    }

    /**
     * POST: place an active Spot order
     */
    private void placeActiveOrder() throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> map = new TreeMap<>(
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
        map.put("symbol", "DOGEUSDT");
        map.put("type", "Market");
        map.put("qty", "50");
        map.put("timeInForce", "GTC");
        map.put("recvWindow", RECV_WINDOW);
        map.put("orderLinkId", "spot30001");
        String query = genQueryString(map);

        //POST wtih url queryString format
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        MediaType mediaType = MediaType.parse("text/plain");
        RequestBody body = RequestBody.create(mediaType, "");
        Request request = new Request.Builder()
                .url("https://api-testnet.bybit.com/spot/v1/order?" + query)
                .method("post", body)
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
     * GET: get the active order
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
        map.put("recvWindow", RECV_WINDOW);
        map.put("orderLinkId", "spot30001");
        String query = genQueryString(map);

        OkHttpClient client = new OkHttpClient().newBuilder().build();
        Request request = new Request.Builder()
                .url("https://api-testnet.bybit.com/spot/v1/order?" + query)
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
     * To generate the queryString with all your parameters
     * @param params: TreeMap input parameters
     * @return queryString
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    private static String genQueryString(Map<String, Object> params) throws NoSuchAlgorithmException, InvalidKeyException {
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

        return sb + "&sign=" + bytesToHex(sha256_HMAC.doFinal(sb.toString().getBytes()));
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


